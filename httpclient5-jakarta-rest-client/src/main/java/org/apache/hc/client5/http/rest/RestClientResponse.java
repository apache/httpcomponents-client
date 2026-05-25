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
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.apache.hc.client5.http.validator.ETag;
import org.apache.hc.client5.http.validator.ValidatorType;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.CharArrayBuffer;

/**
 * Minimal {@link Response} implementation backed by a consumed {@link JsonNode}
 * entity and the headers of an executed {@link HttpResponse}.
 * <p>
 * JSON response entities are digested directly into a {@link JsonNode}. Non-JSON
 * response entities are represented as a textual {@link JsonNode} by the
 * response consumer. The implementation does not depend on a JAX-RS
 * {@code RuntimeDelegate}: media types are constructed via the public
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

    private static final byte[] EMPTY = new byte[0];

    private final ObjectMapper objectMapper;
    private final HttpResponse response;
    private final JsonNode body;
    private final ContentType contentType;
    private final long len;

    private boolean closed;
    private Object cachedEntity;

    RestClientResponse(
            final ObjectMapper objectMapper,
            final HttpResponse response,
            final JsonNode jsonNode,
            final ContentType contentType,
            final long len) {
        this.objectMapper = Args.notNull(objectMapper, "Object mapper");
        this.response = Args.notNull(response, "Response");
        this.contentType = contentType;
        this.body = jsonNode;
        this.len = len;
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
        return response.getCode();
    }

    @Override
    public StatusType getStatusInfo() {
        final int statusCode = response.getCode();
        final Status standard = Status.fromStatusCode(statusCode);
        final String reason = response.getReasonPhrase() != null ? response.getReasonPhrase()
                : standard != null ? standard.getReasonPhrase() : "";
        final Status.Family family = Status.Family.familyOf(statusCode);
        return new StatusType() {

            @Override
            public int getStatusCode() {
                return statusCode;
            }

            @Override
            public Status.Family getFamily() {
                return family;
            }

            @Override
            public String getReasonPhrase() {
                return reason;
            }

        };
    }

    @Override
    public Object getEntity() {
        ensureOpen();
        return body;
    }

    @Override
    public <T> T readEntity(final Class<T> entityType) {
        return readEntity(entityType, null);
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
        if (rawType == void.class || rawType == Void.class) {
            return null;
        }
        if (rawType == JsonNode.class) {
            return body;
        }
        if (rawType == String.class) {
            return bodyAsString();
        }
        if (rawType == byte[].class) {
            return bodyAsBytes();
        }
        if (body == null || body.isMissingNode()) {
            return null;
        }
        try {
            if (genericType != null) {
                final JavaType type = objectMapper.getTypeFactory().constructType(genericType);
                return objectMapper.readerFor(type).readValue(body);
            }
            return objectMapper.treeToValue(body, rawType);
        } catch (final IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private String bodyAsString() {
        if (body == null || body.isMissingNode()) {
            return "";
        }
        if (body.isTextual()) {
            return body.asText();
        }
        try {
            return objectMapper.writeValueAsString(body);
        } catch (final IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private byte[] bodyAsBytes() {
        if (body == null || body.isMissingNode()) {
            return EMPTY;
        }
        if (body.isTextual()) {
            return body.asText().getBytes(charset());
        }
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (final IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private Charset charset() {
        return ContentType.getCharset(contentType, StandardCharsets.UTF_8);
    }

    @Override
    public boolean hasEntity() {
        return body != null && !body.isMissingNode();
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
        return toMediaType(contentType);
    }

    @Override
    public Locale getLanguage() {
        final Header h = response.getFirstHeader(HttpHeaders.CONTENT_LANGUAGE);
        return h != null ? Locale.forLanguageTag(h.getValue()) : null;
    }

    @Override
    public int getLength() {
        return (int) len;
    }

    @Override
    public Set<String> getAllowedMethods() {
        final LinkedHashSet<String> allowedMethods = new LinkedHashSet<>();
        MessageSupport.parseTokens(response, HttpHeaders.ALLOW, allowedMethods::add);
        return allowedMethods;
    }

    @Override
    public Map<String, NewCookie> getCookies() {
        return Collections.emptyMap();
    }

    @Override
    public EntityTag getEntityTag() {
        final ETag eTag = ETag.get(response);
        return eTag != null ? new EntityTag(eTag.getValue(), eTag.getType() == ValidatorType.WEAK) : null;
    }

    @Override
    public Date getDate() {
        final Instant instant = DateUtils.parseStandardDate(response, HttpHeaders.DATE);
        return instant != null ? Date.from(instant) : null;
    }

    @Override
    public Date getLastModified() {
        final Instant instant = DateUtils.parseStandardDate(response, HttpHeaders.LAST_MODIFIED);
        return instant != null ? Date.from(instant) : null;
    }

    @Override
    public URI getLocation() {
        final Header h = response.getFirstHeader(HttpHeaders.LOCATION);
        if (h != null) {
            try {
                return new URI(h.getValue());
            } catch (final URISyntaxException ignore) {
            }
        }
        return null;
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
        final MultivaluedMap<String, Object> multimap = new MultivaluedHashMap<>();
        for (final Iterator<Header> it = response.headerIterator(); it.hasNext(); ) {
            final Header h = it.next();
            multimap.add(h.getName(), h.getValue());
        }
        return multimap;
    }

    @Override
    public MultivaluedMap<String, String> getStringHeaders() {
        final MultivaluedMap<String, String> multimap = new MultivaluedHashMap<>();
        for (final Iterator<Header> it = response.headerIterator(); it.hasNext(); ) {
            final Header h = it.next();
            multimap.add(h.getName(), h.getValue());
        }
        return multimap;
    }

    @Override
    public String getHeaderString(final String name) {
        final Header[] headers = response.getHeaders(name);
        if (headers.length == 0) {
            return null;
        } else if (headers.length == 1) {
            return headers[0].getValue();
        } else {
            final CharArrayBuffer buf = new CharArrayBuffer(128);
            buf.append(headers[0].getValue());
            for (int i = 1; i < headers.length; i++) {
                buf.append(", ");
                buf.append(headers[i].getValue());
            }

            return buf.toString();
        }
    }

    @Override
    public String toString() {
        return response.toString();
    }

}