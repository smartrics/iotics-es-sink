package smartrics.iotics.space;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONObject;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

public final class SpaceData {
    private final String name;
    private URL homeUrl;
    private String fqn;
    private URL resolverUrl;
    private URL metricsUrl;
    private String grpcUri;
    private String version;

    public static class Loader {

        private static final String DOMAIN = "iotics.space";

        private final OkHttpClient client;

        public Loader(OkHttpClient okHttpClient) {
            this.client = okHttpClient;
        }

        public void load(String name, SpaceData spaceData) {
            try {
                spaceData.fqn = name;
                if (!name.endsWith(DOMAIN)) {
                    spaceData.fqn = spaceData.fqn + "." + DOMAIN;
                }
                spaceData.homeUrl = URI.create("https://" + spaceData.fqn).toURL();

                String indexUrl = spaceData.homeUrl + "/index.json";
                Request request = new Request.Builder()
                        .url(indexUrl)
                        .get()
                        .build();
                try (Response response = client.newCall(request).execute()) {
                    if (response.code() > 299) {
                        throw new IllegalStateException(
                                "http " + response.code() + " error accessing " + indexUrl);
                    }
                    ResponseBody body = response.body();
                    if (body == null) {
                        throw new IllegalStateException("invalid response from " + indexUrl);
                    }
                    JSONObject obj = new JSONObject(body.string());
                    spaceData.resolverUrl = URI.create(obj.getString("resolver")).toURL();
                    spaceData.version = obj.getString("version");
                    spaceData.metricsUrl = URI.create(obj.getString("metrics")).toURL();
                    spaceData.grpcUri = spaceData.fqn + ":10001";
                } catch (IOException e) {
                    throw new IllegalStateException("unable to ger response from " + indexUrl, e);
                }

            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("invalid space name: " + name, e);
            }
        }
    }

    public SpaceData(String name, Loader loader) {
        this.name = name;
        loader.load(name, this);
    }

    public String name() {
        return name;
    }

    public URL homeUrl() {
        return homeUrl;
    }

    public String fqn() {
        return fqn;
    }

    public URL resolverUrl() {
        return resolverUrl;
    }

    public URL metricsUrl() {
        return metricsUrl;
    }

    public String grpcUri() {
        return grpcUri;
    }

    public String version() {
        return version;
    }

    @Override
    public String toString() {
        return "SpaceData{" +
                "fqn='" + fqn + '\'' +
                ", resolverUrl=" + resolverUrl +
                ", version='" + version + '\'' +
                '}';
    }
}
