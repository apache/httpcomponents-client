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
package org.apache.http.impl.client.cache;

import java.util.Date;
import java.util.Random;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.protocol.HttpContext;
import org.easymock.classextension.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class DoNotTestProtocolRequirements {

    private static final ProtocolVersion HTTP_1_1 = new ProtocolVersion("HTTP", 1, 1);

    private static final int MAX_BYTES = 1024;
    private static final int MAX_ENTRIES = 100;

    private HttpHost host;
    private HttpEntity mockEntity;
    private HttpClient mockBackend;
    private HttpCache mockCache;
    private HttpRequest request;
    private HttpResponse originResponse;

    private CachingHttpClient impl;

    @Before
    public void setUp() {
        host = new HttpHost("foo.example.com");

        request = new BasicHttpRequest("GET", "/foo", HTTP_1_1);

        originResponse = make200Response();
        CacheConfig params = new CacheConfig();
        params.setMaxObjectSizeBytes(MAX_BYTES);
        params.setMaxCacheEntries(MAX_ENTRIES);

        HttpCache cache = new BasicHttpCache(params);
        mockBackend = EasyMock.createMock(HttpClient.class);
        mockEntity = EasyMock.createMock(HttpEntity.class);
        mockCache = EasyMock.createMock(HttpCache.class);
        impl = new CachingHttpClient(mockBackend, cache, params);
    }

    private HttpResponse make200Response() {
        HttpResponse out = new BasicHttpResponse(HTTP_1_1, HttpStatus.SC_OK, "OK");
        out.setHeader("Date", DateUtils.formatDate(new Date()));
        out.setHeader("Server", "MockOrigin/1.0");
        out.setEntity(makeBody(128));
        return out;
    }

    private void replayMocks() {
        EasyMock.replay(mockBackend);
        EasyMock.replay(mockCache);
        EasyMock.replay(mockEntity);
    }

    private HttpEntity makeBody(int nbytes) {
        byte[] bytes = new byte[nbytes];
        (new Random()).nextBytes(bytes);
        return new ByteArrayEntity(bytes);
    }

    /*
     * "10.2.7 206 Partial Content ... The response MUST include the following
     * header fields:
     *
     * - Either a Content-Range header field (section 14.16) indicating the
     * range included with this response, or a multipart/byteranges Content-Type
     * including Content-Range fields for each part. If a Content-Length header
     * field is present in the response, its value MUST match the actual number
     * of OCTETs transmitted in the message-body.
     *
     * - Date
     *
     * - ETag and/or Content-Location, if the header would have been sent in a
     * 200 response to the same request
     *
     * - Expires, Cache-Control, and/or Vary, if the field-value might differ
     * from that sent in any previous response for the same variant
     *
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.2.7
     */
    @Test
    @Ignore
    public void test206ResponseReturnedToClientMustHaveContentRangeOrByteRangesContentType()
            throws Exception {
        request.addHeader("Range", "bytes 0-499/1234");
        originResponse = new BasicHttpResponse(HTTP_1_1, HttpStatus.SC_PARTIAL_CONTENT,
                "Partial Content");
        originResponse.setHeader("Date", DateUtils.formatDate(new Date()));
        originResponse.setHeader("Server", "MockOrigin/1.0");
        originResponse.setEntity(makeBody(500));

        org.easymock.EasyMock.expect(
                mockBackend.execute(org.easymock.EasyMock.isA(HttpHost.class),
                        org.easymock.EasyMock.isA(HttpRequest.class),
                        (HttpContext) org.easymock.EasyMock.isNull())).andReturn(originResponse);

        replayMocks();

        try {
            HttpResponse result = impl.execute(host, request);
            Header crHdr = result.getFirstHeader("Content-Range");
            Header ctHdr = result.getFirstHeader("Content-Type");
            if (result.getStatusLine().getStatusCode() == HttpStatus.SC_PARTIAL_CONTENT) {
                if (crHdr == null) {
                    Assert.assertNotNull(ctHdr);
                    boolean foundMultipartByteRanges = false;
                    for (HeaderElement elt : ctHdr.getElements()) {
                        if ("multipart/byteranges".equalsIgnoreCase(elt.getName())) {
                            NameValuePair param = elt.getParameterByName("boundary");
                            Assert.assertNotNull(param);
                            String boundary = param.getValue();
                            Assert.assertNotNull(boundary);
                            // perhaps eventually should parse out the
                            // request body to check proper formatting
                            // but that might be excessive; HttpClient
                            // developers have indicated that
                            // HttpClient's job does not involve
                            // parsing a response body
                        }
                    }
                    Assert.assertTrue(foundMultipartByteRanges);
                }
            }
        } catch (ClientProtocolException acceptableBehavior) {
        }
    }

    @Test
    @Ignore
    public void test206ResponseReturnedToClientWithAContentLengthMustMatchActualOctetsTransmitted() {
        // We are explicitly saying that CachingHttpClient does not care about
        // this:
        // We do not attempt to cache 206, nor do we ever construct a 206. We
        // simply pass along a 206,
        // which could be malformed. But protocol compliance of a downstream
        // server is not our responsibility
    }

}
