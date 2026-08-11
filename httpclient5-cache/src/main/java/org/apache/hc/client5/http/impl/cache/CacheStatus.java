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
package org.apache.hc.client5.http.impl.cache;

import org.apache.hc.client5.http.cache.CacheResponseStatus;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.HttpStatus;

/**
 * Mutable, per-exchange record of how the cache handled a request. The executor populates it at the
 * point where it decides to satisfy the request from a stored response or to forward it to the next
 * hop, and {@link CacheStatusHeaderGenerator} serialises it into the RFC 9211 {@code Cache-Status}
 * response header. It is kept separate from {@link org.apache.hc.client5.http.cache.CacheResponseStatus}
 * because the latter is too coarse to describe the disposition RFC 9211 requires.
 *
 * @since 5.7
 */
@Internal
public final class CacheStatus {

    /**
     * Reason a request was forwarded to the next hop, as reported by the RFC 9211 {@code fwd}
     * parameter. The token values are defined once here rather than inline at the call sites.
     */
    enum ForwardReason {

        BYPASS("bypass"),
        METHOD("method"),
        URI_MISS("uri-miss"),
        VARY_MISS("vary-miss"),
        MISS("miss"),
        REQUEST("request"),
        STALE("stale"),
        PARTIAL("partial");

        final String token;

        ForwardReason(final String token) {
            this.token = token;
        }

    }

    private boolean hit;
    private ForwardReason forwardReason;
    private Integer forwardStatus;
    private boolean suppressed;
    private boolean failed;
    private boolean moduleResponse;

    /**
     * Records that the request was satisfied from a stored response without contacting the next hop.
     */
    void hit() {
        this.hit = true;
        this.forwardReason = null;
    }

    /**
     * Records that the request was forwarded to the next hop for the given reason.
     */
    void forward(final ForwardReason reason) {
        this.hit = false;
        this.forwardReason = reason;
    }

    /**
     * Records the status received from the next hop when it differs from the status delivered to the
     * caller, for example a {@code 304} that produced a stored {@code 200}.
     */
    void forwardStatus(final int status) {
        this.forwardStatus = status;
    }

    /**
     * Marks the response as locally generated and not based on a stored response, so that no
     * {@code Cache-Status} header is emitted (RFC 9211 section 2).
     */
    void suppress() {
        this.suppressed = true;
    }

    /**
     * Records that the cache could not serve or store the response because of an internal error and
     * fell back to the next hop. This disposition is not part of the RFC 9211 {@code Cache-Status}
     * vocabulary, so it is never serialised; it only feeds {@link #toResponseStatus()}.
     */
    void fail() {
        this.failed = true;
    }

    /**
     * Records that the cache served a stored response on a stale path that the module owns, such as
     * stale-while-revalidate or stale-if-error. Unlike {@link #suppress()} this does not suppress the
     * {@code Cache-Status} header; the underlying hit or forward disposition is still serialised. It
     * only maps the exchange to {@link CacheResponseStatus#CACHE_MODULE_RESPONSE}.
     */
    void moduleResponse() {
        this.moduleResponse = true;
    }

    boolean isHit() {
        return hit;
    }

    ForwardReason getForwardReason() {
        return forwardReason;
    }

    Integer getForwardStatus() {
        return forwardStatus;
    }

    boolean isSuppressed() {
        return suppressed;
    }

    boolean isFailed() {
        return failed;
    }

    boolean isModuleResponse() {
        return moduleResponse;
    }

    boolean isRecorded() {
        return hit || forwardReason != null;
    }

    /**
     * Derives the coarse {@link CacheResponseStatus} disposition from this record, which is the
     * single source of truth for how the exchange was handled. A locally generated response or a
     * stale response served by the module (stale-while-revalidate, stale-if-error) maps to
     * {@link CacheResponseStatus#CACHE_MODULE_RESPONSE}, an internal failure to
     * {@link CacheResponseStatus#FAILURE}, a response served from a stored entry to
     * {@link CacheResponseStatus#CACHE_HIT}, a successful revalidation of a stored entry (a
     * non-error status received from the next hop, i.e. a {@code 304} or a fresh {@code 200}) to
     * {@link CacheResponseStatus#VALIDATED}, and any other forwarded response to
     * {@link CacheResponseStatus#CACHE_MISS}. Returns {@code null} when nothing has been recorded.
     */
    public CacheResponseStatus toResponseStatus() {
        if (suppressed || moduleResponse) {
            return CacheResponseStatus.CACHE_MODULE_RESPONSE;
        }
        if (failed) {
            return CacheResponseStatus.FAILURE;
        }
        if (hit) {
            return CacheResponseStatus.CACHE_HIT;
        }
        if (forwardReason != null) {
            return forwardStatus != null && forwardStatus < HttpStatus.SC_BAD_REQUEST
                    ? CacheResponseStatus.VALIDATED
                    : CacheResponseStatus.CACHE_MISS;
        }
        return null;
    }

}
