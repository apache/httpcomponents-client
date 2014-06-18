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
package org.apache.http.impl.client.integration;

import org.apache.http.HttpHost;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.localserver.LocalServerTestBase;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.Test;

public class TestBasicConnectionManager extends LocalServerTestBase {

    @Test
    public void testBasics() throws Exception {
        this.clientBuilder.setConnectionManager(new BasicHttpClientConnectionManager());

        final HttpHost target = start();
        final HttpGet get = new HttpGet("/random/1024");
        final CloseableHttpResponse response = this.httpclient.execute(target, get);
        try {
            Assert.assertEquals(200, response.getStatusLine().getStatusCode());
            EntityUtils.consume(response.getEntity());
        } finally {
            response.close();
        }
    }

    @Test(expected=IllegalStateException.class)
    public void testConnectionStillInUse() throws Exception {
        this.clientBuilder.setConnectionManager(new BasicHttpClientConnectionManager());

        final HttpHost target = start();
        final HttpGet get1 = new HttpGet("/random/1024");
        this.httpclient.execute(target, get1);
        final HttpGet get2 = new HttpGet("/random/1024");
        this.httpclient.execute(target, get2);
    }

}
