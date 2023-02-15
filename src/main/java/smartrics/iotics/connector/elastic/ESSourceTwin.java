package smartrics.iotics.connector.elastic;

import com.google.gson.JsonObject;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import smartrics.iotics.space.grpc.IoticsApi;
import smartrics.iotics.space.twins.*;

import java.util.concurrent.Executor;

public class ESSourceTwin extends AbstractTwin implements MappableMaker<DocumentContext>, MappablePublisher<DocumentContext> {

    private final Mapper<DocumentContext> mapper;
    private DocumentContext context;

    private ESSourceTwin(IoticsApi ioticsApi, DocumentContext context, Mapper<DocumentContext> mapper, Executor executor) {
        super(ioticsApi, mapper.getTwinIdentity(context), executor);
        this.mapper = mapper;
        this.context = context;
    }

    public static Builder newBuilder() {
        return new Builder();
    }
    public static Builder newBuilder(Builder conf) {
        return new Builder()
                .withSource(conf.source)
                .withExecutor(conf.executor)
                .withMapper(conf.mapper)
                .withIoticsApi(conf.ioticsApi);
    }




    public static class Builder {
        private IoticsApi ioticsApi;
        private Executor executor;
        private Mapper<DocumentContext> mapper;
        private JsonObject source;


        public Builder withIoticsApi(IoticsApi api) {
            this.ioticsApi = api;
            return this;
        }
        public Builder withMapper(Mapper<DocumentContext> mapper) {
            this.mapper = mapper;
            return this;
        }

        public Builder withSource(JsonObject source) {
            this.source = source;
            return this;
        }

        public Builder withExecutor(Executor executor) {
            this.executor = executor;
            return this;
        }

        public ESSourceTwin build() {
            DocumentContext context = JsonPath.parse(this.source.toString());
            ESSourceTwin t = new ESSourceTwin(this.ioticsApi, context, mapper, executor);
            return t;
        }

    }

    @Override
    public Mapper<DocumentContext> getMapper() {
        return mapper;
    }

    @Override
    public DocumentContext getTwinSource() {
        return context;
    }

    void updateWith(JsonObject object) {
        DocumentContext context = JsonPath.parse(object.toString());
        String newDid = this.mapper.getTwinIdentity(context).did();
        String myIdentity = super.getIdentity().did();
        if(!myIdentity.equals(newDid)) {
            throw new IllegalArgumentException("Invalid object: not with the same identity. Expected: " + myIdentity + ", received: " + newDid);
        }
        this.context = context;
    }
}
