package smartrics.iotics.space.api;

import com.iotics.api.Headers;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smartrics.iotics.space.SpaceData;
import smartrics.iotics.space.api.identity.IdManager;

import java.time.Duration;

public class GrpcHost {

    private static final Logger logger = LoggerFactory.getLogger(GrpcHost.class);

    final ManagedChannel channel;
    private final IdManager idManager;

    public GrpcHost(SpaceData spaceData, IdManager idManager) {
        TokenInjectorClientInterceptor interceptor =
                new TokenInjectorClientInterceptor(idManager, Duration.ofMinutes(10));
        String endpoint = spaceData.grpcUri();
        this.idManager = idManager;
        this.channel = ManagedChannelBuilder.forTarget(endpoint)
                .intercept(interceptor)
                .build();
    }

    public Headers newHeaders() {
        return Headers.newBuilder()
                .setClientAppId(this.idManager.agentIdentity().did())
                .setClientRef("c-" + SUUID.New())
                .addTransactionRef("tx-" + SUUID.New())
                .build();

    }

}
