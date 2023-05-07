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

package org.apache.hc.client5.http.config;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.cookie.StandardCookieSpec;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestRequestConfig {

    @Test
    public void testBasics() {
        final RequestConfig config = RequestConfig.custom().build();
        config.toString();
    }

    @Test
    public void testDefaults() {
        final RequestConfig config = RequestConfig.DEFAULT;
        Assertions.assertEquals(Timeout.ofMinutes(3), config.getConnectionRequestTimeout());
        Assertions.assertFalse(config.isExpectContinueEnabled());
        Assertions.assertTrue(config.isAuthenticationEnabled());
        Assertions.assertTrue(config.isRedirectsEnabled());
        Assertions.assertFalse(config.isCircularRedirectsAllowed());
        Assertions.assertEquals(50, config.getMaxRedirects());
        Assertions.assertNull(config.getCookieSpec());
        Assertions.assertNull(config.getTargetPreferredAuthSchemes());
        Assertions.assertNull(config.getProxyPreferredAuthSchemes());
        Assertions.assertTrue(config.isContentCompressionEnabled());
    }

    @Test
    public void testBuildAndCopy() throws Exception {
        final RequestConfig config0 = RequestConfig.custom()
                .setConnectionRequestTimeout(44, TimeUnit.MILLISECONDS)
                .setExpectContinueEnabled(true)
                .setAuthenticationEnabled(false)
                .setRedirectsEnabled(false)
                .setCircularRedirectsAllowed(true)
                .setMaxRedirects(100)
                .setCookieSpec(StandardCookieSpec.STRICT)
                .setTargetPreferredAuthSchemes(Collections.singletonList(StandardAuthScheme.BEARER))
                .setProxyPreferredAuthSchemes(Collections.singletonList(StandardAuthScheme.DIGEST))
                .setContentCompressionEnabled(false)
                .build();
        final RequestConfig config = RequestConfig.copy(config0).build();
        Assertions.assertEquals(TimeValue.ofMilliseconds(44), config.getConnectionRequestTimeout());
        Assertions.assertTrue(config.isExpectContinueEnabled());
        Assertions.assertFalse(config.isAuthenticationEnabled());
        Assertions.assertFalse(config.isRedirectsEnabled());
        Assertions.assertTrue(config.isCircularRedirectsAllowed());
        Assertions.assertEquals(100, config.getMaxRedirects());
        Assertions.assertEquals(StandardCookieSpec.STRICT, config.getCookieSpec());
        Assertions.assertEquals(Collections.singletonList(StandardAuthScheme.BEARER), config.getTargetPreferredAuthSchemes());
        Assertions.assertEquals(Collections.singletonList(StandardAuthScheme.DIGEST), config.getProxyPreferredAuthSchemes());
        Assertions.assertFalse(config.isContentCompressionEnabled());
    }

}
