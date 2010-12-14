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

package org.apache.http.impl.client;

import static org.junit.Assert.assertEquals;

import org.apache.http.HttpHost;
import org.apache.http.client.methods.HttpHead;
import org.junit.Test;

/**
 * Unit tests for {@link AbstractHttpClient}.
 */
public class TestAbstractHttpClient {

    @Test
    public void testHTTPCLIENT_911() throws Exception{
        assertEquals(new HttpHost("localhost"),AbstractHttpClient.determineTarget(new HttpHead("http://localhost/abcd")));
        assertEquals(new HttpHost("localhost"),AbstractHttpClient.determineTarget(new HttpHead("http://localhost/abcd%3A")));
        
        assertEquals(new HttpHost("local_host"),AbstractHttpClient.determineTarget(new HttpHead("http://local_host/abcd")));
        assertEquals(new HttpHost("local_host"),AbstractHttpClient.determineTarget(new HttpHead("http://local_host/abcd%3A")));
        
        assertEquals(new HttpHost("localhost",8),AbstractHttpClient.determineTarget(new HttpHead("http://localhost:8/abcd")));
        assertEquals(new HttpHost("local_host",8),AbstractHttpClient.determineTarget(new HttpHead("http://local_host:8/abcd")));

        // URI seems to OK with missing port number
        assertEquals(new HttpHost("localhost"),AbstractHttpClient.determineTarget(new HttpHead("http://localhost:/abcd")));
        assertEquals(new HttpHost("local_host"),AbstractHttpClient.determineTarget(new HttpHead("http://local_host:/abcd")));

        assertEquals(new HttpHost("localhost",8080),AbstractHttpClient.determineTarget(new HttpHead("http://user:pass@localhost:8080/abcd")));
        assertEquals(new HttpHost("local_host",8080),AbstractHttpClient.determineTarget(new HttpHead("http://user:pass@local_host:8080/abcd")));

        assertEquals(new HttpHost("localhost",8080),AbstractHttpClient.determineTarget(new HttpHead("http://@localhost:8080/abcd")));
        assertEquals(new HttpHost("local_host",8080),AbstractHttpClient.determineTarget(new HttpHead("http://@local_host:8080/abcd")));

    }
}
