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
package org.apache.hc.client5.http.observation.impl;

import java.io.IOException;
import java.net.URISyntaxException;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecChainHandler;
import org.apache.hc.client5.http.observation.ObservingOptions;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.util.Args;

/**
 * Classic (blocking) execution interceptor that emits Micrometer {@link Observation}s
 * around HTTP client exchanges.
 * <p>
 * The observation is started before the request is executed and stopped when the
 * exchange completes or fails. A simple sampling predicate from {@link ObservingOptions}
 * can disable observations for given URIs without touching Micrometer configuration.
 *
 * @since 5.6
 */
public final class ObservationClassicExecInterceptor implements ExecChainHandler {

    private final ObservationRegistry registry;
    private final ObservingOptions opts;

    public ObservationClassicExecInterceptor(final ObservationRegistry registry,
                                             final ObservingOptions opts) {
        this.registry = Args.notNull(registry, "observationRegistry");
        this.opts = opts != null ? opts : ObservingOptions.DEFAULT;
    }

    @Override
    public ClassicHttpResponse execute(final ClassicHttpRequest request,
                                       final ExecChain.Scope scope,
                                       final ExecChain chain)
            throws IOException, HttpException {

        if (!opts.spanSampling.test(request.getRequestUri())) {
            return chain.proceed(request, scope);
        }

        final String method = request.getMethod();
        final String uriForName = safeUriForName(request);
        final String peer = request.getAuthority().getHostName();

        final Observation obs = Observation
                .createNotStarted("http.client.request", registry)
                .contextualName(method + " " + uriForName)
                .lowCardinalityKeyValue("http.method", method)
                .lowCardinalityKeyValue("net.peer.name", peer)
                .start();

        ClassicHttpResponse response = null;
        Throwable error = null;
        try {
            response = chain.proceed(request, scope);
            return response;
        } catch (final Throwable t) {
            error = t;
            throw t;
        } finally {
            if (response != null) {
                obs.lowCardinalityKeyValue("http.status_code", Integer.toString(response.getCode()));
            }
            if (opts.tagLevel == ObservingOptions.TagLevel.EXTENDED) {
                obs.lowCardinalityKeyValue("http.scheme", scope.route.getTargetHost().getSchemeName())
                        .lowCardinalityKeyValue("net.peer.name", scope.route.getTargetHost().getHostName());
            }
            if (error != null) {
                obs.error(error);
            }
            obs.stop();
        }
    }

    private static String safeUriForName(final ClassicHttpRequest req) {
        try {
            return req.getUri().toString();
        } catch (final URISyntaxException e) {
            return req.getRequestUri();
        }
    }
}
