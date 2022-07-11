package smartrics.iotics.elastic;

import org.junit.jupiter.api.Test;
import smartrics.iotics.space.conf.Configuration;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigurationTest {

    private static String YAML = """
            space: foobar.com
            identities:
                userSeedFile: upath
                user:
                    key: uk
                    name: un
                agentSeedFile: apath
                agent:
                    key: ak
                    name: an
            """;

    private Configuration loadYaml() {
        return Configuration.NewConfiguration(
                new ByteArrayInputStream(YAML.getBytes())
        );

    }

    @Test
    public void LoadsConfigForSpace() {
        Configuration conf =loadYaml();
        assertEquals(conf.space(), "foobar.com");
    }

    @Test
    public void LoadsConfigForIdentities() {
        Configuration conf = loadYaml();
        assertEquals(conf.identities().user().key(), "uk");
        assertEquals(conf.identities().user().name(), "un");
        assertEquals(conf.identities().userSeedFile(), "upath");
        assertEquals(conf.identities().agent().key(), "ak");
        assertEquals(conf.identities().agent().name(), "an");
        assertEquals(conf.identities().agentSeedFile(), "apath");
    }

    @Test
    public void WontLoadFromNullFile() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            Configuration.NewConfiguration((String) null);
        });
        assertEquals("null configuration filename", thrown.getMessage());
    }

    @Test
    public void WontLoadFromNullInputStream(){
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            Configuration.NewConfiguration((InputStream)null);
        });
        assertEquals("null input stream", thrown.getMessage());
    }

    @Test
    public void WontLoadFromInvalidFile(){
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            Configuration.NewConfiguration("/etc/invalid");
        });
        assertEquals("invalid configuration filename", thrown.getMessage());
    }

}