/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package org.apache.hc.client5.http.utils;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;

import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.net.URIBuilder;
import org.junit.Assert;
import org.junit.Test;

/**
 * This TestCase contains test methods for URI resolving according to RFC 3986.
 * The examples are listed in section "5.4 Reference Resolution Examples"
 */
public class TestURIUtils {

    private final URI baseURI = URI.create("http://a/b/c/d;p?q");

    @Test
    public void testNormalization() {
        Assert.assertEquals("example://a/b/c/%7Bfoo%7D", URIUtils.resolve(this.baseURI, "eXAMPLE://a/./b/../b/%63/%7bfoo%7d").toString());
        Assert.assertEquals("http://www.example.com/%3C", URIUtils.resolve(this.baseURI, "http://www.example.com/%3c").toString());
        Assert.assertEquals("http://www.example.com/", URIUtils.resolve(this.baseURI, "HTTP://www.EXAMPLE.com/").toString());
        Assert.assertEquals("http://www.example.com/a%2F", URIUtils.resolve(this.baseURI, "http://www.example.com/a%2f").toString());
        Assert.assertEquals("http://www.example.com/?q=%26", URIUtils.resolve(this.baseURI, "http://www.example.com/?q=%26").toString());
        Assert.assertEquals("http://www.example.com/%23?q=%26", URIUtils.resolve(this.baseURI, "http://www.example.com/%23?q=%26").toString());
        Assert.assertEquals("http://www.example.com/blah-%28%20-blah-%20%26%20-blah-%20%29-blah/",
                URIUtils.resolve(this.baseURI, "http://www.example.com/blah-%28%20-blah-%20&%20-blah-%20)-blah/").toString());
    }

    @Test
    public void testResolve() {
        Assert.assertEquals("g:h", URIUtils.resolve(this.baseURI, "g:h").toString());
        Assert.assertEquals("http://a/b/c/g", URIUtils.resolve(this.baseURI, "g").toString());
        Assert.assertEquals("http://a/b/c/g", URIUtils.resolve(this.baseURI, "./g").toString());
        Assert.assertEquals("http://a/b/c/g/", URIUtils.resolve(this.baseURI, "g/").toString());
        Assert.assertEquals("http://a/g", URIUtils.resolve(this.baseURI, "/g").toString());
        Assert.assertEquals("http://g/", URIUtils.resolve(this.baseURI, "//g").toString());
        Assert.assertEquals("http://a/b/c/d;p?y", URIUtils.resolve(this.baseURI, "?y").toString());
        Assert.assertEquals("http://a/b/c/d;p?y#f", URIUtils.resolve(this.baseURI, "?y#f")
                .toString());
        Assert.assertEquals("http://a/b/c/g?y", URIUtils.resolve(this.baseURI, "g?y").toString());
        Assert.assertEquals("http://a/b/c/d%3Bp?q#s", URIUtils.resolve(this.baseURI, "#s")
                .toString());
        Assert.assertEquals("http://a/b/c/g#s", URIUtils.resolve(this.baseURI, "g#s").toString());
        Assert.assertEquals("http://a/b/c/g?y#s", URIUtils.resolve(this.baseURI, "g?y#s")
                .toString());
        Assert.assertEquals("http://a/b/c/%3Bx", URIUtils.resolve(this.baseURI, ";x").toString());
        Assert.assertEquals("http://a/b/c/g%3Bx", URIUtils.resolve(this.baseURI, "g;x").toString());
        Assert.assertEquals("http://a/b/c/g%3Bx?y#s", URIUtils.resolve(this.baseURI, "g;x?y#s")
                .toString());
        Assert.assertEquals("http://a/b/c/d%3Bp?q", URIUtils.resolve(this.baseURI, "").toString());
        Assert.assertEquals("http://a/b/c/", URIUtils.resolve(this.baseURI, ".").toString());
        Assert.assertEquals("http://a/b/c/", URIUtils.resolve(this.baseURI, "./").toString());
        Assert.assertEquals("http://a/b/", URIUtils.resolve(this.baseURI, "..").toString());
        Assert.assertEquals("http://a/b/", URIUtils.resolve(this.baseURI, "../").toString());
        Assert.assertEquals("http://a/b/g", URIUtils.resolve(this.baseURI, "../g").toString());
        Assert.assertEquals("http://a/", URIUtils.resolve(this.baseURI, "../..").toString());
        Assert.assertEquals("http://a/", URIUtils.resolve(this.baseURI, "../../").toString());
        Assert.assertEquals("http://a/g", URIUtils.resolve(this.baseURI, "../../g").toString());
        Assert.assertEquals("http://a/g", URIUtils.resolve(this.baseURI, "../../../g").toString());
        Assert.assertEquals("http://a/g", URIUtils.resolve(this.baseURI, "../../../../g")
                .toString());
        Assert.assertEquals("http://a/g", URIUtils.resolve(this.baseURI, "/./g").toString());
        Assert.assertEquals("http://a/g", URIUtils.resolve(this.baseURI, "/../g").toString());
        Assert.assertEquals("http://a/b/c/g.", URIUtils.resolve(this.baseURI, "g.").toString());
        Assert.assertEquals("http://a/b/c/.g", URIUtils.resolve(this.baseURI, ".g").toString());
        Assert.assertEquals("http://a/b/c/g..", URIUtils.resolve(this.baseURI, "g..").toString());
        Assert.assertEquals("http://a/b/c/..g", URIUtils.resolve(this.baseURI, "..g").toString());
        Assert.assertEquals("http://a/b/g", URIUtils.resolve(this.baseURI, "./../g").toString());
        Assert.assertEquals("http://a/b/c/g/", URIUtils.resolve(this.baseURI, "./g/.").toString());
        Assert.assertEquals("http://a/b/c/g/h", URIUtils.resolve(this.baseURI, "g/./h").toString());
        Assert.assertEquals("http://a/b/c/h", URIUtils.resolve(this.baseURI, "g/../h").toString());
        Assert.assertEquals("http://a/b/c/g%3Bx%3D1/y", URIUtils.resolve(this.baseURI, "g;x=1/./y")
                .toString());
        Assert.assertEquals("http://a/b/c/y", URIUtils.resolve(this.baseURI, "g;x=1/../y")
                .toString());
        Assert.assertEquals("http://a/b/c/g?y%2F.%2Fx", URIUtils.resolve(this.baseURI, "g?y/./x")
                .toString());
        Assert.assertEquals("http://a/b/c/g?y%2F..%2Fx", URIUtils.resolve(this.baseURI, "g?y/../x")
                .toString());
        Assert.assertEquals("http://a/b/c/g#s%2F.%2Fx", URIUtils.resolve(this.baseURI, "g#s/./x")
                .toString());
        Assert.assertEquals("http://a/b/c/g#s%2F..%2Fx", URIUtils.resolve(this.baseURI, "g#s/../x")
                .toString());
        Assert.assertEquals("http:g", URIUtils.resolve(this.baseURI, "http:g").toString());
        // examples from section 5.2.4
        Assert.assertEquals("http://s/a/g", URIUtils.resolve(this.baseURI,
                "http://s/a/b/c/./../../g").toString());
        Assert.assertEquals("http://s/mid/6", URIUtils.resolve(this.baseURI,
                "http://s/mid/content=5/../6").toString());
    }

    @Test
    public void testResolveOpaque() {
        Assert.assertEquals("example://a/b/c/%7Bfoo%7D", URIUtils.resolve(this.baseURI, "eXAMPLE://a/./b/../b/%63/%7bfoo%7d").toString());
        Assert.assertEquals("file://localhost/etc/fstab", URIUtils.resolve(this.baseURI, "file://localhost/etc/fstab").toString());
        Assert.assertEquals("file:///etc/fstab", URIUtils.resolve(this.baseURI, "file:///etc/fstab").toString());
        Assert.assertEquals("file://localhost/c%3A/WINDOWS/clock.avi", URIUtils.resolve(this.baseURI, "file://localhost/c:/WINDOWS/clock.avi").toString());
        Assert.assertEquals("file:///c:/WINDOWS/clock.avi", URIUtils.resolve(this.baseURI, "file:///c:/WINDOWS/clock.avi").toString());
        Assert.assertEquals("file://hostname/path/to/the%20file.txt", URIUtils.resolve(this.baseURI, "file://hostname/path/to/the%20file.txt").toString());
        Assert.assertEquals("file:///c:/path/to/the%20file.txt", URIUtils.resolve(this.baseURI, "file:///c:/path/to/the%20file.txt").toString());
        Assert.assertEquals("urn:issn:1535-3613", URIUtils.resolve(this.baseURI, "urn:issn:1535-3613").toString());
        Assert.assertEquals("mailto:user@example.com", URIUtils.resolve(this.baseURI, "mailto:user@example.com").toString());
        Assert.assertEquals("ftp://example.org/resource.txt", URIUtils.resolve(this.baseURI, "ftp://example.org/resource.txt").toString());
    }

    @Test
    public void testExtractHost() throws Exception {
        Assert.assertEquals(new HttpHost("localhost"),
                URIUtils.extractHost(new URI("http://localhost/abcd")));
        Assert.assertEquals(new HttpHost("localhost"),
                URIUtils.extractHost(new URI("http://localhost/abcd%3A")));

        Assert.assertEquals(new HttpHost("local_host"),
                URIUtils.extractHost(new URI("http://local_host/abcd")));
        Assert.assertEquals(new HttpHost("local_host"),
                URIUtils.extractHost(new URI("http://local_host/abcd%3A")));

        Assert.assertEquals(new HttpHost("localhost",8),
                URIUtils.extractHost(new URI("http://localhost:8/abcd")));
        Assert.assertEquals(new HttpHost("local_host",8),
                URIUtils.extractHost(new URI("http://local_host:8/abcd")));

        // URI seems to OK with missing port number
        Assert.assertEquals(new HttpHost("localhost",-1),URIUtils.extractHost(
                new URI("http://localhost:/abcd")));
        Assert.assertEquals(new HttpHost("local_host",-1),URIUtils.extractHost(
                new URI("http://local_host:/abcd")));

        Assert.assertEquals(new HttpHost("localhost",8080),
                URIUtils.extractHost(new URI("http://user:pass@localhost:8080/abcd")));
        Assert.assertEquals(new HttpHost("local_host",8080),
                URIUtils.extractHost(new URI("http://user:pass@local_host:8080/abcd")));

        Assert.assertEquals(new HttpHost("localhost",8080),URIUtils.extractHost(
                new URI("http://@localhost:8080/abcd")));
        Assert.assertEquals(new HttpHost("local_host",8080),URIUtils.extractHost(
                new URI("http://@local_host:8080/abcd")));

        Assert.assertEquals(new HttpHost("2a00:1450:400c:c01::69",8080),
                URIUtils.extractHost(new URI("http://[2a00:1450:400c:c01::69]:8080/")));

        Assert.assertEquals(new HttpHost("localhost",8080),
                URIUtils.extractHost(new URI("http://localhost:8080/;sessionid=stuff/abcd")));
        Assert.assertNull(URIUtils.extractHost(new URI("http://localhost:8080;sessionid=stuff/abcd")));
        Assert.assertNull(URIUtils.extractHost(new URI("http://localhost:;sessionid=stuff/abcd")));
        Assert.assertNull(URIUtils.extractHost(new URI("http://:80/robots.txt")));
        Assert.assertNull(URIUtils.extractHost(new URI("http://some%20domain:80/robots.txt")));
    }

    @Test
    public void testHttpLocationWithRelativeFragment() throws Exception {
        final HttpHost target = new HttpHost("http", "localhost", -1);
        final URI requestURI = new URI("/stuff#blahblah");

        final URI location = URIUtils.resolve(requestURI, target, null);
        final URI expectedURI = new URIBuilder(requestURI)
                .setHost(target.getHostName())
                .setScheme(target.getSchemeName())
                .build();
        Assert.assertEquals(expectedURI, location);
    }

    @Test
    public void testHttpLocationWithAbsoluteFragment() throws Exception {
        final HttpHost target = new HttpHost("http", "localhost", 80);

        final URI requestURI = new URIBuilder()
            .setHost(target.getHostName())
            .setScheme(target.getSchemeName())
            .setPath("/stuff")
            .setFragment("blahblah")
            .build();

        final URI location = URIUtils.resolve(requestURI, target, null);
        final URI expectedURI = requestURI;
        Assert.assertEquals(expectedURI, location);
    }

    @Test
    public void testHttpLocationRedirect() throws Exception {
        final HttpHost target = new HttpHost("http", "localhost", -1);
        final URI requestURI = new URI("/People.htm#tim");

        final URI redirect = new URI("http://localhost/people.html");

        final URI location = URIUtils.resolve(requestURI, target, Collections.singletonList(redirect));
        final URI expectedURI = new URIBuilder()
                .setHost(target.getHostName())
                .setScheme(target.getSchemeName())
                .setPath("/people.html")
                .setFragment("tim")
                .build();
        Assert.assertEquals(expectedURI, location);
    }

    @Test
    public void testHttpLocationWithRedirectFragment() throws Exception {
        final HttpHost target = new HttpHost("http", "localhost", -1);
        final URI requestURI = new URI("/~tim");

        final URI redirect1 = new URI("http://localhost/People.htm#tim");
        final URI redirect2 = new URI("http://localhost/people.html");

        final URI location = URIUtils.resolve(requestURI, target, Arrays.asList(redirect1, redirect2));
        final URI expectedURI = new URIBuilder()
                .setHost(target.getHostName())
                .setScheme(target.getSchemeName())
                .setPath("/people.html")
                .setFragment("tim")
                .build();
        Assert.assertEquals(expectedURI, location);
    }

}
