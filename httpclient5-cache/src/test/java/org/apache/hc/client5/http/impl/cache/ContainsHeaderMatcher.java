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

import java.util.Iterator;

import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.MessageHeaders;
import org.apache.hc.core5.util.LangUtils;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;

public class ContainsHeaderMatcher extends BaseMatcher<HttpCacheEntry> {

    private final String headerName;
    private final Object headerValue;

    public ContainsHeaderMatcher(final String headerName, final Object headerValue) {
        this.headerName = headerName;
        this.headerValue = headerValue;
    }

    @Override
    public boolean matches(final Object item) {
        if (item instanceof MessageHeaders) {
            final MessageHeaders messageHeaders = (MessageHeaders) item;
            for (final Iterator<Header> it = messageHeaders.headerIterator(); it.hasNext(); ) {
                final Header header = it.next();
                if (headerName.equalsIgnoreCase(header.getName()) && LangUtils.equals(headerValue, header.getValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void describeTo(final Description description) {
        description.appendText("contains header ").appendValue(headerValue).appendText(": ").appendValue(headerValue);
    }

    @Factory
    public static Matcher<HttpCacheEntry> contains(final String headerName, final Object headerValue) {
        return new ContainsHeaderMatcher(headerName, headerValue);
    }

}
