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

import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Simple tests for {@link RequestSupport}.
 */
public class TestRequestSupport {

    @Test
    public void testPathPrefixExtraction() {
        Assertions.assertEquals("/aaaa/", RequestSupport.extractPathPrefix(new BasicHttpRequest("GET", "/aaaa/bbbb")));
        Assertions.assertEquals("/aaaa/", RequestSupport.extractPathPrefix(new BasicHttpRequest("GET", "/aaaa/")));
        Assertions.assertEquals("/aaaa/", RequestSupport.extractPathPrefix(new BasicHttpRequest("GET", "/aaaa/../aaaa/")));
        Assertions.assertEquals("/aaaa/bbbb/", RequestSupport.extractPathPrefix(new BasicHttpRequest("GET", "/aaaa/bbbb/cccc")));
        Assertions.assertEquals("/aaaa/bbbb/", RequestSupport.extractPathPrefix(new BasicHttpRequest("GET", "/aaaa/bbbb/")));
        Assertions.assertEquals("/aaaa/", RequestSupport.extractPathPrefix(new BasicHttpRequest("GET", "/aaaa/bbbb?////")));
        Assertions.assertEquals("/aa%2Faa/", RequestSupport.extractPathPrefix(new BasicHttpRequest("GET", "/aa%2faa/bbbb")));
        Assertions.assertEquals("/aa%2Faa/", RequestSupport.extractPathPrefix(new BasicHttpRequest("GET", "/a%61%2fa%61/bbbb")));
        Assertions.assertEquals("/", RequestSupport.extractPathPrefix(new BasicHttpRequest("GET", "/")));
        Assertions.assertEquals("/", RequestSupport.extractPathPrefix(new BasicHttpRequest("GET", "/aaaa")));
        Assertions.assertEquals("/", RequestSupport.extractPathPrefix(new BasicHttpRequest("GET", "")));
    }

}
