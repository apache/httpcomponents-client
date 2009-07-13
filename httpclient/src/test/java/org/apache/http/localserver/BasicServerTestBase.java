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

import junit.framework.TestCase;

import org.apache.http.HttpHost;
import org.apache.http.conn.routing.HttpRoute;

/**
 * Base class for tests using {@link LocalTestServer}. The server will not be started 
 * per default. 
 */
public abstract class BasicServerTestBase extends TestCase {

    /** The local server for testing. */
    protected LocalTestServer localServer;

    protected BasicServerTestBase(String testName) {
        super(testName);
    }

    @Override
    protected void tearDown() throws Exception {
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

        return new HttpHost(LocalTestServer.TEST_SERVER_ADDR.getHostName(),
                            localServer.getServicePort(),
                            "http");
    }

    /**
     * Obtains the default route to the local test server.
     *
     * @return the default route to the local test server
     */
    protected HttpRoute getDefaultRoute() {
        return new HttpRoute(getServerHttp());
    }
    
}
