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

/**
 * Standardized {@code error} values of the {@code Proxy-Status} response field as defined by
 * RFC 9209. The constants correspond to the initial contents of the IANA "HTTP Proxy-Status
 * Error Types" registry.
 * <p>
 * The {@code error} parameter may also carry unregistered extension tokens, which this
 * enumeration does not model. {@link #fromToken(String)} returns {@code null} for such tokens,
 * leaving the raw value available through {@link ProxyStatus#getErrorToken()}.
 *
 * @since 5.7
 */
public enum ProxyStatusError {

    DNS_TIMEOUT("dns_timeout"),
    DNS_ERROR("dns_error"),
    DESTINATION_NOT_FOUND("destination_not_found"),
    DESTINATION_UNAVAILABLE("destination_unavailable"),
    DESTINATION_IP_PROHIBITED("destination_ip_prohibited"),
    DESTINATION_IP_UNROUTABLE("destination_ip_unroutable"),
    CONNECTION_REFUSED("connection_refused"),
    CONNECTION_TERMINATED("connection_terminated"),
    CONNECTION_TIMEOUT("connection_timeout"),
    CONNECTION_READ_TIMEOUT("connection_read_timeout"),
    CONNECTION_WRITE_TIMEOUT("connection_write_timeout"),
    CONNECTION_LIMIT_REACHED("connection_limit_reached"),
    TLS_PROTOCOL_ERROR("tls_protocol_error"),
    TLS_CERTIFICATE_ERROR("tls_certificate_error"),
    TLS_ALERT_RECEIVED("tls_alert_received"),
    HTTP_REQUEST_ERROR("http_request_error"),
    HTTP_REQUEST_DENIED("http_request_denied"),
    HTTP_RESPONSE_INCOMPLETE("http_response_incomplete"),
    HTTP_RESPONSE_HEADER_SECTION_SIZE("http_response_header_section_size"),
    HTTP_RESPONSE_HEADER_SIZE("http_response_header_size"),
    HTTP_RESPONSE_BODY_SIZE("http_response_body_size"),
    HTTP_RESPONSE_TRAILER_SECTION_SIZE("http_response_trailer_section_size"),
    HTTP_RESPONSE_TRAILER_SIZE("http_response_trailer_size"),
    HTTP_RESPONSE_TRANSFER_CODING("http_response_transfer_coding"),
    HTTP_RESPONSE_CONTENT_CODING("http_response_content_coding"),
    HTTP_RESPONSE_TIMEOUT("http_response_timeout"),
    HTTP_UPGRADE_FAILED("http_upgrade_failed"),
    HTTP_PROTOCOL_ERROR("http_protocol_error"),
    PROXY_INTERNAL_RESPONSE("proxy_internal_response"),
    PROXY_INTERNAL_ERROR("proxy_internal_error"),
    PROXY_CONFIGURATION_ERROR("proxy_configuration_error"),
    PROXY_LOOP_DETECTED("proxy_loop_detected");

    private final String token;

    ProxyStatusError(final String token) {
        this.token = token;
    }

    /**
     * Returns the lowercase token used on the wire for this error.
     */
    public String getToken() {
        return this.token;
    }

    /**
     * Returns the standardized error matching the given token, or {@code null} when the token is
     * {@code null} or not one of the values registered by RFC 9209.
     */
    public static ProxyStatusError fromToken(final String token) {
        if (token != null) {
            for (final ProxyStatusError error : values()) {
                if (error.token.equals(token)) {
                    return error;
                }
            }
        }
        return null;
    }

}
