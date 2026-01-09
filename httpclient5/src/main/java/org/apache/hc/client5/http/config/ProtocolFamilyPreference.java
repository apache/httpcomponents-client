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

/**
 * Protocol family preference for outbound connections.
 *
 * <p>Used by connection initiation code to filter or order destination
 * addresses and, when enabled, to interleave families during staggered attempts.
 *
 * @since 5.7
 */
public enum ProtocolFamilyPreference {
    /** Keep families as returned (or RFC 6724 ordered). */
    DEFAULT,
    /**
     * Prefer IPv4 addresses but allow IPv6 as a fallback.
     */
    PREFER_IPV4,

    /**
     * Prefer IPv6 addresses but allow IPv4 as a fallback.
     */
    PREFER_IPV6,

    /**
     * Use only IPv4 addresses.
     */
    IPV4_ONLY,

    /**
     * Use only IPv6 addresses.
     */
    IPV6_ONLY,

    /**
     * Interleave address families (v6, then v4, then v6, …) when multiple
     * addresses are available. When staggered connects are enabled, the first
     * address of the other family is delayed by a small offset.
     */
    INTERLEAVE
}

