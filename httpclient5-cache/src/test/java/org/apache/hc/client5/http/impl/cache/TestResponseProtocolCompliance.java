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
package org.apache.hc.client5.http.impl.cache;

import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.Date;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.protocol.ClientProtocolException;
import org.apache.hc.client5.http.sync.methods.HttpGet;
import org.apache.hc.client5.http.sync.methods.HttpHead;
import org.apache.hc.client5.http.impl.sync.RoutedHttpRequest;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.Before;
import org.junit.Test;

public class TestResponseProtocolCompliance {

    private HttpRoute route;
    private ResponseProtocolCompliance impl;

    @Before
    public void setUp() {
        route = new HttpRoute(new HttpHost("foo.example.com", 80));
        impl = new ResponseProtocolCompliance();
    }

    private static class Flag {
        public boolean set;
    }

    private void setMinimalResponseHeaders(final ClassicHttpResponse resp) {
        resp.setHeader("Date", DateUtils.formatDate(new Date()));
        resp.setHeader("Server", "MyServer/1.0");
    }

    private ByteArrayInputStream makeTrackableBody(final int nbytes, final Flag closed) {
        final byte[] buf = HttpTestUtils.getRandomBytes(nbytes);
        final ByteArrayInputStream bais = new ByteArrayInputStream(buf) {
            @Override
            public void close() {
                closed.set = true;
            }
        };
        return bais;
    }

    private ClassicHttpResponse makePartialResponse(final int nbytes) {
        final ClassicHttpResponse resp = new BasicClassicHttpResponse(HttpStatus.SC_PARTIAL_CONTENT, "Partial Content");
        setMinimalResponseHeaders(resp);
        resp.setHeader("Content-Length","" + nbytes);
        resp.setHeader("Content-Range","0-127/256");
        return resp;
    }

    @Test
    public void consumesBodyIfOriginSendsOneInResponseToHEAD() throws Exception {
        final RoutedHttpRequest wrapper = RoutedHttpRequest.adapt(new HttpHead("http://foo.example.com/"), route);;
        final int nbytes = 128;
        final ClassicHttpResponse resp = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        setMinimalResponseHeaders(resp);
        resp.setHeader("Content-Length","" + nbytes);

        final Flag closed = new Flag();
        final ByteArrayInputStream bais = makeTrackableBody(nbytes, closed);
        resp.setEntity(new InputStreamEntity(bais, -1));

        impl.ensureProtocolCompliance(wrapper, resp);
        assertNull(resp.getEntity());
        assertTrue(closed.set || bais.read() == -1);
    }

    @Test(expected=ClientProtocolException.class)
    public void throwsExceptionIfOriginReturnsPartialResponseWhenNotRequested() throws Exception {
        final RoutedHttpRequest wrapper = RoutedHttpRequest.adapt(new HttpGet("http://foo.example.com/"), route);;
        final int nbytes = 128;
        final ClassicHttpResponse resp = makePartialResponse(nbytes);
        resp.setEntity(HttpTestUtils.makeBody(nbytes));

        impl.ensureProtocolCompliance(wrapper, resp);
    }

    @Test
    public void consumesPartialContentFromOriginEvenIfNotRequested() throws Exception {
        final RoutedHttpRequest wrapper = RoutedHttpRequest.adapt(new HttpGet("http://foo.example.com/"), route);;
        final int nbytes = 128;
        final ClassicHttpResponse resp = makePartialResponse(nbytes);

        final Flag closed = new Flag();
        final ByteArrayInputStream bais = makeTrackableBody(nbytes, closed);
        resp.setEntity(new InputStreamEntity(bais, -1));

        try {
            impl.ensureProtocolCompliance(wrapper, resp);
        } catch (final ClientProtocolException expected) {
        }
        assertTrue(closed.set || bais.read() == -1);
    }

    @Test
    public void consumesBodyOf100ContinueResponseIfItArrives() throws Exception {
        final ClassicHttpRequest req = new BasicClassicHttpRequest("POST", "/");
        final int nbytes = 128;
        req.setHeader("Content-Length","" + nbytes);
        req.setHeader("Content-Type", "application/octet-stream");
        final HttpEntity postBody = new ByteArrayEntity(HttpTestUtils.getRandomBytes(nbytes));
        req.setEntity(postBody);
        final RoutedHttpRequest wrapper = RoutedHttpRequest.adapt(req, route);;

        final ClassicHttpResponse resp = new BasicClassicHttpResponse(HttpStatus.SC_CONTINUE, "Continue");
        final Flag closed = new Flag();
        final ByteArrayInputStream bais = makeTrackableBody(nbytes, closed);
        resp.setEntity(new InputStreamEntity(bais, -1));

        try {
            impl.ensureProtocolCompliance(wrapper, resp);
        } catch (final ClientProtocolException expected) {
        }
        assertTrue(closed.set || bais.read() == -1);
    }

}
