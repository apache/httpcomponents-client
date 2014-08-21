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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @since 4.3
 */
final class PropertiesUtils {

    private static final Map<Class<?>, PropertyConverter<?>> CONVERTERS_REGISTRY =
                    new HashMap<Class<?>, PropertiesUtils.PropertyConverter<?>>();

    static {
        register(new BooleanPropertyConverter(), boolean.class, Boolean.class);
        register(new StringPropertyConverter(), String.class);
        register(new StringArrayPropertyConverter(), String[].class);
        register(new IntegerPropertyConverter(), int.class, Integer.class);
        register(new LongPropertyConverter(), long.class, Long.class);
        register(new DoublePropertyConverter(), double.class, Double.class);
    }

    private static void register(final PropertyConverter<?> converter, final Class<?>...targetTypes) {
        for (final Class<?> targetType : targetTypes) {
            CONVERTERS_REGISTRY.put(targetType, converter);
        }
    }

    public static <T> T to(final Object propValue, final Class<T> targetType, final T defaultValue) {
        Object v = propValue;
        if (v == null) {
            return defaultValue;
        }

        if (!targetType.isArray()) {
            v = toObject(v);
        }

        if (targetType.isInstance(v)) {
            return targetType.cast(v);
        }

        if (CONVERTERS_REGISTRY.containsKey(targetType)) {
            @SuppressWarnings("unchecked") final // type driven by targetType
            PropertyConverter<T> converter = (PropertyConverter<T>) CONVERTERS_REGISTRY.get(targetType);
            try {
                return converter.to(v);
            } catch (final Exception ignore) {
            }
        }

        return defaultValue;
    }

    /**
     * Returns the parameter as a single value. If the
     * parameter is neither an array nor a {@code java.util.Collection} the
     * parameter is returned unmodified. If the parameter is a non-empty array,
     * the first array element is returned. If the property is a non-empty
     * {@code java.util.Collection}, the first collection element is returned.
     *
     * @param propValue the parameter to convert.
     */
    private static Object toObject(final Object propValue) {
       if (propValue.getClass().isArray()) {
           final Object[] prop = (Object[]) propValue;
           return prop.length > 0 ? prop[0] : null;
       }

       if (propValue instanceof Collection<?>) {
           final Collection<?> prop = (Collection<?>) propValue;
           return prop.isEmpty() ? null : prop.iterator().next();
       }

       return propValue;
    }

    /**
     * Hidden constructor, this class must not be instantiated.
     */
    private PropertiesUtils() {
        // do nothing
    }

    private static interface PropertyConverter<T> {

        T to(Object propValue);

    }

    private static class BooleanPropertyConverter implements PropertyConverter<Boolean> {

        @Override
        public Boolean to(final Object propValue) {
            return Boolean.valueOf(String.valueOf(propValue));
        }

    }

    private static class StringPropertyConverter implements PropertyConverter<String> {

        @Override
        public String to(final Object propValue) {
            return String.valueOf(propValue);
        }

    }

    private static class StringArrayPropertyConverter implements PropertyConverter<String[]> {

        @Override
        public String[] to(final Object propValue) {
            if (propValue instanceof String) {
                // single string
                return new String[] { (String) propValue };
            }

            if (propValue.getClass().isArray()) {
                // other array
                final Object[] valueArray = (Object[]) propValue;
                final List<String> values = new ArrayList<String>(valueArray.length);
                for (final Object value : valueArray) {
                    if (value != null) {
                        values.add(value.toString());
                    }
                }
                return values.toArray(new String[values.size()]);

            }

            if (propValue instanceof Collection<?>) {
                // collection
                final Collection<?> valueCollection = (Collection<?>) propValue;
                final List<String> valueList = new ArrayList<String>(valueCollection.size());
                for (final Object value : valueCollection) {
                    if (value != null) {
                        valueList.add(value.toString());
                    }
                }
                return valueList.toArray(new String[valueList.size()]);
            }

            // don't care, fall through to default value
            throw new IllegalArgumentException();
        }

    }

    private static class IntegerPropertyConverter implements PropertyConverter<Integer> {

        @Override
        public Integer to(final Object propValue) {
            return Integer.valueOf(String.valueOf(propValue));
        }

    }

    private static class LongPropertyConverter implements PropertyConverter<Long> {

        @Override
        public Long to(final Object propValue) {
            return Long.valueOf(String.valueOf(propValue));
        }

    }

    private static class DoublePropertyConverter implements PropertyConverter<Double> {

        @Override
        public Double to(final Object propValue) {
            return Double.valueOf(String.valueOf(propValue));
        }

    }

}
