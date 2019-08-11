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
package org.apache.hc.client5.http.impl;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.core5.net.URIAuthority;
import org.junit.Assert;
import org.junit.Test;

/**
 * Simple tests for {@link AuthSupport}.
 */
public class TestAuthSupport {

    @Test
    public void testExtractFromAuthority() {
        final URIAuthority uriAuthority = new URIAuthority("testUser", "localhost", 8080);
        final BasicCredentialsProvider basicCredentialsProvider = new BasicCredentialsProvider();

        AuthSupport.extractFromAuthority("http", uriAuthority, basicCredentialsProvider);

        final Credentials credentials = basicCredentialsProvider.getCredentials(new AuthScope("localhost", 8080), null);
        Assert.assertEquals("testUser", credentials.getUserPrincipal().getName());
        Assert.assertNull(credentials.getPassword());
    }
}
