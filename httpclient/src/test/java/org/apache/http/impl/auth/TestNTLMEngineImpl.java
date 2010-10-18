/*
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.impl.auth;

import org.junit.Test;

public class TestNTLMEngineImpl {

    @Test
    public void testMD4() throws Exception {
        checkMD4("", "31d6cfe0d16ae931b73c59d7e0c089c0");
        checkMD4("a", "bde52cb31de33e46245e05fbdbd6fb24");
        checkMD4("abc", "a448017aaf21d8525fc10ae87aa6729d");
        checkMD4("message digest", "d9130a8164549fe818874806e1c7014b");
        checkMD4("abcdefghijklmnopqrstuvwxyz", "d79e1c308aa5bbcdeea8ed63df412da9");
        checkMD4("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789",
                "043f8582f241db351ce627e153e7f0e4");
        checkMD4(
                "12345678901234567890123456789012345678901234567890123456789012345678901234567890",
                "e33b4ddc9c38f2199c3e7b164fcc0536");
    }

    /* Test suite helper */
    static byte checkToNibble(char c) {
        if (c >= 'a' && c <= 'f')
            return (byte) (c - 'a' + 0x0a);
        return (byte) (c - '0');
    }

    /* Test suite helper */
    static byte[] checkToBytes(String hex) {
        byte[] rval = new byte[hex.length() / 2];
        int i = 0;
        while (i < rval.length) {
            rval[i] = (byte) ((checkToNibble(hex.charAt(i * 2)) << 4) | (checkToNibble(hex
                    .charAt(i * 2 + 1))));
            i++;
        }
        return rval;
    }

    /* Test suite MD4 helper */
    static void checkMD4(String input, String hexOutput) throws Exception {
        NTLMEngineImpl.MD4 md4;
        md4 = new NTLMEngineImpl.MD4();
        md4.update(input.getBytes("ASCII"));
        byte[] answer = md4.getOutput();
        byte[] correctAnswer = checkToBytes(hexOutput);
        if (answer.length != correctAnswer.length)
            throw new Exception("Answer length disagrees for MD4('" + input + "')");
        int i = 0;
        while (i < answer.length) {
            if (answer[i] != correctAnswer[i])
                throw new Exception("Answer value for MD4('" + input + "') disagrees at position "
                        + Integer.toString(i));
            i++;
        }
    }

}
