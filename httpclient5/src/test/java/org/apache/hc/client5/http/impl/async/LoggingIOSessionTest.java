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
package org.apache.hc.client5.http.impl.async;

import org.apache.hc.core5.reactor.IOSession;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.nio.ByteBuffer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class LoggingIOSessionTest {
    IOSession delegate = mock(IOSession.class);
    Logger log = mock(Logger.class);
    Logger wireLog = mock(Logger.class);
    LoggingIOSession session = new LoggingIOSession(delegate, log, wireLog);

    @Test
    void allBytes() {
        when(delegate.toString()).thenReturn("IOSession");
        final byte[] bs = new byte[256];
        for (int i = 0; i < bs.length; i++) {
            bs[i] = (byte) i;
        }
        final ByteBuffer buf = ByteBuffer.wrap(bs);

        session.logData(buf, "<< ");

        verify(wireLog).debug("IOSession << .........     ..  00 01 02 03 04 05 06 07 08 09 0a 0b 0c 0d 0e 0f");
        verify(wireLog).debug("IOSession << ............      10 11 12 13 14 15 16 17 18 19 1a 1b 1c 1d 1e 1f");
        verify(wireLog).debug("IOSession <<  !\"#$%&'()*+,-./  20 21 22 23 24 25 26 27 28 29 2a 2b 2c 2d 2e 2f");
        verify(wireLog).debug("IOSession << 0123456789:;<=>?  30 31 32 33 34 35 36 37 38 39 3a 3b 3c 3d 3e 3f");
        verify(wireLog).debug("IOSession << @ABCDEFGHIJKLMNO  40 41 42 43 44 45 46 47 48 49 4a 4b 4c 4d 4e 4f");
        verify(wireLog).debug("IOSession << PQRSTUVWXYZ[\\]^_  50 51 52 53 54 55 56 57 58 59 5a 5b 5c 5d 5e 5f");
        verify(wireLog).debug("IOSession << `abcdefghijklmno  60 61 62 63 64 65 66 67 68 69 6a 6b 6c 6d 6e 6f");
        verify(wireLog).debug("IOSession << pqrstuvwxyz{|}~.  70 71 72 73 74 75 76 77 78 79 7a 7b 7c 7d 7e 7f");
        verify(wireLog).debug("IOSession << ................  80 81 82 83 84 85 86 87 88 89 8a 8b 8c 8d 8e 8f");
        verify(wireLog).debug("IOSession << ................  90 91 92 93 94 95 96 97 98 99 9a 9b 9c 9d 9e 9f");
        verify(wireLog).debug("IOSession << ................  a0 a1 a2 a3 a4 a5 a6 a7 a8 a9 aa ab ac ad ae af");
        verify(wireLog).debug("IOSession << ................  b0 b1 b2 b3 b4 b5 b6 b7 b8 b9 ba bb bc bd be bf");
        verify(wireLog).debug("IOSession << ................  c0 c1 c2 c3 c4 c5 c6 c7 c8 c9 ca cb cc cd ce cf");
        verify(wireLog).debug("IOSession << ................  d0 d1 d2 d3 d4 d5 d6 d7 d8 d9 da db dc dd de df");
        verify(wireLog).debug("IOSession << ................  e0 e1 e2 e3 e4 e5 e6 e7 e8 e9 ea eb ec ed ee ef");
        verify(wireLog).debug("IOSession << ................  f0 f1 f2 f3 f4 f5 f6 f7 f8 f9 fa fb fc fd fe ff");
        verifyNoMoreInteractions(wireLog);
    }
}
