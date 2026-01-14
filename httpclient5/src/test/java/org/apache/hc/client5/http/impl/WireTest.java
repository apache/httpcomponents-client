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

        verify(log).debug("{} {}", "id", "<< \"[0x0][0x1][0x2][0x3][0x4][0x5][0x6][0x7][0x8][0x9][\\n]\"");
        verify(log).debug("{} {}", "id",
            "<< \"[0xb][0xc][\\r][0xe][0xf][0x10][0x11][0x12][0x13][0x14][0x15][0x16][0x17][0x18][0x19][0x1a][0x1b]"
                + "[0x1c][0x1d][0x1e][0x1f] !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefg"
                + "hijklmnopqrstuvwxyz{|}~[0x7f][0xffffff80][0xffffff81][0xffffff82][0xffffff83][0xffffff84]"
                + "[0xffffff85][0xffffff86][0xffffff87][0xffffff88][0xffffff89][0xffffff8a][0xffffff8b][0xffffff8c]"
                + "[0xffffff8d][0xffffff8e][0xffffff8f][0xffffff90][0xffffff91][0xffffff92][0xffffff93][0xffffff94]"
                + "[0xffffff95][0xffffff96][0xffffff97][0xffffff98][0xffffff99][0xffffff9a][0xffffff9b][0xffffff9c]"
                + "[0xffffff9d][0xffffff9e][0xffffff9f][0xffffffa0][0xffffffa1][0xffffffa2][0xffffffa3][0xffffffa4]"
                + "[0xffffffa5][0xffffffa6][0xffffffa7][0xffffffa8][0xffffffa9][0xffffffaa][0xffffffab][0xffffffac]"
                + "[0xffffffad][0xffffffae][0xffffffaf][0xffffffb0][0xffffffb1][0xffffffb2][0xffffffb3][0xffffffb4]"
                + "[0xffffffb5][0xffffffb6][0xffffffb7][0xffffffb8][0xffffffb9][0xffffffba][0xffffffbb][0xffffffbc]"
                + "[0xffffffbd][0xffffffbe][0xffffffbf][0xffffffc0][0xffffffc1][0xffffffc2][0xffffffc3][0xffffffc4]"
                + "[0xffffffc5][0xffffffc6][0xffffffc7][0xffffffc8][0xffffffc9][0xffffffca][0xffffffcb][0xffffffcc]"
                + "[0xffffffcd][0xffffffce][0xffffffcf][0xffffffd0][0xffffffd1][0xffffffd2][0xffffffd3][0xffffffd4]"
                + "[0xffffffd5][0xffffffd6][0xffffffd7][0xffffffd8][0xffffffd9][0xffffffda][0xffffffdb][0xffffffdc]"
                + "[0xffffffdd][0xffffffde][0xffffffdf][0xffffffe0][0xffffffe1][0xffffffe2][0xffffffe3][0xffffffe4]"
                + "[0xffffffe5][0xffffffe6][0xffffffe7][0xffffffe8][0xffffffe9][0xffffffea][0xffffffeb][0xffffffec]"
                + "[0xffffffed][0xffffffee][0xffffffef][0xfffffff0][0xfffffff1][0xfffffff2][0xfffffff3][0xfffffff4]"
                + "[0xfffffff5][0xfffffff6][0xfffffff7][0xfffffff8][0xfffffff9][0xfffffffa][0xfffffffb][0xfffffffc]"
                + "[0xfffffffd][0xfffffffe][0xffffffff]\"");
        verifyNoMoreInteractions(log);
    }
}
