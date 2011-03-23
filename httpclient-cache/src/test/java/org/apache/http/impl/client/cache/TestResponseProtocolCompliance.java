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

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.util.Date;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.http.message.BasicHttpResponse;
import org.junit.Before;
import org.junit.Test;


public class TestResponseProtocolCompliance {

    private ResponseProtocolCompliance impl;

    @Before
    public void setUp() {
        impl = new ResponseProtocolCompliance();
    }
    
    @Test
    public void consumesBodyIfOriginSendsOneInResponseToHEAD() throws Exception {
        HttpRequest req = new HttpHead("http://foo.example.com/");
        HttpResponse resp = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        resp.setHeader("Date", DateUtils.formatDate(new Date()));
        resp.setHeader("Server", "MyServer/1.0");

        int nbytes = 128;
        resp.setHeader("Content-Length","" + nbytes);
        byte[] buf = HttpTestUtils.getRandomBytes(nbytes);
        final Flag closed = new Flag();
        ByteArrayInputStream bais = new ByteArrayInputStream(buf) {
            @Override
            public void close() {
                closed.set = true;
            }
        };
        resp.setEntity(new InputStreamEntity(bais, -1));
        
        impl.ensureProtocolCompliance(req, resp);
        assertNull(resp.getEntity());
        assertTrue(closed.set || bais.read() == -1);
    }
    
    private static class Flag {
        public boolean set;
    }
}
