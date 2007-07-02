package org.apache.http.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class VersionInfo {

    private static final String RESOURCE = "org/apache/http/client/version.properties";

    private static Properties RELEASE_PROPERTIES;
    private static String RELEASE_VERSION;

    private static Properties getReleaseProperties() {
        if (RELEASE_PROPERTIES == null) {
            try {
                ClassLoader cl = VersionInfo.class.getClassLoader();
                InputStream instream = cl.getResourceAsStream(RESOURCE);
                try {
                    Properties props = new Properties();
                    props.load(instream);
                    RELEASE_PROPERTIES = props;
                } finally {
                    instream.close();
                }
            } catch (IOException ex) {
                // shamelessly munch this exception
            }
            if (RELEASE_PROPERTIES == null) {
                // Create dummy properties instance
                RELEASE_PROPERTIES = new Properties();
            }
        }
        return RELEASE_PROPERTIES;
    }
    
    
    public static String getReleaseVersion() {
        if (RELEASE_VERSION == null) {
            Properties props = getReleaseProperties();
            RELEASE_VERSION = (String) props.get("httpclient.release");
            if (RELEASE_VERSION == null 
                    || RELEASE_VERSION.length() == 0 
                    || RELEASE_VERSION.equals("${pom.version}")) {
                RELEASE_VERSION = "UNKNOWN_SNAPSHOT";
            }
        }
        return RELEASE_VERSION;
    }
    
}
