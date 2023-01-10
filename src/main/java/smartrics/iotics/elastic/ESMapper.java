package smartrics.iotics.elastic;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;

public class ESMapper {

    private final ElasticsearchAsyncClient client;

    public ESMapper(ElasticsearchAsyncClient client) {
        this.client = client;
    }
}
