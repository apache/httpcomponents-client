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
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.HttpCacheEntrySerializer;
import org.apache.hc.client5.http.cache.HttpCacheStorageEntry;
import org.apache.hc.client5.http.cache.Resource;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.core5.annotation.Experimental;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.impl.io.AbstractMessageParser;
import org.apache.hc.core5.http.impl.io.AbstractMessageWriter;
import org.apache.hc.core5.http.impl.io.DefaultHttpResponseParser;
import org.apache.hc.core5.http.impl.io.SessionInputBufferImpl;
import org.apache.hc.core5.http.impl.io.SessionOutputBufferImpl;
import org.apache.hc.core5.http.io.SessionInputBuffer;
import org.apache.hc.core5.http.io.SessionOutputBuffer;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.BasicLineFormatter;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.apache.hc.core5.util.TimeValue;

/**
 * Cache serializer and deserializer that uses an HTTP-like format.
 *
 * Existing libraries for reading and writing HTTP are used, and metadata is encoded into HTTP
 * pseudo-headers for storage.
 */
@Experimental
public class HttpByteArrayCacheEntrySerializer implements HttpCacheEntrySerializer<byte[]> {
    public static final HttpByteArrayCacheEntrySerializer INSTANCE = new HttpByteArrayCacheEntrySerializer();

    private static final String SC_CACHE_ENTRY_PREFIX = "hc-";

    private static final String SC_HEADER_NAME_STORAGE_KEY = SC_CACHE_ENTRY_PREFIX + "sk";
    private static final String SC_HEADER_NAME_RESPONSE_DATE = SC_CACHE_ENTRY_PREFIX + "resp-date";
    private static final String SC_HEADER_NAME_REQUEST_DATE = SC_CACHE_ENTRY_PREFIX + "req-date";
    private static final String SC_HEADER_NAME_NO_CONTENT = SC_CACHE_ENTRY_PREFIX + "no-content";
    private static final String SC_HEADER_NAME_VARIANT_MAP_KEY = SC_CACHE_ENTRY_PREFIX + "varmap-key";
    private static final String SC_HEADER_NAME_VARIANT_MAP_VALUE = SC_CACHE_ENTRY_PREFIX + "varmap-val";

    private static final String SC_CACHE_ENTRY_PRESERVE_PREFIX = SC_CACHE_ENTRY_PREFIX + "esc-";

    private static final int BUFFER_SIZE = 8192;

    public HttpByteArrayCacheEntrySerializer() {
    }

    @Override
    public byte[] serialize(final HttpCacheStorageEntry httpCacheEntry) throws ResourceIOException {
        if (httpCacheEntry.getKey() == null) {
            throw new IllegalStateException("Cannot serialize cache object with null storage key");
        }
        // content doesn't need null-check because it's validated in the HttpCacheStorageEntry constructor

        // Fake HTTP request, required by response generator
        // Use request method from httpCacheEntry, but as far as I can tell it will only ever return "GET".
        final HttpRequest httpRequest = new BasicHttpRequest(httpCacheEntry.getContent().getRequestMethod(), "/");

        final CacheValidityPolicy cacheValidityPolicy = new NoAgeCacheValidityPolicy();
        final CachedHttpResponseGenerator cachedHttpResponseGenerator = new CachedHttpResponseGenerator(cacheValidityPolicy);

        final SimpleHttpResponse httpResponse = cachedHttpResponseGenerator.generateResponse(httpRequest, httpCacheEntry.getContent());

        try(final ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            escapeHeaders(httpResponse);
            addMetadataPseudoHeaders(httpResponse, httpCacheEntry);

            final byte[] bodyBytes = httpResponse.getBodyBytes();
            final int resourceLength;

            if (bodyBytes == null) {
                // This means no content, for example a 204 response
                httpResponse.addHeader(SC_HEADER_NAME_NO_CONTENT, Boolean.TRUE.toString());
                resourceLength = 0;
            } else {
                resourceLength = bodyBytes.length;
            }

            // Use the default, ASCII-only encoder for HTTP protocol and header values.
            // It's the only thing that's widely used, and it's not worth it to support anything else.
            final SessionOutputBufferImpl outputBuffer = new SessionOutputBufferImpl(BUFFER_SIZE);
            final AbstractMessageWriter<SimpleHttpResponse> httpResponseWriter = makeHttpResponseWriter(outputBuffer);
            httpResponseWriter.write(httpResponse, outputBuffer, out);
            outputBuffer.flush(out);
            final byte[] headerBytes = out.toByteArray();

            final byte[] bytes = new byte[headerBytes.length + resourceLength];
            System.arraycopy(headerBytes, 0, bytes, 0, headerBytes.length);
            if (resourceLength > 0) {
                System.arraycopy(bodyBytes, 0, bytes, headerBytes.length, resourceLength);
            }
            return bytes;
        } catch(final IOException|HttpException e) {
            throw new ResourceIOException("Exception while serializing cache entry", e);
        }
    }

    @Override
    public HttpCacheStorageEntry deserialize(final byte[] serializedObject) throws ResourceIOException {
        try (final InputStream in = makeByteArrayInputStream(serializedObject);
             final ByteArrayOutputStream bytesOut = new ByteArrayOutputStream(serializedObject.length) // this is bigger than necessary but will save us from reallocating
        ) {
            final SessionInputBufferImpl inputBuffer = new SessionInputBufferImpl(BUFFER_SIZE);
            final AbstractMessageParser<ClassicHttpResponse> responseParser = makeHttpResponseParser();
            final ClassicHttpResponse response = responseParser.parse(inputBuffer, in);

            // Extract metadata pseudo-headers
            final String storageKey = getCachePseudoHeaderAndRemove(response, SC_HEADER_NAME_STORAGE_KEY);
            final Date requestDate = getCachePseudoHeaderDateAndRemove(response, SC_HEADER_NAME_REQUEST_DATE);
            final Date responseDate = getCachePseudoHeaderDateAndRemove(response, SC_HEADER_NAME_RESPONSE_DATE);
            final boolean noBody = getCachePseudoHeaderBooleanAndRemove(response, SC_HEADER_NAME_NO_CONTENT);
            final Map<String, String> variantMap = getVariantMapPseudoHeadersAndRemove(response);
            unescapeHeaders(response);

            final Resource resource;
            if (noBody) {
                // This means no content, for example a 204 response
                resource = null;
            } else {
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

            return new HttpCacheStorageEntry(storageKey, httpCacheEntry);
        } catch (final IOException|HttpException e) {
            throw new ResourceIOException("Error deserializing cache entry", e);
        }
    }

    /**
     * Helper method to make a new HTTP response writer.
     * <p>
     * Useful to override for testing.
     *
     * @param outputBuffer Output buffer to write to
     * @return HTTP response writer to write to
     */
    protected AbstractMessageWriter<SimpleHttpResponse> makeHttpResponseWriter(final SessionOutputBuffer outputBuffer) {
        return new SimpleHttpResponseWriter();
    }

    /**
     * Helper method to make a new ByteArrayInputStream.
     * <p>
     * Useful to override for testing.
     *
     * @param bytes Bytes to read from the stream
     * @return Stream to read the bytes from
     */
    protected InputStream makeByteArrayInputStream(final byte[] bytes) {
        return new ByteArrayInputStream(bytes);
    }

    /**
     * Helper method to make a new HTTP Response parser.
     * <p>
     * Useful to override for testing.
     *
     * @return HTTP response parser
     */
    protected AbstractMessageParser<ClassicHttpResponse> makeHttpResponseParser() {
        return new DefaultHttpResponseParser();
    }

    /**
     * Modify the given response to escape any header names that start with the prefix we use for our own pseudo-headers,
     * prefixing them with an escape sequence we can use to recover them later.
     *
     * @param httpResponse HTTP response object to escape headers in
     * @see #unescapeHeaders(HttpResponse) for the corresponding un-escaper.
     */
    private static void escapeHeaders(final HttpResponse httpResponse) {
        final Header[] headers = httpResponse.getHeaders();
        for (final Header header : headers) {
            if (header.getName().startsWith(SC_CACHE_ENTRY_PREFIX)) {
                httpResponse.removeHeader(header);
                httpResponse.addHeader(SC_CACHE_ENTRY_PRESERVE_PREFIX + header.getName(), header.getValue());
            }
        }
    }

    /**
     * Modify the given response to remove escaping from any header names we escaped before saving.
     *
     * @param httpResponse HTTP response object to un-escape headers in
     * @see #unescapeHeaders(HttpResponse) for the corresponding escaper
     */
    private void unescapeHeaders(final HttpResponse httpResponse) {
        final Header[] headers = httpResponse.getHeaders();
        for (final Header header : headers) {
            if (header.getName().startsWith(SC_CACHE_ENTRY_PRESERVE_PREFIX)) {
                httpResponse.removeHeader(header);
                httpResponse.addHeader(header.getName().substring(SC_CACHE_ENTRY_PRESERVE_PREFIX.length()), header.getValue());
            }
        }
    }

    /**
     * Modify the given response to add our own cache metadata as pseudo-headers.
     *
     * @param httpResponse HTTP response object to add pseudo-headers to
     */
    private void addMetadataPseudoHeaders(final HttpResponse httpResponse, final HttpCacheStorageEntry httpCacheEntry) {
        httpResponse.addHeader(SC_HEADER_NAME_STORAGE_KEY, httpCacheEntry.getKey());
        httpResponse.addHeader(SC_HEADER_NAME_RESPONSE_DATE, Long.toString(httpCacheEntry.getContent().getResponseDate().getTime()));
        httpResponse.addHeader(SC_HEADER_NAME_REQUEST_DATE, Long.toString(httpCacheEntry.getContent().getRequestDate().getTime()));

        // Encode these so map entries are stored in a pair of headers, one for key and one for value.
        // Header keys look like: {Accept-Encoding=gzip}
        // And header values like: {Accept-Encoding=gzip}https://example.com:1234/foo
        for (final Map.Entry<String, String> entry : httpCacheEntry.getContent().getVariantMap().entrySet()) {
            // Headers are ordered
            httpResponse.addHeader(SC_HEADER_NAME_VARIANT_MAP_KEY, entry.getKey());
            httpResponse.addHeader(SC_HEADER_NAME_VARIANT_MAP_VALUE, entry.getValue());
        }
    }

    /**
     * Get the string value for a single metadata pseudo-header, and remove it from the response object.
     *
     * @param response Response object to get and remove the pseudo-header from
     * @param name     Name of metadata pseudo-header
     * @return Value for metadata pseudo-header
     * @throws ResourceIOException if the given pseudo-header is not found
     */
    private static String getCachePseudoHeaderAndRemove(final HttpResponse response, final String name) throws ResourceIOException {
        final String headerValue = getOptionalCachePseudoHeaderAndRemove(response, name);
        if (headerValue == null) {
            throw new ResourceIOException("Expected cache header '" + name + "' not found");
        }
        return headerValue;
    }

    /**
     * Get the string value for a single metadata pseudo-header if it exists, and remove it from the response object.
     *
     * @param response Response object to get and remove the pseudo-header from
     * @param name     Name of metadata pseudo-header
     * @return Value for metadata pseudo-header, or null if it does not exist
     */
    private static String getOptionalCachePseudoHeaderAndRemove(final HttpResponse response, final String name) {
        final Header header = response.getFirstHeader(name);
        if (header == null) {
            return null;
        }
        response.removeHeader(header);
        return header.getValue();
    }

    /**
     * Get the date value for a single metadata pseudo-header, and remove it from the response object.
     *
     * @param response Response object to get and remove the pseudo-header from
     * @param name     Name of metadata pseudo-header
     * @return Value for metadata pseudo-header
     * @throws ResourceIOException if the given pseudo-header is not found, or contains invalid data
     */
    private static Date getCachePseudoHeaderDateAndRemove(final HttpResponse response, final String name) throws ResourceIOException{
        final String value = getCachePseudoHeaderAndRemove(response, name);
        response.removeHeaders(name);
        try {
            final long timestamp = Long.parseLong(value);
            return new Date(timestamp);
        } catch (final NumberFormatException e) {
            throw new ResourceIOException("Invalid value for header '" + name + "'", e);
        }
    }

    /**
     * Get the boolean value for a single metadata pseudo-header, and remove it from the response object.
     *
     * @param response Response object to get and remove the pseudo-header from
     * @param name     Name of metadata pseudo-header
     * @return Value for metadata pseudo-header
     */
    private static boolean getCachePseudoHeaderBooleanAndRemove(final ClassicHttpResponse response, final String name) {
        // parseBoolean does not throw any exceptions, so no try/catch required.
        return Boolean.parseBoolean(getOptionalCachePseudoHeaderAndRemove(response, name));
    }

    /**
     * Get the variant map metadata pseudo-header, and remove it from the response object.
     *
     * @param response Response object to get and remove the pseudo-header from
     * @return Extracted variant map
     * @throws ResourceIOException if the given pseudo-header is not found, or contains invalid data
     */
    private static Map<String, String> getVariantMapPseudoHeadersAndRemove(final HttpResponse response) throws ResourceIOException {
        final Header[] headers = response.getHeaders();
        final Map<String, String> variantMap = new HashMap<>(0);
        String lastKey = null;
        for (final Header header : headers) {
            if (header.getName().equals(SC_HEADER_NAME_VARIANT_MAP_KEY)) {
                lastKey = header.getValue();
                response.removeHeader(header);
            } else if (header.getName().equals(SC_HEADER_NAME_VARIANT_MAP_VALUE)) {
                if (lastKey == null) {
                    throw new ResourceIOException("Found mismatched variant map key/value headers");
                }
                variantMap.put(lastKey, header.getValue());
                lastKey = null;
                response.removeHeader(header);
            }
        }

        if (lastKey != null) {
            throw new ResourceIOException("Found mismatched variant map key/value headers");
        }

        return variantMap;
    }

    /**
     * Copy bytes from the given source buffer and input stream to the given output stream until end-of-file is reached.
     *
     * @param srcBuf Buffered input source
     * @param src Unbuffered input source
     * @param dest Output destination
     * @throws IOException if an I/O error occurs
     */
    private static void copyBytes(final SessionInputBuffer srcBuf, final InputStream src, final OutputStream dest) throws IOException {
        final byte[] buf = new byte[BUFFER_SIZE];
        int lastBytesRead;
        while ((lastBytesRead = srcBuf.read(buf, src)) != -1) {
            dest.write(buf, 0, lastBytesRead);
        }
    }

    /**
     * Writer for SimpleHttpResponse.
     *
     * Copied from DefaultHttpResponseWriter, but wrapping a SimpleHttpResponse instead of a ClassicHttpResponse
     */
    // Seems like the DefaultHttpResponseWriter should be able to do this, but it doesn't seem to be able to
    private class SimpleHttpResponseWriter extends AbstractMessageWriter<SimpleHttpResponse> {

        public SimpleHttpResponseWriter() {
            super(BasicLineFormatter.INSTANCE);
        }

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
     * Cache validity policy that always returns an age of {@link TimeValue#ZERO_MILLISECONDS}.
     *
     * This prevents the Age header from being written to the cache (it does not make sense to cache it),
     * and is the only thing the policy is used for in this case.
     */
    private static class NoAgeCacheValidityPolicy extends CacheValidityPolicy {
        @Override
        public TimeValue getCurrentAge(final HttpCacheEntry entry, final Date now) {
            return TimeValue.ZERO_MILLISECONDS;
        }
    }
}
