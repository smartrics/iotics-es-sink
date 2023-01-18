package smartrics.iotics.connector.elastic.conf;

import smartrics.iotics.space.twins.Follower;

import java.time.Duration;

public record ConnConf(Long tokenDurationSec, Long statsPublishPeriodSec,
                       Long retryDelaySec, Long retryJitterSec, Long retryBackoffDelaySec,
                       Long retryMaxBackoffDelaySec) {

    static ConnConf DEFAULT = new ConnConf(3600L, 60L, 5L, 2L, 5L, 50L);

    public Duration retryDelay() {
        return Duration.ofSeconds(retryDelaySec);
    }

    public Duration retryJitter() {
        return Duration.ofSeconds(retryJitterSec);
    }

    public Duration retryBackoffDelay() {
        return Duration.ofSeconds(retryBackoffDelaySec);
    }

    public Duration retryMaxBackoffDelay() {
        return Duration.ofSeconds(retryMaxBackoffDelaySec);
    }

    public Duration statsPublishPeriod() {
        return Duration.ofSeconds(statsPublishPeriodSec);
    }

    public Duration tokenDuration() {
        return Duration.ofSeconds(tokenDurationSec);
    }

}
