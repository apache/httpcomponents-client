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

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.Resource;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.core5.http.Header;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

public class HttpCacheEntryMatcher extends BaseMatcher<HttpCacheEntry> {

    private final HttpCacheEntry expectedValue;

    public HttpCacheEntryMatcher(final HttpCacheEntry expectedValue) {
        this.expectedValue = expectedValue;
    }

    @Override
    public boolean matches(final Object item) {
        if (item instanceof HttpCacheEntry) {
            try {
                final HttpCacheEntry otherValue = (HttpCacheEntry) item;

                final int expectedStatus = expectedValue.getStatus();
                final int otherStatus = otherValue.getStatus();
                if (expectedStatus != otherStatus) {
                    return false;
                }
                final Instant expectedRequestInstant = expectedValue.getRequestInstant();
                final Instant otherRequestInstant = otherValue.getRequestInstant();
                if (!Objects.equals(expectedRequestInstant, otherRequestInstant)) {
                    return false;
                }
                final Instant expectedResponseInstant = expectedValue.getResponseInstant();
                final Instant otherResponseInstant = otherValue.getResponseInstant();
                if (!Objects.equals(expectedResponseInstant, otherResponseInstant)) {
                    return false;
                }

                final Header[] expectedHeaders = expectedValue.getHeaders();
                final Header[] otherHeaders = otherValue.getHeaders();
                if (expectedHeaders.length != otherHeaders.length) {
                    return false;
                }
                for (int i = 0; i < expectedHeaders.length; i++) {
                    final Header h1 = expectedHeaders[i];
                    final Header h2 = otherHeaders[i];
                    if (!h1.getName().equals(h2.getName()) || !Objects.equals(h1.getValue(), h2.getValue())) {
                        return false;
                    }
                }
                final Resource expectedResource = expectedValue.getResource();
                final byte[] expectedContent = expectedResource != null ? expectedResource.get() : null;
                final Resource otherResource = otherValue.getResource();
                final byte[] otherContent = otherResource != null ? otherResource.get() : null;
                return Arrays.equals(expectedContent, otherContent);
            } catch (final ResourceIOException ex) {
                throw new RuntimeException(ex);
            }
        }
        return false;
    }

    @Override
    public void describeTo(final Description description) {
        description.appendValue(expectedValue);
    }

    public static Matcher<HttpCacheEntry> equivalent(final HttpCacheEntry target) {
        return new HttpCacheEntryMatcher(target);
    }

}
