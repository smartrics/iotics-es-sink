package smartrics.iotics.elastic;

import com.iotics.sdk.identity.SimpleConfig;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smartrics.iotics.space.HttpServiceRegistry;
import smartrics.iotics.space.IoticSpace;
import smartrics.iotics.space.grpc.DataDetails;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;

public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        String userIdPath = System.getProperty("user.id.path");
        SimpleConfig userConf = SimpleConfig.readConf(userIdPath, SimpleConfig.fromEnv("USER_"));
        String agentIdPath = System.getProperty("agent.id.path");
        SimpleConfig agentConf = SimpleConfig.readConf(agentIdPath, SimpleConfig.fromEnv("AGENT_"));
        String spaceDns = System.getProperty("space.dns", System.getenv("SPACE"));

        if (spaceDns == null) {
            throw new IllegalArgumentException("space DNS not defined");
        }

        if (!userConf.isValid() || !agentConf.isValid()) {
            throw new IllegalStateException("invalid identity env variables");
        }

        HttpServiceRegistry sr = new HttpServiceRegistry(spaceDns);

        IoticSpace ioticSpace = new IoticSpace(sr);
        ioticSpace.initialise();

        Connector connector = new Connector(ioticSpace, userConf, agentConf);
        try {
            connector.run();
        } finally {
                LOGGER.info("waiting for cdl");
                LOGGER.info("channel shutting down");
                connector.shutdown(Duration.ofSeconds(5));
                LOGGER.info("channel shut down --");
        }
    }

}
