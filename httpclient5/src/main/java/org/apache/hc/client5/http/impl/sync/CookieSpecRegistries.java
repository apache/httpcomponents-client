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

package org.apache.hc.client5.http.impl.sync;

import org.apache.hc.client5.http.config.CookieSpecs;
import org.apache.hc.client5.http.cookie.CookieSpecProvider;
import org.apache.hc.client5.http.impl.cookie.IgnoreSpecProvider;
import org.apache.hc.client5.http.impl.cookie.RFC6265CookieSpecProvider;
import org.apache.hc.client5.http.psl.PublicSuffixMatcher;
import org.apache.hc.client5.http.psl.PublicSuffixMatcherLoader;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.config.RegistryBuilder;

/**
 * @since 4.5
 */
public final class CookieSpecRegistries {

    /**
     * Creates a builder containing the default registry entries, using the provided public suffix matcher.
     */
    public static RegistryBuilder<CookieSpecProvider> createDefaultBuilder(final PublicSuffixMatcher publicSuffixMatcher) {
        final CookieSpecProvider laxStandardProvider = new RFC6265CookieSpecProvider(
                RFC6265CookieSpecProvider.CompatibilityLevel.RELAXED, publicSuffixMatcher);
        final CookieSpecProvider strictStandardProvider = new RFC6265CookieSpecProvider(
                RFC6265CookieSpecProvider.CompatibilityLevel.STRICT, publicSuffixMatcher);
        return RegistryBuilder.<CookieSpecProvider>create()
                .register(CookieSpecs.DEFAULT, laxStandardProvider)
                .register(CookieSpecs.STANDARD, laxStandardProvider)
                .register(CookieSpecs.STANDARD_STRICT, strictStandardProvider)
                .register(CookieSpecs.IGNORE_COOKIES, new IgnoreSpecProvider());
    }

    /**
     * Creates a builder containing the default registry entries with the default public suffix matcher.
     */
    public static RegistryBuilder<CookieSpecProvider> createDefaultBuilder() {
        return createDefaultBuilder(PublicSuffixMatcherLoader.getDefault());
    }

    /**
     * Creates the default registry, using the default public suffix matcher.
     */
    public static Lookup<CookieSpecProvider> createDefault() {
        return createDefault(PublicSuffixMatcherLoader.getDefault());
    }

    /**
     * Creates the default registry with the provided public suffix matcher
     */
    public static Lookup<CookieSpecProvider> createDefault(final PublicSuffixMatcher publicSuffixMatcher) {
        return createDefaultBuilder(publicSuffixMatcher).build();
    }

    private CookieSpecRegistries() {}

}
