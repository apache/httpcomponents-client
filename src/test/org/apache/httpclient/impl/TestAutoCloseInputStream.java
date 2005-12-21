/*
 * $HeadURL$
 * $Revision$
 * $Date$
 * ====================================================================
 *
 *  Copyright 2002-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class TestAutoCloseInputStream extends TestCase {

    public TestAutoCloseInputStream(String testName) {
        super(testName);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        return new TestSuite(TestAutoCloseInputStream.class);
    }

    // ------------------------------------------------------------------- Main
    public static void main(String args[]) {
        String[] testCaseName = { TestAutoCloseInputStream.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    class TestResponseConsumedWatcher implements ResponseConsumedWatcher {
        
        private int count;
        
        public TestResponseConsumedWatcher() {
            super();
            count = 0;
        }
        
        public void responseConsumed() {
            count++;
        }
        
        public int getCount() {
            return this.count;
        }
    };
    
    
    public void testConstructors() throws Exception {
        InputStream instream = new ByteArrayInputStream(new byte[] {});
        ResponseConsumedWatcher watcher = new TestResponseConsumedWatcher();
        new AutoCloseInputStream(instream, watcher);
        new AutoCloseInputStream(instream, null);
    }

    public void testBasics() throws IOException {
        byte[] input = "0123456789ABCDEF".getBytes("US-ASCII");
        InputStream instream = new ByteArrayInputStream(input);
        TestResponseConsumedWatcher watcher = new TestResponseConsumedWatcher();
        instream = new AutoCloseInputStream(instream, watcher);
        byte[] tmp = new byte[input.length];
        int ch = instream.read();
        assertTrue(ch != -1);
        tmp[0] = (byte)ch;
        assertTrue(instream.read(tmp, 1, tmp.length - 1) != -1);
        assertTrue(instream.read(tmp) == -1);
        for (int i = 0; i < input.length; i++) {
            assertEquals(input[i], tmp[i]);
        }        
        assertTrue(instream.read() == -1);
        instream.close();
        instream.close();
        // Has been triggered once only
        assertEquals(1, watcher.getCount());
    }

    public void testNullWatcher() throws IOException {
        byte[] input = "0".getBytes("US-ASCII");
        InputStream instream = new ByteArrayInputStream(input);
        instream = new AutoCloseInputStream(instream, null);
        assertTrue(instream.read() != -1);
        assertTrue(instream.read() == -1);
        assertTrue(instream.read() == -1);
    }
}

