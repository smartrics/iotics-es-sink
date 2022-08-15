package smartrics.iotics.space;

import com.iotics.api.FeedData;

public class FeedPayload {
    private Feed feed;
    private FeedData feedData;

    public FeedPayload(Feed feed, FeedData feedData) {
        this.feed = feed;
        this.feedData = feedData;
    }

    public Feed feed() {
        return feed;
    }

    public FeedData feedData() {
        return feedData;
    }

    @Override
    public String toString() {
        return "FeedPayload{" +
                "feed=" + feed +
                ", feedData=" + feedData +
                '}';
    }
}
