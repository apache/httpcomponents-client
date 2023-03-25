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

package org.apache.hc.client5.http.impl.cache;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.HttpCacheEntrySerializer;
import org.apache.hc.client5.http.cache.HttpCacheStorageEntry;
import org.apache.hc.client5.http.cache.Resource;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.io.AbstractMessageParser;
import org.apache.hc.core5.http.impl.io.AbstractMessageWriter;
import org.apache.hc.core5.http.impl.io.DefaultHttpResponseParser;
import org.apache.hc.core5.http.impl.io.SessionInputBufferImpl;
import org.apache.hc.core5.http.impl.io.SessionOutputBufferImpl;
import org.apache.hc.core5.http.io.SessionInputBuffer;
import org.apache.hc.core5.http.io.SessionOutputBuffer;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.BasicLineFormatter;
import org.apache.hc.core5.http.message.HeaderGroup;
import org.apache.hc.core5.http.message.LazyLineParser;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.apache.hc.core5.util.TimeValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serializes and deserializes byte arrays into HTTP cache entries using a default buffer size of 8192 bytes.
 * The cache entries contain an HTTP response generated from the byte array data, which can be used to generate
 * HTTP responses for cache hits.
 * <p>
 * This implementation uses the Apache HttpComponents library to perform the serialization and deserialization.
 * <p>
 * To serialize a byte array into an HTTP cache entry, use the {@link #serialize(HttpCacheStorageEntry)} method. To deserialize an HTTP cache
 * entry into a byte array, use the {@link #deserialize(byte[])} method.
 * <p>
 * This class implements the {@link HttpCacheEntrySerializer} interface, which defines the contract for HTTP cache
 * entry serialization and deserialization. It also includes a default buffer size of 8192 bytes, which can be
 * overridden by specifying a different buffer size in the constructor.
 * <p>
 * Note that this implementation only supports HTTP responses and does not support HTTP requests or any other types of
 * HTTP messages.
 *
 * @since 5.3
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public class HttpByteArrayCacheEntrySerializer implements HttpCacheEntrySerializer<byte[]> {

    private static final Logger LOG = LoggerFactory.getLogger(HttpByteArrayCacheEntrySerializer.class);

    /**
     * The default buffer size used for I/O operations, set to 8192 bytes.
     */
    private static final int DEFAULT_BUFFER_SIZE = 8192;

    /**
     * Singleton instance of this class.
     */
    public static final HttpByteArrayCacheEntrySerializer INSTANCE = new HttpByteArrayCacheEntrySerializer(DEFAULT_BUFFER_SIZE);


    private static final String SC_CACHE_ENTRY_PREFIX = "hc-";

    private static final String SC_HEADER_NAME_STORAGE_KEY = SC_CACHE_ENTRY_PREFIX + "sk";
    private static final String SC_HEADER_NAME_RESPONSE_DATE = SC_CACHE_ENTRY_PREFIX + "resp-date";
    private static final String SC_HEADER_NAME_REQUEST_DATE = SC_CACHE_ENTRY_PREFIX + "req-date";
    private static final String SC_HEADER_NAME_NO_CONTENT = SC_CACHE_ENTRY_PREFIX + "no-content";
    private static final String SC_HEADER_NAME_VARIANT_MAP_KEY = SC_CACHE_ENTRY_PREFIX + "varmap-key";
    private static final String SC_HEADER_NAME_VARIANT_MAP_VALUE = SC_CACHE_ENTRY_PREFIX + "varmap-val";
    private static final String SC_CACHE_ENTRY_PRESERVE_PREFIX = SC_CACHE_ENTRY_PREFIX + "esc-";


    /**
     * The generator used to generate cached HTTP responses.
     */
    private final CachedHttpResponseGenerator cachedHttpResponseGenerator;

    /**
     * The size of the buffer used for reading/writing data.
     */
    private final int bufferSize;

    /**
     * The parser used for reading SimpleHttpResponse instances from the network.
     */
    private final AbstractMessageParser<SimpleHttpResponse> responseParser = new SimpleHttpResponseParser();

    /**
     * The writer used for writing SimpleHttpResponse instances to the network.
     */
    private final AbstractMessageWriter<SimpleHttpResponse> responseWriter = new SimpleHttpResponseWriter();

    /**
     * Constructs a HttpByteArrayCacheEntrySerializer with the specified buffer size.
     *
     * @param bufferSize the buffer size to use for serialization and deserialization.
     */
    public HttpByteArrayCacheEntrySerializer(
            final int bufferSize) {
        this.bufferSize = bufferSize > 0 ? bufferSize : DEFAULT_BUFFER_SIZE;
        this.cachedHttpResponseGenerator = new CachedHttpResponseGenerator(NoAgeCacheValidityPolicy.INSTANCE);
    }

    /**
     * Constructs a new instance of {@code HttpByteArrayCacheEntrySerializer} with a default buffer size.
     *
     * @see #DEFAULT_BUFFER_SIZE
     */
    public HttpByteArrayCacheEntrySerializer() {
        this(DEFAULT_BUFFER_SIZE);
    }

    /**
     * Serializes an HttpCacheStorageEntry object into a byte array using an HTTP-like format.
     * <p>
     * The metadata is encoded into HTTP pseudo-headers for storage.
     *
     * @param httpCacheEntry the HttpCacheStorageEntry to serialize.
     * @return the byte array containing the serialized HttpCacheStorageEntry.
     * @throws ResourceIOException if there is an error during serialization.
     */
    @Override
    public byte[] serialize(final HttpCacheStorageEntry httpCacheEntry) throws ResourceIOException {
        if (httpCacheEntry.getKey() == null) {
            throw new IllegalStateException("Cannot serialize cache object with null storage key");
        }
        // content doesn't need null-check because it's validated in the HttpCacheStorageEntry constructor

        // Fake HTTP request, required by response generator
        // Use request method from httpCacheEntry, but as far as I can tell it will only ever return "GET".
        final HttpRequest httpRequest = new BasicHttpRequest(httpCacheEntry.getContent().getRequestMethod(), "/");

        final SimpleHttpResponse httpResponse = cachedHttpResponseGenerator.generateResponse(httpRequest, httpCacheEntry.getContent());
        final int size = httpResponse.getHeaders().length + (httpResponse.getBodyBytes() != null ? httpResponse.getBodyBytes().length : 0);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(size)) {
            escapeHeaders(httpResponse);
            addMetadataPseudoHeaders(httpResponse, httpCacheEntry);

            final byte[] bodyBytes = httpResponse.getBodyBytes();
            int resourceLength = 0;

            if (bodyBytes == null) {
                // This means no content, for example a 204 response
                httpResponse.addHeader(SC_HEADER_NAME_NO_CONTENT, Boolean.TRUE.toString());
            } else {
                resourceLength = bodyBytes.length;
            }

            final SessionOutputBuffer outputBuffer = new SessionOutputBufferImpl(bufferSize);

            responseWriter.write(httpResponse, outputBuffer, out);
            outputBuffer.flush(out);
            final byte[] headerBytes = out.toByteArray();

            final ByteBuffer buffer = ByteBuffer.allocate(headerBytes.length + resourceLength);
            buffer.put(headerBytes, 0, headerBytes.length);
            if (resourceLength > 0) {
                buffer.put(bodyBytes, 0, resourceLength);
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("Serialized cache entry with key {} and {} bytes", httpCacheEntry.getKey(), size);
            }

            return buffer.array();

        } catch (final IOException | HttpException e) {
            throw new ResourceIOException("Exception while serializing cache entry", e);
        }
    }

    /**
     * Deserializes a byte array representation of an HTTP cache storage entry into an instance of
     * {@link HttpCacheStorageEntry}.
     *
     * @param serializedObject the byte array representation of the HTTP cache storage entry
     * @return the deserialized HTTP cache storage entry
     * @throws ResourceIOException if an error occurs during deserialization
     */
    @Override
    public HttpCacheStorageEntry deserialize(final byte[] serializedObject) throws ResourceIOException {
        if (serializedObject == null || serializedObject.length == 0) {
            throw new ResourceIOException("Serialized object is null or empty");
        }
        try (final InputStream in = new ByteArrayInputStream(serializedObject);
             final ByteArrayOutputStream bytesOut = new ByteArrayOutputStream(serializedObject.length) // this is bigger than necessary but will save us from reallocating
        ) {
            final SessionInputBufferImpl inputBuffer = new SessionInputBufferImpl(bufferSize);
            final SimpleHttpResponse response = responseParser.parse(inputBuffer, in);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Deserializing cache entry with headers: {}", response.getHeaders());
            }

            final Map<String, String> variantMap = new HashMap<>();

            String lastKey = null;
            String storageKey = null;
            Instant requestDate = null;
            Instant responseDate= null;
            boolean noBody = false;

            final HeaderGroup headerGroup = new HeaderGroup();

            for (final Iterator<Header> it = response.headerIterator(); it.hasNext(); ) {
                final Header header = it.next();

                if (header != null) {
                    final String headerName = header.getName();
                    final String value = header.getValue();

                    if (headerName.equals(SC_HEADER_NAME_VARIANT_MAP_KEY)) {
                        lastKey = header.getValue();
                        continue;
                    } else if (headerName.equals(SC_HEADER_NAME_VARIANT_MAP_VALUE)) {
                        if (lastKey == null) {
                            throw new ResourceIOException("Found mismatched variant map key/value headers");
                        }
                        variantMap.put(lastKey, value);
                        lastKey = null;
                    }

                    if (headerName.equalsIgnoreCase(SC_HEADER_NAME_STORAGE_KEY)) {
                        storageKey = value;
                        continue;
                    }

                    if (headerName.equalsIgnoreCase(SC_HEADER_NAME_REQUEST_DATE)) {
                        requestDate = parseCachePseudoHeaderDate(value);
                        continue;
                    }

                    if (headerName.equalsIgnoreCase(SC_HEADER_NAME_RESPONSE_DATE)) {
                        responseDate = parseCachePseudoHeaderDate(value);
                        continue;
                    }

                    if (headerName.equalsIgnoreCase(SC_HEADER_NAME_NO_CONTENT)) {
                        noBody = Boolean.parseBoolean(value);
                        continue;
                    }

                    if (headerName.startsWith(SC_CACHE_ENTRY_PRESERVE_PREFIX)) {
                        headerGroup.addHeader(new BasicHeader(header.getName().substring(SC_CACHE_ENTRY_PRESERVE_PREFIX.length()), header.getValue()));
                        continue;
                    }
                    headerGroup.addHeader(header);
                }
            }

            headerNotNull(storageKey,SC_HEADER_NAME_STORAGE_KEY);
            headerNotNull(requestDate, SC_HEADER_NAME_REQUEST_DATE);
            headerNotNull(responseDate, SC_HEADER_NAME_RESPONSE_DATE);


            if (lastKey != null) {
                throw new ResourceIOException("Found mismatched variant map key/value headers");
            }

            response.setHeaders(headerGroup.getHeaders());

            Resource resource = null;
            if (!noBody) {

                copyBytes(inputBuffer, in, bytesOut);
                resource = new HeapResource(bytesOut.toByteArray());
            }

            final HttpCacheEntry httpCacheEntry = new HttpCacheEntry(
                    requestDate,
                    responseDate,
                    response.getCode(),
                    response.getHeaders(),
                    resource,
                    variantMap
            );


            if (LOG.isDebugEnabled()) {
                LOG.debug("Returning deserialized cache entry with storage key '{}'" , httpCacheEntry);
            }


            return new HttpCacheStorageEntry(storageKey, httpCacheEntry);
        } catch (final IOException | HttpException e) {
            throw new ResourceIOException("Error deserializing cache entry", e);
        }
    }

    /**
     * Modify the given response to escape any header names that start with the prefix we use for our own pseudo-headers,
     * prefixing them with an escape sequence we can use to recover them later.
     *
     * @param httpResponse HTTP response object to escape headers in
     */
    private void escapeHeaders(final HttpResponse httpResponse) {
        final HeaderGroup headerGroup = new HeaderGroup();
        for (final Iterator<Header> it = httpResponse.headerIterator(); it.hasNext(); ) {
            final Header header = it.next();
            if (header != null && header.getName().startsWith(SC_CACHE_ENTRY_PREFIX)) {
                headerGroup.addHeader(new BasicHeader(SC_CACHE_ENTRY_PRESERVE_PREFIX + header.getName(), header.getValue()));
            } else if (header != null) {
                headerGroup.addHeader(header);
            }
        }
        httpResponse.setHeaders(headerGroup.getHeaders());
    }


    /**
     * Modify the given response to add our own cache metadata as pseudo-headers.
     *
     * @param httpResponse HTTP response object to add pseudo-headers to
     */
    private void addMetadataPseudoHeaders(final HttpResponse httpResponse, final HttpCacheStorageEntry httpCacheEntry) {
        final HeaderGroup headerGroup = new HeaderGroup();
        headerGroup.setHeaders(httpResponse.getHeaders());
        headerGroup.addHeader(new BasicHeader(SC_HEADER_NAME_STORAGE_KEY, httpCacheEntry.getKey()));
        headerGroup.addHeader(new BasicHeader(SC_HEADER_NAME_RESPONSE_DATE, Long.toString(httpCacheEntry.getContent().getResponseInstant().toEpochMilli())));
        headerGroup.addHeader(new BasicHeader(SC_HEADER_NAME_REQUEST_DATE, Long.toString(httpCacheEntry.getContent().getRequestInstant().toEpochMilli())));

        // Encode these so map entries are stored in a pair of headers, one for key and one for value.
        // Header keys look like: {Accept-Encoding=gzip}
        // And header values like: {Accept-Encoding=gzip}https://example.com:1234/foo
        for (final Map.Entry<String, String> entry : httpCacheEntry.getContent().getVariantMap().entrySet()) {
            // Headers are ordered
            headerGroup.addHeader(new BasicHeader(SC_HEADER_NAME_VARIANT_MAP_KEY, entry.getKey()));
            headerGroup.addHeader(new BasicHeader(SC_HEADER_NAME_VARIANT_MAP_VALUE, entry.getValue()));
        }

        httpResponse.setHeaders(headerGroup.getHeaders());
    }


    /**
     * Parses the date value for a single metadata pseudo-header based on the given header name, and returns it as an Instant.
     *
     * @param name Name of the metadata pseudo-header to parse
     * @return Instant value for the metadata pseudo-header
     * @throws ResourceIOException if the given pseudo-header is not found, or contains invalid data
     */
    private Instant parseCachePseudoHeaderDate(final String name) throws ResourceIOException {
        try {
            return Instant.ofEpochMilli(Long.parseLong(name));
        } catch (final NumberFormatException e) {
            throw new ResourceIOException("Invalid value for header '" + name + "'", e);
        }
    }


    /**
     * Copy bytes from the given source buffer and input stream to the given output stream until end-of-file is reached.
     *
     * @param srcBuf Buffered input source
     * @param src    Unbuffered input source
     * @param dest   Output destination
     * @throws IOException if an I/O error occurs
     */
    private void copyBytes(final SessionInputBuffer srcBuf, final InputStream src, final OutputStream dest) throws IOException {
        final byte[] buf = new byte[bufferSize];
        int lastBytesRead;
        while ((lastBytesRead = srcBuf.read(buf, src)) != -1) {
            dest.write(buf, 0, lastBytesRead);
        }
    }

    /**
     * Validates that a given cache header is not null, throwing a {@link ResourceIOException} if it is.
     *
     * @param obj        the cache header object to validate
     * @param headerName the name of the cache header being validated, used in the exception message if it is null
     * @return the validated cache header object
     * @throws ResourceIOException if the cache header object is null
     */
    private <T> T headerNotNull(final T obj, final String headerName) throws ResourceIOException {
        if (obj == null) {
            throw new ResourceIOException("Expected cache header '" + headerName + "' not found");
        }
        return obj;
    }

    /**
     * This class extends AbstractMessageWriter and provides the ability to write a SimpleHttpResponse message.
     */
    private static class SimpleHttpResponseWriter extends AbstractMessageWriter<SimpleHttpResponse> {

        /**
         * Constructs a SimpleHttpResponseWriter object with the BasicLineFormatter instance.
         */
        public SimpleHttpResponseWriter() {
            super(BasicLineFormatter.INSTANCE);
        }

        /**
         * Writes the head line of the given SimpleHttpResponse message to the given CharArrayBuffer.
         *
         * @param message the SimpleHttpResponse message to write
         * @param lineBuf the CharArrayBuffer to write the head line to
         */
        @Override
        protected void writeHeadLine(
                final SimpleHttpResponse message, final CharArrayBuffer lineBuf) {
            final ProtocolVersion transportVersion = message.getVersion();
            BasicLineFormatter.INSTANCE.formatStatusLine(lineBuf, new StatusLine(
                    transportVersion != null ? transportVersion : HttpVersion.HTTP_1_1,
                    message.getCode(),
                    message.getReasonPhrase()));
        }
    }

    /**
     * This class extends AbstractMessageParser and provides the ability to parse a SimpleHttpResponse message.
     */
    private static class SimpleHttpResponseParser extends AbstractMessageParser<SimpleHttpResponse> {

        /**
         * Constructs a SimpleHttpResponseParser object with the LazyLineParser instance and Http1Config.DEFAULT.
         */
        public SimpleHttpResponseParser() {
            super(LazyLineParser.INSTANCE, Http1Config.DEFAULT);
        }

        /**
         * Creates a SimpleHttpResponse object from the given CharArrayBuffer.
         *
         * @param buffer the CharArrayBuffer to parse the SimpleHttpResponse from
         * @return the SimpleHttpResponse object created from the buffer
         * @throws HttpException if the buffer cannot be parsed
         */
        @Override
        protected SimpleHttpResponse createMessage(final CharArrayBuffer buffer) throws HttpException {
            final StatusLine statusline = LazyLineParser.INSTANCE.parseStatusLine(buffer);
            final SimpleHttpResponse response = new SimpleHttpResponse(statusline.getStatusCode(), statusline.getReasonPhrase());
            response.setVersion(statusline.getProtocolVersion());
            return response;
        }
    }

    /**
     * Cache validity policy that always returns an age of {@link TimeValue#ZERO_MILLISECONDS}.
     * <p>
     * This prevents the Age header from being written to the cache (it does not make sense to cache it),
     * and is the only thing the policy is used for in this case.
     */
    private static class NoAgeCacheValidityPolicy extends CacheValidityPolicy {

        public static final NoAgeCacheValidityPolicy INSTANCE = new NoAgeCacheValidityPolicy();

        @Override
        public TimeValue getCurrentAge(final HttpCacheEntry entry, final Instant now) {
            return TimeValue.ZERO_MILLISECONDS;
        }
    }


    /**
     * Helper method to make a new ByteArrayInputStream.
     * <p>
     * Useful to override for testing.
     *
     * @param bytes Bytes to read from the stream
     * @return Stream to read the bytes from
     * @deprecated not need it anymore.
     */
    @Deprecated
    protected InputStream makeByteArrayInputStream(final byte[] bytes) {
        return new ByteArrayInputStream(bytes);
    }

    /**
     * Helper method to make a new HTTP Response parser.
     * <p>
     * Useful to override for testing.
     *
     * @return HTTP response parser
     * @deprecated not need it anymore.
     */
    @Deprecated
    protected AbstractMessageParser<ClassicHttpResponse> makeHttpResponseParser() {
        return new DefaultHttpResponseParser();
    }

    /**
     * Helper method to make a new HTTP response writer.
     * <p>
     * Useful to override for testing.
     *
     * @param outputBuffer Output buffer to write to
     * @return HTTP response writer to write to
     * @deprecated not need it anymore.
     */
    @Deprecated
    protected AbstractMessageWriter<SimpleHttpResponse> makeHttpResponseWriter(final SessionOutputBuffer outputBuffer) {
        return new SimpleHttpResponseWriter();
    }

}
