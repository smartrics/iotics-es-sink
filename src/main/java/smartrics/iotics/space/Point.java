package smartrics.iotics.space;

import com.iotics.api.Property;
import com.iotics.api.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public abstract class Point {

    private List<Property> properties = new ArrayList<>();
    private String remoteHostId;

    protected Point(String remoteHostId, List<Property> properties) {
        this.remoteHostId = remoteHostId;
        this.properties.addAll(properties);
    }

    public List<Property> properties() {
        return properties;
    }

    public Optional<String> remoteHostId() {
        return Optional.ofNullable(remoteHostId);
    }
}
