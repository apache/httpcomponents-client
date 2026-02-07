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

import java.util.Collection;
import java.util.Iterator;

import org.apache.hc.core5.http.NameValuePair;
import org.junit.jupiter.api.Assertions;

public class NameValuePairsMatcher {

    public static void assertSame(final Collection<? extends NameValuePair> actual,
            final Collection<? extends NameValuePair> expected) {
        Assertions.assertNotNull(actual, "Expected name/value pairs");
        Assertions.assertNotNull(expected, "Expected name/value pairs");
        Assertions.assertEquals(expected.size(), actual.size(),
                "Expected " + expected.size() + " name/value pairs but got " + actual.size());
        final Iterator<? extends NameValuePair> actualIterator = actual.iterator();
        final Iterator<? extends NameValuePair> expectedIterator = expected.iterator();
        while (actualIterator.hasNext() && expectedIterator.hasNext()) {
            final NameValuePair actualPair = actualIterator.next();
            final NameValuePair expectedPair = expectedIterator.next();
            Assertions.assertTrue(actualPair.getName().equalsIgnoreCase(expectedPair.getName()),
                    "Expected name '" + expectedPair.getName() + "' but was '" + actualPair.getName() + "'");
            Assertions.assertEquals(expectedPair.getValue(), actualPair.getValue(),
                    "Expected value for '" + expectedPair.getName() + "'");
        }
    }

    public static void assertSame(final Collection<? extends NameValuePair> actual,
            final NameValuePair... expected) {
        Assertions.assertNotNull(actual, "Expected name/value pairs");
        Assertions.assertEquals(expected.length, actual.size(),
                "Expected " + expected.length + " name/value pairs but got " + actual.size());
        int i = 0;
        for (final NameValuePair actualPair : actual) {
            final NameValuePair expectedPair = expected[i];
            Assertions.assertTrue(actualPair.getName().equalsIgnoreCase(expectedPair.getName()),
                    "Expected name '" + expectedPair.getName() + "' but was '" + actualPair.getName() + "'");
            Assertions.assertEquals(expectedPair.getValue(), actualPair.getValue(),
                    "Expected value for '" + expectedPair.getName() + "'");
            i++;
        }
    }

}
