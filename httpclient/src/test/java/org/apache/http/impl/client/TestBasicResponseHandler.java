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

package org.apache.http.impl.client;

import java.io.InputStream;

import junit.framework.Assert;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpResponseException;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicStatusLine;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit tests for {@link BasicResponseHandler}.
 */
public class TestBasicResponseHandler {

    @Test
    public void testSuccessfulResponse() throws Exception {
        StatusLine sl = new BasicStatusLine(HttpVersion.HTTP_1_1, 200, "OK");
        HttpResponse response = Mockito.mock(HttpResponse.class);
        HttpEntity entity = new StringEntity("stuff");
        Mockito.when(response.getStatusLine()).thenReturn(sl);
        Mockito.when(response.getEntity()).thenReturn(entity);

        BasicResponseHandler handler = new BasicResponseHandler();
        String s = handler.handleResponse(response);
        Assert.assertEquals("stuff", s);
    }

    @Test
    public void testUnsuccessfulResponse() throws Exception {
        InputStream instream = Mockito.mock(InputStream.class);
        HttpEntity entity = Mockito.mock(HttpEntity.class);
        Mockito.when(entity.isStreaming()).thenReturn(true);
        Mockito.when(entity.getContent()).thenReturn(instream);
        StatusLine sl = new BasicStatusLine(HttpVersion.HTTP_1_1, 404, "Not Found");
        HttpResponse response = Mockito.mock(HttpResponse.class);
        Mockito.when(response.getStatusLine()).thenReturn(sl);
        Mockito.when(response.getEntity()).thenReturn(entity);

        BasicResponseHandler handler = new BasicResponseHandler();
        try {
            handler.handleResponse(response);
            Assert.fail("HttpResponseException expected");
        } catch (HttpResponseException ex) {
            Assert.assertEquals(404, ex.getStatusCode());
            Assert.assertEquals("Not Found", ex.getMessage());
        }
        Mockito.verify(entity).getContent();
        Mockito.verify(instream).close();
    }

}
