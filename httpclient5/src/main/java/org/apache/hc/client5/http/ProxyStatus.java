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

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.util.Args;

/**
 * A single member of the {@code Proxy-Status} response field defined by RFC 9209. Each member
 * identifies one intermediary and carries the parameters that describe how that intermediary
 * handled the request.
 * <p>
 * This type is a passive, immutable data holder. It neither interprets nor acts on the reported
 * values; callers decide whether and how to use them. Parameter values keep their Structured
 * Fields (RFC 8941) types: a {@link Token} for tokens, a {@link String} for strings, a
 * {@link Long} for integers, a {@link java.math.BigDecimal} for decimals, a {@link Boolean} for
 * booleans and a {@code byte[]} for byte sequences. Byte-sequence values are defensively copied
 * on the way in and out, so instances remain immutable. Parsing of the field value is performed
 * by {@link org.apache.hc.client5.http.support.ProxyStatusSupport}.
 *
 * @since 5.7
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public final class ProxyStatus {

    /**
     * An RFC 8941 Token value, kept distinct from {@link String} so that Token and String
     * parameter values are not conflated.
     */
    @Contract(threading = ThreadingBehavior.IMMUTABLE)
    public static final class Token {

        private final String value;

        public Token(final String value) {
            this.value = Args.notNull(value, "Token value");
        }

        public String getValue() {
            return this.value;
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            return obj instanceof Token && this.value.equals(((Token) obj).value);
        }

        @Override
        public int hashCode() {
            return this.value.hashCode();
        }

        @Override
        public String toString() {
            return this.value;
        }
    }

    private static final String ERROR = "error";
    private static final String NEXT_HOP = "next-hop";
    private static final String NEXT_PROTOCOL = "next-protocol";
    private static final String RECEIVED_STATUS = "received-status";
    private static final String DETAILS = "details";

    private final String name;
    private final Map<String, Object> parameters;

    /**
     * Creates a member with the given intermediary identity and parameters. The parameter map is
     * deep-copied and {@code byte[]} values are cloned, so the instance cannot be mutated through
     * the supplied map. To keep the instance immutable, each value must be one of the supported
     * Structured Fields types ({@link Token}, {@link String}, {@link Long}, {@link BigDecimal},
     * {@link Boolean} or {@code byte[]}); any other value is rejected.
     *
     * @param name the identity of the intermediary; must not be {@code null}.
     * @param parameters the member parameters, or {@code null} for none.
     * @throws IllegalArgumentException if a parameter value is not a supported type.
     */
    public ProxyStatus(final String name, final Map<String, Object> parameters) {
        this.name = Args.notNull(name, "Proxy identity");
        this.parameters = Collections.unmodifiableMap(copy(parameters));
    }

    private static Map<String, Object> copy(final Map<String, Object> source) {
        final Map<String, Object> target = new LinkedHashMap<>();
        if (source != null) {
            for (final Map.Entry<String, Object> entry : source.entrySet()) {
                target.put(entry.getKey(), copyValue(entry.getValue()));
            }
        }
        return target;
    }

    private static Object copyValue(final Object value) {
        if (value instanceof byte[]) {
            return ((byte[]) value).clone();
        }
        if (value instanceof String
                || value instanceof Token
                || value instanceof Long
                || value instanceof BigDecimal
                || value instanceof Boolean) {
            return value;
        }
        throw new IllegalArgumentException("Unsupported Proxy-Status parameter value type: "
                + (value != null ? value.getClass().getName() : "null"));
    }

    /**
     * Returns the identity of the intermediary, that is, the value of the list member.
     */
    public String getName() {
        return this.name;
    }

    /**
     * Returns the parameters attached to this member as an unmodifiable map in parsed order. Any
     * {@code byte[]} value is cloned, so mutating it does not affect this instance.
     */
    public Map<String, Object> getParameters() {
        return Collections.unmodifiableMap(copy(this.parameters));
    }

    /**
     * Returns the value of the named parameter, or {@code null} when it is absent. A {@code byte[]}
     * value is cloned before being returned.
     */
    public Object getParameter(final String name) {
        final Object value = this.parameters.get(name);
        return value != null ? copyValue(value) : null;
    }

    /**
     * Returns the raw {@code error} token, or {@code null} when the parameter is absent or not a
     * token.
     */
    public String getErrorToken() {
        final Object value = this.parameters.get(ERROR);
        return value instanceof Token ? ((Token) value).getValue() : null;
    }

    /**
     * Returns the standardized {@code error}, or {@code null} when the parameter is absent or its
     * token is not registered by RFC 9209.
     */
    public ProxyStatusError getError() {
        return ProxyStatusError.fromToken(getErrorToken());
    }

    /**
     * Returns the {@code next-hop} value, or {@code null} when the parameter is absent. The
     * parameter may be a string or a token; both are returned as their character value.
     */
    public String getNextHop() {
        final Object value = this.parameters.get(NEXT_HOP);
        if (value instanceof String) {
            return (String) value;
        }
        return value instanceof Token ? ((Token) value).getValue() : null;
    }

    /**
     * Returns the {@code next-protocol} ALPN identifier when it is expressed as a token, or
     * {@code null} when the parameter is absent or expressed as a byte sequence. A byte-sequence
     * identifier is available as a {@code byte[]} through {@link #getParameter(String)}.
     */
    public String getNextProtocol() {
        final Object value = this.parameters.get(NEXT_PROTOCOL);
        return value instanceof Token ? ((Token) value).getValue() : null;
    }

    /**
     * Returns the {@code received-status} code, or {@code null} when the parameter is absent or
     * not an integer. The value is validated as an HTTP status code during parsing, so it always
     * fits in an {@code int}.
     */
    public Integer getReceivedStatus() {
        final Object value = this.parameters.get(RECEIVED_STATUS);
        return value instanceof Long ? Integer.valueOf(((Long) value).intValue()) : null;
    }

    /**
     * Returns the free-form {@code details} string, or {@code null} when the parameter is absent.
     */
    public String getDetails() {
        final Object value = this.parameters.get(DETAILS);
        return value instanceof String ? (String) value : null;
    }

    @Override
    public String toString() {
        return this.name + this.parameters;
    }

}
