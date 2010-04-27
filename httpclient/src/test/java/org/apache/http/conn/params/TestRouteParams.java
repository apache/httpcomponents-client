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

package org.apache.http.conn.params;

import java.net.InetAddress;

import org.apache.http.HttpHost;
import org.apache.http.params.HttpParams;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.DefaultedHttpParams;
import org.apache.http.conn.routing.HttpRoute;

// for hierarchy testing
import org.apache.http.impl.client.ClientParamsStack;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for parameters.
 * Trivial, but it looks better in the Clover reports.
 */
public class TestRouteParams {

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

    @Test
    public void testSetGet() {
        HttpParams params = new BasicHttpParams();

        Assert.assertNull("phantom proxy",
                   ConnRouteParams.getDefaultProxy(params));
        Assert.assertNull("phantom route",
                   ConnRouteParams.getForcedRoute(params));
        Assert.assertNull("phantom address",
                   ConnRouteParams.getLocalAddress(params));

        ConnRouteParams.setDefaultProxy(params, TARGET1);
        Assert.assertSame("wrong proxy", TARGET1,
                   ConnRouteParams.getDefaultProxy(params));
        ConnRouteParams.setForcedRoute(params, ROUTE1);
        Assert.assertSame("wrong route", ROUTE1,
                   ConnRouteParams.getForcedRoute(params));
        ConnRouteParams.setLocalAddress(params, LOCAL1);
        Assert.assertSame("wrong address", LOCAL1,
                   ConnRouteParams.getLocalAddress(params));
    }

    @Test
    public void testSetNull() {
        HttpParams params = new BasicHttpParams();

        ConnRouteParams.setDefaultProxy(params, null);
        ConnRouteParams.setForcedRoute(params, null);
        ConnRouteParams.setLocalAddress(params, null);

        Assert.assertNull("phantom proxy",
                   ConnRouteParams.getDefaultProxy(params));
        Assert.assertNull("phantom route",
                   ConnRouteParams.getForcedRoute(params));
        Assert.assertNull("phantom address",
                   ConnRouteParams.getLocalAddress(params));

        ConnRouteParams.setDefaultProxy(params, ConnRouteParams.NO_HOST);
        Assert.assertNull("null proxy not detected",
                   ConnRouteParams.getDefaultProxy(params));

        ConnRouteParams.setForcedRoute(params, ConnRouteParams.NO_ROUTE);
        Assert.assertNull("null route not detected",
                   ConnRouteParams.getForcedRoute(params));
    }

    @Test
    public void testUnsetHierarchy() {
        // hierarchical unsetting is only tested for the default proxy
        HttpParams daddy = new BasicHttpParams();
        HttpParams dummy  = new BasicHttpParams();
        HttpParams child  = new BasicHttpParams();

        ConnRouteParams.setDefaultProxy(daddy, TARGET1);
        ConnRouteParams.setDefaultProxy(child, ConnRouteParams.NO_HOST);

        HttpParams hierarchy =
            new ClientParamsStack(null, daddy, child, null);
        Assert.assertNull("1", ConnRouteParams.getDefaultProxy(hierarchy));

        hierarchy = new ClientParamsStack
            (null,
             daddy,
             new ClientParamsStack(null, child, dummy, null),
             null);
        Assert.assertNull("2", ConnRouteParams.getDefaultProxy(hierarchy));

        hierarchy = new ClientParamsStack
            (null, daddy, new DefaultedHttpParams(child, dummy), null);
        Assert.assertNull("3", ConnRouteParams.getDefaultProxy(hierarchy));

        hierarchy = new DefaultedHttpParams(child, daddy);
        Assert.assertNull("4", ConnRouteParams.getDefaultProxy(hierarchy));

        hierarchy = new DefaultedHttpParams
            (new DefaultedHttpParams(child, dummy), daddy);
        Assert.assertNull("5", ConnRouteParams.getDefaultProxy(hierarchy));

        hierarchy = new DefaultedHttpParams
            (child, new DefaultedHttpParams(dummy, daddy));
        Assert.assertNull("6", ConnRouteParams.getDefaultProxy(hierarchy));
    }

    @Test
    public void testBadArgs() {

        try {
            ConnRouteParams.getDefaultProxy(null);
        } catch (IllegalArgumentException iax) {
            // expected
        }

        try {
            ConnRouteParams.getForcedRoute(null);
        } catch (IllegalArgumentException iax) {
            // expected
        }

        try {
            ConnRouteParams.getLocalAddress(null);
        } catch (IllegalArgumentException iax) {
            // expected
        }

        try {
            ConnRouteParams.setDefaultProxy(null, null);
        } catch (IllegalArgumentException iax) {
            // expected
        }

        try {
            ConnRouteParams.setForcedRoute(null, null);
        } catch (IllegalArgumentException iax) {
            // expected
        }

        try {
            ConnRouteParams.setLocalAddress(null, null);
        } catch (IllegalArgumentException iax) {
            // expected
        }
    }

}
