package smartrics.iotics.connector.elastic;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.protobuf.ByteString;
import com.iotics.api.*;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.GsonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.GsonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smartrics.iotics.connector.elastic.conf.ConnConf;
import smartrics.iotics.space.Builders;
import smartrics.iotics.space.grpc.IoticsApi;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static smartrics.iotics.space.UriConstants.*;
import static smartrics.iotics.space.grpc.ListenableFutureAdapter.toCompletable;

/**
 * TODO: extract as a twin service - Generic JSON Twin that extends from abstract twin
 */
public class TwinFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(TwinFactory.class);

    static {
        Configuration.setDefaults(new Configuration.Defaults() {

            private final JsonProvider jsonProvider = new GsonJsonProvider();
            private final MappingProvider mappingProvider = new GsonMappingProvider();

            @Override
            public JsonProvider jsonProvider() {
                return jsonProvider;
            }

            @Override
            public MappingProvider mappingProvider() {
                return mappingProvider;
            }

            @Override
            public Set<Option> options() {
                return EnumSet.noneOf(Option.class);
            }
        });

    }

    private final ConnConf.TwinMapper twinMapperConf;
    private final IoticsApi api;

    public TwinFactory(IoticsApi api, ConnConf.TwinMapper conf) {
        this.api = api;
        this.twinMapperConf = conf;
    }

    public CompletableFuture<UpsertTwinResponse> make(JsonObject jsonDoc) {
        UpsertTwinRequest upsertRequest = makeUpsertRequestFor(jsonDoc);
        LOGGER.info("upserting twin {}", upsertRequest.getPayload().getTwinId());
        return toCompletable(api.twinAPIFutureStub().upsertTwin(upsertRequest));
    }

    public CompletableFuture<Void> share(JsonObject jsonDoc) {
        DocumentContext jsonContext = JsonPath.parse(jsonDoc.toString());
        // TODO: Dup below
        int twinId = twinMapperConf
                .idPaths()
                .stream()
                .map(s -> ((JsonPrimitive) jsonContext.read(s)).getAsString())
                .collect(Collectors.joining("|")).hashCode();
        // TODO: should not need to recreate
        String twinDid = api.getSim().newTwinIdentity("key_" + twinId).did();
        List<CompletableFuture<?>> list = new ArrayList<>();
        JsonObject toShare = new JsonObject();
        twinMapperConf.feeds().forEach(feedMapper -> {
            JsonObject values = jsonContext.read(feedMapper.path());
            values.keySet()
                    .stream()
                    .filter(s -> values.get(s).isJsonPrimitive())
                    .forEach(s -> toShare.add(s, values.get(s)));
            LOGGER.info("sharing data for {}: {}", twinDid, toShare);
            ShareFeedDataRequest shareFeedDataRequest = ShareFeedDataRequest.newBuilder()
                    .setHeaders(Builders.newHeadersBuilder(api.getSim().agentIdentity().did()))
                    .setArgs(ShareFeedDataRequest.Arguments.newBuilder()
                            .setFeedId(FeedID.newBuilder()
                                    .setId(feedMapper.name())
                                    .setTwinId(twinDid)
                                    .build())
                            .build())
                    .setPayload(ShareFeedDataRequest.Payload.newBuilder()
                            .setSample(FeedData.newBuilder()
                                    .setMime("application/json")
                                    .setData(ByteString.copyFrom(toShare.toString().getBytes(StandardCharsets.UTF_8)))
                                    .build())
                            .build())
                    .build();
            CompletableFuture<ShareFeedDataResponse> c =
                    toCompletable(this.api.feedAPIFutureStub().shareFeedData(shareFeedDataRequest));
            list.add(c);
        });
        return CompletableFuture.allOf(list.toArray(new CompletableFuture[0]));
    }

    private UpsertTwinRequest makeUpsertRequestFor(JsonObject makeTwinFor) {
        DocumentContext jsonContext = JsonPath.parse(makeTwinFor.toString());

        JsonObject location = jsonContext.read(twinMapperConf.locationPath());

        JsonObject metadata = jsonContext.read(twinMapperConf.metadataPath());
        List<Property> props = metadata.keySet()
                .stream()
                .filter(s -> metadata.get(s).isJsonPrimitive())
                .map(s -> Property.newBuilder()
                        .setKey(twinMapperConf.ontologyRoot() + s)
                        .setStringLiteralValue(
                                StringLiteral.newBuilder()
                                        .setValue(metadata.get(s).getAsString())
                                        .build())
                        .build()).toList();

        int twinId = twinMapperConf
                .idPaths()
                .stream()
                .map(s -> ((JsonPrimitive) jsonContext.read(s)).getAsString())
                .collect(Collectors.joining("|")).hashCode();

        String twinLabel = twinMapperConf
                .labelPaths()
                .stream()
                .map(s -> ((JsonPrimitive) jsonContext.read(s)).getAsString())
                .collect(Collectors.joining(", "));

        UpsertTwinRequest.Builder reqBuilder = UpsertTwinRequest.newBuilder()
                .setHeaders(Builders.newHeadersBuilder(api.getSim().agentIdentity().did()));
        UpsertTwinRequest.Payload.Builder payloadBuilder = UpsertTwinRequest.Payload.newBuilder();
        payloadBuilder.setTwinId(TwinID.newBuilder().setId(api.getSim().newTwinIdentity("key_" + twinId).did()));
        payloadBuilder.setVisibility(Visibility.PUBLIC);
        payloadBuilder.setLocation(GeoLocation.newBuilder()
                .setLon(location.get("lon").getAsDouble())
                .setLat(location.get("lat").getAsDouble())
                .build());
        props.forEach(payloadBuilder::addProperties);
        payloadBuilder.addProperties(Property.newBuilder()
                .setKey(IOTICS_PUBLIC_ALLOW_LIST_PROP)
                .setUriValue(Uri.newBuilder().setValue(IOTICS_PUBLIC_ALLOW_ALL_VALUE).build())
                .build());
        payloadBuilder.addProperties(Property.newBuilder()
                .setKey(ON_RDFS_LABEL_PROP)
                .setLiteralValue(Literal.newBuilder().setValue(twinLabel).build())
                .build());
        payloadBuilder.addProperties(Property.newBuilder()
                .setKey(ON_RDFS_COMMENT_PROP)
                .setLiteralValue(Literal.newBuilder().setValue(twinMapperConf.commentPrefix() + "'" + twinLabel + "'").build())
                .build());

        twinMapperConf.feeds().forEach(feedMapper -> {
            UpsertFeedWithMeta.Builder builder = UpsertFeedWithMeta.newBuilder()
                    .setId(feedMapper.name())
                    .setStoreLast(true)
                    .addProperties(Property.newBuilder()
                            .setKey(ON_RDFS_COMMENT_PROP)
                            .setLiteralValue(Literal.newBuilder().setValue("Comment for '" + feedMapper.name() + "'").build())
                            .build())
                    .addProperties(Property.newBuilder()
                            .setKey(ON_RDFS_LABEL_PROP)
                            .setLiteralValue(Literal.newBuilder().setValue(feedMapper.name()).build())
                            .build());
            JsonObject values = jsonContext.read(feedMapper.path());
            values.keySet()
                    .stream()
                    .filter(s -> values.get(s).isJsonPrimitive())
                    .forEach(s -> builder.addValues(Value.newBuilder()
                    .setLabel(s)
                    .setDataType("string")
                    .setComment("Comment for '" + s + "'")
                    .build()));
            payloadBuilder.addFeeds(builder.build());

        });

        reqBuilder.setPayload(payloadBuilder.build());

        return reqBuilder.build();
    }
}
