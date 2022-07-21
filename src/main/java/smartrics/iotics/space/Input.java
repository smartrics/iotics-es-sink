package smartrics.iotics.space;

import com.iotics.api.FeedID;
import com.iotics.api.InputID;
import com.iotics.api.SearchResponse;

public class Input extends Point {
    private Twin parent;

    private String id;

    public Input(Twin parent, SearchResponse.InputDetails inputDetails) {
        super(parent.remoteHostId().orElse(null), inputDetails.getPropertiesList());
        this.parent = parent;
        this.id = inputDetails.getInput().getId().getValue();
    }

    public Twin parent() {
        return parent;
    }

    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return "Input{" +
                "id='" + id + '\'' +
                ", point=" + super.toString() +
                '}';
    }
}
