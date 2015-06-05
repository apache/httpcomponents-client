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
package org.apache.http.impl.execchain;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;

import org.apache.http.HttpEntity;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

@SuppressWarnings("boxing") // test code
public class TestResponseEntityWrapper {

    private InputStream instream;
    private HttpEntity entity;
    private ConnectionHolder connHolder;
    private ResponseEntityProxy wrapper;

    @Before
    public void setup() throws Exception {
        instream = Mockito.mock(InputStream.class);
        entity = Mockito.mock(HttpEntity.class);
        Mockito.when(entity.getContent()).thenReturn(instream);
        connHolder = Mockito.mock(ConnectionHolder.class);
        wrapper = new ResponseEntityProxy(entity, connHolder);
    }

    @Test
    public void testReusableEntityStreamClosed() throws Exception {
        Mockito.when(entity.isStreaming()).thenReturn(true);
        Mockito.when(connHolder.isReusable()).thenReturn(true);
        EntityUtils.consume(wrapper);

        Mockito.verify(instream, Mockito.times(1)).close();
        Mockito.verify(connHolder).releaseConnection();
    }

    @Test
    public void testReusableEntityStreamClosedIOError() throws Exception {
        Mockito.when(entity.isStreaming()).thenReturn(true);
        Mockito.when(connHolder.isReusable()).thenReturn(true);
        Mockito.doThrow(new IOException()).when(instream).close();
        try {
            EntityUtils.consume(wrapper);
            Assert.fail("IOException expected");
        } catch (final IOException ex) {
        }
        Mockito.verify(connHolder).abortConnection();
    }

    @Test
    public void testEntityStreamClosedIOErrorAlreadyReleased() throws Exception {
        Mockito.when(entity.isStreaming()).thenReturn(true);
        Mockito.when(connHolder.isReusable()).thenReturn(true);
        Mockito.when(connHolder.isReleased()).thenReturn(true);
        Mockito.doThrow(new SocketException()).when(instream).close();
        EntityUtils.consume(wrapper);
        Mockito.verify(connHolder).close();
    }

    @Test
    public void testReusableEntityWriteTo() throws Exception {
        final OutputStream outstream = Mockito.mock(OutputStream.class);
        Mockito.when(entity.isStreaming()).thenReturn(true);
        Mockito.when(connHolder.isReusable()).thenReturn(true);
        wrapper.writeTo(outstream);
        Mockito.verify(connHolder).releaseConnection();
    }

    @Test
    public void testReusableEntityWriteToIOError() throws Exception {
        final OutputStream outstream = Mockito.mock(OutputStream.class);
        Mockito.when(entity.isStreaming()).thenReturn(true);
        Mockito.when(connHolder.isReusable()).thenReturn(true);
        Mockito.doThrow(new IOException()).when(entity).writeTo(outstream);
        try {
            wrapper.writeTo(outstream);
            Assert.fail("IOException expected");
        } catch (final IOException ex) {
        }
        Mockito.verify(connHolder, Mockito.never()).releaseConnection();
        Mockito.verify(connHolder).abortConnection();
    }

    @Test
    public void testReusableEntityEndOfStream() throws Exception {
        Mockito.when(instream.read()).thenReturn(-1);
        Mockito.when(entity.isStreaming()).thenReturn(true);
        Mockito.when(connHolder.isReusable()).thenReturn(true);
        final InputStream content = wrapper.getContent();
        Assert.assertEquals(-1, content.read());
        Mockito.verify(instream).close();
        Mockito.verify(connHolder).releaseConnection();
    }

    @Test
    public void testReusableEntityEndOfStreamIOError() throws Exception {
        Mockito.when(instream.read()).thenReturn(-1);
        Mockito.when(entity.isStreaming()).thenReturn(true);
        Mockito.when(connHolder.isReusable()).thenReturn(true);
        Mockito.doThrow(new IOException()).when(instream).close();
        final InputStream content = wrapper.getContent();
        try {
            content.read();
            Assert.fail("IOException expected");
        } catch (final IOException ex) {
        }
        Mockito.verify(connHolder).abortConnection();
    }

}
