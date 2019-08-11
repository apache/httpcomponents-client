package org.apache.hc.client5.http.impl;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.core5.net.URIAuthority;
import org.junit.Assert;
import org.junit.Test;

public class TestAuthSupport {

    @Test
    public void testExtractFromAuthority() {
        URIAuthority uriAuthority = new URIAuthority("testUser", "localhost", 8080);
        BasicCredentialsProvider basicCredentialsProvider = new BasicCredentialsProvider();

        AuthSupport.extractFromAuthority("http", uriAuthority, basicCredentialsProvider);

        Credentials credentials = basicCredentialsProvider.getCredentials(new AuthScope("localhost", 8080), null);
        Assert.assertEquals("testUser", credentials.getUserPrincipal().getName());
        Assert.assertNull(credentials.getPassword());
    }
}
