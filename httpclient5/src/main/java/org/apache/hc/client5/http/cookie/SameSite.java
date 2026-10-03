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

package org.apache.hc.client5.http.cookie;

import java.util.Locale;

/**
 * Enumeration of the values of the {@code SameSite} cookie attribute.
 *
 * @since 5.7
 */
public enum SameSite {

    /**
     * The cookie is only sent for same-site requests.
     */
    STRICT("Strict"),

    /**
     * The cookie is sent for same-site requests and top-level cross-site navigations.
     */
    LAX("Lax"),

    /**
     * The cookie is sent for all requests. A cookie with this value must also be secure.
     */
    NONE("None");

    private final String attributeValue;

    SameSite(final String attributeValue) {
        this.attributeValue = attributeValue;
    }

    /**
     * Returns the canonical attribute value as it appears in a {@code Set-Cookie} header.
     */
    public String getAttributeValue() {
        return attributeValue;
    }

    /**
     * Resolves a {@code SameSite} value from a raw attribute value using a case-insensitive match,
     * returning {@code null} when the value is absent or unrecognized.
     */
    public static SameSite fromString(final String value) {
        if (value == null) {
            return null;
        }
        switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "strict":
                return STRICT;
            case "lax":
                return LAX;
            case "none":
                return NONE;
            default:
                return null;
        }
    }

}
