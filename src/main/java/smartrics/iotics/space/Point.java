package smartrics.iotics.space;

import com.iotics.api.Property;
import com.iotics.api.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public abstract class Point {

    private List<Property> properties = new ArrayList<>();
    private final List<Value> values= new ArrayList<>();;
    private Twin parent;
    private final String id;


    protected Point(Twin parent, String id, List<Property> properties, List<Value> values) {
        this.parent = parent;
        this.id = id;
        this.properties.addAll(properties);
        this.values.addAll(values);
    }

    public String id() {
        return id;
    }

    public List<Property> properties() {
        return Collections.unmodifiableList(properties);
    }

    public List<Value> values() {
        return Collections.unmodifiableList(values);
    }

    public Twin parent() {
        return parent;
    }

    @Override
    public String toString() {
        return "Point{" +
                "id='" + id + '\'' +
                ", properties=" + properties +
                ", values=" + values +
                ", parent=" + parent +
                '}';
    }
}
