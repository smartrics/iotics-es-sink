package smartrics.iotics.elastic;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.iotics.api.*;
import com.iotics.sdk.identity.SimpleConfig;
import com.iotics.sdk.identity.SimpleIdentityManager;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smartrics.iotics.space.HttpServiceRegistry;
import smartrics.iotics.space.IoticSpace;
import smartrics.iotics.space.grpc.HostManagedChannelBuilderFactory;
import smartrics.iotics.space.twins.SearchFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Timer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static smartrics.iotics.elastic.ListenableFutureAdapter.toCompletable;

public class Connector {
    private static final Logger LOGGER = LoggerFactory.getLogger(Connector.class);

    private static final Duration AUTH_TOKEN_DURATION = Duration.ofSeconds(300);
    private static final Integer MAX_RETRY_ATTEMPTS = 10;
    private static final GeoCircle LONDON = GeoCircle.newBuilder()
            .setRadiusKm(25)
            .setLocation(GeoLocation.newBuilder()
                    .setLat(51.509865)
                    .setLon(-0.118092)
                    .build())
            .build();



    private final SimpleIdentityManager sim;
    private final IoticSpace ioticSpace;
    private final ManagedChannel channel;
    private final Timer timer;

    public Connector(String dns, SimpleConfig user, SimpleConfig agent) throws IOException {
        HttpServiceRegistry sr = new HttpServiceRegistry(dns);

        ioticSpace = new IoticSpace(sr);
        ioticSpace.initialise();

        sim = SimpleIdentityManager.Builder
                .anIdentityManager()
                .withAgentKeyID("#test-agent-0")
                .withUserKeyID("#test-user-0")
                .withAgentKeyName(agent.keyName())
                .withUserKeyName(user.keyName())
                .withResolverAddress(ioticSpace.endpoints().resolver())
                .withUserSeed(user.seed())
                .withAgentSeed(agent.seed())
                .build();
        timer = new Timer("token-scheduler");
        channel = hostManagedChannel();
    }

    public void shutdown(Duration timeout) throws InterruptedException {
        timer.cancel();
        channel.shutdown().awaitTermination(timeout.getSeconds(),TimeUnit.SECONDS);
    }

    public CompletableFuture<Integer> run() {
        TwinAPIGrpc.TwinAPIFutureStub twinAPIStub = TwinAPIGrpc.newFutureStub(channel);
        FeedAPIGrpc.FeedAPIFutureStub feedAPIStub = FeedAPIGrpc.newFutureStub(channel);
        InterestAPIGrpc.InterestAPIStub interestAPIStub = InterestAPIGrpc.newStub(channel);
        SearchAPIGrpc.SearchAPIStub searchAPIStub = SearchAPIGrpc.newStub(channel);
        ModelOfReceiver model = new ModelOfReceiver(this.sim, twinAPIStub, MoreExecutors.directExecutor());
        ListenableFuture<TwinID> modelFuture = model.makeIfAbsent();

        CompletableFuture<Integer> resFuture = new CompletableFuture<>();
        toCompletable(modelFuture).thenAccept((modelID) -> {
            LOGGER.info("model id: {}", modelID);
            ReceiverTwin t = new ReceiverTwin(this.sim, "receiver_key_0",
                    twinAPIStub, feedAPIStub, interestAPIStub, searchAPIStub,
                    MoreExecutors.directExecutor(), modelID);
            toCompletable(t.delete()).thenAccept(deleteTwinResponse -> {
                LOGGER.info("delete: {}", deleteTwinResponse);
                toCompletable(t.make()).thenAccept(upsertTwinResponse -> {
                    LOGGER.info("upsert: {}", upsertTwinResponse);
                });
            }).thenRun(() -> {
                AtomicLong counter = new AtomicLong(0);
                StreamObserver<SearchResponse.TwinDetails> f = new AbstractLoggingStreamObserver<>() {
                    @Override
                    public void onNext(SearchResponse.TwinDetails twinDetails) {
                        LOGGER.info("rec {}", counter.incrementAndGet());
                    }
                };
                t.search(SearchFilter.Builder.aSearchFilter()
                        .withScope(Scope.GLOBAL)
                        .withLocation(LONDON).build(), f);
                resFuture.complete(counter.intValue());
            });
        });
        return resFuture;
    }

    private ManagedChannel hostManagedChannel() {
        ManagedChannelBuilder channelBuilder = new HostManagedChannelBuilderFactory()
                .withSimpleIdentityManager(sim)
                .withTimer(timer)
                .withSGrpcEndpoint(ioticSpace.endpoints().grpc())
                .withTokenTokenDuration(AUTH_TOKEN_DURATION)
                .withMaxRetryAttempts(MAX_RETRY_ATTEMPTS)
                .makeManagedChannelBuilder();
        return channelBuilder.keepAliveWithoutCalls(true).build();
    }

}
