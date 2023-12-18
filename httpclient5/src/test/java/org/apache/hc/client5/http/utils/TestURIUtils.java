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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * This TestCase contains test methods for URI resolving according to RFC 3986.
 * The examples are listed in section "5.4 Reference Resolution Examples"
 */
public class TestURIUtils {

    private final URI baseURI = URI.create("http://a/b/c/d;p?q");

    @Test
    public void testResolve() {
        Assertions.assertEquals("g:h", URIUtils.resolve(this.baseURI, "g:h").toString());
        Assertions.assertEquals("http://a/b/c/g", URIUtils.resolve(this.baseURI, "g").toString());
        Assertions.assertEquals("http://a/b/c/g", URIUtils.resolve(this.baseURI, "./g").toString());
        Assertions.assertEquals("http://a/b/c/g/", URIUtils.resolve(this.baseURI, "g/").toString());
        Assertions.assertEquals("http://a/g", URIUtils.resolve(this.baseURI, "/g").toString());
        Assertions.assertEquals("http://g/", URIUtils.resolve(this.baseURI, "//g").toString());
        Assertions.assertEquals("http://a/b/c/d;p?y", URIUtils.resolve(this.baseURI, "?y").toString());
        Assertions.assertEquals("http://a/b/c/d;p?y#f", URIUtils.resolve(this.baseURI, "?y#f")
                .toString());
        Assertions.assertEquals("http://a/b/c/g?y", URIUtils.resolve(this.baseURI, "g?y").toString());
        Assertions.assertEquals("http://a/b/c/d;p?q#s", URIUtils.resolve(this.baseURI, "#s")
                .toString());
        Assertions.assertEquals("http://a/b/c/g#s", URIUtils.resolve(this.baseURI, "g#s").toString());
        Assertions.assertEquals("http://a/b/c/g?y#s", URIUtils.resolve(this.baseURI, "g?y#s")
                .toString());
        Assertions.assertEquals("http://a/b/c/;x", URIUtils.resolve(this.baseURI, ";x").toString());
        Assertions.assertEquals("http://a/b/c/g;x", URIUtils.resolve(this.baseURI, "g;x").toString());
        Assertions.assertEquals("http://a/b/c/g;x?y#s", URIUtils.resolve(this.baseURI, "g;x?y#s")
                .toString());
        Assertions.assertEquals("http://a/b/c/d;p?q", URIUtils.resolve(this.baseURI, "").toString());
        Assertions.assertEquals("http://a/b/c/", URIUtils.resolve(this.baseURI, ".").toString());
        Assertions.assertEquals("http://a/b/c/", URIUtils.resolve(this.baseURI, "./").toString());
        Assertions.assertEquals("http://a/b/", URIUtils.resolve(this.baseURI, "..").toString());
        Assertions.assertEquals("http://a/b/", URIUtils.resolve(this.baseURI, "../").toString());
        Assertions.assertEquals("http://a/b/g", URIUtils.resolve(this.baseURI, "../g").toString());
        Assertions.assertEquals("http://a/", URIUtils.resolve(this.baseURI, "../..").toString());
        Assertions.assertEquals("http://a/", URIUtils.resolve(this.baseURI, "../../").toString());
        Assertions.assertEquals("http://a/g", URIUtils.resolve(this.baseURI, "../../g").toString());
        Assertions.assertEquals("http://a/../g", URIUtils.resolve(this.baseURI, "../../../g").toString());
        Assertions.assertEquals("http://a/../../g", URIUtils.resolve(this.baseURI, "../../../../g")
                .toString());
        Assertions.assertEquals("http://a/./g", URIUtils.resolve(this.baseURI, "/./g").toString());
        Assertions.assertEquals("http://a/../g", URIUtils.resolve(this.baseURI, "/../g").toString());
        Assertions.assertEquals("http://a/b/c/g.", URIUtils.resolve(this.baseURI, "g.").toString());
        Assertions.assertEquals("http://a/b/c/.g", URIUtils.resolve(this.baseURI, ".g").toString());
        Assertions.assertEquals("http://a/b/c/g..", URIUtils.resolve(this.baseURI, "g..").toString());
        Assertions.assertEquals("http://a/b/c/..g", URIUtils.resolve(this.baseURI, "..g").toString());
        Assertions.assertEquals("http://a/b/g", URIUtils.resolve(this.baseURI, "./../g").toString());
        Assertions.assertEquals("http://a/b/c/g/", URIUtils.resolve(this.baseURI, "./g/.").toString());
        Assertions.assertEquals("http://a/b/c/g/h", URIUtils.resolve(this.baseURI, "g/./h").toString());
        Assertions.assertEquals("http://a/b/c/h", URIUtils.resolve(this.baseURI, "g/../h").toString());
        Assertions.assertEquals("http://a/b/c/g;x=1/y", URIUtils.resolve(this.baseURI, "g;x=1/./y")
                .toString());
        Assertions.assertEquals("http://a/b/c/y", URIUtils.resolve(this.baseURI, "g;x=1/../y")
                .toString());
        Assertions.assertEquals("http://a/b/c/g?y/./x", URIUtils.resolve(this.baseURI, "g?y/./x")
                .toString());
        Assertions.assertEquals("http://a/b/c/g?y/../x", URIUtils.resolve(this.baseURI, "g?y/../x")
                .toString());
        Assertions.assertEquals("http://a/b/c/g#s/./x", URIUtils.resolve(this.baseURI, "g#s/./x")
                .toString());
        Assertions.assertEquals("http://a/b/c/g#s/../x", URIUtils.resolve(this.baseURI, "g#s/../x")
                .toString());
        Assertions.assertEquals("http:g", URIUtils.resolve(this.baseURI, "http:g").toString());
        // examples from section 5.2.4
        Assertions.assertEquals("http://s/a/b/c/./../../g", URIUtils.resolve(this.baseURI,
                "http://s/a/b/c/./../../g").toString());
        Assertions.assertEquals("http://s/mid/content=5/../6", URIUtils.resolve(this.baseURI,
                "http://s/mid/content=5/../6").toString());
    }

    @Test
    public void testResolveOpaque() {
        Assertions.assertEquals("example://a/./b/../b/%63/%7bfoo%7d", URIUtils.resolve(this.baseURI, "eXAMPLE://a/./b/../b/%63/%7bfoo%7d").toString());
        Assertions.assertEquals("file://localhost/etc/fstab", URIUtils.resolve(this.baseURI, "file://localhost/etc/fstab").toString());
        Assertions.assertEquals("file:///etc/fstab", URIUtils.resolve(this.baseURI, "file:///etc/fstab").toString());
        Assertions.assertEquals("file://localhost/c:/WINDOWS/clock.avi", URIUtils.resolve(this.baseURI, "file://localhost/c:/WINDOWS/clock.avi").toString());
        Assertions.assertEquals("file:///c:/WINDOWS/clock.avi", URIUtils.resolve(this.baseURI, "file:///c:/WINDOWS/clock.avi").toString());
        Assertions.assertEquals("file://hostname/path/to/the%20file.txt", URIUtils.resolve(this.baseURI, "file://hostname/path/to/the%20file.txt").toString());
        Assertions.assertEquals("file:///c:/path/to/the%20file.txt", URIUtils.resolve(this.baseURI, "file:///c:/path/to/the%20file.txt").toString());
        Assertions.assertEquals("urn:issn:1535-3613", URIUtils.resolve(this.baseURI, "urn:issn:1535-3613").toString());
        Assertions.assertEquals("mailto:user@example.com", URIUtils.resolve(this.baseURI, "mailto:user@example.com").toString());
        Assertions.assertEquals("ftp://example.org/resource.txt", URIUtils.resolve(this.baseURI, "ftp://example.org/resource.txt").toString());
    }

    @Test
    public void testExtractHost() throws Exception {
        Assertions.assertEquals(new HttpHost("localhost"),
                URIUtils.extractHost(new URI("http://localhost/abcd")));
        Assertions.assertEquals(new HttpHost("localhost"),
                URIUtils.extractHost(new URI("http://localhost/abcd%3A")));

        Assertions.assertEquals(new HttpHost("local_host"),
                URIUtils.extractHost(new URI("http://local_host/abcd")));
        Assertions.assertEquals(new HttpHost("local_host"),
                URIUtils.extractHost(new URI("http://local_host/abcd%3A")));

        Assertions.assertEquals(new HttpHost("localhost",8),
                URIUtils.extractHost(new URI("http://localhost:8/abcd")));
        Assertions.assertEquals(new HttpHost("local_host",8),
                URIUtils.extractHost(new URI("http://local_host:8/abcd")));

        // URI seems to OK with missing port number
        Assertions.assertEquals(new HttpHost("localhost",-1),URIUtils.extractHost(
                new URI("http://localhost:/abcd")));
        Assertions.assertEquals(new HttpHost("local_host",-1),URIUtils.extractHost(
                new URI("http://local_host:/abcd")));

        Assertions.assertEquals(new HttpHost("localhost",8080),
                URIUtils.extractHost(new URI("http://user:pass@localhost:8080/abcd")));

        Assertions.assertEquals(new HttpHost("local_host",8080),
                URIUtils.extractHost(new URI("http://user:pass@local_host:8080/abcd")));

        Assertions.assertEquals(new HttpHost("localhost",8080),URIUtils.extractHost(
                new URI("http://@localhost:8080/abcd")));
        Assertions.assertEquals(new HttpHost("local_host",8080),URIUtils.extractHost(
                new URI("http://@local_host:8080/abcd")));

        Assertions.assertEquals(new HttpHost("2a00:1450:400c:c01::69",8080),
                URIUtils.extractHost(new URI("http://[2a00:1450:400c:c01::69]:8080/")));

        Assertions.assertEquals(new HttpHost("localhost",8080),
                URIUtils.extractHost(new URI("http://localhost:8080/;sessionid=stuff/abcd")));
        Assertions.assertNull(URIUtils.extractHost(new URI("http://localhost:8080;sessionid=stuff/abcd")));
        Assertions.assertNull(URIUtils.extractHost(new URI("http://localhost:;sessionid=stuff/abcd")));
        Assertions.assertNull(URIUtils.extractHost(new URI("http://:80/robots.txt")));
        Assertions.assertNull(URIUtils.extractHost(new URI("http://some%20domain:80/robots.txt")));
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
        Assertions.assertEquals(expectedURI, location);
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
        Assertions.assertEquals(expectedURI, location);
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
        Assertions.assertEquals(expectedURI, location);
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
        Assertions.assertEquals(expectedURI, location);
    }

}
