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


import org.apache.http.Consts;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.junit.Assert;
import org.junit.Test;

import java.net.URLEncoder;
import java.nio.charset.Charset;

public class TestRequestBuilder {

    @Test
    public void testBuildGETwithUTF8() throws Exception {
        assertBuild(Consts.UTF_8);
    }

    @Test
    public void testBuildGETwithISO88591() throws Exception {
        assertBuild(Consts.ISO_8859_1);
    }

    private void assertBuild(final Charset charset) throws Exception {
        final RequestBuilder requestBuilder = RequestBuilder.create("GET").setCharset(charset);
        requestBuilder.setUri("https://somehost.com/stuff");
        requestBuilder.addParameters(createParameters());

        final String encodedData1 = URLEncoder.encode("\"1\u00aa position\"", charset.displayName());
        final String encodedData2 = URLEncoder.encode("Jos\u00e9 Abra\u00e3o", charset.displayName());

        final String uriExpected = String.format("https://somehost.com/stuff?parameter1=value1&parameter2=%s&parameter3=%s", encodedData1, encodedData2);

        final HttpUriRequest request = requestBuilder.build();
        Assert.assertEquals(uriExpected, request.getURI().toString());
    }

    private NameValuePair[] createParameters() {
        final NameValuePair parameters[] = new NameValuePair[3];
        parameters[0] = new BasicNameValuePair("parameter1", "value1");
        parameters[1] = new BasicNameValuePair("parameter2", "\"1\u00aa position\"");
        parameters[2] = new BasicNameValuePair("parameter3", "Jos\u00e9 Abra\u00e3o");
        return parameters;
    }
}
