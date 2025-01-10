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

package org.apache.hc.client5.testing.extension.async;

import java.util.Collection;

import org.apache.hc.client5.http.AuthenticationStrategy;
import org.apache.hc.client5.http.HttpRequestRetryStrategy;
import org.apache.hc.client5.http.UserTokenHandler;
import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.util.Timeout;

public interface TestAsyncClientBuilder {

    ClientProtocolLevel getProtocolLevel();

    TestAsyncClientBuilder setTimeout(Timeout soTimeout);

    default TestAsyncClientBuilder addResponseInterceptorFirst(final HttpResponseInterceptor interceptor) {
        return this;
    }

    default TestAsyncClientBuilder addResponseInterceptorLast(HttpResponseInterceptor interceptor) {
        throw new UnsupportedOperationException("Operation not supported by " + getProtocolLevel());
    }

    default TestAsyncClientBuilder addRequestInterceptorFirst(HttpRequestInterceptor interceptor) {
        throw new UnsupportedOperationException("Operation not supported by " + getProtocolLevel());
    }

    default TestAsyncClientBuilder addRequestInterceptorLast(HttpRequestInterceptor interceptor) {
        throw new UnsupportedOperationException("Operation not supported by " + getProtocolLevel());
    }

    default TestAsyncClientBuilder setTlsStrategy(TlsStrategy tlsStrategy) {
        throw new UnsupportedOperationException("Operation not supported by " + getProtocolLevel());
    }

    default TestAsyncClientBuilder setDefaultTlsConfig(TlsConfig tlsConfig) {
        throw new UnsupportedOperationException("Operation not supported by " + getProtocolLevel());
    }

    default TestAsyncClientBuilder useMessageMultiplexing() {
        throw new UnsupportedOperationException("Operation not supported by " + getProtocolLevel());
    }

    default TestAsyncClientBuilder setHttp1Config(Http1Config http1Config) {
        throw new UnsupportedOperationException("Operation not supported by " + getProtocolLevel());
    }

    default TestAsyncClientBuilder setH2Config(H2Config http1Config) {
        throw new UnsupportedOperationException("Operation not supported by " + getProtocolLevel());
    }

    default TestAsyncClientBuilder setUserTokenHandler(UserTokenHandler userTokenHandler) {
        throw new UnsupportedOperationException("Operation not supported by " + getProtocolLevel());
    }

    default TestAsyncClientBuilder setDefaultHeaders(Collection<? extends Header> defaultHeaders) {
        throw new UnsupportedOperationException("Operation not supported by " + getProtocolLevel());
    }

    default TestAsyncClientBuilder setRetryStrategy(HttpRequestRetryStrategy retryStrategy) {
        throw new UnsupportedOperationException("Operation not supported by " + getProtocolLevel());
    }

    default TestAsyncClientBuilder setTargetAuthenticationStrategy(AuthenticationStrategy targetAuthStrategy) {
        throw new UnsupportedOperationException("Operation not supported by " + getProtocolLevel());
    }

    default TestAsyncClientBuilder setDefaultAuthSchemeRegistry(Lookup<AuthSchemeFactory> authSchemeRegistry) {
        throw new UnsupportedOperationException("Operation not supported by " + getProtocolLevel());
    }

    default TestAsyncClientBuilder setDefaultCredentialsProvider(CredentialsProvider credentialsProvider) {
        throw new UnsupportedOperationException("Operation not supported by " + getProtocolLevel());
    }

    TestAsyncClient build() throws Exception;

}
