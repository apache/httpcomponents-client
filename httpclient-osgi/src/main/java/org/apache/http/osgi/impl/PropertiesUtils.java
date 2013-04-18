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

    private static void register(PropertyConverter<?> converter, Class<?>...targetTypes) {
        for (Class<?> targetType : targetTypes) {
            CONVERTERS_REGISTRY.put(targetType, converter);
        }
    }

    public static <T> T to(Object propValue, Class<T> targetType, T defaultValue) {
        if (propValue == null) {
            return defaultValue;
        }

        if (!targetType.isArray()) {
            propValue = toObject(propValue);
        }

        if (targetType.isInstance(propValue)) {
            return targetType.cast(propValue);
        }

        if (CONVERTERS_REGISTRY.containsKey(targetType)) {
            @SuppressWarnings("unchecked") // type driven by targetType
            PropertyConverter<T> converter = (PropertyConverter<T>) CONVERTERS_REGISTRY.get(targetType);
            try {
                return converter.to(propValue);
            } catch (Throwable t) {
                // don't care, fall through to default value
            }
        }

        return defaultValue;
    }

    /**
     * Returns the parameter as a single value. If the
     * parameter is neither an array nor a <code>java.util.Collection</code> the
     * parameter is returned unmodified. If the parameter is a non-empty array,
     * the first array element is returned. If the property is a non-empty
     * <code>java.util.Collection</code>, the first collection element is returned.
     *
     * @param propValue the parameter to convert.
     */
    private static Object toObject(Object propValue) {
       if (propValue.getClass().isArray()) {
           Object[] prop = (Object[]) propValue;
           return prop.length > 0 ? prop[0] : null;
       }

       if (propValue instanceof Collection<?>) {
           Collection<?> prop = (Collection<?>) propValue;
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

        public Boolean to(Object propValue) {
            return Boolean.valueOf(String.valueOf(propValue));
        }

    }

    private static class StringPropertyConverter implements PropertyConverter<String> {

        public String to(Object propValue) {
            return String.valueOf(propValue);
        }

    }

    private static class StringArrayPropertyConverter implements PropertyConverter<String[]> {

        public String[] to(Object propValue) {
            if (propValue instanceof String) {
                // single string
                return new String[] { (String) propValue };
            }

            if (propValue.getClass().isArray()) {
                // other array
                Object[] valueArray = (Object[]) propValue;
                List<String> values = new ArrayList<String>(valueArray.length);
                for (Object value : valueArray) {
                    if (value != null) {
                        values.add(value.toString());
                    }
                }
                return values.toArray(new String[values.size()]);

            }

            if (propValue instanceof Collection<?>) {
                // collection
                Collection<?> valueCollection = (Collection<?>) propValue;
                List<String> valueList = new ArrayList<String>(valueCollection.size());
                for (Object value : valueCollection) {
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

        public Integer to(Object propValue) {
            return Integer.valueOf(String.valueOf(propValue));
        }

    }

    private static class LongPropertyConverter implements PropertyConverter<Long> {

        public Long to(Object propValue) {
            return Long.valueOf(String.valueOf(propValue));
        }

    }

    private static class DoublePropertyConverter implements PropertyConverter<Double> {

        public Double to(Object propValue) {
            return Double.valueOf(String.valueOf(propValue));
        }

    }

}
