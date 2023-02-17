package smartrics.iotics.connector.elastic.conf;

import com.google.protobuf.util.JsonFormat;
import com.iotics.api.SearchRequest;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;

public record SearchConf(String id, String path, Handler type, Map<String, String> conf) {

    public record FindAndDescribeConf(Long startDelaySec, Long pollFrequencySec) {
        public static FindAndDescribeConf from(Map<String, String> conf) {
            long v1 = Long.parseLong(conf.get("startDelaySec"));
            long v2 = Long.parseLong(conf.get("pollFrequencySec"));
            return new FindAndDescribeConf(v1, v2);
        }
    }

    public enum Handler {
        FIND_AND_BIND,
        FIND_AND_DESCRIBE;
    }

    public SearchRequest.Payload parse() throws IOException {
        SearchRequest.Payload.Builder searchRequestBuilder = SearchRequest.Payload.newBuilder();
        JsonFormat.parser().ignoringUnknownFields().merge(new FileReader(path), searchRequestBuilder);
        return searchRequestBuilder.build();
    }
}
