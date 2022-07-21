package smartrics.iotics.elastic;

import com.google.protobuf.StringValue;
import com.iotics.api.GeoCircle;
import com.iotics.api.GeoLocation;
import com.iotics.api.Scope;
import com.iotics.api.SearchRequest;
import com.iotics.sdk.identity.SimpleIdentity;
import com.iotics.sdk.identity.experimental.ResolverClient;
import com.iotics.sdk.identity.jna.JnaSdkApiInitialiser;
import com.iotics.sdk.identity.jna.SdkApi;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.functions.Action1;
import smartrics.iotics.space.Twin;
import smartrics.iotics.space.api.GrpcHost;
import smartrics.iotics.space.api.SearchApi;
import smartrics.iotics.space.api.SearchFilter;
import smartrics.iotics.space.api.identity.IdManager;
import smartrics.iotics.space.SpaceData;
import smartrics.iotics.space.conf.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws IOException {
        String configFile = "./config.yaml";
        if (args.length == 1) {
            configFile = args[0];
        }
        Configuration configuration = Configuration.NewConfiguration(configFile);
        logger.info("Config loaded!");
        SpaceData spaceData = new SpaceData(configuration.space(), new SpaceData.Loader(new OkHttpClient()));
        logger.info("Loaded space data: " + spaceData);

        SdkApi idSkdApi = new JnaSdkApiInitialiser().get();

        ResolverClient resolverClient = new ResolverClient(spaceData.resolverUrl());
        SimpleIdentity simpleIdentity = new SimpleIdentity(idSkdApi,
                spaceData.resolverUrl().toString(),
                Files.readString(Path.of(configuration.identities().userSeedFile())),
                Files.readString(Path.of(configuration.identities().agentSeedFile()))
        );

        IdManager idManager = new IdManager(resolverClient,
                configuration.identities().user(),
                configuration.identities().agent(),
                simpleIdentity);
        logger.info("user: " + idManager.userIdentity());
        logger.info("agent: " + idManager.agentIdentity());

        GrpcHost host = new GrpcHost(spaceData, idManager);

        SearchApi searchApi = new SearchApi(host);
        GeoCircle LONDON = GeoCircle.newBuilder()
                .setRadiusKm(25)
                .setLocation(GeoLocation.newBuilder()
                        .setLat(51.509865)
                        .setLon(-0.118092)
                        .build())
                .build();

        SearchFilter f = SearchFilter.Builder.aSearchFilter()
                .withScope(Scope.GLOBAL)
                .withLocation(LONDON)
                .build();


        Observable<Twin> obs = searchApi.search(SearchApi.aSearchRequest(host.newHeaders(), f));
        obs.count().subscribe(c -> logger.info("Count=" + c));
        obs.subscribe();
    }
}
