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
import java.time.Instant;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

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
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.impl.io.AbstractMessageParser;
import org.apache.hc.core5.http.impl.io.AbstractMessageWriter;
import org.apache.hc.core5.http.impl.io.SessionInputBufferImpl;
import org.apache.hc.core5.http.impl.io.SessionOutputBufferImpl;
import org.apache.hc.core5.http.io.SessionOutputBuffer;
import org.apache.hc.core5.http.message.BasicLineFormatter;
import org.apache.hc.core5.http.message.BasicLineParser;
import org.apache.hc.core5.http.message.HeaderGroup;
import org.apache.hc.core5.http.message.LineFormatter;
import org.apache.hc.core5.http.message.LineParser;
import org.apache.hc.core5.http.message.RequestLine;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class implements the {@link HttpCacheEntrySerializer} interface, which defines the contract for HTTP cache
 * entry serialization and deserialization. It also includes a default buffer size of 8192 bytes, which can be
 * overridden by specifying a different buffer size in the constructor.
 *
 * @since 5.4
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public class HttpByteArrayCacheEntrySerializer implements HttpCacheEntrySerializer<byte[]> {

    private static final Logger LOG = LoggerFactory.getLogger(HttpByteArrayCacheEntrySerializer.class);

    /**
     * The default buffer size used for I/O operations, set to 8192 bytes.
     */
    private static final int DEFAULT_BUFFER_SIZE = 8192;

    static final String HC_CACHE_VERSION = "1.0";
    static final String HC_CACHE_VERSION_LINE = "HttpClient CacheEntry " + HC_CACHE_VERSION;

    static final String HC_CACHE_KEY = "HC-Key";
    static final String HC_CACHE_LENGTH = "HC-Resource-Length";
    static final String HC_REQUEST_INSTANT = "HC-Request-Instant";
    static final String HC_RESPONSE_INSTANT = "HC-Response-Instant";
    static final String HC_VARIANT = "HC-Variant";

    /**
     * Singleton instance of this class.
     */
    public static final HttpByteArrayCacheEntrySerializer INSTANCE = new HttpByteArrayCacheEntrySerializer();

    private final LineParser lineParser;
    private final LineFormatter lineFormatter;
    private final int bufferSize;

    /**
     * Constructs a HttpByteArrayCacheEntrySerializer with the specified buffer size.
     *
     * @param bufferSize the buffer size to use for serialization and deserialization.
     */
    public HttpByteArrayCacheEntrySerializer(final int bufferSize) {
        this.lineParser = BasicLineParser.INSTANCE;
        this.lineFormatter = BasicLineFormatter.INSTANCE;
        this.bufferSize = bufferSize > 0 ? bufferSize : DEFAULT_BUFFER_SIZE;
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
     * @param storageEntry the HttpCacheStorageEntry to serialize.
     * @return the byte array containing the serialized HttpCacheStorageEntry.
     * @throws ResourceIOException if there is an error during serialization.
     */
    @Override
    public byte[] serialize(final HttpCacheStorageEntry storageEntry) throws ResourceIOException {
        final String key = storageEntry.getKey();
        final HttpCacheEntry cacheEntry = storageEntry.getContent();
        final Resource resource = cacheEntry.getResource();

        try (ByteArrayOutputStream out = new ByteArrayOutputStream((resource != null ? (int) resource.length() : 0) + DEFAULT_BUFFER_SIZE)) {
            final SessionOutputBuffer outputBuffer = new SessionOutputBufferImpl(bufferSize);
            final CharArrayBuffer line = new CharArrayBuffer(DEFAULT_BUFFER_SIZE);

            line.append(HC_CACHE_VERSION_LINE);
            outputBuffer.writeLine(line, out);

            line.clear();
            line.append(HC_CACHE_KEY);
            line.append(": ");
            line.append(key);
            outputBuffer.writeLine(line, out);

            if (resource != null) {
                line.clear();
                line.append(HC_CACHE_LENGTH);
                line.append(": ");
                line.append(asStr(resource.length()));
                outputBuffer.writeLine(line, out);
            }

            line.clear();
            line.append(HC_REQUEST_INSTANT);
            line.append(": ");
            line.append(asStr(cacheEntry.getRequestInstant()));
            outputBuffer.writeLine(line, out);

            line.clear();
            line.append(HC_RESPONSE_INSTANT);
            line.append(": ");
            line.append(asStr(cacheEntry.getResponseInstant()));
            outputBuffer.writeLine(line, out);

            for (final String variant : cacheEntry.getVariants()) {
                line.clear();
                line.append(HC_VARIANT);
                line.append(": ");
                line.append(variant);
                outputBuffer.writeLine(line, out);
            }
            line.clear();
            outputBuffer.writeLine(line, out);

            line.clear();
            final RequestLine requestLine = new RequestLine(cacheEntry.getRequestMethod(), cacheEntry.getRequestURI(), HttpVersion.HTTP_1_1);
            lineFormatter.formatRequestLine(line, requestLine);
            outputBuffer.writeLine(line, out);
            for (Iterator<Header> it = cacheEntry.requestHeaderIterator(); it.hasNext(); ) {
                line.clear();
                lineFormatter.formatHeader(line, it.next());
                outputBuffer.writeLine(line, out);
            }
            line.clear();
            outputBuffer.writeLine(line, out);

            line.clear();
            final StatusLine statusLine = new StatusLine(HttpVersion.HTTP_1_1, cacheEntry.getStatus(), "");
            lineFormatter.formatStatusLine(line, statusLine);
            outputBuffer.writeLine(line, out);
            for (Iterator<Header> it = cacheEntry.headerIterator(); it.hasNext(); ) {
                line.clear();
                lineFormatter.formatHeader(line, it.next());
                outputBuffer.writeLine(line, out);
            }
            line.clear();
            outputBuffer.writeLine(line, out);
            outputBuffer.flush(out);

            if (resource != null) {
                out.write(resource.get());
            }
            out.flush();

            final byte[] bytes = out.toByteArray();

            if (LOG.isDebugEnabled()) {
                LOG.debug("Serialized cache entry with key {} and {} bytes", key, bytes.length);
            }
            return bytes;
        } catch (final IOException ex) {
            throw new ResourceIOException("Exception while serializing cache entry", ex);
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
        try (final InputStream in = new ByteArrayInputStream(serializedObject)) {
            final SessionInputBufferImpl inputBuffer = new SessionInputBufferImpl(bufferSize);
            final CharArrayBuffer line = new CharArrayBuffer(DEFAULT_BUFFER_SIZE);
            checkReadResult(inputBuffer.readLine(line, in));
            final String versionLine = line.toString();
            if (!versionLine.equals(HC_CACHE_VERSION_LINE)) {
                throw new ResourceIOException("Unexpected cache entry version line");
            }
            String storageKey = null;
            long length = -1;
            Instant requestDate = null;
            Instant responseDate = null;
            final Set<String> variants = new HashSet<>();

            while (true) {
                line.clear();
                checkReadResult(inputBuffer.readLine(line, in));
                if (line.isEmpty()) {
                    break;
                }
                final Header header = lineParser.parseHeader(line);
                final String name = header.getName();
                final String value = header.getValue();
                if (name.equalsIgnoreCase(HC_CACHE_KEY)) {
                    storageKey = value;
                } else if (name.equalsIgnoreCase(HC_CACHE_LENGTH)) {
                    length = asLong(value);
                } else if (name.equalsIgnoreCase(HC_REQUEST_INSTANT)) {
                    requestDate = asInstant(value);
                } else if (name.equalsIgnoreCase(HC_RESPONSE_INSTANT)) {
                    responseDate = asInstant(value);
                } else if (name.equalsIgnoreCase(HC_VARIANT)) {
                    variants.add(value);
                } else {
                    throw new ResourceIOException("Unexpected header entry");
                }
            }

            if (storageKey == null || requestDate == null || responseDate == null) {
                throw new ResourceIOException("Invalid cache header format");
            }

            line.clear();
            checkReadResult(inputBuffer.readLine(line, in));
            final RequestLine requestLine = lineParser.parseRequestLine(line);
            final HeaderGroup requestHeaders = new HeaderGroup();
            while (true) {
                line.clear();
                checkReadResult(inputBuffer.readLine(line, in));
                if (line.isEmpty()) {
                    break;
                }
                requestHeaders.addHeader(lineParser.parseHeader(line));
            }
            line.clear();
            checkReadResult(inputBuffer.readLine(line, in));
            final StatusLine statusLine = lineParser.parseStatusLine(line);
            final HeaderGroup responseHeaders = new HeaderGroup();
            while (true) {
                line.clear();
                checkReadResult(inputBuffer.readLine(line, in));
                if (line.isEmpty()) {
                    break;
                }
                responseHeaders.addHeader(lineParser.parseHeader(line));
            }

            final Resource resource;
            if (length != -1) {
                int off = 0;
                int remaining = (int) length;
                final byte[] buf = new byte[remaining];
                while (remaining > 0) {
                    final int i = inputBuffer.read(buf, off, remaining, in);
                    if (i > 0) {
                        off += i;
                        remaining -= i;
                    }
                    if (i == -1) {
                        throw new ResourceIOException("Unexpected end of cache content");
                    }
                }
                resource = new HeapResource(buf);
            } else {
                resource = null;
            }
            if (inputBuffer.read(in) != -1) {
                throw new ResourceIOException("Unexpected content at the end of cache content");
            }

            final HttpCacheEntry httpCacheEntry = new HttpCacheEntry(
                    requestDate,
                    responseDate,
                    requestLine.getMethod(),
                    requestLine.getUri(),
                    requestHeaders,
                    statusLine.getStatusCode(),
                    responseHeaders,
                    resource,
                    !variants.isEmpty() ? variants : null
            );

            if (LOG.isDebugEnabled()) {
                LOG.debug("Returning deserialized cache entry with storage key '{}'", httpCacheEntry);
            }

            return new HttpCacheStorageEntry(storageKey, httpCacheEntry);
        } catch (final ResourceIOException ex) {
            throw ex;
        } catch (final ParseException ex) {
            throw new ResourceIOException("Invalid cache header format", ex);
        } catch (final IOException ex) {
            throw new ResourceIOException("I/O error deserializing cache entry", ex);
        }
    }

    private static String asStr(final long value) {
        return Long.toString(value);
    }

    private static String asStr(final Instant instant) {
        return Long.toString(instant.toEpochMilli());
    }

    private static long asLong(final String value) throws ResourceIOException {
        try {
            return Long.parseLong(value);
        } catch (final NumberFormatException ex) {
            throw new ResourceIOException("Invalid cache header format");
        }
    }

    private static Instant asInstant(final String value) throws ResourceIOException {
        return Instant.ofEpochMilli(asLong(value));
    }

    private static void checkReadResult(final int n) throws ResourceIOException {
        if (n == -1) {
            throw new ResourceIOException("Unexpected end of stream");
        }
    }

    /**
     * @return null
     * @deprecated Do not use.
     */
    @Deprecated
    protected InputStream makeByteArrayInputStream(final byte[] bytes) {
        return null;
    }

    /**
     * @return null
     * @deprecated Do not use.
     */
    @Deprecated
    protected AbstractMessageParser<ClassicHttpResponse> makeHttpResponseParser() {
        return null;
    }

    /**
     * @return null
     * @deprecated Do not use.
     */
    @Deprecated
    protected AbstractMessageWriter<SimpleHttpResponse> makeHttpResponseWriter(final SessionOutputBuffer outputBuffer) {
        return null;
    }

}
