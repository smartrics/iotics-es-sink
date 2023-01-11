package smartrics.iotics.elastic;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.iotics.api.*;
import com.iotics.sdk.identity.SimpleConfig;
import com.iotics.sdk.identity.SimpleIdentityManager;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smartrics.iotics.space.IoticSpace;
import smartrics.iotics.space.grpc.AbstractLoggingStreamObserver;
import smartrics.iotics.space.grpc.FeedData;
import smartrics.iotics.space.grpc.HostManagedChannelBuilderFactory;
import smartrics.iotics.space.grpc.TwinData;
import smartrics.iotics.space.twins.FindAndBindTwin;
import smartrics.iotics.space.twins.FollowerModelTwin;
import smartrics.iotics.space.twins.SearchFilter;

import java.time.Duration;
import java.util.Timer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static smartrics.iotics.space.grpc.ListenableFutureAdapter.toCompletable;

public class Connector {
    private static final Logger LOGGER = LoggerFactory.getLogger(Connector.class);

    private static final Duration AUTH_TOKEN_DURATION = Duration.ofSeconds(10);
    private static final String TEXT = "joke";
    private static final GeoCircle LONDON = GeoCircle.newBuilder()
            .setRadiusKm(25)
            .setLocation(GeoLocation.newBuilder()
                    .setLat(51.509865)
                    .setLon(-0.118092)
                    .build())
            .build();

    private final SimpleIdentityManager sim;
    private final ManagedChannel channel;
    private final Timer timer;
    private final FollowerModelTwin modelTwin;
    private final FindAndBindTwin findAndBindTwin;
    private final LoadingCache<TwinData, String> indexPrefixCache;

    public Connector(IoticSpace ioticSpace, SimpleConfig userConf, SimpleConfig agentConf) {
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

        ManagedChannelBuilder channelBuilder = new HostManagedChannelBuilderFactory()
                .withSimpleIdentityManager(sim)
                .withTimer(timer)
                .withSGrpcEndpoint(ioticSpace.endpoints().grpc())
                .withTokenTokenDuration(AUTH_TOKEN_DURATION)
                .makeManagedChannelBuilder();
        channel = channelBuilder
                .keepAliveWithoutCalls(true)
                .build();


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
        timer.cancel();
        channel.shutdown().awaitTermination(timeout.getSeconds(), TimeUnit.SECONDS);
    }

    public void run() {
        SearchFilter searchFilter = SearchFilter.Builder.aSearchFilter()
//                .withLocation(LONDON)
                .withText(TEXT)
                .build();
        CountDownLatch done = new CountDownLatch(1);

        try {
            findAndBindTwin.findAndBind(searchFilter, new AbstractLoggingStreamObserver<>("twin>") {
                @Override
                public void onNext(TwinData twinData) {
                    try {
                        LOGGER.info("twin_data_index_prefix={}", indexPrefixCache.get(twinData));
                    } catch (ExecutionException e) {
                        LOGGER.error("thrown", e);
                    }
                }
            }, new AbstractLoggingStreamObserver<>("feed>") {
                @Override
                public void onNext(FeedData feedData) {
                    LOGGER.info("feed_index={}", indexPrefixCache.getUnchecked(feedData.twinData()) + "_" + feedData.feedDetails().getFeedId().getId());
                }

            }).get();
            LOGGER.info("Waiting to complete");
            done.await();
        } catch (Exception e) {
            LOGGER.error("exc when calling", e);
        }
    }

    private FindAndBindTwin create(TwinAPIGrpc.TwinAPIFutureStub twinAPIStub, FeedAPIGrpc.FeedAPIFutureStub feedAPIStub, InterestAPIGrpc.InterestAPIStub interestAPIStub, InterestAPIGrpc.InterestAPIBlockingStub interestAPIBlockingStub, SearchAPIGrpc.SearchAPIStub searchAPIStub, TwinID modelID) {
        return new FindAndBindTwin(Connector.this.sim, "receiver_key_0",
                twinAPIStub, feedAPIStub, interestAPIStub, interestAPIBlockingStub, searchAPIStub,
                MoreExecutors.directExecutor(), modelID);
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
