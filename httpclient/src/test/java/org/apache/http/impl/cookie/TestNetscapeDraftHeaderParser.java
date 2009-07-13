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

package org.apache.http.impl.cookie;

import org.apache.http.HeaderElement;
import org.apache.http.NameValuePair;
import org.apache.http.message.ParserCursor;
import org.apache.http.util.CharArrayBuffer;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit tests for {@link NetscapeDraftHeaderParser}.
 *
 */
public class TestNetscapeDraftHeaderParser extends TestCase {

    public TestNetscapeDraftHeaderParser(String testName) {
        super(testName);
    }

    public static void main(String args[]) {
        String[] testCaseName = { TestNetscapeDraftHeaderParser.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    public static Test suite() {
        return new TestSuite(TestNetscapeDraftHeaderParser.class);
    }

    public void testNetscapeCookieParsing() throws Exception {
        NetscapeDraftHeaderParser parser = NetscapeDraftHeaderParser.DEFAULT;
        
        String s = 
            "name  = value; test; test1 =  stuff,with,commas   ; test2 =  \"stuff; stuff\"; test3=\"stuff";
        CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.append(s);
        ParserCursor cursor = new ParserCursor(0, s.length());
        
        HeaderElement he = parser.parseHeader(buffer, cursor);
        assertEquals("name", he.getName());
        assertEquals("value", he.getValue());
        NameValuePair[] params = he.getParameters();
        assertEquals("test", params[0].getName());
        assertEquals(null, params[0].getValue());
        assertEquals("test1", params[1].getName());
        assertEquals("stuff,with,commas", params[1].getValue());
        assertEquals("test2", params[2].getName());
        assertEquals("stuff; stuff", params[2].getValue());
        assertEquals("test3", params[3].getName());
        assertEquals("\"stuff", params[3].getValue());
        assertEquals(s.length(), cursor.getPos());
        assertTrue(cursor.atEnd());

        s = "  ";
        buffer = new CharArrayBuffer(16);
        buffer.append(s);
        cursor = new ParserCursor(0, s.length());
        he = parser.parseHeader(buffer, cursor);
        assertEquals("", he.getName());
        assertEquals(null, he.getValue());
    }

}
