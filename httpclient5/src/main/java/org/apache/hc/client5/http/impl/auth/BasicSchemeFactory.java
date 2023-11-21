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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * {@link AuthSchemeFactory} implementation that creates and initializes
 * {@link BasicScheme} instances.
 *
 * @since 4.0
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public class BasicSchemeFactory implements AuthSchemeFactory {

    /**
     * Singleton instance with a null Charset.
     */
    public static final BasicSchemeFactory INSTANCE = new BasicSchemeFactory();

    private final Charset charset;

    /**
     * @deprecated This constructor is deprecated to enforce the use of {@link StandardCharsets#UTF_8} encoding
     * in compliance with RFC 7617 for HTTP Basic Authentication. Use the default constructor {@link #BasicSchemeFactory()} instead.
     *
     * @param charset the {@link Charset} set to be used for encoding credentials. This parameter is ignored as UTF-8 is always used.
     */
    @Deprecated
    public BasicSchemeFactory(final Charset charset) {
        super();
        this.charset = StandardCharsets.UTF_8; // Always use UTF-8
    }

    /**
     * Constructs a new {@link BasicSchemeFactory} with {@link StandardCharsets#UTF_8} as the charset.
     * This default setting aligns with standard practices for encoding credentials in Basic Authentication.
     */
    public BasicSchemeFactory() {
        this.charset = StandardCharsets.UTF_8;
    }

    @Override
    public AuthScheme create(final HttpContext context) {
        return new BasicScheme();
    }

}
