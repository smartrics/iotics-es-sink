package smartrics.iotics.elastic;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Configuration {

    private String space;

    public static Configuration NewConfiguration(String fileName) {
        if (fileName == null) {
            throw new IllegalArgumentException("null configuration filename");
        }
        try (InputStream in = Files.newInputStream(Paths.get(fileName))) {
            return NewConfiguration(in);
        } catch (IOException e) {
            throw new IllegalArgumentException("invalid configuration filename", e);
        }
    }

    public static Configuration NewConfiguration(InputStream is) {
        if (is == null) {
            throw new IllegalArgumentException("null input stream");
        }
        Yaml yaml = new Yaml();
        Configuration config = yaml.loadAs(is, Configuration.class);
        return config;
    }

    public String space() {
        return space;
    }

    public void setSpace(String space) {
        this.space = space;
    }
}
