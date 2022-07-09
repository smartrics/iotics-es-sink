package smartrics.iotics.space;

import okhttp3.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpaceDataTest {

    private String foobarJson = """
{
  "resolver":"https://did.prd.iotics.com",
  "stomp": "wss://foobar.iotics.space/ws",
  "qapi":"https://foobar.iotics.space/qapi",
  "metrics":"https://foobar.iotics.space/metrics",
  "core-health": "https://foobar.iotics.space/core/health",
  "core-metrics": "https://foobar.iotics.space/core/metrics",
  "hosttwin-health": "https://foobar.iotics.space/hosttwin/health",
  "version":"3.0.760"
}
""";

    @Test
    public void loadFromHttpEndpoint() throws MalformedURLException {
        SpaceData spaceData = new SpaceData("someName", new SpaceData.Loader(mockHttpClientOK(foobarJson)));
        assertEquals("someName.iotics.space", spaceData.fqn());
        assertEquals("someName.iotics.space:10001", spaceData.grpcUri());
        assertEquals("someName", spaceData.name ());
        assertEquals("3.0.760", spaceData.version ());
        assertEquals(URI.create("https://someName.iotics.space").toURL(), spaceData.homeUrl());
        assertEquals(URI.create("https://foobar.iotics.space/metrics").toURL(), spaceData.metricsUrl());
        assertEquals(URI.create("https://did.prd.iotics.com").toURL(), spaceData.resolverUrl());
    }

    @Test
    public void handlesRemoteHTTPErrorCode()  {
        IllegalStateException exc = assertThrows(IllegalStateException.class, () -> {
            new SpaceData("someName", new SpaceData.Loader(mockHttpClientHTTPError(501)));
        });
        assertEquals("http 501 error accessing https://someName.iotics.space/index.json", exc.getMessage());
    }

    @Test
    public void handlesRemoteNullBody()  {
        IllegalStateException exc = assertThrows(IllegalStateException.class, () -> {
            new SpaceData("someName", new SpaceData.Loader(mockHttpClientWithNullBody()));
        });
        assertEquals("invalid response from https://someName.iotics.space/index.json", exc.getMessage());
    }

    @Test
    public void handlesRemoteIOE()  {
        IllegalStateException exc = assertThrows(IllegalStateException.class, () -> {
            new SpaceData("someName", new SpaceData.Loader(mockHttpClientThrowsIOE("boom")));
        });
        assertEquals("unable to ger response from https://someName.iotics.space/index.json", exc.getMessage());
    }

    @Test
    public void handlesInvalidSpaceName()  {
        IllegalArgumentException exc = assertThrows(IllegalArgumentException.class, () -> {
            new SpaceData("some:Name", new SpaceData.Loader(mockHttpClientThrowsIOE("boom")));
        });
        assertEquals("invalid space name: some:Name", exc.getMessage());
    }

    private static OkHttpClient mockHttpClientOK(final String serializedBody) {
        return mockHttpClient(200, serializedBody);
    }

    private static OkHttpClient mockHttpClientHTTPError(final int httpCode) {
        return mockHttpClient(httpCode, "");
    }

    private static OkHttpClient mockHttpClientWithNullBody() {
           return mockHttpClient(0, null);
    }

    private static OkHttpClient mockHttpClient(final int code, final String serializedBody) {
        final OkHttpClient okHttpClient = mock(OkHttpClient.class);

        final Call remoteCall = mock(Call.class);

        ResponseBody body = null;
        if(serializedBody != null) {
            body = ResponseBody.create(
                    serializedBody,
                    MediaType.parse("application/json")
            );
        }

        final Response response = new Response.Builder()
                .request(new Request.Builder().url("https://name.iotics.space/index.json").build())
                .protocol(Protocol.HTTP_1_1)
                .code(code).message("").body(body)
                .build();

        try {
            when(remoteCall.execute()).thenReturn(response);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        when(okHttpClient.newCall(any())).thenReturn(remoteCall);
        return okHttpClient;
    }

    private static OkHttpClient mockHttpClientThrowsIOE(String message) {
        final OkHttpClient okHttpClient = mock(OkHttpClient.class);

        final Call remoteCall = mock(Call.class);

        try {
            when(remoteCall.execute()).thenThrow(new IOException(message));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        when(okHttpClient.newCall(any())).thenReturn(remoteCall);
        return okHttpClient;
    }


}