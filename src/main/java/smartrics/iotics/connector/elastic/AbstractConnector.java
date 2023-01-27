package smartrics.iotics.connector.elastic;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.JsonObject;
import com.iotics.api.*;
import com.iotics.sdk.identity.SimpleConfig;
import com.iotics.sdk.identity.SimpleIdentityManager;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.checkerframework.checker.units.qual.C;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smartrics.iotics.connector.elastic.conf.ConnConf;
import smartrics.iotics.space.IoticSpace;
import smartrics.iotics.space.grpc.AbstractLoggingStreamObserver;
import smartrics.iotics.space.grpc.FeedDatabag;
import smartrics.iotics.space.grpc.HostManagedChannelBuilderFactory;
import smartrics.iotics.space.grpc.TwinDatabag;
import smartrics.iotics.space.twins.FindAndBindTwin;
import smartrics.iotics.space.twins.Follower;
import smartrics.iotics.space.twins.FollowerModelTwin;

import java.time.Duration;
import java.util.Locale;
import java.util.Timer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static smartrics.iotics.space.grpc.ListenableFutureAdapter.toCompletable;

public abstract class AbstractConnector {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractConnector.class);

    protected final SimpleIdentityManager sim;
    protected final ManagedChannel channel;
    private final Timer timer;
    protected final Jsonifier jsonifier;

    protected final TwinAPIGrpc.TwinAPIFutureStub twinAPIStub;
    protected final FeedAPIGrpc.FeedAPIFutureStub feedAPIStub;
    protected final InterestAPIGrpc.InterestAPIStub interestAPIStub;
    protected final InterestAPIGrpc.InterestAPIBlockingStub interestAPIBlockingStub;
    protected final SearchAPIGrpc.SearchAPIStub searchAPIStub;
    protected final PrefixGenerator prefixGenerator;

    public AbstractConnector(ConnConf connConf, IoticSpace ioticSpace, SimpleConfig userConf, SimpleConfig agentConf) {
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

        ManagedChannelBuilder<?> channelBuilder = new HostManagedChannelBuilderFactory()
                .withSimpleIdentityManager(sim)
                .withTimer(timer)
                .withSGrpcEndpoint(ioticSpace.endpoints().grpc())
                .withTokenTokenDuration(Duration.ofSeconds(connConf.tokenDurationSec()))
                .makeManagedChannelBuilder();
        channel = channelBuilder
                .executor(Executors.newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat("esc-grpc-%d").build()))
//                .keepAliveWithoutCalls(true)
                .build();
        this.prefixGenerator = new PrefixGenerator();
        this.jsonifier = new Jsonifier(prefixGenerator);

        this.twinAPIStub = TwinAPIGrpc.newFutureStub(channel);
        this.feedAPIStub = FeedAPIGrpc.newFutureStub(channel);
        this.interestAPIStub = InterestAPIGrpc.newStub(channel);
        this.interestAPIBlockingStub = InterestAPIGrpc.newBlockingStub(channel);
        this.searchAPIStub = SearchAPIGrpc.newStub(channel);
    }

    private static String IndexNameForFeed(String prefix, FeedID feedID) {
        return String.join("_", prefix, feedID.getId()).toLowerCase(Locale.ROOT);
    }

    public CompletableFuture<Void> stop(Duration timeout){
        CompletableFuture<Void> c = new CompletableFuture<>();
        c.thenAccept(unused -> {
            timer.cancel();
            try {
                channel.shutdown().awaitTermination(timeout.getSeconds(), TimeUnit.SECONDS);
                channel.shutdownNow();
            } catch (InterruptedException e) {
                // TOOD fix this handling of IEx
                throw new RuntimeException(e);
            }
        }).complete(null);
        return c;
    }
    public abstract CompletableFuture<Void> start();

}
