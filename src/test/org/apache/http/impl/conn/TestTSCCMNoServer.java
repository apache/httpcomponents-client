/*
 * $HeadURL$
 * $Revision$
 * $Date$
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
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.http.HttpHost;
import org.apache.http.HttpVersion;
import org.apache.http.params.HttpParams;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.conn.Scheme;
import org.apache.http.conn.SchemeRegistry;
import org.apache.http.conn.SocketFactory;
import org.apache.http.conn.PlainSocketFactory;
import org.apache.http.conn.HttpRoute;
import org.apache.http.conn.ManagedClientConnection;


/**
 * Tests for <code>ThreadSafeClientConnManager</code> that do not require
 * a server to communicate with.
 */
public class TestTSCCMNoServer extends TestCase {

    public TestTSCCMNoServer(String testName) {
        super(testName);
    }

    public static void main(String args[]) {
        String[] testCaseName = { TestTSCCMNoServer.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    public static Test suite() {
        return new TestSuite(TestTSCCMNoServer.class);
    }


    /**
     * Helper to instantiate a <code>ThreadSafeClientConnManager</code>.
     *
     * @param params    the parameters, or
     *                  <code>null</code> to use defaults
     * @param schreg    the scheme registry, or
     *                  <code>null</code> to use defaults
     *
     * @return  a connection manager to test
     */
    public ThreadSafeClientConnManager createTSCCM(HttpParams params,
                                                   SchemeRegistry schreg) {
        if (params == null)
            params = createDefaultParams();
        if (schreg == null)
            schreg = createSchemeRegistry();

        return new ThreadSafeClientConnManager(params, schreg);
    }


    /**
     * Instantiates default parameters.
     *
     * @return  the default parameters
     */
    public HttpParams createDefaultParams() {

        HttpParams params = new BasicHttpParams();
        HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
        HttpProtocolParams.setUseExpectContinue(params, false);

        return params;
    }

    /**
     * Instantiates a default scheme registry.
     *
     * @return the default scheme registry
     */
    public SchemeRegistry createSchemeRegistry() {

        SchemeRegistry schreg = new SchemeRegistry();
        SocketFactory sf = PlainSocketFactory.getSocketFactory();
        schreg.register(new Scheme("http", sf, 80));

        return schreg;
    }


    public void testGetConnection() {
        ThreadSafeClientConnManager mgr = createTSCCM(null, null);

        HttpHost target = new HttpHost("www.test.invalid", 80, "http");
        HttpRoute route = new HttpRoute(target, null, false);

        ManagedClientConnection conn = mgr.getConnection(route);
        assertNotNull(conn);
        assertNull(conn.getRoute());
        assertFalse(conn.isOpen());

        mgr.releaseConnection(conn);
        mgr.shutdown();
    }


} // class TestTSCCMNoServer
