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

import org.apache.http.Header;
import org.apache.http.HttpConnection;
import org.apache.http.HttpMutableEntity;
import org.apache.http.HttpMutableResponse;
import org.apache.http.HttpVersion;
import org.apache.http.StatusLine;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.mockup.HttpConnectionMockup;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class TestDefaultResponseConsumedWatcher extends TestCase {

    public TestDefaultResponseConsumedWatcher(String testName) {
        super(testName);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        return new TestSuite(TestDefaultResponseConsumedWatcher.class);
    }

    // ------------------------------------------------------------------- Main
    public static void main(String args[]) {
        String[] testCaseName = { TestDefaultResponseConsumedWatcher.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    public void testIllegalResponseArg() throws Exception {
        try {
            new DefaultResponseConsumedWatcher(null, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            new DefaultResponseConsumedWatcher(
                    new HttpConnectionMockup(), null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    public void testConnectionAutoClose() throws Exception {
        byte[] data = new byte[] {'1', '2', '3'};
        HttpConnection conn = new HttpConnectionMockup();
        HttpMutableEntity entity = new BasicHttpEntity();
        entity.setChunked(false);
        entity.setContentLength(data.length);
        entity.setContent(new ByteArrayInputStream(data));
        
        StatusLine statusline = new StatusLine(HttpVersion.HTTP_1_0, 200, "OK");
        HttpMutableResponse response = new BasicHttpResponse(statusline);
        response.setHeader(new Header("Connection", "Close"));
        response.setParams(new DefaultHttpParams(null));
        response.setEntity(entity);
        
        // Wrap the entity input stream 
        ResponseConsumedWatcher watcher = new DefaultResponseConsumedWatcher(conn, response);
        entity.setContent(new AutoCloseInputStream(entity.getContent(), watcher));
        
        assertTrue(conn.isOpen());
        while (entity.getContent().read() != -1) {}
        assertFalse(conn.isOpen());
    }

    public void testConnectionKeepAlive() throws Exception {
        byte[] data = new byte[] {'1', '2', '3'};
        HttpConnection conn = new HttpConnectionMockup();
        HttpMutableEntity entity = new BasicHttpEntity();
        entity.setChunked(false);
        entity.setContentLength(data.length);
        entity.setContent(new ByteArrayInputStream(data));
        
        StatusLine statusline = new StatusLine(HttpVersion.HTTP_1_1, 200, "OK");
        HttpMutableResponse response = new BasicHttpResponse(statusline);
        response.setHeader(new Header("Connection", "Keep-alive"));
        response.setParams(new DefaultHttpParams(null));
        response.setEntity(entity);
        
        // Wrap the entity input stream 
        ResponseConsumedWatcher watcher = new DefaultResponseConsumedWatcher(conn, response);
        entity.setContent(new AutoCloseInputStream(entity.getContent(), watcher));
        
        assertTrue(conn.isOpen());
        while (entity.getContent().read() != -1) {}
        assertTrue(conn.isOpen());
    }
}

