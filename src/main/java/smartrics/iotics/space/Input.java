package smartrics.iotics.space;

import com.iotics.api.FeedID;
import com.iotics.api.InputID;
import com.iotics.api.InputMeta;
import com.iotics.api.SearchResponse;

import java.util.ArrayList;

public class Input extends Point {
    private Twin parent;

    private String id;

    public Input(Twin parent, SearchResponse.InputDetails inputDetails) {
        super(parent.remoteHostId().orElse(null), inputDetails.getPropertiesList());
        this.parent = parent;
        this.id = inputDetails.getInput().getId().getValue();
    }

    public Input(Twin parent, InputMeta input) {
        super(parent.remoteHostId().orElse(null), new ArrayList<>());
        this.parent = parent;
        this.id = input.getInputId().getValue();
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
