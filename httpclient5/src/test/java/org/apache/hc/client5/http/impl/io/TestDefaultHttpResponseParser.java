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

package org.apache.hc.client5.http.impl.io;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.MessageConstraintException;
import org.apache.hc.core5.http.NoHttpResponseException;
import org.apache.hc.core5.http.config.H1Config;
import org.apache.hc.core5.http.impl.io.SessionInputBufferImpl;
import org.apache.hc.core5.http.io.HttpMessageParser;
import org.apache.hc.core5.http.io.SessionInputBuffer;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link LenientHttpResponseParser}.
 */
public class TestDefaultHttpResponseParser {

    @Test
    public void testResponseParsingWithSomeGarbage() throws Exception {
        final String s =
            "garbage\r\n" +
            "garbage\r\n" +
            "more garbage\r\n" +
            "HTTP/1.1 200 OK\r\n" +
            "header1: value1\r\n" +
            "header2: value2\r\n" +
            "\r\n";

        final SessionInputBuffer inbuffer = new SessionInputBufferImpl(1024);
        final H1Config h1Config = H1Config.custom().setMaxEmptyLineCount(Integer.MAX_VALUE).build();
        final HttpMessageParser<ClassicHttpResponse> parser = new LenientHttpResponseParser(h1Config);

        final HttpResponse response = parser.parse(inbuffer,
                new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII)));
        Assert.assertNotNull(response);
        Assert.assertEquals(HttpVersion.HTTP_1_1, response.getVersion());
        Assert.assertEquals(200, response.getCode());

        final Header[] headers = response.getAllHeaders();
        Assert.assertNotNull(headers);
        Assert.assertEquals(2, headers.length);
        Assert.assertEquals("header1", headers[0].getName());
        Assert.assertEquals("header2", headers[1].getName());
    }

    @Test(expected=MessageConstraintException.class)
    public void testResponseParsingWithTooMuchGarbage() throws Exception {
        final String s =
            "garbage\r\n" +
            "garbage\r\n" +
            "more garbage\r\n" +
            "HTTP/1.1 200 OK\r\n" +
            "header1: value1\r\n" +
            "header2: value2\r\n" +
            "\r\n";

        final SessionInputBuffer inbuffer = new SessionInputBufferImpl(1024);
        final H1Config h1Config = H1Config.custom().setMaxEmptyLineCount(2).build();
        final HttpMessageParser<ClassicHttpResponse> parser = new LenientHttpResponseParser(h1Config);
        parser.parse(inbuffer, new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII)));
    }

    @Test(expected=NoHttpResponseException.class)
    public void testResponseParsingNoResponse() throws Exception {
        final SessionInputBuffer inbuffer = new SessionInputBufferImpl(1024);
        final HttpMessageParser<ClassicHttpResponse> parser = new LenientHttpResponseParser(H1Config.DEFAULT);
        parser.parse(inbuffer, new ByteArrayInputStream(new byte[]{}));
    }

    @Test(expected=NoHttpResponseException.class)
    public void testResponseParsingOnlyGarbage() throws Exception {
        final String s =
            "garbage\r\n" +
            "garbage\r\n" +
            "more garbage\r\n" +
            "a lot more garbage\r\n";
        final SessionInputBuffer inbuffer = new SessionInputBufferImpl(1024);
        final HttpMessageParser<ClassicHttpResponse> parser = new LenientHttpResponseParser(H1Config.DEFAULT);
        parser.parse(inbuffer, new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII)));
    }

}
