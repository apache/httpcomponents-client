/*
 * $HeadURL$
 * $Revision$
 * $Date$
 * 
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

package org.apache.http.conn.params;

import java.net.InetAddress;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.http.HttpHost;
import org.apache.http.params.HttpParams;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.DefaultedHttpParams;
import org.apache.http.conn.routing.HttpRoute;

// for hierarchy testing
import org.apache.http.impl.client.ClientParamsStack;

/**
 * Unit tests for parameters.
 * Trivial, but it looks better in the Clover reports.
 */
public class TestRouteParams extends TestCase {

    public final static
        HttpHost TARGET1 = new HttpHost("target1.test.invalid");
    public final static
        HttpRoute ROUTE1 = new HttpRoute(TARGET1);
    public final static InetAddress LOCAL1;

    // need static initializer to deal with exceptions
    static {
        try {
            LOCAL1 = InetAddress.getByAddress(new byte[]{ 127, 0, 0, 1 });
        } catch (Exception x) {
            throw new ExceptionInInitializerError(x);
        }
    }


    public TestRouteParams(String testName) {
        super(testName);
    }

    public static void main(String args[]) {
        String[] testCaseName = { TestRouteParams.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    public static Test suite() {
        return new TestSuite(TestRouteParams.class);
    }



    public void testSetGet() {
        HttpParams params = new BasicHttpParams();

        assertNull("phantom proxy",
                   HttpRouteParams.getDefaultProxy(params));
        assertNull("phantom route",
                   HttpRouteParams.getForcedRoute(params));
        assertNull("phantom address",
                   HttpRouteParams.getLocalAddress(params));

        HttpRouteParams.setDefaultProxy(params, TARGET1);
        assertSame("wrong proxy", TARGET1,
                   HttpRouteParams.getDefaultProxy(params));
        HttpRouteParams.setForcedRoute(params, ROUTE1);
        assertSame("wrong route", ROUTE1,
                   HttpRouteParams.getForcedRoute(params));
        HttpRouteParams.setLocalAddress(params, LOCAL1);
        assertSame("wrong address", LOCAL1,
                   HttpRouteParams.getLocalAddress(params));
    }


    public void testSetNull() {
        HttpParams params = new BasicHttpParams();

        HttpRouteParams.setDefaultProxy(params, null);
        HttpRouteParams.setForcedRoute(params, null);
        HttpRouteParams.setLocalAddress(params, null);

        assertNull("phantom proxy",
                   HttpRouteParams.getDefaultProxy(params));
        assertNull("phantom route",
                   HttpRouteParams.getForcedRoute(params));
        assertNull("phantom address",
                   HttpRouteParams.getLocalAddress(params));

        HttpRouteParams.setDefaultProxy(params, HttpRouteParams.NO_HOST);
        assertNull("null proxy not detected",
                   HttpRouteParams.getDefaultProxy(params));

        HttpRouteParams.setForcedRoute(params, HttpRouteParams.NO_ROUTE);
        assertNull("null route not detected",
                   HttpRouteParams.getForcedRoute(params));
    }


    public void testUnsetHierarchy() {
        // hierarchical unsetting is only tested for the default proxy
        HttpParams daddy = new BasicHttpParams();
        HttpParams dummy  = new BasicHttpParams();
        HttpParams child  = new BasicHttpParams();

        HttpRouteParams.setDefaultProxy(daddy, TARGET1);
        HttpRouteParams.setDefaultProxy(child, HttpRouteParams.NO_HOST);

        HttpParams hierarchy =
            new ClientParamsStack(null, daddy, child, null);
        assertNull("1", HttpRouteParams.getDefaultProxy(hierarchy));

        hierarchy = new ClientParamsStack
            (null,
             daddy,
             new ClientParamsStack(null, child, dummy, null),
             null);
        assertNull("2", HttpRouteParams.getDefaultProxy(hierarchy));

        hierarchy = new ClientParamsStack
            (null, daddy, new DefaultedHttpParams(child, dummy), null);
        assertNull("3", HttpRouteParams.getDefaultProxy(hierarchy));

        hierarchy = new DefaultedHttpParams(child, daddy);
        assertNull("4", HttpRouteParams.getDefaultProxy(hierarchy));

        hierarchy = new DefaultedHttpParams
            (new DefaultedHttpParams(child, dummy), daddy);
        assertNull("5", HttpRouteParams.getDefaultProxy(hierarchy));

        hierarchy = new DefaultedHttpParams
            (child, new DefaultedHttpParams(dummy, daddy));
        assertNull("6", HttpRouteParams.getDefaultProxy(hierarchy));
    }


    public void testBadArgs() {

        try {
            HttpRouteParams.getDefaultProxy(null);
        } catch (IllegalArgumentException iax) {
            // expected
        }

        try {
            HttpRouteParams.getForcedRoute(null);
        } catch (IllegalArgumentException iax) {
            // expected
        }

        try {
            HttpRouteParams.getLocalAddress(null);
        } catch (IllegalArgumentException iax) {
            // expected
        }

        try {
            HttpRouteParams.setDefaultProxy(null, null);
        } catch (IllegalArgumentException iax) {
            // expected
        }

        try {
            HttpRouteParams.setForcedRoute(null, null);
        } catch (IllegalArgumentException iax) {
            // expected
        }

        try {
            HttpRouteParams.setLocalAddress(null, null);
        } catch (IllegalArgumentException iax) {
            // expected
        }
    }


}
