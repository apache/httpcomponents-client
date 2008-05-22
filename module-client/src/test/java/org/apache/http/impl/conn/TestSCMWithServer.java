/*
 * $HeadURL:$
 * $Revision:$
 * $Date:$
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

package org.apache.http.impl.conn;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.apache.http.HttpHost;
import org.apache.http.conn.ManagedClientConnection;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.localserver.ServerTestBase;
import org.apache.http.params.HttpParams;

public class TestSCMWithServer extends ServerTestBase {

    public TestSCMWithServer(String testName) {
        super(testName);
    }

    public static void main(String args[]) {
        String[] testCaseName = { TestSCMWithServer.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    public static Test suite() {
        return new TestSuite(TestSCMWithServer.class);
    }
    
    /**
     * Helper to instantiate a <code>SingleClientConnManager</code>.
     *
     * @param params    the parameters, or
     *                  <code>null</code> to use defaults
     * @param schreg    the scheme registry, or
     *                  <code>null</code> to use defaults
     *
     * @return  a connection manager to test
     */
    public SingleClientConnManager createSCCM(HttpParams params,
                                              SchemeRegistry schreg) {
        if (params == null)
            params = defaultParams;
        if (schreg == null)
            schreg = supportedSchemes;

        return new SingleClientConnManager(params, schreg);
    }
    
    /**
     * Tests that SCM can still connect to the same host after
     * a connection was aborted.
     */
    public void testOpenAfterAbort() throws Exception {
        HttpParams mgrpar = defaultParams.copy();
        ConnManagerParams.setMaxTotalConnections(mgrpar, 1);

        SingleClientConnManager mgr = createSCCM(mgrpar, null);

        final HttpHost target = getServerHttp();
        final HttpRoute route = new HttpRoute(target, null, false);

        ManagedClientConnection conn = mgr.getConnection(route, null);
        assertTrue(conn instanceof AbstractClientConnAdapter);
        ((AbstractClientConnAdapter) conn).abortConnection();
        
        conn = mgr.getConnection(route, null);
        assertFalse("connection should have been closed", conn.isOpen());
        conn.open(route, httpContext, defaultParams);

        mgr.releaseConnection(conn);
        mgr.shutdown();
    }
}
