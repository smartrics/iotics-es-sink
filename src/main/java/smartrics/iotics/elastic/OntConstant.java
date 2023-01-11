package smartrics.iotics.elastic;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum OntConstant {

    RDF_TYPE_URI("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
    RDFS_CLASS_URI("http://www.w3.org/2000/01/rdf-schema#Class"),
    OWL_CLASS_URI( "http://www.w3.org/2002/07/owl#Class");

    private static List<String> uris;
    private final String uri;

    OntConstant(String uri) {
        this.uri = uri;
    }

    public static List<String> uris() {
        if(uris == null) {
            OntConstant.uris = Arrays
                    .stream(OntConstant.values())
                    .map(ontConstants -> ontConstants.uri)
                    .collect(Collectors.toList());
        }
        return uris;
    }

}
