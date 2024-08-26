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

package org.apache.hc.client5.testing.extension.sync;

import java.util.Collection;

import org.apache.hc.client5.http.AuthenticationStrategy;
import org.apache.hc.client5.http.HttpRequestRetryStrategy;
import org.apache.hc.client5.http.UserTokenHandler;
import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.classic.ExecChainHandler;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.util.Timeout;

public interface TestClientBuilder {

    ClientProtocolLevel getProtocolLevel();

    TestClientBuilder setTimeout(Timeout soTimeout);

    TestClientBuilder setConnectionManager(HttpClientConnectionManager connManager);

    default TestClientBuilder addResponseInterceptorFirst(final HttpResponseInterceptor interceptor) {
        return this;
    }

    default TestClientBuilder addResponseInterceptorLast(HttpResponseInterceptor interceptor) {
        throw new UnsupportedOperationException("Operation not supported by " + getProtocolLevel());
    }

    default TestClientBuilder addRequestInterceptorFirst(HttpRequestInterceptor interceptor) {
        throw new UnsupportedOperationException("Operation not supported by " + getProtocolLevel());
    }

    default TestClientBuilder addRequestInterceptorLast(HttpRequestInterceptor interceptor) {
        throw new UnsupportedOperationException("Operation not supported by " + getProtocolLevel());
    }

    default TestClientBuilder setUserTokenHandler(UserTokenHandler userTokenHandler) {
        throw new UnsupportedOperationException("Operation not supported by " + getProtocolLevel());
    }

    default TestClientBuilder setDefaultHeaders(Collection<? extends Header> defaultHeaders) {
        throw new UnsupportedOperationException("Operation not supported by " + getProtocolLevel());
    }

    default TestClientBuilder setRetryStrategy(HttpRequestRetryStrategy retryStrategy) {
        throw new UnsupportedOperationException("Operation not supported by " + getProtocolLevel());
    }

    default TestClientBuilder setTargetAuthenticationStrategy(AuthenticationStrategy targetAuthStrategy) {
        throw new UnsupportedOperationException("Operation not supported by " + getProtocolLevel());
    }

    default TestClientBuilder setDefaultAuthSchemeRegistry(Lookup<AuthSchemeFactory> authSchemeRegistry) {
        throw new UnsupportedOperationException("Operation not supported by " + getProtocolLevel());
    }

    default TestClientBuilder setRequestExecutor(HttpRequestExecutor requestExec) {
        throw new UnsupportedOperationException("Operation not supported by " + getProtocolLevel());
    }

    default TestClientBuilder addExecInterceptorFirst(String name, ExecChainHandler interceptor) {
        throw new UnsupportedOperationException("Operation not supported by " + getProtocolLevel());
    }

    default TestClientBuilder addExecInterceptorLast(String name, ExecChainHandler interceptor) {
        throw new UnsupportedOperationException("Operation not supported by " + getProtocolLevel());
    }

    default TestClientBuilder setDefaultCredentialsProvider(CredentialsProvider credentialsProvider) {
        throw new UnsupportedOperationException("Operation not supported by " + getProtocolLevel());
    }

    TestClient build() throws Exception;

}
