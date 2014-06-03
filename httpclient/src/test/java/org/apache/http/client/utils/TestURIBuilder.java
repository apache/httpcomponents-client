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
package org.apache.http.client.utils;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.Consts;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.junit.Assert;
import org.junit.Test;

public class TestURIBuilder {

    @Test
    public void testHierarchicalUri() throws Exception {
        final URI uri = new URI("http", "stuff", "localhost", 80, "/some stuff", "param=stuff", "fragment");
        final URIBuilder uribuilder = new URIBuilder(uri);
        final URI result = uribuilder.build();
        Assert.assertEquals(new URI("http://stuff@localhost:80/some%20stuff?param=stuff#fragment"), result);
    }

    @Test
    public void testMutationToRelativeUri() throws Exception {
        final URI uri = new URI("http://stuff@localhost:80/stuff?param=stuff#fragment");
        final URIBuilder uribuilder = new URIBuilder(uri).setHost(null);
        final URI result = uribuilder.build();
        Assert.assertEquals(new URI("http:///stuff?param=stuff#fragment"), result);
    }

    @Test
    public void testMutationRemoveFragment() throws Exception {
        final URI uri = new URI("http://stuff@localhost:80/stuff?param=stuff#fragment");
        final URI result = new URIBuilder(uri).setFragment(null).build();
        Assert.assertEquals(new URI("http://stuff@localhost:80/stuff?param=stuff"), result);
    }

    @Test
    public void testMutationRemoveUserInfo() throws Exception {
        final URI uri = new URI("http://stuff@localhost:80/stuff?param=stuff#fragment");
        final URI result = new URIBuilder(uri).setUserInfo(null).build();
        Assert.assertEquals(new URI("http://localhost:80/stuff?param=stuff#fragment"), result);
    }

    @Test
    public void testMutationRemovePort() throws Exception {
        final URI uri = new URI("http://stuff@localhost:80/stuff?param=stuff#fragment");
        final URI result = new URIBuilder(uri).setPort(-1).build();
        Assert.assertEquals(new URI("http://stuff@localhost/stuff?param=stuff#fragment"), result);
    }

    @Test
    public void testOpaqueUri() throws Exception {
        final URI uri = new URI("stuff", "some-stuff", "fragment");
        final URIBuilder uribuilder = new URIBuilder(uri);
        final URI result = uribuilder.build();
        Assert.assertEquals(uri, result);
    }

    @Test
    public void testOpaqueUriMutation() throws Exception {
        final URI uri = new URI("stuff", "some-stuff", "fragment");
        final URIBuilder uribuilder = new URIBuilder(uri).setCustomQuery("param1&param2=stuff").setFragment(null);
        Assert.assertEquals(new URI("stuff:?param1&param2=stuff"), uribuilder.build());
    }

    @Test
    public void testHierarchicalUriMutation() throws Exception {
        final URIBuilder uribuilder = new URIBuilder("/").setScheme("http").setHost("localhost").setPort(80).setPath("/stuff");
        Assert.assertEquals(new URI("http://localhost:80/stuff"), uribuilder.build());
    }

    @Test
    public void testEmpty() throws Exception {
        final URIBuilder uribuilder = new URIBuilder();
        final URI result = uribuilder.build();
        Assert.assertEquals(new URI(""), result);
    }

    @Test
    public void testSetUserInfo() throws Exception {
        final URI uri = new URI("http", null, "localhost", 80, "/", "param=stuff", null);
        final URIBuilder uribuilder = new URIBuilder(uri).setUserInfo("user", "password");
        final URI result = uribuilder.build();
        Assert.assertEquals(new URI("http://user:password@localhost:80/?param=stuff"), result);
    }

    @Test
    public void testRemoveParameters() throws Exception {
        final URI uri = new URI("http", null, "localhost", 80, "/", "param=stuff", null);
        final URIBuilder uribuilder = new URIBuilder(uri).removeQuery();
        final URI result = uribuilder.build();
        Assert.assertEquals(new URI("http://localhost:80/"), result);
    }

    @Test
    public void testSetParameter() throws Exception {
        final URI uri = new URI("http", null, "localhost", 80, "/", "param=stuff&blah&blah", null);
        final URIBuilder uribuilder = new URIBuilder(uri).setParameter("param", "some other stuff")
            .setParameter("blah", "blah");
        final URI result = uribuilder.build();
        Assert.assertEquals(new URI("http://localhost:80/?param=some+other+stuff&blah=blah"), result);
    }

    @Test
    public void testParameterWithSpecialChar() throws Exception {
        final URI uri = new URI("http", null, "localhost", 80, "/", "param=stuff", null);
        final URIBuilder uribuilder = new URIBuilder(uri).addParameter("param", "1 + 1 = 2")
            .addParameter("param", "blah&blah");
        final URI result = uribuilder.build();
        Assert.assertEquals(new URI("http://localhost:80/?param=stuff&param=1+%2B+1+%3D+2&" +
                "param=blah%26blah"), result);
    }

    @Test
    public void testAddParameter() throws Exception {
        final URI uri = new URI("http", null, "localhost", 80, "/", "param=stuff&blah&blah", null);
        final URIBuilder uribuilder = new URIBuilder(uri).addParameter("param", "some other stuff")
            .addParameter("blah", "blah");
        final URI result = uribuilder.build();
        Assert.assertEquals(new URI("http://localhost:80/?param=stuff&blah&blah&" +
                "param=some+other+stuff&blah=blah"), result);
    }

    @Test
    public void testQueryEncoding() throws Exception {
        final URI uri1 = new URI("https://somehost.com/stuff?client_id=1234567890" +
                "&redirect_uri=https%3A%2F%2Fsomehost.com%2Fblah+blah%2F");
        final URI uri2 = new URIBuilder("https://somehost.com/stuff")
            .addParameter("client_id","1234567890")
            .addParameter("redirect_uri","https://somehost.com/blah blah/").build();
        Assert.assertEquals(uri1, uri2);
    }

    @Test
    public void testQueryAndParameterEncoding() throws Exception {
        final URI uri1 = new URI("https://somehost.com/stuff?param1=12345&param2=67890");
        final URI uri2 = new URIBuilder("https://somehost.com/stuff")
            .setCustomQuery("this&that")
            .addParameter("param1","12345")
            .addParameter("param2","67890").build();
        Assert.assertEquals(uri1, uri2);
    }

    @Test
    public void testPathEncoding() throws Exception {
        final URI uri1 = new URI("https://somehost.com/some%20path%20with%20blanks/");
        final URI uri2 = new URIBuilder()
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
        final URI uri = new URI(scheme, specials, host, 80, specials, specials, specials);

        final URI bld = new URIBuilder()
                .setScheme(scheme)
                .setHost(host)
                .setUserInfo(specials)
                .setPath(specials)
                .setCustomQuery(specials)
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
        final String specials="/ abcd!$&*()_-+.,=:;'~<>/@[]|#^%\"{}\\`xyz"; // N.B. excludes \u00a3\u00ac\u00a6
        final URI uri = new URI(scheme, specials, host, 80, specials, specials, specials);

        final URI bld = new URIBuilder()
                .setScheme(scheme)
                .setHost(host)
                .setUserInfo(specials)
                .setPath(specials)
                .setCustomQuery(specials)
                .setFragment(specials)
                .build();

        Assert.assertEquals(uri.getHost(), bld.getHost());

        Assert.assertEquals(uri.getRawUserInfo(), bld.getRawUserInfo());

        Assert.assertEquals(uri.getRawPath(), bld.getRawPath());

        Assert.assertEquals(uri.getRawQuery(), bld.getRawQuery());

        Assert.assertEquals(uri.getRawFragment(), bld.getRawFragment());

    }

    @Test
    public void testBuildAddParametersUTF8() throws Exception {
        assertAddParameters(Consts.UTF_8);
    }

    @Test
    public void testBuildAddParametersISO88591() throws Exception {
        assertAddParameters(Consts.ISO_8859_1);
    }

    public void assertAddParameters(final Charset charset) throws Exception {
        final URI uri = new URIBuilder("https://somehost.com/stuff")
                .setCharset(charset)
                .addParameters(createParameters()).build();

        assertBuild(charset, uri);
    }

    @Test
    public void testBuildSetParametersUTF8() throws Exception {
        assertSetParameters(Consts.UTF_8);
    }

    @Test
    public void testBuildSetParametersISO88591() throws Exception {
        assertSetParameters(Consts.ISO_8859_1);
    }

    public void assertSetParameters(final Charset charset) throws Exception {
        final URI uri = new URIBuilder("https://somehost.com/stuff")
                .setCharset(charset)
                .setParameters(createParameters()).build();

        assertBuild(charset, uri);
    }

    public void assertBuild(final Charset charset, final URI uri) throws Exception {
        final String encodedData1 = URLEncoder.encode("\"1\u00aa position\"", charset.displayName());
        final String encodedData2 = URLEncoder.encode("Jos\u00e9 Abra\u00e3o", charset.displayName());

        final String uriExpected = String.format("https://somehost.com/stuff?parameter1=value1&parameter2=%s&parameter3=%s", encodedData1, encodedData2);

        Assert.assertEquals(uriExpected, uri.toString());
    }

    private List<NameValuePair> createParameters() {
        final List<NameValuePair> parameters = new ArrayList<NameValuePair>();
        parameters.add(new BasicNameValuePair("parameter1", "value1"));
        parameters.add(new BasicNameValuePair("parameter2", "\"1\u00aa position\""));
        parameters.add(new BasicNameValuePair("parameter3", "Jos\u00e9 Abra\u00e3o"));
        return parameters;
    }

}
