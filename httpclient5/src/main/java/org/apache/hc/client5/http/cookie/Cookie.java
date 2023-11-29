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

import java.time.Instant;
import java.util.Date;

/**
 * Cookie interface represents a token or short packet of state information
 * (also referred to as "magic-cookie") that the HTTP agent and the target
 * server can exchange to maintain a session. In its simples form an HTTP
 * cookie is merely a name / value pair.
 *
 * @since 4.0
 */
public interface Cookie {

    String PATH_ATTR       = "path";
    String DOMAIN_ATTR     = "domain";
    String MAX_AGE_ATTR    = "max-age";
    String SECURE_ATTR     = "secure";
    String EXPIRES_ATTR    = "expires";
    String HTTP_ONLY_ATTR  = "httpOnly";

    /**
     * @since 5.0
     */
    String getAttribute(String name);

    /**
     * @since 5.0
     */
    boolean containsAttribute(String name);

    /**
     * Returns the name.
     *
     * @return String name The name
     */
    String getName();

    /**
     * Returns the value.
     *
     * @return String value The current value.
     */
    String getValue();

    /**
     * Returns the expiration {@link Date} of the cookie, or {@code null}
     * if none exists.
     * <p><strong>Note:</strong> the object returned by this method is
     * considered immutable. Changing it (e.g. using setTime()) could result
     * in undefined behaviour. Do so at your peril. </p>
     * @return Expiration {@link Date}, or {@code null}.
     * @deprecated Use {{@link #getExpiryInstant()}}
     */
    @Deprecated
    Date getExpiryDate();

    /**
     * Returns the expiration {@link Instant} of the cookie, or {@code null} if none exists.
     * <p><strong>Note:</strong> the object returned by this method is
     * considered immutable. Changing it (e.g. using setTime()) could result in undefined behaviour.
     * Do so at your peril. </p>
     *
     * @return Expiration {@link Instant}, or {@code null}.
     * @since 5.2
     */
    @SuppressWarnings("deprecated")
    default Instant getExpiryInstant() {
        final Date date = getExpiryDate();
        return date != null ? Instant.ofEpochMilli(date.getTime()) : null;
    }

    /**
     * Returns {@code false} if the cookie should be discarded at the end
     * of the "session"; {@code true} otherwise.
     *
     * @return {@code false} if the cookie should be discarded at the end
     *         of the "session"; {@code true} otherwise
     */
    boolean isPersistent();

    /**
     * Returns domain attribute of the cookie. The value of the Domain
     * attribute specifies the domain for which the cookie is valid.
     *
     * @return the value of the domain attribute.
     */
    String getDomain();

    /**
     * Returns the path attribute of the cookie. The value of the Path
     * attribute specifies the subset of URLs on the origin server to which
     * this cookie applies.
     *
     * @return The value of the path attribute.
     */
    String getPath();

    /**
     * Indicates whether this cookie requires a secure connection.
     *
     * @return  {@code true} if this cookie should only be sent
     *          over secure connections, {@code false} otherwise.
     */
    boolean isSecure();

    /**
     * Returns true if this cookie has expired.
     * @param date Current time
     *
     * @return {@code true} if the cookie has expired.
     * @deprecated Use {{@link #isExpired(Instant)}}
     */
    @Deprecated
    boolean isExpired(final Date date);

    /**
     * Returns true if this cookie has expired.
     *
     * @param date Current time
     * @return {@code true} if the cookie has expired.
     * @since 5.2
     */
    @SuppressWarnings("deprecation")
    default boolean isExpired(final Instant date) {
        return isExpired(date != null ? new Date(date.toEpochMilli()) : null);
    }

    /**
     * Returns creation time of the cookie.
     * @deprecated Use {@link #getCreationInstant()}
     */
    @Deprecated
    Date getCreationDate();

    /**
     * Returns creation time of the cookie.
     */
    default Instant getCreationInstant() { return null;  }

    /**
     * Checks whether this Cookie has been marked as {@code httpOnly}.
     * <p>The default implementation returns {@code false}.
     *
     * @return true if this Cookie has been marked as {@code httpOnly},
     * false otherwise
     *
     * @since 5.2
     */
    default boolean isHttpOnly(){
        return false;
    }

}

