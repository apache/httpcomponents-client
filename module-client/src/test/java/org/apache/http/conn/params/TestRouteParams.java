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

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.http.HttpHost;
import org.apache.http.params.HttpParams;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.conn.params.HttpConnectionManagerParams;
import org.apache.http.conn.routing.HttpRoute;


/**
 * Unit tests for parameters.
 * Trivial, but it looks better in the Clover reports.
 */
public class TestRouteParams extends TestCase {

    public final static
        HttpHost TARGET1 = new HttpHost("target1.test.invalid");
    public final static
        HttpRoute ROUTE1 = new HttpRoute(TARGET1);


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
