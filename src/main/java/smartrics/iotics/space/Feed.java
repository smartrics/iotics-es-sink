package smartrics.iotics.space;

import com.iotics.api.FeedMeta;
import com.iotics.api.SearchResponse;

import java.util.ArrayList;

public class Feed extends Point {

    private Twin parent;
    private String id;
    private boolean storeLast;

    public Feed(Twin parent, SearchResponse.FeedDetails feedDetails) {
        super(parent.remoteHostId().orElse(null), feedDetails.getPropertiesList());
        this.parent = parent;
        this.storeLast = feedDetails.getStoreLast();
        this.id = feedDetails.getFeed().getId().getValue();
    }

    public Feed(Twin parent, FeedMeta feed) {
        super(parent.remoteHostId().orElse(null), new ArrayList<>());
        this.parent = parent;
        this.storeLast = feed.getStoreLast();
        this.id = feed.getFeedId().getValue();
    }

    public Twin parent() {
        return parent;
    }

    public String id() {
        return id;
    }

    public boolean storeLast() {
        return storeLast;
    }

    @Override
    public String toString() {
        return "Feed{" +
                "id='" + id + '\'' +
                ", storeLast=" + storeLast +
                ", point=" + super.toString() +
                '}';
    }
}
