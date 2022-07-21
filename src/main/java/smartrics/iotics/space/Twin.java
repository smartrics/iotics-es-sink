package smartrics.iotics.space;

import com.iotics.api.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class Twin {

    private TwinID id;

    private Visibility visibility;
    private GeoLocation location;
    private List<Property> properties = new ArrayList<>();
    private List<Feed> feeds= new ArrayList<>();
    private List<Input> inputs= new ArrayList<>();
    private String remoteHostId;

    public Twin(String remoteHostId, SearchResponse.TwinDetails t) {
        this.properties.addAll(t.getPropertiesList());
        t.getFeedsList().stream().map(feedDetails -> new Feed(Twin.this, feedDetails));
        t.getInputsList().stream().map(inputDetails -> new Input(Twin.this, inputDetails));
        this.id = t.getId();
        this.location = t.getLocation();
        this.visibility = t.getVisibility();
        this.remoteHostId = remoteHostId;
    }

    public TwinID id() {
        return id;
    }

    public Visibility visibility() {
        return visibility;
    }

    public GeoLocation location() {
        return location;
    }

    public List<Property> properties() {
        return properties;
    }

    public List<Feed> feeds() {
        return Collections.unmodifiableList(feeds);
    }

    public List<Input> inputs() {
        return Collections.unmodifiableList(inputs);
    }

    public Optional<String> remoteHostId() {
        return Optional.ofNullable(remoteHostId);
    }

    @Override
    public String toString() {
        return "Twin{" +
                "id=" + id +
                ", visibility=" + visibility +
                ", location=" + location +
                ", properties=" + properties +
                ", feeds=" + feeds +
                ", inputs=" + inputs +
                ", remoteHostId='" + remoteHostId + '\'' +
                '}';
    }
}
