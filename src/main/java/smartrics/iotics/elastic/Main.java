package smartrics.iotics.elastic;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.iotics.sdk.identity.SimpleConfig;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.ssl.SSLContexts;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smartrics.iotics.elastic.conf.EsConf;
import smartrics.iotics.space.HttpServiceRegistry;
import smartrics.iotics.space.IoticSpace;

import javax.net.ssl.SSLContext;
import java.io.FileReader;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        /** read configuration **/
        String userIdPath = System.getProperty("user.id.path");
        SimpleConfig userConf = SimpleConfig.readConf(userIdPath, SimpleConfig.fromEnv("USER_"));
        String agentIdPath = System.getProperty("agent.id.path");
        SimpleConfig agentConf = SimpleConfig.readConf(agentIdPath, SimpleConfig.fromEnv("AGENT_"));
        String spaceDns = System.getProperty("space.dns", System.getenv("SPACE"));

        String elasticSearchConfPath = System.getProperty("es.conf.path");
        Gson gson = new Gson();
        JsonReader reader = new JsonReader(new FileReader(elasticSearchConfPath));
        EsConf esConf = gson.fromJson(reader, EsConf.class);
        URL esURL = URI.create(esConf.endpoint()).toURL();

        if (spaceDns == null) {
            throw new IllegalArgumentException("space DNS not defined");
        }

        if (!userConf.isValid() || !agentConf.isValid()) {
            throw new IllegalStateException("invalid identity env variables");
        }

        /** initilise elastic search client **/
        BasicCredentialsProvider credsProv = new BasicCredentialsProvider();
        credsProv.setCredentials(
                AuthScope.ANY, new UsernamePasswordCredentials(esConf.credentials().username(), esConf.credentials().password())
        );
        SSLContext sslContext = SSLContexts.custom()
                .loadTrustMaterial(null, TrustAllStrategy.INSTANCE)
                .build();

        RestClient restClient = RestClient.builder(new HttpHost(esURL.getHost(), esURL.getPort(), esURL.getProtocol()))
                .setHttpClientConfigCallback(hc ->
                        hc.setSSLContext(sslContext)
                                .setDefaultCredentialsProvider(credsProv)
                                .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE /* skipp ssl verification*/))
                .build();
        ElasticsearchTransport transport = new RestClientTransport(
                restClient, new JacksonJsonpMapper());
        ElasticsearchAsyncClient esClient = new ElasticsearchAsyncClient(transport);

        System.out.println(esClient.info().get());

        ESMapper mapper = new ESMapper(esClient);

        /** initilise iotics and run connector **/
        HttpServiceRegistry sr = new HttpServiceRegistry(spaceDns);

        IoticSpace ioticSpace = new IoticSpace(sr);
        ioticSpace.initialise();

        Connector connector = new Connector(ioticSpace, userConf, agentConf);
        try {
            connector.run();
        } finally {
            LOGGER.info("waiting for cdl");
            LOGGER.info("channel shutting down");
            connector.shutdown(Duration.ofSeconds(5));
            LOGGER.info("channel shut down --");
        }
    }

}
