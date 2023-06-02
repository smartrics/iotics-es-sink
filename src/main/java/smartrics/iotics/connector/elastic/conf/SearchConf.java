package smartrics.iotics.connector.elastic.conf;

import com.google.protobuf.util.JsonFormat;
import com.iotics.api.SearchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

public class SearchConf {
    private static final Logger LOGGER = LoggerFactory.getLogger(SearchConf.class);

    private final boolean enabled;
    private final String id;
    private final Handler type;
    private final String path;
    private final Map<String, String> conf;
    private SearchRequest.Payload payload;

    public SearchConf(String id, boolean enabled, String path, Handler type, Map<String, String> conf) {
        this.enabled = enabled;
        this.id = id;
        this.path = path;
        this.type =type;
        this.conf = conf;
    }

    public SearchConf parse() {
        try {
            SearchRequest.Payload.Builder searchRequestBuilder = SearchRequest.Payload.newBuilder();
            String json = Files.readString(Paths.get(path));
            JsonFormat.parser().ignoringUnknownFields().merge(json, searchRequestBuilder);
            this.payload = searchRequestBuilder.build();
        } catch (Throwable t) {
            LOGGER.error("error parsing search conf", t);
            this.payload = null;
        }
        return this;
    }

    public boolean enabled() {
        return this.enabled;
    }

    public String id() {
        return this.id;
    }

    public Handler type() {
        return this.type;
    }

    public boolean valid() {
        return this.payload != null;
    }

    public SearchRequest.Payload payload() {
        return this.payload;
    }

    public Map<String, String> conf() {
        return this.conf;
    }

    public record FindAndDescribeConf(Long startDelaySec, Long pollFrequencySec) {
        public static FindAndDescribeConf from(Map<String, String> conf) {
            long v1 = Long.parseLong(conf.get("startDelaySec"));
            long v2 = Long.parseLong(conf.get("pollFrequencySec"));
            return new FindAndDescribeConf(v1, v2);
        }
    }

    public enum Handler {
        FIND_AND_BIND,
        FIND_AND_DESCRIBE
    }

    @Override
    public String toString() {
        return "SearchConf{" +
                "id='" + id + '\'' +
                ", enabled=" + enabled +
                ", type=" + type +
                ", path='" + path + '\'' +
                ", conf=`" + conf + '\'' +
                ", payload=`" + payload + '\'' +
                '}';
    }
}
