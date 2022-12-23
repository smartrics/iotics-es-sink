package smartrics.iotics.elastic;

import com.iotics.sdk.identity.SimpleConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;

public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        String userIdPath = System.getProperty("user.id.path");
        SimpleConfig user = SimpleConfig.readConf(userIdPath, SimpleConfig.fromEnv("USER_"));
        String agentIdPath = System.getProperty("agent.id.path");
        SimpleConfig agent = SimpleConfig.readConf(agentIdPath, SimpleConfig.fromEnv("AGENT_"));
        String spaceDns = System.getProperty("space.dns", System.getenv("SPACE"));

        if (spaceDns == null) {
            throw new IllegalArgumentException("$SPACE not defined in env (SPACE=<yourSpace>.iotics.space");
        }

        if (!user.isValid() || !agent.isValid()) {
            throw new IllegalStateException("invalid identity env variables");
        }
        Connector connector = new Connector(spaceDns, user, agent);

        try {
            connector.run().get();
        } catch (Exception e) {
            LOGGER.error("exc when calling", e);
        } finally {
            LOGGER.info("waiting for cdl");
            LOGGER.info("channel shutting down");
            connector.shutdown(Duration.ofSeconds(5));
            LOGGER.info("channel shut down --");
        }


    }

}
