package smartrics.iotics.connector.elastic.conf;

import smartrics.iotics.space.twins.Follower;

import java.time.Duration;
import java.util.List;

public record ConnConf(Long tokenDurationSec, Long statsPublishPeriodSec,
                       Long retryDelaySec, Long retryJitterSec, Long retryBackoffDelaySec,
                       Long retryMaxBackoffDelaySec,
                       TwinMapper twinMapper) {

    public record FeedMapper(String name, String path){}
    public record TwinMapper(String ontologyRoot,
                             List<String> idPaths,
                             List<String> labelPaths,
                             String locationPath,
                             String commentPrefix,
                             String metadataPath,
                             List<FeedMapper> feeds) {}

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
