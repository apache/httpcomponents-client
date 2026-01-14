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
package org.apache.hc.client5.http.impl;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class WireTest {
    Logger log = mock(Logger.class);
    String id = "id";
    Wire wire = new Wire(log, id);

    @Test
    void allBytes() {
        final byte[] bs = new byte[256];
        for (int i = 0; i < bs.length; i++) {
            bs[i] = (byte) i;
        }
        when(log.isDebugEnabled()).thenReturn(true);

        wire.input(bs);

        verify(log).debug("{} {}", "id", "<< \"[0x00][0x01][0x02][0x03][0x04][0x05][0x06][0x07][0x08][0x09][\\n]\"");
        verify(log).debug("{} {}", "id", "<< \"[0x0b][0x0c][\\r][0x0e][0x0f][0x10][0x11][0x12][0x13][0x14][0x15][0x16]"
            + "[0x17][0x18][0x19][0x1a][0x1b][0x1c][0x1d][0x1e][0x1f] !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNO"
            + "PQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~[0x7f][0x80][0x81][0x82][0x83][0x84][0x85][0x86][0x87]"
            + "[0x88][0x89][0x8a][0x8b][0x8c][0x8d][0x8e][0x8f][0x90][0x91][0x92][0x93][0x94][0x95][0x96][0x97][0x98]"
            + "[0x99][0x9a][0x9b][0x9c][0x9d][0x9e][0x9f][0xa0][0xa1][0xa2][0xa3][0xa4][0xa5][0xa6][0xa7][0xa8][0xa9]"
            + "[0xaa][0xab][0xac][0xad][0xae][0xaf][0xb0][0xb1][0xb2][0xb3][0xb4][0xb5][0xb6][0xb7][0xb8][0xb9][0xba]"
            + "[0xbb][0xbc][0xbd][0xbe][0xbf][0xc0][0xc1][0xc2][0xc3][0xc4][0xc5][0xc6][0xc7][0xc8][0xc9][0xca][0xcb]"
            + "[0xcc][0xcd][0xce][0xcf][0xd0][0xd1][0xd2][0xd3][0xd4][0xd5][0xd6][0xd7][0xd8][0xd9][0xda][0xdb][0xdc]"
            + "[0xdd][0xde][0xdf][0xe0][0xe1][0xe2][0xe3][0xe4][0xe5][0xe6][0xe7][0xe8][0xe9][0xea][0xeb][0xec][0xed]"
            + "[0xee][0xef][0xf0][0xf1][0xf2][0xf3][0xf4][0xf5][0xf6][0xf7][0xf8][0xf9][0xfa][0xfb][0xfc][0xfd][0xfe]"
            + "[0xff]\"");
        verifyNoMoreInteractions(log);
    }
}
