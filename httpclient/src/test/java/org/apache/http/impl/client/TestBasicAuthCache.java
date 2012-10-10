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

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScheme;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit tests for {@link BasicAuthCache}.
 */
public class TestBasicAuthCache {

    @Test
    public void testBasics() throws Exception {
        BasicAuthCache cache = new BasicAuthCache();
        AuthScheme authScheme = Mockito.mock(AuthScheme.class);
        cache.put(new HttpHost("localhost", 80), authScheme);
        Assert.assertSame(authScheme, cache.get(new HttpHost("localhost", 80)));
        cache.remove(new HttpHost("localhost", 80));
        Assert.assertNull(cache.get(new HttpHost("localhost", 80)));
        cache.put(new HttpHost("localhost", 80), authScheme);
        cache.clear();
        Assert.assertNull(cache.get(new HttpHost("localhost", 80)));
    }

    @Test
    public void testGetKey() throws Exception {
        BasicAuthCache cache = new BasicAuthCache();
        HttpHost target = new HttpHost("localhost", 443, "https");
        Assert.assertSame(target, cache.getKey(target));
        Assert.assertEquals(target, cache.getKey(new HttpHost("localhost", -1, "https")));
    }

}
