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
package org.apache.hc.client5.http.impl.classic;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;

import org.apache.hc.client5.http.classic.ExecRuntime;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

@SuppressWarnings("boxing") // test code
public class TestResponseEntityWrapper {

    private InputStream inStream;
    private HttpEntity entity;
    private ExecRuntime execRuntime;
    private ResponseEntityProxy wrapper;

    @Before
    public void setup() throws Exception {
        inStream = Mockito.mock(InputStream.class);
        entity = Mockito.mock(HttpEntity.class);
        Mockito.when(entity.getContent()).thenReturn(inStream);
        execRuntime = Mockito.mock(ExecRuntime.class);
        wrapper = new ResponseEntityProxy(entity, execRuntime);
    }

    @Test
    public void testReusableEntityStreamClosed() throws Exception {
        Mockito.when(entity.isStreaming()).thenReturn(true);
        Mockito.when(execRuntime.isConnectionReusable()).thenReturn(true);
        EntityUtils.consume(wrapper);

        Mockito.verify(inStream, Mockito.times(1)).close();
        Mockito.verify(execRuntime).releaseEndpoint();
    }

    @Test
    public void testReusableEntityStreamClosedIOError() throws Exception {
        Mockito.when(entity.isStreaming()).thenReturn(true);
        Mockito.when(execRuntime.isConnectionReusable()).thenReturn(true);
        Mockito.doThrow(new IOException()).when(inStream).close();
        try {
            EntityUtils.consume(wrapper);
            Assert.fail("IOException expected");
        } catch (final IOException ex) {
        }
        Mockito.verify(execRuntime, Mockito.atLeast(1)).discardEndpoint();
    }

    @Test
    public void testEntityStreamClosedIOErrorAlreadyReleased() throws Exception {
        Mockito.when(entity.isStreaming()).thenReturn(true);
        Mockito.when(execRuntime.isConnectionReusable()).thenReturn(true);
        Mockito.when(execRuntime.isEndpointAcquired()).thenReturn(false);
        Mockito.doThrow(new SocketException()).when(inStream).close();
        EntityUtils.consume(wrapper);
        Mockito.verify(execRuntime).discardEndpoint();
    }

    @Test
    public void testReusableEntityWriteTo() throws Exception {
        final OutputStream outStream = Mockito.mock(OutputStream.class);
        Mockito.when(entity.isStreaming()).thenReturn(true);
        Mockito.when(execRuntime.isConnectionReusable()).thenReturn(true);
        wrapper.writeTo(outStream);
        Mockito.verify(execRuntime).releaseEndpoint();
    }

    @Test
    public void testReusableEntityWriteToIOError() throws Exception {
        final OutputStream outStream = Mockito.mock(OutputStream.class);
        Mockito.when(entity.isStreaming()).thenReturn(true);
        Mockito.when(execRuntime.isConnectionReusable()).thenReturn(true);
        Mockito.doThrow(new IOException()).when(entity).writeTo(outStream);
        try {
            wrapper.writeTo(outStream);
            Assert.fail("IOException expected");
        } catch (final IOException ex) {
        }
        Mockito.verify(execRuntime, Mockito.never()).releaseEndpoint();
        Mockito.verify(execRuntime, Mockito.atLeast(1)).discardEndpoint();
    }

    @Test
    public void testReusableEntityEndOfStream() throws Exception {
        Mockito.when(inStream.read()).thenReturn(-1);
        Mockito.when(entity.isStreaming()).thenReturn(true);
        Mockito.when(execRuntime.isConnectionReusable()).thenReturn(true);
        final InputStream content = wrapper.getContent();
        Assert.assertEquals(-1, content.read());
        Mockito.verify(inStream).close();
        Mockito.verify(execRuntime).releaseEndpoint();
    }

    @Test
    public void testReusableEntityEndOfStreamIOError() throws Exception {
        Mockito.when(inStream.read()).thenReturn(-1);
        Mockito.when(entity.isStreaming()).thenReturn(true);
        Mockito.when(execRuntime.isConnectionReusable()).thenReturn(true);
        Mockito.doThrow(new IOException()).when(inStream).close();
        final InputStream content = wrapper.getContent();
        try {
            content.read();
            Assert.fail("IOException expected");
        } catch (final IOException ex) {
        }
        Mockito.verify(execRuntime, Mockito.atLeast(1)).discardEndpoint();
    }

}
