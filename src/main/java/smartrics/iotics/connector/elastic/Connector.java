package smartrics.iotics.connector.elastic;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.JsonObject;
import com.iotics.api.*;
import com.iotics.sdk.identity.SimpleConfig;
import com.iotics.sdk.identity.SimpleIdentityManager;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smartrics.iotics.space.IoticSpace;
import smartrics.iotics.space.grpc.AbstractLoggingStreamObserver;
import smartrics.iotics.space.grpc.FeedDatabag;
import smartrics.iotics.space.grpc.HostManagedChannelBuilderFactory;
import smartrics.iotics.space.grpc.TwinDatabag;
import smartrics.iotics.space.twins.FindAndBindTwin;
import smartrics.iotics.space.twins.FollowerModelTwin;

import java.time.Duration;
import java.util.Locale;
import java.util.Timer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static smartrics.iotics.space.grpc.ListenableFutureAdapter.toCompletable;

public class Connector {
    private static final Logger LOGGER = LoggerFactory.getLogger(Connector.class);

    private static final Duration AUTH_TOKEN_DURATION = Duration.ofSeconds(3600);

    private static final Duration SHARE_DATA_PERIOD = Duration.ofSeconds(5);

    private final SimpleIdentityManager sim;
    private final ManagedChannel channel;
    private final Timer timer;
    private final FollowerModelTwin modelTwin;
    private final FindAndBindTwin findAndBindTwin;
    private final LoadingCache<TwinDatabag, String> indexPrefixCache;
    private final ESMapper esMapper;
    private final Timer shareTimer;

    private CompletableFuture<Void> fnbFuture;

    private static String IndexNameForFeed(String prefix, FeedID feedID) {
        return String.join("_", prefix, feedID.getId()).toLowerCase(Locale.ROOT);
    }

    public Connector(IoticSpace ioticSpace, SimpleConfig userConf, SimpleConfig agentConf, ESMapper esMapper) {
        sim = SimpleIdentityManager.Builder
                .anIdentityManager()
                .withAgentKeyID("#test-agent-0")
                .withUserKeyID("#test-user-0")
                .withAgentKeyName(agentConf.keyName())
                .withUserKeyName(userConf.keyName())
                .withResolverAddress(ioticSpace.endpoints().resolver())
                .withUserSeed(userConf.seed())
                .withAgentSeed(agentConf.seed())
                .build();
        timer = new Timer("token-scheduler");
        shareTimer = new Timer("status-share-scheduler");

        ManagedChannelBuilder channelBuilder = new HostManagedChannelBuilderFactory()
                .withSimpleIdentityManager(sim)
                .withTimer(timer)
                .withSGrpcEndpoint(ioticSpace.endpoints().grpc())
                .withTokenTokenDuration(AUTH_TOKEN_DURATION)
                .makeManagedChannelBuilder();
        channel = channelBuilder
                .keepAliveWithoutCalls(true)
                .build();

        this.esMapper = esMapper;

        TwinAPIGrpc.TwinAPIFutureStub twinAPIStub = TwinAPIGrpc.newFutureStub(channel);
        FeedAPIGrpc.FeedAPIFutureStub feedAPIStub = FeedAPIGrpc.newFutureStub(channel);
        InterestAPIGrpc.InterestAPIStub interestAPIStub = InterestAPIGrpc.newStub(channel);
        InterestAPIGrpc.InterestAPIBlockingStub interestAPIBlockingStub = InterestAPIGrpc.newBlockingStub(channel);
        SearchAPIGrpc.SearchAPIStub searchAPIStub = SearchAPIGrpc.newStub(channel);

        modelTwin = new FollowerModelTwin(this.sim, twinAPIStub, MoreExecutors.directExecutor());
        ListenableFuture<TwinID> modelFuture = modelTwin.makeIfAbsent();

        findAndBindTwin = new SafeGetter<FindAndBindTwin>().safeGet(() -> toCompletable(modelFuture)
                .thenApply(modelID -> create(twinAPIStub, feedAPIStub, interestAPIStub, interestAPIBlockingStub, searchAPIStub, modelID))
                .thenApply(this::delete)
                .thenApply(this::make)
                .get());

        // make it external
        indexPrefixCache = CacheBuilder.newBuilder().build(new IndexesCacheLoader(findAndBindTwin));
    }

    public void shutdown(Duration timeout) throws InterruptedException {
        stop();
        timer.cancel();
        shareTimer.cancel();
        channel.shutdown().awaitTermination(timeout.getSeconds(), TimeUnit.SECONDS);
        channel.shutdownNow();
    }

    public synchronized boolean stop() {
        if(fnbFuture != null && !fnbFuture.isDone()) {
            boolean res = fnbFuture.cancel(true);
            fnbFuture = null;
            return res;
        }
        return true;
    }

    public void run(SearchRequest.Payload searchPayload) {
        try {
            StreamObserver<FeedDatabag> fObs = feedDataStreamObserver();
            StreamObserver<TwinDatabag> tObs = twinDatabagStreamObserver();
            fnbFuture = findAndBindTwin.findAndBind(searchPayload, tObs, fObs);
            LOGGER.info("Waiting to complete");
            Void v = fnbFuture.get();
            LOGGER.info("completed! {}", v);
        } catch (Exception e) {
            LOGGER.error("exc when calling find and bind", e);
        }
    }

    @NotNull
    private StreamObserver<TwinDatabag> twinDatabagStreamObserver() {
        StreamObserver<TwinDatabag> tObs = new AbstractLoggingStreamObserver<>("twin>") {
            @Override
            public void onNext(TwinDatabag value) {
                LOGGER.info("Found twin: {}", value.twinDetails().getTwinId());
            }

            @Override
            public void onError(Throwable throwable) {
                super.onError(throwable);
                Connector.this.fnbFuture.complete(null);
                this.onCompleted();
            }
        };
        return tObs;
    }

    @NotNull
    private AbstractLoggingStreamObserver<FeedDatabag> feedDataStreamObserver() {
        return new AbstractLoggingStreamObserver<>("feed>") {
            @Override
            public void onNext(FeedDatabag feedData) {
                try {
                    String indexPrefix = indexPrefixCache.getUnchecked(feedData.twinData());
                    String index = IndexNameForFeed(indexPrefix, feedData.feedDetails().getFeedId());
                    JsonObject doc = Jsonifier.toJson(feedData);
                    esMapper.index(index, doc).exceptionally(throwable -> {
                        throwable.printStackTrace();
                        JsonObject o = new JsonObject();
                        o.addProperty("error", throwable.getMessage());
                        return o;
                    }).thenAccept(object -> LOGGER.info("stored {}", object.toString()));
                } catch (Exception e) {
                    LOGGER.error("exc when calling es store", e);
                }
            }
        };
    }

    private FindAndBindTwin create(TwinAPIGrpc.TwinAPIFutureStub twinAPIStub,
                                   FeedAPIGrpc.FeedAPIFutureStub feedAPIStub,
                                   InterestAPIGrpc.InterestAPIStub interestAPIStub,
                                   InterestAPIGrpc.InterestAPIBlockingStub interestAPIBlockingStub,
                                   SearchAPIGrpc.SearchAPIStub searchAPIStub,
                                   TwinID modelID) {
        return new FindAndBindTwin(Connector.this.sim, "receiver_key_0",
                twinAPIStub, feedAPIStub, interestAPIStub, interestAPIBlockingStub, searchAPIStub,
                MoreExecutors.directExecutor(), modelID, shareTimer, SHARE_DATA_PERIOD);
    }

    private FindAndBindTwin make(FindAndBindTwin fabt) {
        return new SafeGetter<FindAndBindTwin>().safeGet(() -> {
            UpsertTwinResponse upsertTwinResponse = fabt.make().get();
            LOGGER.info("upsert: {}", upsertTwinResponse);
            return fabt;
        });
    }

    private FindAndBindTwin delete(FindAndBindTwin fabt) {
        return new SafeGetter<FindAndBindTwin>().safeGet(() -> {
            DeleteTwinResponse deleteTwinResponse = fabt.delete().get();
            LOGGER.info("delete: {}", deleteTwinResponse);
            return fabt;
        });
    }

    private interface MyFuture<V> {
        V apply() throws InterruptedException, ExecutionException;
    }

    private class SafeGetter<V> {
        public V safeGet(MyFuture<V> delegate) {
            try {
                return delegate.apply();
            } catch (InterruptedException e) {
                Thread.interrupted();
                throw new IllegalStateException("operation interrupted", e);
            } catch (ExecutionException e) {
                throw new IllegalStateException("operation failed", e);
            }

        }
    }

}
