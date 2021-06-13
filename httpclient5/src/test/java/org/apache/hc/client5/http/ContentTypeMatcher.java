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

import org.apache.hc.core5.http.ContentType;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;

public class ContentTypeMatcher extends BaseMatcher<ContentType> {

    private final ContentType expectedContentType;

    public ContentTypeMatcher(final ContentType contentType) {
        this.expectedContentType = contentType;
    }

    @Override
    public boolean matches(final Object item) {
        if (item instanceof ContentType) {
            final ContentType contentType = (ContentType) item;
            return contentType.isSameMimeType(expectedContentType);
        }
        return false;
    }

    @Override
    public void describeTo(final Description description) {
        description.appendText("same MIME type as ").appendValue(expectedContentType);
    }

    @Factory
    public static Matcher<ContentType> sameMimeType(final ContentType contentType) {
        return new ContentTypeMatcher(contentType);
    }

}
