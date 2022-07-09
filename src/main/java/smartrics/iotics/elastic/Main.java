package smartrics.iotics.elastic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Configuration.class);

    public static void main(String[] args) throws IOException {
        String configFile = "./config.yaml";
        if(args.length == 1) {
            configFile = args[0];
        }
        Configuration configuration = Configuration.NewConfiguration(configFile);

        logger.info("Config loaded!");
    }
}
