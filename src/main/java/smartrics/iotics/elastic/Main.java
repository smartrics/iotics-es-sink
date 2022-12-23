package smartrics.iotics.elastic;

import com.iotics.sdk.identity.SimpleConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;

public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        SimpleConfig user = SimpleConfig.fromEnv("USER_");
        SimpleConfig agent = SimpleConfig.fromEnv("AGENT_");

        String spaceDns = System.getenv("SPACE");

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
