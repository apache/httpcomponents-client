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
package org.apache.hc.client5.http.impl;

import java.net.URI;

import org.apache.hc.core5.annotation.Internal;

/**
 * Validates and evaluates the URL pattern carried by a
 * {@code Use-As-Dictionary} response header.
 *
 * @since 5.7
 */
@Internal
public interface CompressionDictionaryUrlPatternMatcher {

    /**
     * Validates a pattern using the dictionary request URL as its base URL.
     *
     * @param pattern       the URL pattern.
     * @param dictionaryUri the dictionary request URL.
     * @return whether the pattern is valid and confined to the dictionary origin.
     */
    boolean isValid(String pattern, URI dictionaryUri);

    /**
     * Evaluates a pattern using the outbound request URL as its base URL.
     *
     * @param pattern       the URL pattern.
     * @param dictionaryUri the dictionary request URL.
     * @param requestUri    the outbound request URL.
     * @return whether the outbound URL matches.
     */
    boolean matches(String pattern, URI dictionaryUri, URI requestUri);
}
