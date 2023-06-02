package smartrics.iotics.connector.elastic;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.JsonObject;
import com.iotics.api.*;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smartrics.iotics.connector.elastic.conf.ConnConf;
import smartrics.iotics.connector.elastic.conf.SearchConf;
import smartrics.iotics.space.connector.AbstractConnector;
import smartrics.iotics.space.connector.PrefixGenerator;
import smartrics.iotics.space.grpc.AbstractLoggingStreamObserver;
import smartrics.iotics.space.grpc.FeedDataBag;
import smartrics.iotics.space.grpc.IoticsApi;
import smartrics.iotics.space.grpc.TwinDataBag;
import smartrics.iotics.space.twins.*;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Function;

import static smartrics.iotics.connector.elastic.ESSink.indexNameForFeed;
import static smartrics.iotics.connector.elastic.ESSink.indexNameForTwin;
import static smartrics.iotics.space.grpc.ListenableFutureAdapter.toCompletable;

public class Connector extends AbstractConnector {
    private static final Logger LOGGER = LoggerFactory.getLogger(Connector.class);

    private final Map<String, FindAndDoTwin> findAndDoTwins;
    private final LoadingCache<TwinDataBag, String> indexPrefixCache;
    private final ESSink esSink;
    private final ESConfigurer esConfigurer;
    private final Timer shareTimer;
    private final Timer describerTimer;
    private final ESSource esSource;
    private final ConnConf connConf;
    private final Executor executor;
    private CompletableFuture<Void> fnbFuture;

    public Connector(IoticsApi api,
                     ConnConf connConf,
                     ESSink esSink,
                     ESSource esSource,
                     ESConfigurer esConfigurer) {
        super(api);

        this.executor = MoreExecutors.directExecutor();

        this.describerTimer = new Timer("describer-scheduler");
        this.shareTimer = new Timer("status-share-scheduler");
        this.esSink = esSink;
        this.esConfigurer = esConfigurer;
        this.esSource = esSource;
        this.findAndDoTwins = new HashMap<>();
        this.connConf = connConf;
        PrefixGenerator prefixGenerator = new PrefixGenerator();
        indexPrefixCache = CacheBuilder
                .newBuilder()
                .build(new IndexesCacheLoader(findAndDoTwins
                        .values()
                        .stream()
                        .map((Function<FindAndDoTwin, Describer>) f -> f).toList(),
                        prefixGenerator));
     }

    @Override
    public CompletableFuture<Void> stop(Duration timeout) {
        CompletableFuture<Void> b = esSource.stop();
        CompletableFuture<Void> c = super.stop(timeout);
        CompletableFuture<Void> d = new CompletableFuture<>();
        d.thenAccept(unused -> {
            if (fnbFuture != null && !fnbFuture.isDone()) {
                boolean res = fnbFuture.cancel(true);
                if (!res) {
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
            // TODO: bug here on its configuration
            // StreamObserver<JsonObject> fObj = ioticsMapperStreamObserver();
            // this.esSource.run(fObj);
            // needs to configure indices and so on
            this.esConfigurer.run();

            FollowerModelTwin modelTwin = new FollowerModelTwin(this.getApi(), this.executor);
            ListenableFuture<TwinID> modelFuture = modelTwin.makeIfAbsent();

            List<CompletableFuture<CompletableFuture<Void>>> list = connConf.searches().stream()
                    .filter(SearchConf::enabled)
                    .map(SearchConf::parse)
                    .filter(SearchConf::valid)
                    .map(sc -> {
                        LOGGER.info("VALID SEARCH CONF: {}", sc);
                        return sc;
                    })
                    .map(searchConf -> {
                        String keyIndex = searchConf.id();
                        String label = "key_" + keyIndex;
                        return toCompletable(modelFuture)
                                .thenApply(modelID ->
                                        create(modelID,
                                                searchConf.type(),
                                                keyIndex,
                                                label,
                                                Duration.ofSeconds(connConf.statsPublishPeriodSec())
                                        ))
                                .thenApply(twin -> this.delete(twin))
                                .thenApply(twin -> this.make(twin))
                                .thenApply(twin -> Connector.this.put(keyIndex, twin))
                                .thenApply(twin -> {
                                    SearchRequest.Payload payload = searchConf.payload();
                                    CompletableFuture<Void> f = null;
                                    if (twin instanceof FindAndBindTwin) {
                                        StreamObserver<FeedDataBag> fObs = feedDataStreamObserver();
                                        StreamObserver<TwinDataBag> tObs = twinDatabagStreamObserver();
                                        f = ((FindAndBindTwin) twin).findAndBind(payload, tObs, fObs);
                                    }
                                    if (twin instanceof FindAndDescribeTwin) {
                                        SearchConf.FindAndDescribeConf c = SearchConf.FindAndDescribeConf.from(searchConf.conf());
                                        StreamObserver<DescribeTwinResponse> tObs = describerStreamObserver();
                                        f = ((FindAndDescribeTwin) twin)
                                                .describeAll(payload,
                                                        Duration.ofSeconds(c.startDelaySec()),
                                                        Duration.ofSeconds(c.pollFrequencySec()),
                                                        tObs);
                                    }
                                    return f;
                                });
                    }).toList();

            fnbFuture = CompletableFuture.allOf(list.toArray(new CompletableFuture[0]));
            return fnbFuture;
        } catch (Exception e) {
            LOGGER.error("exc when calling find and bind", e);
            CompletableFuture<Void> c = new CompletableFuture<>();
            c.completeExceptionally(e);
            return c;
        }
    }

    private StreamObserver<JsonObject> ioticsMapperStreamObserver() {
        JsonPathMapper mapper = new JsonPathMapper(this.getApi(), this.connConf.twinMapper());
        ESSourceTwin.Builder twinBuilderDefaults = ESSourceTwin.newBuilder()
                .withExecutor(this.executor)
                .withIoticsApi(this.getApi())
                .withMapper(mapper);

        return new StreamObserver<>() {
            @Override
            public void onNext(JsonObject value) {
                ESSourceTwin twin = ESSourceTwin.newBuilder(twinBuilderDefaults).withSource(value).build();
                CompletableFuture<UpsertTwinResponse> r = toCompletable(twin.make());
                r.thenApply(upsertTwinResponse -> twin.share());
            }

            @Override
            public void onError(Throwable t) {
                LOGGER.error("problems upserting/sharing", t);
            }

            @Override
            public void onCompleted() {

            }
        };
    }

    private StreamObserver<DescribeTwinResponse> describerStreamObserver() {
        return new AbstractLoggingStreamObserver<>("twin>") {
            @Override
            public void onNext(DescribeTwinResponse value) {
                TwinDataBag twinDataBag = TwinDataBag.from(value);
                String index = indexNameForTwin(indexPrefixCache.getUnchecked(twinDataBag));
                JsonObject doc = jsonifier.toJson(twinDataBag);
                System.out.println(doc);
                esSink.bulk(index, doc).exceptionally(throwable -> {
                    JsonObject o = new JsonObject();
                    o.addProperty("error", throwable.getMessage());
                    return o;
                }).thenAccept(object -> LOGGER.trace("sent to ES {}", object.toString()));
            }

        };
    }

    private StreamObserver<TwinDataBag> twinDatabagStreamObserver() {
        return new AbstractLoggingStreamObserver<>("twin>") {
            @Override
            public void onNext(TwinDataBag value) {
                LOGGER.info("Found host:'{}' twin:'{}'", value.twinID().getHostId(), value.twinID().getId());
            }
        };
    }

    private AbstractLoggingStreamObserver<FeedDataBag> feedDataStreamObserver() {
        return new AbstractLoggingStreamObserver<>("feed>") {
            @Override
            public void onNext(FeedDataBag feedDataBag) {
                try {
                    String indexPrefix = indexPrefixCache.getUnchecked(feedDataBag.twinData());
                    String index = indexNameForFeed(indexPrefix, feedDataBag.feedDetails().getFeedId());
                    JsonObject doc = jsonifier.toJson(feedDataBag);
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

    private FindAndDoTwin create(TwinID modelID,
                                 SearchConf.Handler type,
                                 String keyIndex,
                                 String label,
                                 Duration statsSharePeriod) {
        if (type.equals(SearchConf.Handler.FIND_AND_BIND)) {
            Follower.RetryConf rc = new Follower.RetryConf(connConf.retryDelay(), connConf.retryJitter(),
                    connConf.retryBackoffDelay(), connConf.retryMaxBackoffDelay());
            return new FindAndBindTwin(Connector.this.getApi(), "receiver_key_" + keyIndex, label,
                    MoreExecutors.directExecutor(), modelID, shareTimer, statsSharePeriod, rc);

        }
        return new FindAndDescribeTwin(Connector.this.getApi(), "receiver_key_" + keyIndex, label,
                MoreExecutors.directExecutor(), modelID, describerTimer, shareTimer, statsSharePeriod);
    }

    private FindAndDoTwin make(FindAndDoTwin fabt) {
        return new SafeGetter<FindAndDoTwin>().safeGet(() -> {
            UpsertTwinResponse upsertTwinResponse = fabt.make().get();
            LOGGER.info("upsert: {}", upsertTwinResponse);
            return fabt;
        });
    }

    private FindAndDoTwin delete(FindAndDoTwin fabt) {
        return new SafeGetter<FindAndDoTwin>().safeGet(() -> {
            DeleteTwinResponse deleteTwinResponse = fabt.delete().get();
            LOGGER.info("delete: {}", deleteTwinResponse);
            return fabt;
        });
    }

    private FindAndDoTwin put(String keyIndex, FindAndDoTwin t) {
        this.findAndDoTwins.put(keyIndex, t);
        return t;
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
