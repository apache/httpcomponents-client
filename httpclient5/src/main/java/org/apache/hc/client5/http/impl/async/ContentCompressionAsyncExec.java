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
package org.apache.hc.client5.http.impl.async;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.UnaryOperator;

import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.apache.hc.client5.http.async.methods.InflatingAsyncDataConsumer;
import org.apache.hc.client5.http.async.methods.InflatingBrotliDataConsumer;
import org.apache.hc.client5.http.async.methods.InflatingDictionaryBrotliDataConsumer;
import org.apache.hc.client5.http.async.methods.InflatingDictionaryZstdDataConsumer;
import org.apache.hc.client5.http.async.methods.InflatingGzipDataConsumer;
import org.apache.hc.client5.http.async.methods.InflatingZstdDataConsumer;
import org.apache.hc.client5.http.entity.compress.CompressionDictionary;
import org.apache.hc.client5.http.entity.compress.CompressionDictionaryStore;
import org.apache.hc.client5.http.entity.compress.ContentCoding;
import org.apache.hc.client5.http.impl.Brotli4jRuntime;
import org.apache.hc.client5.http.impl.ContentCodingSupport;
import org.apache.hc.client5.http.impl.ZstdRuntime;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.util.Args;

@Contract(threading = ThreadingBehavior.STATELESS)
@Internal
public final class ContentCompressionAsyncExec implements AsyncExecChainHandler {

    private static final int DEFAULT_MAX_DICTIONARY_SIZE = 16 * 1024 * 1024;

    private final Lookup<UnaryOperator<AsyncDataConsumer>> decoders;
    private final List<String> acceptTokens;
    private final List<String> dictionaryAcceptTokens;
    private final int maxCodecListLen;
    private final CompressionDictionaryStore compressionDictionaryStore;
    private final CompressionDictionaryMatcher compressionDictionaryMatcher;

    public ContentCompressionAsyncExec(
            final LinkedHashMap<String, UnaryOperator<AsyncDataConsumer>> decoderMap,
            final int maxCodecListLen,
            final CompressionDictionaryStore compressionDictionaryStore) {
        Args.notEmpty(decoderMap, "Decoder map");

        final RegistryBuilder<UnaryOperator<AsyncDataConsumer>> rb = RegistryBuilder.create();
        decoderMap.forEach(rb::register);

        final List<String> tokens = new ArrayList<>();
        decoderMap.keySet().forEach(token -> {
            if (!ContentCoding.DCB.token().equalsIgnoreCase(token)
                    && !ContentCoding.DCZ.token().equalsIgnoreCase(token)) {
                tokens.add(token);
            }
        });

        final List<String> dictionaryTokens = new ArrayList<>();
        if (compressionDictionaryStore != null) {
            if (decoderMap.containsKey(ContentCoding.DCB.token())) {
                dictionaryTokens.add(ContentCoding.DCB.token());
            } else if (Brotli4jRuntime.available()) {
                rb.register(ContentCoding.DCB.token(),
                        d -> new InflatingDictionaryBrotliDataConsumer(d, compressionDictionaryStore));
                dictionaryTokens.add(ContentCoding.DCB.token());
            }

            if (decoderMap.containsKey(ContentCoding.DCZ.token())) {
                dictionaryTokens.add(ContentCoding.DCZ.token());
            } else if (ZstdRuntime.available()) {
                rb.register(ContentCoding.DCZ.token(),
                        d -> new InflatingDictionaryZstdDataConsumer(d, compressionDictionaryStore));
                dictionaryTokens.add(ContentCoding.DCZ.token());
            }
        }

        this.decoders = rb.build();
        this.acceptTokens = tokens;
        this.dictionaryAcceptTokens = dictionaryTokens;
        this.maxCodecListLen = maxCodecListLen;
        this.compressionDictionaryStore = compressionDictionaryStore;
        this.compressionDictionaryMatcher = compressionDictionaryStore != null
                ? new DefaultCompressionDictionaryMatcher()
                : null;
    }

    public ContentCompressionAsyncExec(
            final LinkedHashMap<String, UnaryOperator<AsyncDataConsumer>> decoderMap,
            final int maxCodecListLen) {
        this(decoderMap, maxCodecListLen, null);
    }

    public ContentCompressionAsyncExec(
            final LinkedHashMap<String, UnaryOperator<AsyncDataConsumer>> decoderMap,
            final CompressionDictionaryStore compressionDictionaryStore) {
        this(decoderMap, ContentCodingSupport.MAX_CODEC_LIST_LEN, compressionDictionaryStore);
    }

    public ContentCompressionAsyncExec(
            final LinkedHashMap<String, UnaryOperator<AsyncDataConsumer>> decoderMap) {
        this(decoderMap, ContentCodingSupport.MAX_CODEC_LIST_LEN);
    }

    /**
     * Default: DEFLATE + GZIP (plus <code>x-gzip</code> alias).
     */
    public ContentCompressionAsyncExec(
            final int maxCodecListLen,
            final CompressionDictionaryStore compressionDictionaryStore) {
        final LinkedHashMap<String, UnaryOperator<AsyncDataConsumer>> map = new LinkedHashMap<>();
        map.put(ContentCoding.DEFLATE.token(), d -> new InflatingAsyncDataConsumer(d, null));
        map.put(ContentCoding.GZIP.token(), InflatingGzipDataConsumer::new);
        map.put(ContentCoding.X_GZIP.token(), InflatingGzipDataConsumer::new);

        final RegistryBuilder<UnaryOperator<AsyncDataConsumer>> rb =
                RegistryBuilder.<UnaryOperator<AsyncDataConsumer>>create()
                        .register(ContentCoding.GZIP.token(), InflatingGzipDataConsumer::new)
                        .register(ContentCoding.X_GZIP.token(), InflatingGzipDataConsumer::new)
                        .register(ContentCoding.DEFLATE.token(), d -> new InflatingAsyncDataConsumer(d, null));

        // Add zstd only when zstd-jni is present (no reflection needed)
        final List<String> tokens = new ArrayList<>(Arrays.asList("gzip", "x-gzip", "deflate"));
        if (ZstdRuntime.available()) {
            rb.register(ContentCoding.ZSTD.token(), InflatingZstdDataConsumer::new);
            tokens.add("zstd");
        }

        if (Brotli4jRuntime.available()) {
            rb.register(ContentCoding.BROTLI.token(), InflatingBrotliDataConsumer::new);
            tokens.add(ContentCoding.BROTLI.token());
        }

        final List<String> dictionaryTokens = new ArrayList<>();
        if (compressionDictionaryStore != null) {
            if (Brotli4jRuntime.available()) {
                rb.register(ContentCoding.DCB.token(),
                        d -> new InflatingDictionaryBrotliDataConsumer(d, compressionDictionaryStore));
                dictionaryTokens.add(ContentCoding.DCB.token());
            }

            if (ZstdRuntime.available()) {
                rb.register(ContentCoding.DCZ.token(),
                        d -> new InflatingDictionaryZstdDataConsumer(d, compressionDictionaryStore));
                dictionaryTokens.add(ContentCoding.DCZ.token());
            }
        }

        this.decoders = rb.build();
        this.acceptTokens = tokens;
        this.dictionaryAcceptTokens = dictionaryTokens;
        this.maxCodecListLen = maxCodecListLen;
        this.compressionDictionaryStore = compressionDictionaryStore;
        this.compressionDictionaryMatcher = compressionDictionaryStore != null
                ? new DefaultCompressionDictionaryMatcher()
                : null;
    }

    public ContentCompressionAsyncExec(final int maxCodecListLen) {
        this(maxCodecListLen, null);
    }

    public ContentCompressionAsyncExec(
            final CompressionDictionaryStore compressionDictionaryStore) {
        this(ContentCodingSupport.MAX_CODEC_LIST_LEN, compressionDictionaryStore);
    }

    public ContentCompressionAsyncExec() {
        this(ContentCodingSupport.MAX_CODEC_LIST_LEN);
    }

    @Override
    public void execute(
            final HttpRequest request,
            final AsyncEntityProducer producer,
            final AsyncExecChain.Scope scope,
            final AsyncExecChain chain,
            final AsyncExecCallback cb) throws IOException, HttpException {

        final HttpClientContext ctx = scope != null ? scope.clientContext : HttpClientContext.create();
        final boolean enabled = ctx.getRequestConfigOrDefault().isContentCompressionEnabled();
        final URI requestUri = resolveRequestUri(request, scope);

        final CompressionDictionary dictionary = enabled
                ? findDictionary(request, requestUri)
                : null;

        if (dictionary != null) {
            request.addHeader(
                    CompressionDictionaryHeaderSupport.AVAILABLE_DICTIONARY,
                    CompressionDictionaryHeaderSupport.formatAvailableDictionary(dictionary.getSha256()));

            if (!dictionary.getId().isEmpty()) {
                request.addHeader(
                        CompressionDictionaryHeaderSupport.DICTIONARY_ID,
                        CompressionDictionaryHeaderSupport.formatDictionaryId(dictionary.getId()));
            }
        }

        if (enabled && !request.containsHeader(HttpHeaders.ACCEPT_ENCODING)) {
            if (dictionary != null && !dictionaryAcceptTokens.isEmpty()) {
                final List<String> tokens = new ArrayList<>(
                        acceptTokens.size() + dictionaryAcceptTokens.size());
                tokens.addAll(acceptTokens);
                tokens.addAll(dictionaryAcceptTokens);
                request.addHeader(MessageSupport.headerOfTokens(HttpHeaders.ACCEPT_ENCODING, tokens));
            } else {
                request.addHeader(MessageSupport.headerOfTokens(HttpHeaders.ACCEPT_ENCODING, acceptTokens));
            }
        }

        chain.proceed(request, producer, scope, new AsyncExecCallback() {

            @Override
            public AsyncDataConsumer handleResponse(final HttpResponse rsp,
                                                    final EntityDetails details)
                    throws HttpException, IOException {

                if (!enabled) {
                    return cb.handleResponse(rsp, details);
                }

                final UseAsDictionary useAsDictionary =
                        parseUseAsDictionary(rsp, requestUri);

                final Instant storedAt = useAsDictionary != null
                        ? Instant.now()
                        : null;

                final Instant validUntil = storedAt != null
                        ? getValidUntil(rsp, storedAt)
                        : null;

                final List<String> codecs = ContentCodingSupport.parseContentCodecs(details);
                ContentCodingSupport.validate(codecs, maxCodecListLen);
                if (!codecs.isEmpty()) {
                    AsyncDataConsumer downstream = cb.handleResponse(rsp, wrapEntityDetails(details));
                    if (downstream == null) {
                        return null;
                    }

                    if (useAsDictionary != null && validUntil != null) {
                        downstream = new DictionaryCapturingAsyncDataConsumer(
                                downstream,
                                compressionDictionaryStore,
                                requestUri,
                                useAsDictionary,
                                storedAt,
                                validUntil,
                                DEFAULT_MAX_DICTIONARY_SIZE);
                    }

                    for (int i = codecs.size() - 1; i >= 0; i--) {
                        final String codec = codecs.get(i);

                        if ((ContentCoding.DCB.token().equalsIgnoreCase(codec)
                                || ContentCoding.DCZ.token().equalsIgnoreCase(codec))
                                && dictionary == null) {
                            throw new HttpException(
                                    "Dictionary Content-Encoding without negotiated dictionary: " + codec);
                        }

                        final UnaryOperator<AsyncDataConsumer> op = decoders.lookup(codec);
                        if (op != null) {
                            downstream = op.apply(downstream);
                        } else {
                            throw new HttpException("Unsupported Content-Encoding: " + codec);
                        }
                    }
                    return downstream;
                }

                AsyncDataConsumer downstream = cb.handleResponse(rsp, details);
                if (downstream != null && useAsDictionary != null && validUntil != null) {
                    downstream = new DictionaryCapturingAsyncDataConsumer(
                            downstream,
                            compressionDictionaryStore,
                            requestUri,
                            useAsDictionary,
                            storedAt,
                            validUntil,
                            DEFAULT_MAX_DICTIONARY_SIZE);
                }
                return downstream;
            }

            @Override
            public void handleInformationResponse(final HttpResponse r)
                    throws HttpException, IOException {
                cb.handleInformationResponse(r);
            }

            @Override
            public void completed() {
                cb.completed();
            }

            @Override
            public void failed(final Exception ex) {
                cb.failed(ex);
            }
        });
    }

    private CompressionDictionary findDictionary(
            final HttpRequest request,
            final URI requestUri) {
        if (compressionDictionaryStore == null
                || compressionDictionaryMatcher == null
                || requestUri == null
                || !"https".equalsIgnoreCase(requestUri.getScheme())
                || request.containsHeader(CompressionDictionaryHeaderSupport.AVAILABLE_DICTIONARY)
                || request.containsHeader(CompressionDictionaryHeaderSupport.DICTIONARY_ID)) {
            return null;
        }

        return compressionDictionaryMatcher.match(
                requestUri,
                compressionDictionaryStore.getByOrigin(requestUri));
    }

    private UseAsDictionary parseUseAsDictionary(
            final HttpResponse response,
            final URI requestUri) {
        if (compressionDictionaryStore == null
                || requestUri == null
                || !"https".equalsIgnoreCase(requestUri.getScheme())) {
            return null;
        }

        final Header header =
                response.getFirstHeader(CompressionDictionaryHeaderSupport.USE_AS_DICTIONARY);
        if (header == null) {
            return null;
        }

        try {
            final UseAsDictionary useAsDictionary = UseAsDictionary.parse(header.getValue());
            return useAsDictionary.isSupported() ? useAsDictionary : null;
        } catch (final ParseException ex) {
            return null;
        }
    }

    private static Instant getValidUntil(
            final HttpResponse response,
            final Instant storedAt) {
        long maxAge = -1;

        for (final Header header : response.getHeaders(HttpHeaders.CACHE_CONTROL)) {
            final String[] directives = header.getValue().split(",");
            for (final String value : directives) {
                final String directive = value.trim();
                final String lowerCase = directive.toLowerCase(Locale.ROOT);

                if ("no-store".equals(lowerCase) || "no-cache".equals(lowerCase)) {
                    return null;
                }

                if (lowerCase.startsWith("max-age=")) {
                    final String maxAgeValue = unquote(
                            directive.substring(directive.indexOf('=') + 1).trim());
                    try {
                        maxAge = Long.parseLong(maxAgeValue);
                    } catch (final NumberFormatException ex) {
                        return null;
                    }
                }
            }
        }

        if (maxAge <= 0) {
            return null;
        }

        long age = 0;
        final Header ageHeader = response.getFirstHeader("Age");
        if (ageHeader != null) {
            try {
                age = Long.parseLong(ageHeader.getValue());
            } catch (final NumberFormatException ex) {
                return null;
            }
        }

        final long remaining = maxAge - Math.max(0, age);
        if (remaining <= 0) {
            return null;
        }

        try {
            return storedAt.plusSeconds(remaining);
        } catch (final DateTimeException | ArithmeticException ex) {
            return null;
        }
    }

    private static String unquote(final String value) {
        if (value.length() > 1
                && value.charAt(0) == '"'
                && value.charAt(value.length() - 1) == '"') {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static URI resolveRequestUri(
            final HttpRequest request,
            final AsyncExecChain.Scope scope) {
        try {
            if (scope != null) {
                final URI originalUri = scope.originalRequest.getUri();
                if (originalUri.isAbsolute()) {
                    return originalUri;
                }
            }

            final URI requestUri = request.getUri();
            if (requestUri.isAbsolute()) {
                return requestUri;
            }

            if (scope != null) {
                final URI baseUri = URI.create(scope.route.getTargetHost().toURI() + "/");
                return baseUri.resolve(requestUri);
            }

            return null;
        } catch (final URISyntaxException | IllegalArgumentException ex) {
            return null;
        }
    }

    private static EntityDetails wrapEntityDetails(final EntityDetails original) {
        return new EntityDetails() {
            @Override
            public long getContentLength() {
                return -1;
            }

            @Override
            public String getContentType() {
                return original.getContentType();
            }

            @Override
            public String getContentEncoding() {
                return null;
            }

            @Override
            public boolean isChunked() {
                return true;
            }

            @Override
            public Set<String> getTrailerNames() {
                return original.getTrailerNames();
            }
        };
    }
}