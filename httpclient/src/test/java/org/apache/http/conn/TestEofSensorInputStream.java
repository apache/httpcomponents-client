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
package org.apache.http.conn;

import java.io.IOException;
import java.io.InputStream;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

@SuppressWarnings({"boxing","static-access"}) // test code
public class TestEofSensorInputStream {

    private InputStream instream;
    private EofSensorWatcher eofwatcher;
    private EofSensorInputStream eofstream;

    @Before
    public void setup() throws Exception {
        instream = Mockito.mock(InputStream.class);
        eofwatcher = Mockito.mock(EofSensorWatcher.class);
        eofstream = new EofSensorInputStream(instream, eofwatcher);
    }

    @Test
    public void testClose() throws Exception {
        Mockito.when(eofwatcher.streamClosed(Mockito.<InputStream>any())).thenReturn(Boolean.TRUE);

        eofstream.close();

        Assert.assertTrue(eofstream.isSelfClosed());
        Assert.assertNull(eofstream.getWrappedStream());

        Mockito.verify(instream, Mockito.times(1)).close();
        Mockito.verify(eofwatcher).streamClosed(instream);

        eofstream.close();
    }

    @Test
    public void testCloseIOError() throws Exception {
        Mockito.when(eofwatcher.streamClosed(Mockito.<InputStream>any())).thenThrow(new IOException());

        try {
            eofstream.close();
            Assert.fail("IOException expected");
        } catch (final IOException ex) {
        }
        Assert.assertTrue(eofstream.isSelfClosed());
        Assert.assertNull(eofstream.getWrappedStream());

        Mockito.verify(eofwatcher).streamClosed(instream);
    }

    @Test
    public void testReleaseConnection() throws Exception {
        Mockito.when(eofwatcher.streamClosed(Mockito.<InputStream>any())).thenReturn(Boolean.TRUE);

        eofstream.releaseConnection();

        Assert.assertTrue(eofstream.isSelfClosed());
        Assert.assertNull(eofstream.getWrappedStream());

        Mockito.verify(instream, Mockito.times(1)).close();
        Mockito.verify(eofwatcher).streamClosed(instream);

        eofstream.releaseConnection();
    }

    @Test
    public void testAbortConnection() throws Exception {
        Mockito.when(eofwatcher.streamAbort(Mockito.<InputStream>any())).thenReturn(Boolean.TRUE);

        eofstream.abortConnection();

        Assert.assertTrue(eofstream.isSelfClosed());
        Assert.assertNull(eofstream.getWrappedStream());

        Mockito.verify(instream, Mockito.times(1)).close();
        Mockito.verify(eofwatcher).streamAbort(instream);

        eofstream.abortConnection();
    }

    @Test
    public void testAbortConnectionIOError() throws Exception {
        Mockito.when(eofwatcher.streamAbort(Mockito.<InputStream>any())).thenThrow(new IOException());

        try {
            eofstream.abortConnection();
            Assert.fail("IOException expected");
        } catch (final IOException ex) {
        }
        Assert.assertTrue(eofstream.isSelfClosed());
        Assert.assertNull(eofstream.getWrappedStream());

        Mockito.verify(eofwatcher).streamAbort(instream);
    }

    @Test
    public void testRead() throws Exception {
        Mockito.when(eofwatcher.eofDetected(Mockito.<InputStream>any())).thenReturn(Boolean.TRUE);
        Mockito.when(instream.read()).thenReturn(0, -1);

        Assert.assertEquals(0, eofstream.read());

        Assert.assertFalse(eofstream.isSelfClosed());
        Assert.assertNotNull(eofstream.getWrappedStream());

        Mockito.verify(eofwatcher, Mockito.never()).eofDetected(instream);

        Assert.assertEquals(-1, eofstream.read());

        Assert.assertFalse(eofstream.isSelfClosed());
        Assert.assertNull(eofstream.getWrappedStream());

        Mockito.verify(instream, Mockito.times(1)).close();
        Mockito.verify(eofwatcher).eofDetected(instream);

        Assert.assertEquals(-1, eofstream.read());
    }

    @Test
    public void testReadIOError() throws Exception {
        Mockito.when(eofwatcher.eofDetected(Mockito.<InputStream>any())).thenReturn(Boolean.TRUE);
        Mockito.when(instream.read()).thenThrow(new IOException());

        try {
            eofstream.read();
            Assert.fail("IOException expected");
        } catch (final IOException ex) {
        }
        Assert.assertFalse(eofstream.isSelfClosed());
        Assert.assertNull(eofstream.getWrappedStream());

        Mockito.verify(eofwatcher).streamAbort(instream);
    }

    @Test
    public void testReadByteArray() throws Exception {
        Mockito.when(eofwatcher.eofDetected(Mockito.<InputStream>any())).thenReturn(Boolean.TRUE);
        Mockito.when(instream.read(Mockito.<byte []>any(), Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(1, -1);

        final byte[] tmp = new byte[1];

        Assert.assertEquals(1, eofstream.read(tmp));

        Assert.assertFalse(eofstream.isSelfClosed());
        Assert.assertNotNull(eofstream.getWrappedStream());

        Mockito.verify(eofwatcher, Mockito.never()).eofDetected(instream);

        Assert.assertEquals(-1, eofstream.read(tmp));

        Assert.assertFalse(eofstream.isSelfClosed());
        Assert.assertNull(eofstream.getWrappedStream());

        Mockito.verify(instream, Mockito.times(1)).close();
        Mockito.verify(eofwatcher).eofDetected(instream);

        Assert.assertEquals(-1, eofstream.read(tmp));
    }

    @Test
    public void testReadByteArrayIOError() throws Exception {
        Mockito.when(eofwatcher.eofDetected(Mockito.<InputStream>any())).thenReturn(Boolean.TRUE);
        Mockito.when(instream.read(Mockito.<byte []>any(), Mockito.anyInt(), Mockito.anyInt()))
            .thenThrow(new IOException());

        final byte[] tmp = new byte[1];
        try {
            eofstream.read(tmp);
            Assert.fail("IOException expected");
        } catch (final IOException ex) {
        }
        Assert.assertFalse(eofstream.isSelfClosed());
        Assert.assertNull(eofstream.getWrappedStream());

        Mockito.verify(eofwatcher).streamAbort(instream);
    }

    @Test
    public void testReadAfterAbort() throws Exception {
        Mockito.when(eofwatcher.streamAbort(Mockito.<InputStream>any())).thenReturn(Boolean.TRUE);

        eofstream.abortConnection();

        try {
            eofstream.read();
            Assert.fail("IOException expected");
        } catch (final IOException ex) {
        }
        final byte[] tmp = new byte[1];
        try {
            eofstream.read(tmp);
            Assert.fail("IOException expected");
        } catch (final IOException ex) {
        }
    }

}
