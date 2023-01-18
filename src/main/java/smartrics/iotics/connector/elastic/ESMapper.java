package smartrics.iotics.connector.elastic;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.JsonpMapper;
import com.google.gson.JsonObject;
import jakarta.json.spi.JsonProvider;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smartrics.iotics.connector.elastic.conf.EsConf;

import java.io.StringReader;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

public class ESMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(ESMapper.class);

    private record QueueItem(String index, String jsonString){}

    private final ElasticsearchAsyncClient client;
    private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final ConcurrentLinkedQueue<QueueItem> queue;

    public ESMapper(ElasticsearchAsyncClient client, Timer timer, EsConf.Bulk bulkConf) {
        this.client = client;
        this.queue = new ConcurrentLinkedQueue<>();
        final AtomicLong id = new AtomicLong(-1);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                while(true) {
                    LOGGER.info("bulk_{} current queue size {}", id.incrementAndGet(), ESMapper.this.queue.size());
                    BulkRequest.Builder br = new BulkRequest.Builder();
                    var count = 0;
                    for (int i = 0; i < bulkConf.size(); i++) {
                        QueueItem item = queue.poll();
                        if (item != null) {
                            JsonpMapper jsonpMapper = ESMapper.this.client._transport().jsonpMapper();
                            JsonProvider jsonProvider = jsonpMapper.jsonProvider();
                            JsonData jd = JsonData.from(jsonProvider.createParser(new StringReader(item.jsonString())), jsonpMapper);

                            br.operations(op -> op
                                    .index(idx -> idx
                                            .index(item.index())
                                            .document(jd)
                                    )
                            );
                            count++;
                        } else {
                            break;
                        }
                    }
                    if(count == 0) {
                        break;
                    }
                    LOGGER.info("bulk_{} operations with {} elements", id.get(), count);
                    ESMapper.this.client.bulk(br.build()).thenAccept(bulkResponse -> {

                        LOGGER.info("bulk_{} op took tot={}ms", id.get(), bulkResponse.took());
                        if (bulkResponse.errors()) {
                            LOGGER.error("bulk_{} had errors", id.get());
                            bulkResponse.items()
                                    .stream()
                                    .filter(bri -> bri.error() != null)
                                    .forEach(bri -> LOGGER.error("bulk_{} - err: {}", id.get(), bri.error().reason()));
                        }
                    });
                    if(ESMapper.this.queue.size() == 0) {
                        break;
                    }
                }
            }
        }, 0, Duration.ofSeconds(bulkConf.periodSec()).toMillis());
    }

    public CompletableFuture<JsonObject> bulk(String indexName, JsonObject object) {
        CompletableFuture<JsonObject> c = new CompletableFuture<>();
        try {
            String index = makeFullIndexName(indexName);
            this.queue.add(new QueueItem(index, object.toString()));
            JsonObject res = new JsonObject();
            res.addProperty("index", index);
            c.complete(res);
        } catch (Exception e) {
            c.completeExceptionally(e);
        }
        return c;
    }

    public CompletableFuture<JsonObject> index(String indexName, JsonObject object) {
        String jsonString = object.toString();
        IndexRequest<JsonData> request = IndexRequest.of(i -> i
                .index(makeFullIndexName(indexName))
                .withJson(new StringReader(jsonString))
        );
        return this.client.index(request).thenApply(indexResponse -> indexResponseToJson(indexResponse));

    }

    @NotNull
    private static JsonObject indexResponseToJson(IndexResponse indexResponse) {
        JsonObject res = new JsonObject();
        res.addProperty("id", indexResponse.id());
        res.addProperty("status", indexResponse.result().jsonValue());
        res.addProperty("seqNo", indexResponse.seqNo());
        res.addProperty("index", indexResponse.index());
        return res;
    }

    @NotNull
    private static String makeFullIndexName(String indexName) {
        return String.join("_", indexName, LocalDate.now().format(formatter));
    }
}
