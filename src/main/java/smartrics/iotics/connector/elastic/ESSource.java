package smartrics.iotics.connector.elastic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.grpc.stub.StreamObserver;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.elasticsearch.client.*;
import smartrics.iotics.connector.elastic.conf.EsConf;

import java.io.*;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;

public class ESSource {

    private final RestClient client;
    private final EsConf.Loader loaderConf;
    private final Timer timer;

    public ESSource(RestClient client, Timer timer, EsConf.Loader loaderConf) {
        this.client = client;
        this.loaderConf = loaderConf;
        this.timer = timer;
    }


    public Cancellable search(StreamObserver<JsonObject> objects) throws IOException {
        JsonElement root = JsonParser.parseReader(new FileReader(loaderConf.queryFilePath()));
        JsonObject obj = root.getAsJsonObject();
        String index = obj.get("index").getAsString();
        obj.remove("index");
        String query = obj.toString();

        Request request = new Request("GET", "/" + index + "/_search");
        request.setEntity(new NStringEntity(query, ContentType.APPLICATION_JSON));
        request.addParameter("pretty", "true");
        return client.performRequestAsync(request, new ResponseListener() {
            @Override
            public void onSuccess(Response response) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode rootNode = mapper.readTree(response.getEntity().getContent());
                    JsonNode hitsNode = rootNode.path("hits");
                    JsonNode hitsArray = hitsNode.path("hits");
                    for (JsonNode hit : hitsArray) {
                        JsonNode source = hit.path("_source");
                        JsonElement el = JsonParser.parseReader(new StringReader(source.toPrettyString()));
                        objects.onNext(el.getAsJsonObject());
                    }
                    objects.onNext(obj);
                } catch (IOException e) {
                    objects.onError(e);
                }
            }

            @Override
            public void onFailure(Exception e) {
                objects.onError(e);
            }
        });
    }

    public void run(StreamObserver<JsonObject> objects) {
        this.timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    ESSource.this.search(objects);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            }
        }, 0, loaderConf.periodSec() * 1000);
    }

    public CompletableFuture<Void> stop() {
        CompletableFuture<Void> f = new CompletableFuture<>();
        f.thenRun(() -> ESSource.this.timer.cancel()).complete(null);
        return f;
    }
}
