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

package org.apache.http.localserver;

import java.net.InetSocketAddress;

import org.apache.http.HttpHost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.After;
import org.mockito.Mockito;

/**
 * Base class for tests using {@link LocalTestServer}. The server will not be started
 * per default.
 */
public abstract class BasicServerTestBase extends Mockito {

    /** The local server for testing. */
    protected LocalTestServer localServer;
    protected DefaultHttpClient httpclient;

    @After
    public void shutDownClient() throws Exception {
        if (httpclient != null) {
            httpclient.getConnectionManager().shutdown();
        }
    }

    @After
    public void shutDownServer() throws Exception {
        if (localServer != null) {
            localServer.stop();
        }
    }

    /**
     * Obtains the address of the local test server.
     *
     * @return  the test server host, with a scheme name of "http"
     */
    protected HttpHost getServerHttp() {
        InetSocketAddress address = localServer.getServiceAddress();
        return new HttpHost(
                address.getHostName(),
                address.getPort(),
                "http");
    }

}
