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

import java.util.Date;

import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.ClassicRequestCopier;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.junit.Before;
import org.junit.Test;

public class TestResponseProtocolCompliance {

    private ResponseProtocolCompliance impl;

    @Before
    public void setUp() {
        impl = new ResponseProtocolCompliance();
    }

    private void setMinimalResponseHeaders(final HttpResponse resp) {
        resp.setHeader("Date", DateUtils.formatDate(new Date()));
        resp.setHeader("Server", "MyServer/1.0");
    }

    private HttpResponse makePartialResponse(final int nbytes) {
        final HttpResponse resp = new BasicHttpResponse(HttpStatus.SC_PARTIAL_CONTENT, "Partial Content");
        setMinimalResponseHeaders(resp);
        resp.setHeader("Content-Length","" + nbytes);
        resp.setHeader("Content-Range","0-127/256");
        return resp;
    }

    @Test(expected=ClientProtocolException.class)
    public void throwsExceptionIfOriginReturnsPartialResponseWhenNotRequested() throws Exception {
        final HttpGet req = new HttpGet("http://foo.example.com/");
        final HttpRequest wrapper = ClassicRequestCopier.INSTANCE.copy(req);
        final int nbytes = 128;
        final HttpResponse resp = makePartialResponse(nbytes);

        impl.ensureProtocolCompliance(wrapper, req, resp);
    }

}
