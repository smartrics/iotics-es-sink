package smartrics.iotics.space;

import com.iotics.api.DescribeFeedResponse;
import com.iotics.api.FeedMeta;
import com.iotics.api.SearchResponse;
import com.iotics.api.Value;

import java.util.ArrayList;
import java.util.List;

public class Feed extends Point {

    private boolean storeLast;

    public Feed(Twin parent, SearchResponse.FeedDetails feedDetails) {
        super(parent, feedDetails.getFeed().getId().getValue(), feedDetails.getPropertiesList(), new ArrayList<>());
        this.storeLast = feedDetails.getStoreLast();
    }

    public Feed(Twin parent, FeedMeta feed) {
        super(parent, feed.getFeedId().getValue(), new ArrayList<>(), new ArrayList<>());
        this.storeLast = feed.getStoreLast();
    }

    public Feed(Twin parent, DescribeFeedResponse value) {
        super(parent, value.getPayload().getFeed().getId().getValue(), value.getPayload().getResult().getPropertiesList(), new ArrayList<>());
        this.storeLast = value.getPayload().getResult().getStoreLast();
    }

    public boolean storeLast() {
        return storeLast;
    }

    @Override
    public String toString() {
        return "Feed{" +
                "point=" + super.toString() +
                ", storeLast=" + storeLast +
                '}';
    }
}
