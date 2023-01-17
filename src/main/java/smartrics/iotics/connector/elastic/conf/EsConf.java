package smartrics.iotics.connector.elastic.conf;

import com.google.common.base.Strings;

public record EsConf(String endpoint, Credentials credentials) {

    public EsConf validate() {
        if(Strings.isNullOrEmpty(endpoint)) {
            throw new IllegalArgumentException("null endpoint");
        }
        credentials.validate();
        return this;
    }
}
