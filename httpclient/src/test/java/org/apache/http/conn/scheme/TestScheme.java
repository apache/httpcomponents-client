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

package org.apache.http.conn.scheme;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit tests for {@link Scheme}.
 */
public class TestScheme extends TestCase {

    public TestScheme(String testName) {
        super(testName);
    }

    public static void main(String args[]) {
        String[] testCaseName = { TestScheme.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    public static Test suite() {
        return new TestSuite(TestScheme.class);
    }
    
    public void testPortResolution() {
        Scheme http = new Scheme("http", PlainSocketFactory.getSocketFactory(), 80);
        assertEquals(80, http.resolvePort(0));
        assertEquals(80, http.resolvePort(-1));
        assertEquals(8080, http.resolvePort(8080));
        assertEquals(80808080, http.resolvePort(80808080));
    }

}
