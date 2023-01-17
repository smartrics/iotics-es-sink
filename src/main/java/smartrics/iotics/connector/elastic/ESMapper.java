package smartrics.iotics.connector.elastic;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.json.JsonData;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.StringReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

public class ESMapper {

    private final ElasticsearchAsyncClient client;
    private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");

    public ESMapper(ElasticsearchAsyncClient client) {
        this.client = client;
    }

    public CompletableFuture<JsonObject> index(String indexName, JsonObject object) {
        String jsonString = object.toString();
        IndexRequest<JsonData> request = IndexRequest.of(i -> i
                .index(String.join("_", indexName, LocalDate.now().format(formatter)))
                .withJson(new StringReader(jsonString))
        );
        return this.client.index(request).thenApply(indexResponse -> {
            JsonObject res = new JsonObject();
            res.addProperty("id", indexResponse.id());
            res.addProperty("status", indexResponse.result().jsonValue());
            res.addProperty("seqNo", indexResponse.seqNo());
            res.addProperty("index", indexResponse.index());
            return res;
        });

    }
}
