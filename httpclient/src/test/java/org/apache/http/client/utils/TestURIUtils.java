/*
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package org.apache.http.client.utils;

import java.net.URI;

import org.apache.http.HttpHost;
import org.junit.Assert;
import org.junit.Test;

/**
 * This TestCase contains test methods for URI resolving according to RFC 3986.
 * The examples are listed in section "5.4 Reference Resolution Examples"
 */
public class TestURIUtils {

    private URI baseURI = URI.create("http://a/b/c/d;p?q");

    @Test
    public void testRewrite() throws Exception {
        HttpHost target = new HttpHost("thathost", -1);
        Assert.assertEquals("http://thathost/stuff", URIUtils.rewriteURI(
                URI.create("http://thishost/stuff"), target).toString());
        Assert.assertEquals("/stuff", URIUtils.rewriteURI(
                URI.create("http://thishost/stuff"), null).toString());
        Assert.assertEquals("/", URIUtils.rewriteURI(
                URI.create("http://thishost//"), null).toString());
        Assert.assertEquals("/stuff///morestuff", URIUtils.rewriteURI(
                URI.create("http://thishost//stuff///morestuff"), null).toString());
        Assert.assertEquals("http://thathost/stuff", URIUtils.rewriteURI(
                URI.create("http://thishost/stuff#crap"), target, true).toString());
        Assert.assertEquals("http://thathost/stuff#crap", URIUtils.rewriteURI(
                URI.create("http://thishost/stuff#crap"), target, false).toString());
        Assert.assertEquals("http://thathost/", URIUtils.rewriteURI(
                URI.create("http://thishost#crap"), target, true).toString());
        Assert.assertEquals("http://thathost/#crap", URIUtils.rewriteURI(
                URI.create("http://thishost#crap"), target, false).toString());
        Assert.assertEquals("/stuff/", URIUtils.rewriteURI(
                URI.create("http://thishost//////////////stuff/"), null).toString());
        Assert.assertEquals("http://thathost/stuff", URIUtils.rewriteURI(
                URI.create("http://thathost/stuff")).toString());
        Assert.assertEquals("http://thathost/stuff", URIUtils.rewriteURI(
                URI.create("http://thathost/stuff#fragment")).toString());
        Assert.assertEquals("http://thathost/stuff", URIUtils.rewriteURI(
                URI.create("http://userinfo@thathost/stuff#fragment")).toString());
        Assert.assertEquals("http://thathost/", URIUtils.rewriteURI(
                URI.create("http://thathost")).toString());
    }

    @Test
    public void testRewritePort() throws Exception {
        HttpHost target = new HttpHost("thathost", 8080); // port should be copied
        Assert.assertEquals("http://thathost:8080/stuff", URIUtils.rewriteURI(
                URI.create("http://thishost:80/stuff#crap"), target, true).toString());
        Assert.assertEquals("http://thathost:8080/stuff#crap", URIUtils.rewriteURI(
                URI.create("http://thishost:80/stuff#crap"), target, false).toString());
        target = new HttpHost("thathost", -1); // input port should be dropped
        Assert.assertEquals("http://thathost/stuff", URIUtils.rewriteURI(
                URI.create("http://thishost:80/stuff#crap"), target, true).toString());
        Assert.assertEquals("http://thathost/stuff#crap", URIUtils.rewriteURI(
                URI.create("http://thishost:80/stuff#crap"), target, false).toString());
    }

    @Test
    public void testRewriteScheme() throws Exception {
        HttpHost target = new HttpHost("thathost", -1, "file"); // scheme should be copied
        Assert.assertEquals("file://thathost/stuff", URIUtils.rewriteURI(
                URI.create("http://thishost:80/stuff#crap"), target, true).toString());
    }

    @Test
    public void testResolve() {
        Assert.assertEquals("g:h", URIUtils.resolve(this.baseURI, "g:h").toString());
        Assert.assertEquals("http://a/b/c/g", URIUtils.resolve(this.baseURI, "g").toString());
        Assert.assertEquals("http://a/b/c/g", URIUtils.resolve(this.baseURI, "./g").toString());
        Assert.assertEquals("http://a/b/c/g/", URIUtils.resolve(this.baseURI, "g/").toString());
        Assert.assertEquals("http://a/g", URIUtils.resolve(this.baseURI, "/g").toString());
        Assert.assertEquals("http://g", URIUtils.resolve(this.baseURI, "//g").toString());
        Assert.assertEquals("http://a/b/c/d;p?y", URIUtils.resolve(this.baseURI, "?y").toString());
        Assert.assertEquals("http://a/b/c/d;p?y#f", URIUtils.resolve(this.baseURI, "?y#f")
                .toString());
        Assert.assertEquals("http://a/b/c/g?y", URIUtils.resolve(this.baseURI, "g?y").toString());
        Assert.assertEquals("http://a/b/c/d;p?q#s", URIUtils.resolve(this.baseURI, "#s")
                .toString());
        Assert.assertEquals("http://a/b/c/g#s", URIUtils.resolve(this.baseURI, "g#s").toString());
        Assert.assertEquals("http://a/b/c/g?y#s", URIUtils.resolve(this.baseURI, "g?y#s")
                .toString());
        Assert.assertEquals("http://a/b/c/;x", URIUtils.resolve(this.baseURI, ";x").toString());
        Assert.assertEquals("http://a/b/c/g;x", URIUtils.resolve(this.baseURI, "g;x").toString());
        Assert.assertEquals("http://a/b/c/g;x?y#s", URIUtils.resolve(this.baseURI, "g;x?y#s")
                .toString());
        Assert.assertEquals("http://a/b/c/d;p?q", URIUtils.resolve(this.baseURI, "").toString());
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
        Assert.assertEquals("http://a/b/c/g;x=1/y", URIUtils.resolve(this.baseURI, "g;x=1/./y")
                .toString());
        Assert.assertEquals("http://a/b/c/y", URIUtils.resolve(this.baseURI, "g;x=1/../y")
                .toString());
        Assert.assertEquals("http://a/b/c/g?y/./x", URIUtils.resolve(this.baseURI, "g?y/./x")
                .toString());
        Assert.assertEquals("http://a/b/c/g?y/../x", URIUtils.resolve(this.baseURI, "g?y/../x")
                .toString());
        Assert.assertEquals("http://a/b/c/g#s/./x", URIUtils.resolve(this.baseURI, "g#s/./x")
                .toString());
        Assert.assertEquals("http://a/b/c/g#s/../x", URIUtils.resolve(this.baseURI, "g#s/../x")
                .toString());
        Assert.assertEquals("http:g", URIUtils.resolve(this.baseURI, "http:g").toString());
        // examples from section 5.2.4
        Assert.assertEquals("http://s/a/g", URIUtils.resolve(this.baseURI,
                "http://s/a/b/c/./../../g").toString());
        Assert.assertEquals("http://s/mid/6", URIUtils.resolve(this.baseURI,
                "http://s/mid/content=5/../6").toString());
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

        Assert.assertEquals(new HttpHost("[2a00:1450:400c:c01::69]",8080),
                URIUtils.extractHost(new URI("http://[2a00:1450:400c:c01::69]:8080/")));

        Assert.assertEquals(new HttpHost("localhost",8080),
                URIUtils.extractHost(new URI("http://localhost:8080/;sessionid=stuff/abcd")));
        Assert.assertEquals(new HttpHost("localhost",8080),
                URIUtils.extractHost(new URI("http://localhost:8080;sessionid=stuff/abcd")));
        Assert.assertEquals(new HttpHost("localhost",-1),
                URIUtils.extractHost(new URI("http://localhost:;sessionid=stuff/abcd")));
    }

}
