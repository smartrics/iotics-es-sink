package smartrics.iotics.elastic.conf;

import com.google.common.base.Strings;

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
