package smartrics.iotics.connector.elastic.conf;

import com.google.common.base.Strings;

import java.time.Duration;
import java.util.List;

public record EsConf(String endpoint,
                     Credentials credentials,
                     Bulk bulk,
                     Loader loader) {

    public EsConf validate() {
        if(Strings.isNullOrEmpty(endpoint)) {
            throw new IllegalArgumentException("null endpoint");
        }
        credentials.validate();
        return this;
    }

    public record Sink(String searchRequestPath) {}
    public record Bulk(Integer size, Integer periodSec){}
    public record Loader(Integer periodSec, String queryFilePath){}

    public record Credentials(String username, String password) {
        public Credentials validate() {
            if(Strings.isNullOrEmpty(username)) {
                throw new IllegalArgumentException("null username");
            }
            if(Strings.isNullOrEmpty(password)) {
                throw new IllegalArgumentException("null password");
            }
            return this;
        }
    }
}
