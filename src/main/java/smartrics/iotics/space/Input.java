package smartrics.iotics.space;

import com.iotics.api.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Input extends Point {

    public Input(Twin parent, SearchResponse.InputDetails inputDetails) {
        super(parent, inputDetails.getInput().getId().getValue(), inputDetails.getPropertiesList(), new ArrayList<>());
    }

    public Input(Twin parent, InputMeta input) {
        super(parent, input.getInputId().getValue(), new ArrayList<>(), new ArrayList<>());
    }

    public Input(Twin parent, DescribeInputResponse value) {
        super(parent,
                value.getPayload().getInput().getId().getValue(),
                value.getPayload().getResult().getPropertiesList(),
                value.getPayload().getResult().getValuesList());
    }

    @Override
    public String toString() {
        return "Input{point=" + super.toString() + '}';
    }
}
