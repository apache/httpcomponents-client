package org.apache.http.osgi.impl;

import org.junit.Test;

import java.util.Dictionary;
import java.util.Hashtable;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;

public class OSGiProxyConfigurationTest {

    @Test
    public void testToString() {

        final Dictionary<String, Object> config = new Hashtable<>();
        config.put("proxy.enabled", false);
        config.put("proxy.host", "h");
        config.put("proxy.port", 1);
        config.put("proxy.username", "u");
        config.put("proxy.password", "p");
        config.put("proxy.exceptions", new String[]{"e"});

        final OSGiProxyConfiguration configuration = new OSGiProxyConfiguration();
        configuration.update(config);

        final String string = configuration.toString();
        assertThat(string, containsString("enabled=false"));
        assertThat(string, containsString("hostname=h"));
        assertThat(string, containsString("port=1"));
        assertThat(string, containsString("username=u"));
        assertThat(string, containsString("password=p"));
        assertThat(string, containsString("proxyExceptions=[e]"));
    }
}
