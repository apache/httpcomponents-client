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

    /**
     * No family bias. Preserve RFC 6724 order.
     */
    DEFAULT,

    /**
     * Prefer IPv4 addresses (stable: preserves RFC order within each family).
     */
    PREFER_IPV4,

    /**
     * Prefer IPv6 addresses (stable: preserves RFC order within each family).
     */
    PREFER_IPV6,

    /**
     * Filter out all non-IPv4 addresses.
     */
    IPV4_ONLY,

    /**
     * Filter out all non-IPv6 addresses.
     */
    IPV6_ONLY,

    /**
     * Interleave address families (v6, then v4, then v6, …) when multiple
     * addresses are available, preserving the relative order within each family
     * as produced by RFC 6724 sorting.
     */
    INTERLEAVE

}

