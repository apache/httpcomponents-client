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

package org.apache.http.conn;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;


/**
 * Unit tests for exceptions.
 * Trivial, but it looks better in the Clover reports.
 */
public class TestExceptions extends TestCase {

    public TestExceptions(String testName) {
        super(testName);
    }

    public static void main(String args[]) {
        String[] testCaseName = { TestExceptions.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    public static Test suite() {
        return new TestSuite(TestExceptions.class);
    }

    public void testCTX() {
        String msg = "sample exception message";
        ConnectTimeoutException ctx =
            new ConnectTimeoutException(msg);
        assertFalse(ctx.toString().indexOf(msg) < 0);
        assertSame(msg, ctx.getMessage());

        ctx = new ConnectTimeoutException();
        assertNull(ctx.getMessage());
    }

    public void testCPTX() {
        String msg = "sample exception message";
        ConnectionPoolTimeoutException cptx =
            new ConnectionPoolTimeoutException(msg);
        assertFalse(cptx.toString().indexOf(msg) < 0);
        assertSame(msg, cptx.getMessage());

        cptx = new ConnectionPoolTimeoutException();
        assertNull(cptx.getMessage());
    }
}
