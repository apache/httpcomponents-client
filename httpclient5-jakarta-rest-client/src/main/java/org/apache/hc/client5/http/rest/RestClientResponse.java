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
package org.apache.hc.client5.http.rest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.Link;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;

import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpResponse;

/**
 * Minimal {@link Response} implementation backed by an in-memory byte body and
 * the headers of an executed {@link HttpResponse}.
 * <p>
 * The entity is fully buffered into a byte array on construction and decoded on
 * demand by {@link #readEntity(Class)}. The implementation does not depend on a
 * JAX-RS {@code RuntimeDelegate}: media types are constructed via the public
 * {@link MediaType} constructor and {@link EntityTag} is only created on demand
 * by {@link #getEntityTag()}.
 * <p>
 * JAX-RS runtime delegate backed link builder operations such as
 * {@link #getLinkBuilder(String)} are not supported and throw
 * {@link UnsupportedOperationException}.
 *
 * @since 5.7
 */
final class RestClientResponse extends Response {

    private final int status;
    private final String reasonPhrase;
    private final byte[] body;
    private final MediaType mediaType;
    private final MultivaluedMap<String, Object> metadata;
    private final MultivaluedMap<String, String> stringHeaders;
    private final ObjectMapper objectMapper;

    private boolean closed;
    private Object cachedEntity;

    RestClientResponse(final HttpResponse response, final byte[] body, final ObjectMapper objectMapper) {
        this.status = response.getCode();
        this.reasonPhrase = response.getReasonPhrase();
        this.body = body != null ? body : new byte[0];
        this.objectMapper = objectMapper;
        this.metadata = new MultivaluedHashMap<>();
        this.stringHeaders = new MultivaluedHashMap<>();
        for (final Header h : response.getHeaders()) {
            this.metadata.add(h.getName(), h.getValue());
            this.stringHeaders.add(h.getName(), h.getValue());
        }
        final Header ct = response.getFirstHeader(HttpHeaders.CONTENT_TYPE);
        this.mediaType = ct != null ? toMediaType(ContentType.parse(ct.getValue())) : null;
    }

    private static MediaType toMediaType(final ContentType ct) {
        if (ct == null) {
            return null;
        }
        final String mime = ct.getMimeType();
        final int slash = mime.indexOf('/');
        final String type = slash > 0 ? mime.substring(0, slash) : MediaType.MEDIA_TYPE_WILDCARD;
        final String subtype = slash > 0 ? mime.substring(slash + 1) : MediaType.MEDIA_TYPE_WILDCARD;
        if (ct.getCharset() != null) {
            return new MediaType(type, subtype, ct.getCharset().name());
        }
        return new MediaType(type, subtype);
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public StatusType getStatusInfo() {
        final Status standard = Status.fromStatusCode(status);
        final String reason = reasonPhrase != null ? reasonPhrase
                : standard != null ? standard.getReasonPhrase() : "";
        final Status.Family family = Status.Family.familyOf(status);
        return new StatusType() {
            @Override public int getStatusCode() { return status; }
            @Override public Status.Family getFamily() { return family; }
            @Override public String getReasonPhrase() { return reason; }
        };
    }

    @Override
    public Object getEntity() {
        ensureOpen();
        return body.length == 0 ? null : body;
    }

    @Override
    public <T> T readEntity(final Class<T> entityType) {
        return readEntity(entityType, (Annotation[]) null);
    }

    @Override
    public <T> T readEntity(final GenericType<T> entityType) {
        return readEntity(entityType, null);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T readEntity(final Class<T> entityType, final Annotation[] annotations) {
        ensureOpen();
        if (cachedEntity != null && entityType.isInstance(cachedEntity)) {
            return (T) cachedEntity;
        }
        final T value = (T) decodeBody(entityType, null);
        cachedEntity = value;
        return value;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T readEntity(final GenericType<T> entityType, final Annotation[] annotations) {
        ensureOpen();
        return (T) decodeBody(entityType.getRawType(), entityType.getType());
    }

    private Object decodeBody(final Class<?> rawType, final java.lang.reflect.Type genericType) {
        if (rawType == byte[].class) {
            return body;
        }
        if (rawType == String.class) {
            return new String(body, charset());
        }
        if (rawType == void.class || rawType == Void.class) {
            return null;
        }
        if (body.length == 0) {
            return null;
        }
        try {
            if (genericType != null) {
                return objectMapper.readValue(body, objectMapper.getTypeFactory().constructType(genericType));
            }
            return objectMapper.readValue(body, rawType);
        } catch (final IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private Charset charset() {
        if (mediaType != null) {
            final String cs = mediaType.getParameters().get(MediaType.CHARSET_PARAMETER);
            if (cs != null) {
                return Charset.forName(cs);
            }
        }
        return StandardCharsets.UTF_8;
    }

    @Override
    public boolean hasEntity() {
        return body.length > 0;
    }

    @Override
    public boolean bufferEntity() {
        ensureOpen();
        return true;
    }

    @Override
    public void close() {
        closed = true;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Response has been closed");
        }
    }

    @Override
    public MediaType getMediaType() {
        return mediaType;
    }

    @Override
    public Locale getLanguage() {
        final String lang = getHeaderString(HttpHeaders.CONTENT_LANGUAGE);
        return lang != null ? Locale.forLanguageTag(lang) : null;
    }

    @Override
    public int getLength() {
        final String len = getHeaderString(HttpHeaders.CONTENT_LENGTH);
        if (len != null) {
            try {
                return Integer.parseInt(len);
            } catch (final NumberFormatException ignore) {
            }
        }
        return body.length > 0 ? body.length : -1;
    }

    @Override
    public Set<String> getAllowedMethods() {
        final List<String> values = headerValues(HttpHeaders.ALLOW);
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        final Set<String> result = new LinkedHashSet<>();
        for (final String v : values) {
            for (final String m : v.split(",")) {
                final String trimmed = m.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed.toUpperCase(Locale.ROOT));
                }
            }
        }
        return result;
    }

    @Override
    public Map<String, NewCookie> getCookies() {
        return Collections.emptyMap();
    }

    @Override
    public EntityTag getEntityTag() {
        final String etag = getHeaderString(HttpHeaders.ETAG);
        if (etag == null) {
            return null;
        }
        String raw = etag.trim();
        boolean weak = false;
        if (raw.startsWith("W/")) {
            weak = true;
            raw = raw.substring(2).trim();
        }
        if (raw.length() >= 2 && raw.charAt(0) == '"' && raw.charAt(raw.length() - 1) == '"') {
            raw = raw.substring(1, raw.length() - 1);
        }
        return new EntityTag(raw, weak);
    }

    @Override
    public Date getDate() {
        return parseHttpDate(getHeaderString(HttpHeaders.DATE));
    }

    @Override
    public Date getLastModified() {
        return parseHttpDate(getHeaderString(HttpHeaders.LAST_MODIFIED));
    }

    private static Date parseHttpDate(final String value) {
        if (value == null) {
            return null;
        }
        final Instant instant = DateUtils.parseDate(value, DateUtils.STANDARD_PATTERNS);
        return instant != null ? Date.from(instant) : null;
    }

    @Override
    public URI getLocation() {
        final String loc = getHeaderString(HttpHeaders.LOCATION);
        if (loc == null) {
            return null;
        }
        try {
            return new URI(loc);
        } catch (final URISyntaxException ex) {
            return null;
        }
    }

    @Override
    public Set<Link> getLinks() {
        return Collections.emptySet();
    }

    @Override
    public boolean hasLink(final String relation) {
        return false;
    }

    @Override
    public Link getLink(final String relation) {
        return null;
    }

    @Override
    public Link.Builder getLinkBuilder(final String relation) {
        throw new UnsupportedOperationException(
                "Link.Builder requires a JAX-RS RuntimeDelegate implementation");
    }

    @Override
    public MultivaluedMap<String, Object> getMetadata() {
        return metadata;
    }

    @Override
    public MultivaluedMap<String, String> getStringHeaders() {
        return stringHeaders;
    }

    @Override
    public String getHeaderString(final String name) {
        final List<String> values = headerValues(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        if (values.size() == 1) {
            return values.get(0);
        }
        final StringBuilder sb = new StringBuilder();
        for (final String v : values) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(v);
        }
        return sb.toString();
    }

    private List<String> headerValues(final String name) {
        for (final Map.Entry<String, List<String>> entry : stringHeaders.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("RestClientResponse[status=");
        sb.append(status);
        if (mediaType != null) {
            sb.append(", mediaType=").append(mediaType);
        }
        sb.append(", length=").append(body.length);
        sb.append(']');
        return sb.toString();
    }
}
