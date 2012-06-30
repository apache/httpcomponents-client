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

import org.junit.Assert;
import org.junit.Test;

public class TestURIBuilder {

    @Test
    public void testHierarchicalUri() throws Exception {
        URI uri = new URI("http", "stuff", "localhost", 80, "/some stuff", "param=stuff", "fragment");
        URIBuilder uribuilder = new URIBuilder(uri);
        URI result = uribuilder.build();
        Assert.assertEquals(new URI("http://stuff@localhost:80/some%20stuff?param=stuff#fragment"), result);
    }

    @Test
    public void testMutationToRelativeUri() throws Exception {
        URI uri = new URI("http://stuff@localhost:80/stuff?param=stuff#fragment");
        URIBuilder uribuilder = new URIBuilder(uri).setHost(null);
        URI result = uribuilder.build();
        Assert.assertEquals(new URI("http:///stuff?param=stuff#fragment"), result);
    }

    @Test
    public void testMutationRemoveFragment() throws Exception {
        URI uri = new URI("http://stuff@localhost:80/stuff?param=stuff#fragment");
        URI result = new URIBuilder(uri).setFragment(null).build();
        Assert.assertEquals(new URI("http://stuff@localhost:80/stuff?param=stuff"), result);
    }

    @Test
    public void testMutationRemoveUserInfo() throws Exception {
        URI uri = new URI("http://stuff@localhost:80/stuff?param=stuff#fragment");
        URI result = new URIBuilder(uri).setUserInfo(null).build();
        Assert.assertEquals(new URI("http://localhost:80/stuff?param=stuff#fragment"), result);
    }

    @Test
    public void testMutationRemovePort() throws Exception {
        URI uri = new URI("http://stuff@localhost:80/stuff?param=stuff#fragment");
        URI result = new URIBuilder(uri).setPort(-1).build();
        Assert.assertEquals(new URI("http://stuff@localhost/stuff?param=stuff#fragment"), result);
    }

    @Test
    public void testOpaqueUri() throws Exception {
        URI uri = new URI("stuff", "some-stuff", "fragment");
        URIBuilder uribuilder = new URIBuilder(uri);
        URI result = uribuilder.build();
        Assert.assertEquals(uri, result);
    }

    @Test
    public void testOpaqueUriMutation() throws Exception {
        URI uri = new URI("stuff", "some-stuff", "fragment");
        URIBuilder uribuilder = new URIBuilder(uri).setQuery("param1&param2=stuff").setFragment(null);
        Assert.assertEquals(new URI("stuff:?param1&param2=stuff"), uribuilder.build());
    }

    @Test
    public void testHierarchicalUriMutation() throws Exception {
        URIBuilder uribuilder = new URIBuilder("/").setScheme("http").setHost("localhost").setPort(80).setPath("/stuff");
        Assert.assertEquals(new URI("http://localhost:80/stuff"), uribuilder.build());
    }

    @Test
    public void testEmpty() throws Exception {
        URIBuilder uribuilder = new URIBuilder();
        URI result = uribuilder.build();
        Assert.assertEquals(new URI(""), result);
    }

    @Test
    public void testSetUserInfo() throws Exception {
        URI uri = new URI("http", null, "localhost", 80, "/", "param=stuff", null);
        URIBuilder uribuilder = new URIBuilder(uri).setUserInfo("user", "password");
        URI result = uribuilder.build();
        Assert.assertEquals(new URI("http://user:password@localhost:80/?param=stuff"), result);
    }

    @Test
    public void testRemoveParameters() throws Exception {
        URI uri = new URI("http", null, "localhost", 80, "/", "param=stuff", null);
        URIBuilder uribuilder = new URIBuilder(uri).removeQuery();
        URI result = uribuilder.build();
        Assert.assertEquals(new URI("http://localhost:80/"), result);
    }

    @Test
    public void testSetParameter() throws Exception {
        URI uri = new URI("http", null, "localhost", 80, "/", "param=stuff&blah&blah", null);
        URIBuilder uribuilder = new URIBuilder(uri).setParameter("param", "some other stuff")
            .setParameter("blah", "blah");
        URI result = uribuilder.build();
        Assert.assertEquals(new URI("http://localhost:80/?param=some+other+stuff&blah=blah"), result);
    }

    @Test
    public void testParameterWithSpecialChar() throws Exception {
        URI uri = new URI("http", null, "localhost", 80, "/", "param=stuff", null);
        URIBuilder uribuilder = new URIBuilder(uri).addParameter("param", "1 + 1 = 2")
            .addParameter("param", "blah&blah");
        URI result = uribuilder.build();
        Assert.assertEquals(new URI("http://localhost:80/?param=stuff&param=1+%2B+1+%3D+2&" +
                "param=blah%26blah"), result);
    }

    @Test
    public void testAddParameter() throws Exception {
        URI uri = new URI("http", null, "localhost", 80, "/", "param=stuff&blah&blah", null);
        URIBuilder uribuilder = new URIBuilder(uri).addParameter("param", "some other stuff")
            .addParameter("blah", "blah");
        URI result = uribuilder.build();
        Assert.assertEquals(new URI("http://localhost:80/?param=stuff&blah&blah&" +
                "param=some+other+stuff&blah=blah"), result);
    }

    @Test
    public void testQueryEncoding() throws Exception {
        URI uri1 = new URI("https://somehost.com/stuff?client_id=1234567890" +
                "&redirect_uri=https%3A%2F%2Fsomehost.com%2Fblah+blah%2F");
        URI uri2 = new URIBuilder("https://somehost.com/stuff")
            .addParameter("client_id","1234567890")
            .addParameter("redirect_uri","https://somehost.com/blah blah/").build();
        Assert.assertEquals(uri1, uri2);
    }

    @Test
    public void testPathEncoding() throws Exception {
        URI uri1 = new URI("https://somehost.com/some%20path%20with%20blanks/");
        URI uri2 = new URIBuilder()
            .setScheme("https")
            .setHost("somehost.com")
            .setPath("/some path with blanks/")
            .build();
        Assert.assertEquals(uri1, uri2);
    }

    @Test
    public void testAgainstURI() throws Exception {
        // Check that the URI generated by URI builder agrees with that generated by using URI directly
        final String scheme="https";
        final String host="localhost";
        final String specials="/abcd!$&*()_-+.,=:;'~@[]?<>|#^%\"{}\\\u00a3`\u00ac\u00a6xyz"; // N.B. excludes space
        URI uri = new URI(scheme, specials, host, 80, specials, specials, specials);

        URI bld = new URIBuilder()
                .setScheme(scheme)
                .setHost(host)
                .setUserInfo(specials)
                .setPath(specials)
                .addParameter(specials, null) // hack to bypass parsing of query data
                .setFragment(specials)
                .build();

        Assert.assertEquals(uri.getHost(), bld.getHost());
        
        Assert.assertEquals(uri.getUserInfo(), bld.getUserInfo());
        
        Assert.assertEquals(uri.getPath(), bld.getPath());

        Assert.assertEquals(uri.getQuery(), bld.getQuery());

        Assert.assertEquals(uri.getFragment(), bld.getFragment());

    }

    @Test
    public void testAgainstURIEncoded() throws Exception {
        // Check that the encoded URI generated by URI builder agrees with that generated by using URI directly
        final String scheme="https";
        final String host="localhost";
        final String specials="/ abcd!$&*()_-+.,=:;'~<>/@[]|#^%\"{}\\`xyz"; // N.B. excludes \u00a3`\u00ac\u00a6
        final String formdatasafe = "abcd-_.*zyz";
        URI uri = new URI(scheme, specials, host, 80, specials,
                          formdatasafe, // TODO replace with specials when supported
                          specials);

        URI bld = new URIBuilder()
                .setScheme(scheme)
                .setHost(host)
                .setUserInfo(specials)
                .setPath(specials)
                .addParameter(formdatasafe, null) // TODO replace with specials when supported
                .setFragment(specials)
                .build();

        Assert.assertEquals(uri.getHost(), bld.getHost());
        
        Assert.assertEquals(uri.getRawUserInfo(), bld.getRawUserInfo());
        
        Assert.assertEquals(uri.getRawPath(), bld.getRawPath());

        Assert.assertEquals(uri.getRawQuery(), bld.getRawQuery());

        Assert.assertEquals(uri.getRawFragment(), bld.getRawFragment());

    }

}
