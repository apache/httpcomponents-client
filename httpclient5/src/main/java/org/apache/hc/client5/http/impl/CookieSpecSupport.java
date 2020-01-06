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

import org.apache.hc.client5.http.cookie.StandardCookieSpec;
import org.apache.hc.client5.http.cookie.CookieSpecFactory;
import org.apache.hc.client5.http.impl.cookie.IgnoreCookieSpecFactory;
import org.apache.hc.client5.http.impl.cookie.RFC6265CookieSpecFactory;
import org.apache.hc.client5.http.psl.PublicSuffixMatcher;
import org.apache.hc.client5.http.psl.PublicSuffixMatcherLoader;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.config.RegistryBuilder;

/**
 * Cookie support methods.
 *
 * @since 5.0
 */
@Internal
public final class CookieSpecSupport {

    /**
     * Creates a builder containing the default registry entries, using the provided public suffix matcher.
     */
    public static RegistryBuilder<CookieSpecFactory> createDefaultBuilder(final PublicSuffixMatcher publicSuffixMatcher) {
        return RegistryBuilder.<CookieSpecFactory>create()
                .register(StandardCookieSpec.RELAXED, new RFC6265CookieSpecFactory(
                        RFC6265CookieSpecFactory.CompatibilityLevel.RELAXED, publicSuffixMatcher))
                .register(StandardCookieSpec.STRICT, new RFC6265CookieSpecFactory(
                        RFC6265CookieSpecFactory.CompatibilityLevel.STRICT, publicSuffixMatcher))
                .register(StandardCookieSpec.IGNORE, new IgnoreCookieSpecFactory());
    }

    /**
     * Creates a builder containing the default registry entries with the default public suffix matcher.
     */
    public static RegistryBuilder<CookieSpecFactory> createDefaultBuilder() {
        return createDefaultBuilder(PublicSuffixMatcherLoader.getDefault());
    }

    /**
     * Creates the default registry, using the default public suffix matcher.
     */
    public static Lookup<CookieSpecFactory> createDefault() {
        return createDefault(PublicSuffixMatcherLoader.getDefault());
    }

    /**
     * Creates the default registry with the provided public suffix matcher
     */
    public static Lookup<CookieSpecFactory> createDefault(final PublicSuffixMatcher publicSuffixMatcher) {
        return createDefaultBuilder(publicSuffixMatcher).build();
    }

    private CookieSpecSupport() {}

}
