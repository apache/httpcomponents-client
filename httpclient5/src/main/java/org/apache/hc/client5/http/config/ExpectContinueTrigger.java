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
 * Enumeration of strategies that govern automatic inclusion of the
 * {@code Expect: 100-continue} request header when
 * {@link org.apache.hc.client5.http.config.RequestConfig#isExpectContinueEnabled()
 * expect-continue support} is enabled.
 *
 * @since 5.6
 */
public enum ExpectContinueTrigger {

    /**
     * Always add {@code Expect: 100-continue} to every entity-enclosing request.
     */
    ALWAYS,

    /**
     * Add {@code Expect: 100-continue} <em>only</em> when the underlying
     * connection has already processed at least one request (that is, when the
     * socket has been taken from the connection pool and may be stale).
     */
    IF_REUSED
}