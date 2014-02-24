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
package org.apache.http.osgi.impl;

import static org.apache.http.osgi.impl.PropertiesUtils.to;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * @since 4.3
 */
@SuppressWarnings("boxing") // test code
public final class TestPropertiesUtils {

    @Test
    public void toBoolean() {
        assertConverted(true, null, boolean.class, true);
        assertConverted(true, null, Boolean.class, true);
        assertConverted(false, "false", boolean.class, null);
        assertConverted(false, "false", Boolean.class, null);
        // whatever value is interpreted as `false` by Boolean.valueOf
        assertConverted(false, "not a boolean", boolean.class, true);
        assertConverted(false, "not a boolean", Boolean.class, true);
    }

    @Test
    public void toSingleString() {
        assertConverted("fallback to default value", null, String.class, "fallback to default value");
        // use an object which represents the string
        assertConverted("use the passed value", new StringBuilder("use the passed value"), String.class, null);
        // use the "identity" converter
        assertConverted("use the passed value", "use the passed value", String.class, null);
        // convert another object
        assertConverted("456789", 456789, String.class, null);
    }

    @Test
    public void toStringArray() {
        assertConvertedArray(new String[]{"fallback to default value"},
                             null,
                             String[].class,
                             new String[]{"fallback to default value"});
        // a string is converted to an array with 1 element
        assertConvertedArray(new String[]{"a single string"},
                             "a single string",
                             String[].class,
                             null);
        // use an object which represents the string
        assertConvertedArray(new String[]{"null objects", "will be ignored"},
                             new Object[]{new StringBuilder("null objects"), null, new StringBuilder("will be ignored")},
                             String[].class,
                             null);
        // use the "identity" converter
        assertConvertedArray(new String[]{"null objects", "will be ignored"},
                             new Object[]{"null objects", null, "will be ignored"},
                             String[].class,
                             null);
        assertConvertedArray(new String[]{"fallback to default value"},
                             456789,
                             String[].class,
                             new String[]{"fallback to default value"});
    }

    @Test
    public void toInt() {
        assertConverted(123, null, int.class, 123);
        assertConverted(123, null, Integer.class, 123);
        assertConverted(456, "456", int.class, null);
        assertConverted(456, "456", Integer.class, null);
        assertConverted(789, "not an integer", int.class, 789);
        assertConverted(789, "not an integer", Integer.class, 789);
    }

    @Test
    public void toLong() {
        assertConverted(123l, null, long.class, 123l);
        assertConverted(123l, null, Long.class, 123l);
        assertConverted(456l, "456", long.class, null);
        assertConverted(456l, "456", Long.class, null);
        assertConverted(789l, "not a long", long.class, 789l);
        assertConverted(789l, "not a long", Long.class, 789l);
    }

    @Test
    public void toDouble() {
        assertConverted(123d, null, double.class, 123d);
        assertConverted(123d, null, Double.class, 123d);
        assertConverted(456d, "456", double.class, null);
        assertConverted(456d, "456", Double.class, null);
        assertConverted(789d, "not a double", double.class, 789d);
        assertConverted(789d, "not a double", Double.class, 789d);
    }

    private static <T> void assertConverted(
            final T expected, final Object propValue, final Class<T> targetType, final T defaultValue) {
        final T actual = to(propValue, targetType, defaultValue);
        assertEquals(expected, actual);
    }

    private static <T> void assertConvertedArray(
            final T[] expected, final Object propValue, final Class<T[]> targetType, final T[] defaultValue) {
        final T[] actual = to(propValue, targetType, defaultValue);
        assertArrayEquals(expected, actual);
    }

}
