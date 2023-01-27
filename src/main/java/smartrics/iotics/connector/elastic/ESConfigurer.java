package smartrics.iotics.connector.elastic;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.indices.PutIndexTemplateRequest;
import co.elastic.clients.elasticsearch.indices.PutIndexTemplateResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutionException;

public class ESConfigurer {
    public static DateTimeFormatter INDEX_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Logger LOGGER = LoggerFactory.getLogger(ESConfigurer.class);
    private static final String IOT_INDEX_TEMPLATE_RES = "iot.index_template.json";
    private final ElasticsearchAsyncClient client;

    public ESConfigurer(ElasticsearchAsyncClient client) {
        this.client = client;
    }

    public void run() throws ExecutionException, InterruptedException {
        InputStream str = Thread.currentThread().getContextClassLoader().getResourceAsStream(IOT_INDEX_TEMPLATE_RES);
        PutIndexTemplateRequest request = PutIndexTemplateRequest.of(builder -> builder.name("iot").withJson(str));
        PutIndexTemplateResponse resp = client.indices().putIndexTemplate(request).get();
        LOGGER.info("response: {}", resp);
    }
}
