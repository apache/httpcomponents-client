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

package org.apache.http.client.config;

import java.net.InetAddress;
import java.util.Arrays;

import org.apache.http.HttpHost;
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
        Assert.assertEquals(-1, config.getSocketTimeout());
        Assert.assertEquals(-1, config.getConnectTimeout());
        Assert.assertEquals(-1, config.getConnectionRequestTimeout());
        Assert.assertEquals(false, config.isExpectContinueEnabled());
        Assert.assertEquals(true, config.isAuthenticationEnabled());
        Assert.assertEquals(true, config.isRedirectsEnabled());
        Assert.assertEquals(true, config.isRelativeRedirectsAllowed());
        Assert.assertEquals(false, config.isCircularRedirectsAllowed());
        Assert.assertEquals(50, config.getMaxRedirects());
        Assert.assertEquals(null, config.getCookieSpec());
        Assert.assertEquals(null, config.getLocalAddress());
        Assert.assertEquals(null, config.getProxy());
        Assert.assertEquals(null, config.getTargetPreferredAuthSchemes());
        Assert.assertEquals(null, config.getProxyPreferredAuthSchemes());
        Assert.assertEquals(true, config.isContentCompressionEnabled());
    }

    @Test
    public void testBuildAndCopy() throws Exception {
        final RequestConfig config0 = RequestConfig.custom()
                .setSocketTimeout(22)
                .setConnectTimeout(33)
                .setConnectionRequestTimeout(44)
                .setExpectContinueEnabled(true)
                .setAuthenticationEnabled(false)
                .setRedirectsEnabled(false)
                .setRelativeRedirectsAllowed(false)
                .setCircularRedirectsAllowed(true)
                .setMaxRedirects(100)
                .setCookieSpec(CookieSpecs.STANDARD)
                .setLocalAddress(InetAddress.getLocalHost())
                .setProxy(new HttpHost("someproxy"))
                .setTargetPreferredAuthSchemes(Arrays.asList(AuthSchemes.NTLM))
                .setProxyPreferredAuthSchemes(Arrays.asList(AuthSchemes.DIGEST))
                .setContentCompressionEnabled(false)
                .build();
        final RequestConfig config = RequestConfig.copy(config0).build();
        Assert.assertEquals(22, config.getSocketTimeout());
        Assert.assertEquals(33, config.getConnectTimeout());
        Assert.assertEquals(44, config.getConnectionRequestTimeout());
        Assert.assertEquals(true, config.isExpectContinueEnabled());
        Assert.assertEquals(false, config.isAuthenticationEnabled());
        Assert.assertEquals(false, config.isRedirectsEnabled());
        Assert.assertEquals(false, config.isRelativeRedirectsAllowed());
        Assert.assertEquals(true, config.isCircularRedirectsAllowed());
        Assert.assertEquals(100, config.getMaxRedirects());
        Assert.assertEquals(CookieSpecs.STANDARD, config.getCookieSpec());
        Assert.assertEquals(InetAddress.getLocalHost(), config.getLocalAddress());
        Assert.assertEquals(new HttpHost("someproxy"), config.getProxy());
        Assert.assertEquals(Arrays.asList(AuthSchemes.NTLM), config.getTargetPreferredAuthSchemes());
        Assert.assertEquals(Arrays.asList(AuthSchemes.DIGEST), config.getProxyPreferredAuthSchemes());
        Assert.assertEquals(false, config.isContentCompressionEnabled());
    }

}
