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
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

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

                if (!setEqual(expectedValue.getVariants(), otherValue.getVariants())) {
                    return false;
                }
                if (!Objects.equals(expectedValue.getRequestMethod(), otherValue.getRequestMethod())) {
                    return false;
                }
                if (!Objects.equals(expectedValue.getRequestURI(), otherValue.getRequestURI())) {
                    return false;
                }
                if (!headersEqual(expectedValue.requestHeaderIterator(), otherValue.requestHeaderIterator())) {
                    return false;
                }
                if (!instantEqual(expectedValue.getRequestInstant(), otherValue.getRequestInstant())) {
                    return false;
                }
                if (expectedValue.getStatus() != otherValue.getStatus()) {
                    return false;
                }
                if (!headersEqual(expectedValue.headerIterator(), otherValue.headerIterator())) {
                    return false;
                }
                if (!instantEqual(expectedValue.getResponseInstant(), otherValue.getResponseInstant())) {
                    return false;
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

    private static boolean instantEqual(final Instant expected, final Instant other) {
        final Instant expectedMs = expected != null ? expected.truncatedTo(ChronoUnit.MILLIS) : null;
        final Instant otherMs = other != null ? other.truncatedTo(ChronoUnit.MILLIS) : null;
        return Objects.equals(expectedMs, otherMs);
    }

    private static boolean headersEqual(final Iterator<Header> expected, final Iterator<Header> other) {
        while (expected.hasNext()) {
            final Header h1 = expected.next();
            if (!other.hasNext()) {
                return false;
            }
            final Header h2 = other.next();
            if (!h1.getName().equals(h2.getName()) || !Objects.equals(h1.getValue(), h2.getValue())) {
                return false;
            }
        }
        if (other.hasNext()) {
            return false;
        }
        return true;
    }

    private static boolean setEqual(final Set<?> expected, final Set<?> actual) {
        if (expected.size() != actual.size()) {
            return false;
        }
        return actual.containsAll(expected);
    }

    @Override
    public void describeTo(final Description description) {
        description.appendValue(expectedValue);
    }

    public static Matcher<HttpCacheEntry> equivalent(final HttpCacheEntry target) {
        return new HttpCacheEntryMatcher(target);
    }

}
