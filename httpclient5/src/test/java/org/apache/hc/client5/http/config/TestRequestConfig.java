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
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.Assert;
import org.junit.Test;

public class TestRequestConfig {

    @Test
    public void testBasics() {
        final RequestConfig config = RequestConfig.custom().build();
        config.toString();
    }

    @Test
    public void testDefaults() {
        final RequestConfig config = RequestConfig.DEFAULT;
        Assert.assertEquals(Timeout.ofMinutes(3), config.getConnectTimeout());
        Assert.assertEquals(Timeout.ofMinutes(3), config.getConnectionRequestTimeout());
        Assert.assertEquals(false, config.isExpectContinueEnabled());
        Assert.assertEquals(true, config.isAuthenticationEnabled());
        Assert.assertEquals(true, config.isRedirectsEnabled());
        Assert.assertEquals(false, config.isCircularRedirectsAllowed());
        Assert.assertEquals(50, config.getMaxRedirects());
        Assert.assertEquals(null, config.getCookieSpec());
        Assert.assertEquals(null, config.getProxy());
        Assert.assertEquals(null, config.getTargetPreferredAuthSchemes());
        Assert.assertEquals(null, config.getProxyPreferredAuthSchemes());
        Assert.assertEquals(true, config.isContentCompressionEnabled());
    }

    @Test
    public void testBuildAndCopy() throws Exception {
        final RequestConfig config0 = RequestConfig.custom()
                .setConnectTimeout(33, TimeUnit.MILLISECONDS)
                .setConnectionRequestTimeout(44, TimeUnit.MILLISECONDS)
                .setExpectContinueEnabled(true)
                .setAuthenticationEnabled(false)
                .setRedirectsEnabled(false)
                .setCircularRedirectsAllowed(true)
                .setMaxRedirects(100)
                .setCookieSpec(StandardCookieSpec.STRICT)
                .setProxy(new HttpHost("someproxy"))
                .setTargetPreferredAuthSchemes(Collections.singletonList(StandardAuthScheme.NTLM))
                .setProxyPreferredAuthSchemes(Collections.singletonList(StandardAuthScheme.DIGEST))
                .setContentCompressionEnabled(false)
                .build();
        final RequestConfig config = RequestConfig.copy(config0).build();
        Assert.assertEquals(TimeValue.ofMilliseconds(33), config.getConnectTimeout());
        Assert.assertEquals(TimeValue.ofMilliseconds(44), config.getConnectionRequestTimeout());
        Assert.assertEquals(true, config.isExpectContinueEnabled());
        Assert.assertEquals(false, config.isAuthenticationEnabled());
        Assert.assertEquals(false, config.isRedirectsEnabled());
        Assert.assertEquals(true, config.isCircularRedirectsAllowed());
        Assert.assertEquals(100, config.getMaxRedirects());
        Assert.assertEquals(StandardCookieSpec.STRICT, config.getCookieSpec());
        Assert.assertEquals(new HttpHost("someproxy"), config.getProxy());
        Assert.assertEquals(Collections.singletonList(StandardAuthScheme.NTLM), config.getTargetPreferredAuthSchemes());
        Assert.assertEquals(Collections.singletonList(StandardAuthScheme.DIGEST), config.getProxyPreferredAuthSchemes());
        Assert.assertEquals(false, config.isContentCompressionEnabled());
    }

}
