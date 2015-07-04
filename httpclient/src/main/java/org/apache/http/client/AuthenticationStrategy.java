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

package org.apache.http.client;

import java.util.Map;
import java.util.Queue;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthChallenge;
import org.apache.http.auth.AuthOption;
import org.apache.http.auth.ChallengeType;
import org.apache.http.auth.MalformedChallengeException;
import org.apache.http.protocol.HttpContext;

/**
 * Strategy to select auth schemes in order of preference based on auth challenges
 * presented by the opposite endpoint (target server or a proxy).
 * <p>
 * Implementations of this interface must be thread-safe. Access to shared data must be
 * synchronized as methods of this interface may be executed from multiple threads.
 *
 * @since 4.2
 */
public interface AuthenticationStrategy {

    /**
     * Selects one authentication challenge out of all available and
     * creates and generates {@link AuthOption} instance capable of
     * processing that challenge.
     *
     * @param challengeType challenge type.
     * @param host authentication host.
     * @param challenges collection of challenges.
     * @param context HTTP context.
     * @return authentication auth schemes that can be used for authentication. Can be empty.
     * @throws MalformedChallengeException if one of the authentication
     *  challenges is not valid or malformed.
     *
     *  @since 5.0
     */
    Queue<AuthOption> select(
            ChallengeType challengeType,
            HttpHost host,
            Map<String, AuthChallenge> challenges,
            HttpContext context) throws MalformedChallengeException;

}
