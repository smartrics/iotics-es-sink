package smartrics.iotics.connector.elastic;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.JsonObject;
import com.iotics.api.*;
import com.iotics.sdk.identity.SimpleConfig;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smartrics.iotics.connector.elastic.conf.ConnConf;
import smartrics.iotics.space.IoticSpace;
import smartrics.iotics.space.connector.AbstractConnector;
import smartrics.iotics.space.connector.PrefixGenerator;
import smartrics.iotics.space.grpc.AbstractLoggingStreamObserver;
import smartrics.iotics.space.grpc.FeedDatabag;
import smartrics.iotics.space.grpc.IoticsApi;
import smartrics.iotics.space.grpc.TwinDatabag;
import smartrics.iotics.space.twins.FindAndBindTwin;
import smartrics.iotics.space.twins.Follower;
import smartrics.iotics.space.twins.FollowerModelTwin;

import java.time.Duration;
import java.util.Locale;
import java.util.Timer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static smartrics.iotics.space.grpc.ListenableFutureAdapter.toCompletable;

public class Connector extends AbstractConnector {
    private static final Logger LOGGER = LoggerFactory.getLogger(Connector.class);

    private final FindAndBindTwin findAndBindTwin;
    private final LoadingCache<TwinDatabag, String> indexPrefixCache;
    private final ESMapper esMapper;
    private final ESConfigurer esConfigurer;
    private final Timer shareTimer;
    private final SearchRequest.Payload searchPayload;

    private CompletableFuture<Void> fnbFuture;

    public Connector(IoticsApi api, ConnConf connConf, ESMapper esMapper, ESConfigurer esConfigurer, SearchRequest.Payload searchPayload) {
        super(api);

        this.shareTimer = new Timer("status-share-scheduler");
        this.esMapper = esMapper;
        this.searchPayload = searchPayload;

        FollowerModelTwin modelTwin = new FollowerModelTwin(api, MoreExecutors.directExecutor());
        ListenableFuture<TwinID> modelFuture = modelTwin.makeIfAbsent();

        this.esConfigurer = esConfigurer;

        findAndBindTwin = new SafeGetter<FindAndBindTwin>().safeGet(() -> toCompletable(modelFuture)
                .thenApply(modelID -> create(modelID, Duration.ofSeconds(connConf.statsPublishPeriodSec()),
                        new Follower.RetryConf(connConf.retryDelay(), connConf.retryJitter(),
                                connConf.retryBackoffDelay(), connConf.retryMaxBackoffDelay())
                ))
                .thenApply(this::delete)
                .thenApply(this::make)
                .get());

        PrefixGenerator prefixGenerator = new PrefixGenerator();
        indexPrefixCache = CacheBuilder.newBuilder().build(new IndexesCacheLoader(findAndBindTwin, prefixGenerator));
    }

    private static String IndexNameForFeed(String prefix, FeedID feedID) {
        return String.join("_", prefix, feedID.getId()).toLowerCase(Locale.ROOT);
    }

    @Override
    public CompletableFuture<Void> stop(Duration timeout) {
        CompletableFuture<Void> c = super.stop(timeout);
        CompletableFuture<Void> d = new CompletableFuture<>();
        d.thenAccept(unused -> {
            if (fnbFuture != null && !fnbFuture.isDone()) {
                boolean res = fnbFuture.cancel(true);
                fnbFuture = null;
            }
            shareTimer.cancel();
        });
        return CompletableFuture.allOf(c, d);
    }

    public CompletableFuture<Void> start() {
        try {
            // needs to configure indices and so on
            this.esConfigurer.run();

            StreamObserver<FeedDatabag> fObs = feedDataStreamObserver();
            StreamObserver<TwinDatabag> tObs = twinDatabagStreamObserver();
            fnbFuture = findAndBindTwin.findAndBind(searchPayload, tObs, fObs);
            return fnbFuture;
        } catch (Exception e) {
            LOGGER.error("exc when calling find and bind", e);
            CompletableFuture<Void> c = new CompletableFuture<>();
            c.completeExceptionally(e);
            return c;
        }
    }

    private StreamObserver<TwinDatabag> twinDatabagStreamObserver() {
        StreamObserver<TwinDatabag> tObs = new AbstractLoggingStreamObserver<>("twin>") {
            @Override
            public void onNext(TwinDatabag value) {
                LOGGER.info("Found twin: {}", value.twinDetails().getTwinId());
            }

//            @Override
//            public void onError(Throwable throwable) {
//                super.onError(throwable);
//                Connector.this.fnbFuture.complete(null);
//                this.onCompleted();
//            }
        };
        return tObs;
    }

    private AbstractLoggingStreamObserver<FeedDatabag> feedDataStreamObserver() {
        return new AbstractLoggingStreamObserver<>("feed>") {
            @Override
            public void onNext(FeedDatabag feedData) {
                try {
                    String indexPrefix = indexPrefixCache.getUnchecked(feedData.twinData());
                    String index = IndexNameForFeed(indexPrefix, feedData.feedDetails().getFeedId());
                    JsonObject doc = jsonifier.toJson(feedData);
                    esMapper.bulk(index, doc).exceptionally(throwable -> {
                        JsonObject o = new JsonObject();
                        o.addProperty("error", throwable.getMessage());
                        return o;
                    }).thenAccept(object -> LOGGER.trace("sent to ES {}", object.toString()));
                } catch (Exception e) {
                    LOGGER.error("exc when calling es store", e);
                }
            }
        };
    }

    private FindAndBindTwin create(TwinID modelID,
                                   Duration statsSharePeriod,
                                   Follower.RetryConf retryConf) {
        return new FindAndBindTwin(Connector.this.getApi(), "receiver_key_0",
                MoreExecutors.directExecutor(), modelID, shareTimer, statsSharePeriod, retryConf);
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
