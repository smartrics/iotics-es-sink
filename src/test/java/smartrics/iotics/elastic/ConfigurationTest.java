package smartrics.iotics.elastic;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationTest {

    @Test
    public void LoadsConfigFromIs(){
        Configuration conf = Configuration.NewConfiguration(
                new ByteArrayInputStream("spaceDns: foobar.com".getBytes())
        );
        assertEquals(conf.space(), "foobar.com");
    }

    @Test
    public void WontLoadFromNullFile(){
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            Configuration.NewConfiguration((String)null);
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