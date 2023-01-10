package smartrics.iotics.elastic.conf;

import com.google.common.base.Strings;

import java.util.Optional;
import java.util.function.Supplier;

public record EsConf(String endpoint, Credentials credentials) {

    public EsConf validate() {
        if(Strings.isNullOrEmpty(endpoint)) {
            throw new IllegalArgumentException("null endpoint");
        }
        credentials.validate();
        return this;
    }
}
