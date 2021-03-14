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
package org.apache.hc.client5.http;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.util.LangUtils;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;

public class HeadersMatcher extends BaseMatcher<Header[]> {

    private final Header[] expectedHeaders;

    public HeadersMatcher(final Header... headers) {
        this.expectedHeaders = headers;
    }

    @Override
    public boolean matches(final Object item) {
        if (item instanceof Header[]) {
            final Header[] headers = (Header[]) item;
            if (headers.length == expectedHeaders.length) {
                for (int i = 0; i < headers.length; i++) {
                    final Header h1 = headers[i];
                    final Header h2 = expectedHeaders[i];
                    if (!h1.getName().equalsIgnoreCase(h2.getName())
                            || !LangUtils.equals(h1.getValue(), h2.getValue())) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public void describeTo(final Description description) {
        description.appendText("same headers as ").appendValueList("[", ", ", "]", expectedHeaders);
    }

    @Factory
    public static Matcher<Header[]> same(final Header... headers) {
        return new HeadersMatcher(headers);
    }

}
