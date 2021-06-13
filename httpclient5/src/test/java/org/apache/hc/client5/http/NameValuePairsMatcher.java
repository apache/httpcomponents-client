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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.util.LangUtils;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;

public class NameValuePairsMatcher extends BaseMatcher<Collection<? extends NameValuePair>> {

    private final List<? extends NameValuePair> expectedNameValuePairList;

    public NameValuePairsMatcher(final List<? extends NameValuePair> nameValuePairList) {
        this.expectedNameValuePairList = nameValuePairList;
    }

    @Override
    public boolean matches(final Object item) {
        if (item instanceof Collection<?>) {
            final Collection<?> collection = (Collection<?>) item;
            int i = 0;
            for (final Object obj : collection) {
                if (obj instanceof NameValuePair) {
                    final NameValuePair nvp1 = (NameValuePair) obj;
                    final NameValuePair nvp2 = expectedNameValuePairList.get(i);
                    if (!nvp1.getName().equalsIgnoreCase(nvp2.getName())
                            || !LangUtils.equals(nvp1.getValue(), nvp2.getValue())) {
                        return false;
                    }
                } else {
                    return false;
                }
                i++;
            }
            return true;
        }
        return false;
    }

    @Override
    public void describeTo(final Description description) {
        description.appendText("same name/value pairs as ").appendValueList("[", ", ", "]", expectedNameValuePairList);
    }

    @Factory
    public static Matcher<Collection<? extends NameValuePair>> same(final Collection<? extends NameValuePair> nameValuePairs) {
        return new NameValuePairsMatcher(new ArrayList<>(nameValuePairs));
    }

    @Factory
    public static Matcher<Collection<? extends NameValuePair>> same(final NameValuePair... nameValuePairs) {
        return new NameValuePairsMatcher(Arrays.asList(nameValuePairs));
    }

}
