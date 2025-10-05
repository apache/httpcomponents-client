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

package org.apache.hc.client5.http.impl.auth;

import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.core5.annotation.Experimental;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * Factory for creating instances of {@link ScramScheme}.
 *
 * @since 5.6
 */
@Experimental
public final class ScramSchemeFactory implements AuthSchemeFactory {

    /**
     * Singleton instance.
     */
    public static final ScramSchemeFactory INSTANCE = new ScramSchemeFactory();

    private ScramSchemeFactory() {
    }

    /**
     * Creates a new {@link ScramScheme}.
     *
     * @param context the HTTP context (unused)
     * @return a new {@link ScramScheme} instance
     * @since 5.6
     */
    @Override
    public AuthScheme create(final HttpContext context) {
        return new ScramScheme();
    }
}
