package smartrics.iotics.connector.elastic.conf;

import smartrics.iotics.space.twins.JsonPathMapper;

import java.time.Duration;
import java.util.List;

public record ConnConf(Long tokenDurationSec, Long statsPublishPeriodSec,
                       Long retryDelaySec, Long retryJitterSec, Long retryBackoffDelaySec,
                       Long retryMaxBackoffDelaySec,
                       JsonPathMapper.TwinConf twinMapper,
                       List<SearchConf> searches) {

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
