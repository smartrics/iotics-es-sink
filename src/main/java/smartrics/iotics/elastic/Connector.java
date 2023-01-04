package smartrics.iotics.elastic;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.iotics.api.*;
import com.iotics.sdk.identity.SimpleConfig;
import com.iotics.sdk.identity.SimpleIdentityManager;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smartrics.iotics.space.IoticSpace;
import smartrics.iotics.space.grpc.HostManagedChannelBuilderFactory;
import smartrics.iotics.space.twins.SearchFilter;

import java.time.Duration;
import java.util.Timer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static smartrics.iotics.elastic.ListenableFutureAdapter.toCompletable;

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
    }


    public void shutdown(Duration timeout) throws InterruptedException {
        timer.cancel();
        channel.shutdown().awaitTermination(timeout.getSeconds(), TimeUnit.SECONDS);
    }

    public CompletableFuture<Integer> findAndBind(StreamObserver<DataDetails> streamObserver) {
        TwinAPIGrpc.TwinAPIFutureStub twinAPIStub = TwinAPIGrpc.newFutureStub(channel);
        FeedAPIGrpc.FeedAPIFutureStub feedAPIStub = FeedAPIGrpc.newFutureStub(channel);
        InterestAPIGrpc.InterestAPIStub interestAPIStub = InterestAPIGrpc.newStub(channel);
        InterestAPIGrpc.InterestAPIBlockingStub interestAPIBlockingStub = InterestAPIGrpc.newBlockingStub(channel);
        SearchAPIGrpc.SearchAPIStub searchAPIStub = SearchAPIGrpc.newStub(channel);
        ModelOfReceiver model = new ModelOfReceiver(this.sim, twinAPIStub, MoreExecutors.directExecutor());
        ListenableFuture<TwinID> modelFuture = model.makeIfAbsent();

        CompletableFuture<Integer> resFuture = new CompletableFuture<>();
        toCompletable(modelFuture).thenAccept((modelID) -> {
            LOGGER.info("model id: {}", modelID);
            ReceiverTwin receiverTwin = new ReceiverTwin(this.sim, "receiver_key_0",
                    twinAPIStub, feedAPIStub, interestAPIStub, interestAPIBlockingStub, searchAPIStub,
                    MoreExecutors.directExecutor(), modelID);
            toCompletable(receiverTwin.delete()).thenAccept(deleteTwinResponse -> {
                LOGGER.info("delete: {}", deleteTwinResponse);
                toCompletable(receiverTwin.make()).thenAccept(upsertTwinResponse -> {
                    LOGGER.info("upsert: {}", upsertTwinResponse);
                });
            }).thenRun(() -> {
                AtomicLong counter = new AtomicLong(0);
                StreamObserver<SearchResponse.TwinDetails> resultsStreamObserver = new AbstractLoggingStreamObserver<>("'search'") {
                    @Override
                    public void onNext(SearchResponse.TwinDetails twinDetails) {
                        for (SearchResponse.FeedDetails feedDetails : twinDetails.getFeedsList()) {
                            receiverTwin.follow(feedDetails.getFeedId(), new AbstractLoggingStreamObserver<>(feedDetails.getFeedId().toString()) {
                                @Override
                                public void onNext(FetchInterestResponse value) {
                                    streamObserver.onNext(new DataDetails(twinDetails, feedDetails, value));
                                }
                            });
                        }
                    }
                };
                receiverTwin.search(SearchFilter.Builder.aSearchFilter()
                        .withScope(Scope.GLOBAL)
                        .withText(TEXT)
//                        .withLocation(LONDON)
                        .build(), resultsStreamObserver);
                resFuture.complete(counter.intValue());
            });
        });
        return resFuture;
    }

}
