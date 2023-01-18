package smartrics.iotics.connector.elastic.conf;

public record ConnConf(Long tokenDurationSec, Long statsPublishPeriodSec) {

    static ConnConf DEFAULT = new ConnConf(3600L, 60L);

}
