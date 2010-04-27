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

package org.apache.http.client.utils;

import org.apache.http.client.utils.Rfc3492Idn;
import org.junit.Assert;
import org.junit.Test;

public class TestRfc3492Idn {

    /**
     * Some of the sample strings from RFC 3492
     */
    @Test
    public void testDecode() throws Exception {
        Rfc3492Idn idn = new Rfc3492Idn();
        // (A) Arabic
        Assert.assertEquals("\u0644\u064A\u0647\u0645\u0627\u0628\u062A\u0643\u0644" +
                     "\u0645\u0648\u0634\u0639\u0631\u0628\u064A\u061F",
                     idn.decode("egbpdaj6bu4bxfgehfvwxn"));

        // (B) Chinese (simplified)
        Assert.assertEquals("\u4ED6\u4EEC\u4E3A\u4EC0\u4E48\u4E0D\u8BF4\u4E2D\u6587",
                     idn.decode("ihqwcrb4cv8a8dqg056pqjye"));

        // (I) Russian (Cyrillic)
        Assert.assertEquals("\u043F\u043E\u0447\u0435\u043C\u0443\u0436\u0435\u043E"+
                     "\u043D\u0438\u043D\u0435\u0433\u043E\u0432\u043E\u0440"+
                     "\u044F\u0442\u043F\u043E\u0440\u0443\u0441\u0441\u043A"+
                     "\u0438",
                     idn.decode("b1abfaaepdrnnbgefbaDotcwatmq2g4l"));

        // (P) Maji<de>Koi<suru>5<byou><mae>
        Assert.assertEquals("\u004D\u0061\u006A\u0069\u3067\u004B\u006F\u0069\u3059" +
                     "\u308B\u0035\u79D2\u524D",
                     idn.decode("MajiKoi5-783gue6qz075azm5e"));

    }

    @Test
    public void testToUnicode() throws Exception {
        Rfc3492Idn idn = new Rfc3492Idn();
        // (A) Arabic
        Assert.assertEquals("\u0644\u064A\u0647\u0645\u0627\u0628\u062A\u0643\u0644" +
                     "\u0645\u0648\u0634\u0639\u0631\u0628\u064A\u061F",
                     idn.toUnicode("xn--egbpdaj6bu4bxfgehfvwxn"));

        // some real-world domains
        Assert.assertEquals("www.z\u00fcrich.ch",
                     idn.toUnicode("www.xn--zrich-kva.ch"));

        Assert.assertEquals("www.g\u00e4ggelig\u00e4\u00e4l.ch",
                     idn.toUnicode("www.xn--gggeligl-0zaga.ch"));
    }

}
