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

package org.apache.hc.client5.http.entity;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.BasicHttpClientResponseHandler;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestBrotli {

    /**
     * Brotli decompression test implemented by request with specified response encoding br
     *
     * @throws Exception
     */
    @Test
    public void testDecompressionWithBrotli() throws Exception {
        final HttpClient client = HttpClientBuilder.create().build();
        final HttpGet get = new HttpGet("https://www.baidu.com");
        get.addHeader(new BasicHeader("Accept", "*/*"));
        get.addHeader(new BasicHeader("Accept-Encoding", "br"));
        get.addHeader(new BasicHeader("User-Agent",
                                      "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, "
                                          + "like Gecko) Chrome/101.0.4951.54 Safari/537.36"));
        final BasicHttpClientResponseHandler responseHandler = new BasicHttpClientResponseHandler();
        final String content = client.execute(get, responseHandler);
        Assertions.assertEquals(content.contains("<!DOCTYPE html"), true);
    }

}
