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

package org.apache.http.impl.conn.tsccm;

/*
import java.util.Date;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
*/

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;


import org.apache.http.HttpHost;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.scheme.SocketFactory;
import org.apache.http.conn.ClientConnectionOperator;
import org.apache.http.impl.conn.DefaultClientConnectionOperator;



/**
 * Tests for simple helper classes without advanced functionality.
 */
public class TestDumbHelpers extends TestCase {

    public final static
        HttpHost TARGET = new HttpHost("target.test.invalid");


    /** The default scheme registry. */
    private SchemeRegistry supportedSchemes;



    public TestDumbHelpers(String testName) {
        super(testName);
    }

    public static void main(String args[]) {
        String[] testCaseName = { TestDumbHelpers.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    public static Test suite() {
        return new TestSuite(TestDumbHelpers.class);
    }


    @Override
    protected void setUp() {
        supportedSchemes = new SchemeRegistry();
        SocketFactory sf = PlainSocketFactory.getSocketFactory();
        supportedSchemes.register(new Scheme("http", sf, 80));
    }




    public void testBasicPoolEntry() {
        HttpRoute route = new HttpRoute(TARGET);
        ClientConnectionOperator ccop =
            new DefaultClientConnectionOperator(supportedSchemes);

        BasicPoolEntry bpe = null;
        try {
            bpe = new BasicPoolEntry(null, null, null);
            fail("null operator not detected");
        } catch (NullPointerException npx) {
            // expected
        } catch (IllegalArgumentException iax) {
            // would be preferred
        }

        try {
            bpe = new BasicPoolEntry(ccop, null, null);
            fail("null route not detected");
        } catch (IllegalArgumentException iax) {
            // expected
        }

        bpe = new BasicPoolEntry(ccop, route, null);
        assertEquals ("wrong operator", ccop, bpe.getOperator());
        assertEquals ("wrong route", route, bpe.getPlannedRoute());
        assertNotNull("missing ref", bpe.getWeakRef());

        assertEquals("bad weak ref", bpe, bpe.getWeakRef().get());
        assertEquals("bad ref route", route, bpe.getWeakRef().getRoute());
    }


    public void testBasicPoolEntryRef() {
        // the actual reference is tested implicitly with BasicPoolEntry
        // but we need to cover the argument check in the constructor
        try {
            new BasicPoolEntryRef(null, null);
            fail("null pool entry not detected");
        } catch (IllegalArgumentException iax) {
            // expected
        }
    }


} // class TestDumbHelpers
