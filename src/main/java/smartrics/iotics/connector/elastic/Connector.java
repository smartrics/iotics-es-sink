package smartrics.iotics.connector.elastic;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.JsonObject;
import com.iotics.api.DeleteTwinResponse;
import com.iotics.api.SearchRequest;
import com.iotics.api.TwinID;
import com.iotics.api.UpsertTwinResponse;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smartrics.iotics.connector.elastic.conf.ConnConf;
import smartrics.iotics.space.connector.AbstractConnector;
import smartrics.iotics.space.connector.PrefixGenerator;
import smartrics.iotics.space.grpc.AbstractLoggingStreamObserver;
import smartrics.iotics.space.grpc.FeedDatabag;
import smartrics.iotics.space.grpc.IoticsApi;
import smartrics.iotics.space.grpc.TwinDatabag;
import smartrics.iotics.space.twins.Describer;
import smartrics.iotics.space.twins.FindAndBindTwin;
import smartrics.iotics.space.twins.Follower;
import smartrics.iotics.space.twins.FollowerModelTwin;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;

import static smartrics.iotics.connector.elastic.ESSink.indexNameForFeed;
import static smartrics.iotics.space.grpc.ListenableFutureAdapter.toCompletable;

public class Connector extends AbstractConnector {
    private static final Logger LOGGER = LoggerFactory.getLogger(Connector.class);

    private final Map<String, FindAndBindTwin> findAndBindTwins;
    private final LoadingCache<TwinDatabag, String> indexPrefixCache;
    private final ESSink esSink;
    private final ESConfigurer esConfigurer;
    private final Timer shareTimer;
    private final Map<String, SearchRequest.Payload> searches;
    private final ESSource esSource;
    private final TwinFactory twinFactory;
    private CompletableFuture<Void> fnbFuture;

    public Connector(IoticsApi api, ConnConf connConf, ESSink esSink, ESSource esSource, ESConfigurer esConfigurer, Map<String, SearchRequest.Payload> searches) {
        super(api);

        this.shareTimer = new Timer("status-share-scheduler");
        this.esSink = esSink;
        this.searches = searches;

        FollowerModelTwin modelTwin = new FollowerModelTwin(api, MoreExecutors.directExecutor());
        ListenableFuture<TwinID> modelFuture = modelTwin.makeIfAbsent();

        this.esConfigurer = esConfigurer;
        this.esSource = esSource;
        this.findAndBindTwins = new HashMap<>();

        searches.entrySet().forEach(stringPayloadEntry -> {
            String keyIndex = stringPayloadEntry.getKey();
            String label = "key_" + keyIndex;
            FindAndBindTwin findAndBindTwin = new SafeGetter<FindAndBindTwin>().safeGet(() -> toCompletable(modelFuture)
                    .thenApply(modelID -> create(modelID, keyIndex, label, Duration.ofSeconds(connConf.statsPublishPeriodSec()),
                            new Follower.RetryConf(connConf.retryDelay(), connConf.retryJitter(),
                                    connConf.retryBackoffDelay(), connConf.retryMaxBackoffDelay())
                    ))
                    .thenApply(Connector.this::delete)
                    .thenApply(Connector.this::make)
                    .get());
            Connector.this.findAndBindTwins.put(keyIndex, findAndBindTwin);
        });

        PrefixGenerator prefixGenerator = new PrefixGenerator();
        indexPrefixCache = CacheBuilder
                .newBuilder()
                .build(new IndexesCacheLoader(findAndBindTwins
                        .values()
                        .stream()
                        .map((Function<FindAndBindTwin, Describer>) f -> f).toList(),
                        prefixGenerator));

        // TODO: inject
        twinFactory = new TwinFactory(api, connConf.twinMapper());
    }

    @Override
    public CompletableFuture<Void> stop(Duration timeout) {
        CompletableFuture<Void> b = esSource.stop();
        CompletableFuture<Void> c = super.stop(timeout);
        CompletableFuture<Void> d = new CompletableFuture<>();
        d.thenAccept(unused -> {
            if (fnbFuture != null && !fnbFuture.isDone()) {
                boolean res = fnbFuture.cancel(true);
                if(!res) {
                    LOGGER.debug("future not cancelled " + fnbFuture);
                }
                fnbFuture = null;
            }
            shareTimer.cancel();
        });
        return CompletableFuture.allOf(b, c, d);
    }

    public CompletableFuture<Void> start() {
        try {
            StreamObserver<JsonObject> fObj = ioticsMapperStreamObserver(twinFactory);
            this.esSource.run(fObj);
            // needs to configure indices and so on
            this.esConfigurer.run();

            List<CompletableFuture<Void>> list = new ArrayList<>();
            findAndBindTwins.keySet().forEach(keyIndex -> {
                FindAndBindTwin findAndBindTwin = findAndBindTwins.get(keyIndex);
                StreamObserver<FeedDatabag> fObs = feedDataStreamObserver();
                StreamObserver<TwinDatabag> tObs = twinDatabagStreamObserver();
                SearchRequest.Payload payload = searches.get(keyIndex);
                CompletableFuture<Void> f = findAndBindTwin.findAndBind(payload, tObs, fObs);
                list.add(f);
            });


            fnbFuture = CompletableFuture.allOf(list.toArray(new CompletableFuture[0]));
            return fnbFuture;
        } catch (Exception e) {
            LOGGER.error("exc when calling find and bind", e);
            CompletableFuture<Void> c = new CompletableFuture<>();
            c.completeExceptionally(e);
            return c;
        }
    }

    private StreamObserver<JsonObject> ioticsMapperStreamObserver(TwinFactory tf) {
        return new StreamObserver<>() {
            @Override
            public void onNext(JsonObject value) {
                CompletableFuture<UpsertTwinResponse> r = tf.make(value);
                // TODO: should attempt to share if the upsert hasn't worked on the basis that the twin exists
                r.thenApply(upsertTwinResponse -> tf.share(value));
            }

            @Override
            public void onError(Throwable t) {
                LOGGER.error("problems upserting/sharing", t);
                t.printStackTrace();
            }

            @Override
            public void onCompleted() {

            }
        };
    }

    private StreamObserver<TwinDatabag> twinDatabagStreamObserver() {
        return new AbstractLoggingStreamObserver<>("twin>") {
            @Override
            public void onNext(TwinDatabag value) {
                LOGGER.info("Found twin: {}", value.twinDetails().getTwinId());
            }

        };
    }

    private AbstractLoggingStreamObserver<FeedDatabag> feedDataStreamObserver() {
        return new AbstractLoggingStreamObserver<>("feed>") {
            @Override
            public void onNext(FeedDatabag feedData) {
                try {
                    String indexPrefix = indexPrefixCache.getUnchecked(feedData.twinData());
                    String index = indexNameForFeed(indexPrefix, feedData.feedDetails().getFeedId());
                    JsonObject doc = jsonifier.toJson(feedData);
                    esSink.bulk(index, doc).exceptionally(throwable -> {
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
                                   String keyIndex,
                                   String label,
                                   Duration statsSharePeriod,
                                   Follower.RetryConf retryConf) {
        return new FindAndBindTwin(Connector.this.getApi(), "receiver_key_" + keyIndex, label,
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

    private static class SafeGetter<V> {
        public V safeGet(MyFuture<V> delegate) {
            try {
                return delegate.apply();
            } catch (InterruptedException e) {
                throw new IllegalStateException("operation interrupted", e);
            } catch (ExecutionException e) {
                throw new IllegalStateException("operation failed", e);
            }

        }
    }

}
