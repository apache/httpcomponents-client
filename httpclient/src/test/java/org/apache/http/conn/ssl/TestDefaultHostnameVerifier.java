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

package org.apache.http.conn.ssl;

import javax.net.ssl.SSLException;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link org.apache.http.conn.ssl.DefaultHostnameVerifier}.
 */
public class TestDefaultHostnameVerifier {

    @Test
    public void testExtractCN() throws Exception {
        Assert.assertEquals("blah", DefaultHostnameVerifier.extractCN("cn=blah, ou=blah, o=blah"));
        Assert.assertEquals("blah", DefaultHostnameVerifier.extractCN("cn=blah, cn=yada, cn=booh"));
        Assert.assertEquals("blah", DefaultHostnameVerifier.extractCN("c = pampa ,  cn  =    blah    , ou = blah , o = blah"));
        Assert.assertEquals("blah", DefaultHostnameVerifier.extractCN("cn=\"blah\", ou=blah, o=blah"));
        Assert.assertEquals("blah  blah", DefaultHostnameVerifier.extractCN("cn=\"blah  blah\", ou=blah, o=blah"));
        Assert.assertEquals("blah, blah", DefaultHostnameVerifier.extractCN("cn=\"blah, blah\", ou=blah, o=blah"));
        Assert.assertEquals("blah, blah", DefaultHostnameVerifier.extractCN("cn=blah\\, blah, ou=blah, o=blah"));
        Assert.assertEquals("blah", DefaultHostnameVerifier.extractCN("c = cn=uuh, cn=blah, ou=blah, o=blah"));
    }

    @Test(expected = SSLException.class)
    public void testExtractCNMissing() throws Exception {
        DefaultHostnameVerifier.extractCN("blah,blah");
    }

    @Test(expected = SSLException.class)
    public void testExtractCNNull() throws Exception {
        DefaultHostnameVerifier.extractCN("cn,o=blah");
    }

}
