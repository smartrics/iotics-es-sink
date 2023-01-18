package smartrics.iotics.connector.elastic.conf;

import com.google.common.base.Strings;

public record EsConf(String endpoint,
                     Credentials credentials,
                     Bulk bulk) {

    public EsConf validate() {
        if(Strings.isNullOrEmpty(endpoint)) {
            throw new IllegalArgumentException("null endpoint");
        }
        credentials.validate();
        return this;
    }

    public record Bulk(Integer size, Integer periodSec){}
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
