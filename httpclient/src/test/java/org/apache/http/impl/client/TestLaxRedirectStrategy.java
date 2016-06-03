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

import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.junit.Assert;
import org.junit.Test;

public class TestLaxRedirectStrategy {

    @Test
    public void testIsRedirectable() {
        assertLaxRedirectable(new LaxRedirectStrategy());
    }

    @Test
    public void testInstance() {
        assertLaxRedirectable(LaxRedirectStrategy.INSTANCE);
    }

    private void assertLaxRedirectable(final LaxRedirectStrategy redirectStrategy) {
        Assert.assertTrue(redirectStrategy.isRedirectable(HttpGet.METHOD_NAME));
        Assert.assertTrue(redirectStrategy.isRedirectable(HttpHead.METHOD_NAME));
        Assert.assertFalse(redirectStrategy.isRedirectable(HttpPut.METHOD_NAME));
        Assert.assertTrue(redirectStrategy.isRedirectable(HttpPost.METHOD_NAME));
        Assert.assertTrue(redirectStrategy.isRedirectable(HttpDelete.METHOD_NAME));
    }
}
