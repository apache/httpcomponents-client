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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.apache.hc.client5.http.cookie.CookieStore;
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
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.URIScheme;
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
        Args.check(!containsToken(decoderMap, ContentCoding.DCB.token()),
                "The dcb content coding is managed by Compression Dictionary Transport");
        Args.check(!containsToken(decoderMap, ContentCoding.DCZ.token()),
                "The dcz content coding is managed by Compression Dictionary Transport");

        final RegistryBuilder<UnaryOperator<AsyncDataConsumer>> rb = RegistryBuilder.create();
        decoderMap.forEach(rb::register);

        final List<String> tokens = new ArrayList<>(decoderMap.keySet());

        this.decoders = rb.build();
        this.acceptTokens = tokens;
        this.dictionaryAcceptTokens = createDictionaryAcceptTokens(compressionDictionaryStore);
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

        this.decoders = rb.build();
        this.acceptTokens = tokens;
        this.dictionaryAcceptTokens = createDictionaryAcceptTokens(compressionDictionaryStore);
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

        final HttpClientContext ctx = scope.clientContext;
        final boolean enabled = ctx.getRequestConfigOrDefault().isContentCompressionEnabled();
        final URI requestUri = resolveRequestUri(request);
        final Instant requestTime = Instant.now();
        final CookieStore privacyPartition = getPrivacyPartition(ctx);

        final CompressionDictionary candidate = enabled
                ? findDictionary(request, requestUri, privacyPartition)
                : null;
        final List<String> requestDictionaryAcceptTokens =
                selectDictionaryAcceptTokens(candidate);
        final CompressionDictionary dictionary = prepareDictionaryNegotiation(
                request, enabled, candidate, requestDictionaryAcceptTokens);

        chain.proceed(request, producer, scope, new AsyncExecCallback() {

            @Override
            public AsyncDataConsumer handleResponse(final HttpResponse rsp,
                                                    final EntityDetails details)
                    throws HttpException, IOException {

                if (!enabled) {
                    return cb.handleResponse(rsp, details);
                }

                final UseAsDictionary useAsDictionary =
                        parseUseAsDictionary(rsp, requestUri, privacyPartition);

                final Instant responseTime = Instant.now();
                final Instant storedAt = useAsDictionary != null ? responseTime : null;
                final Instant validUntil = storedAt != null
                        ? CompressionDictionaryFreshness.determineValidUntil(
                        request, rsp, requestTime, responseTime)
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
                                privacyPartition,
                                requestUri,
                                useAsDictionary,
                                storedAt,
                                validUntil,
                                DEFAULT_MAX_DICTIONARY_SIZE);
                    }

                    for (int i = 0; i < codecs.size(); i++) {
                        final String codec = codecs.get(i);

                        if ((ContentCoding.DCB.token().equalsIgnoreCase(codec)
                                || ContentCoding.DCZ.token().equalsIgnoreCase(codec))
                                && dictionary == null) {
                            throw new HttpException(
                                    "Dictionary Content-Encoding without negotiated dictionary: " + codec);
                        }

                        if (ContentCoding.DCB.token().equalsIgnoreCase(codec)) {
                            if (!requestDictionaryAcceptTokens.contains(ContentCoding.DCB.token())) {
                                throw new HttpException("Unsupported Content-Encoding: " + codec);
                            }
                            downstream = new InflatingDictionaryBrotliDataConsumer(
                                    downstream, dictionary);
                        } else if (ContentCoding.DCZ.token().equalsIgnoreCase(codec)) {
                            if (!requestDictionaryAcceptTokens.contains(ContentCoding.DCZ.token())) {
                                throw new HttpException("Unsupported Content-Encoding: " + codec);
                            }
                            downstream = new InflatingDictionaryZstdDataConsumer(
                                    downstream, dictionary);
                        } else {
                            final UnaryOperator<AsyncDataConsumer> op = decoders.lookup(codec);
                            if (op != null) {
                                downstream = op.apply(downstream);
                            } else {
                                throw new HttpException("Unsupported Content-Encoding: " + codec);
                            }
                        }
                    }
                    return downstream;
                }

                AsyncDataConsumer downstream = cb.handleResponse(rsp, details);
                if (downstream != null && useAsDictionary != null && validUntil != null) {
                    downstream = new DictionaryCapturingAsyncDataConsumer(
                            downstream,
                            compressionDictionaryStore,
                            privacyPartition,
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

    private static List<String> createDictionaryAcceptTokens(
            final CompressionDictionaryStore compressionDictionaryStore) {
        final List<String> tokens = new ArrayList<>();
        if (compressionDictionaryStore != null && Brotli4jRuntime.available()) {
            tokens.add(ContentCoding.DCB.token());
        }
        if (compressionDictionaryStore != null && ZstdRuntime.available()) {
            tokens.add(ContentCoding.DCZ.token());
        }
        return tokens;
    }

    private List<String> selectDictionaryAcceptTokens(
            final CompressionDictionary dictionary) {
        if (dictionary == null
                || !dictionaryAcceptTokens.contains(ContentCoding.DCZ.token())
                || isDczDictionaryCompatible(dictionary)) {
            return dictionaryAcceptTokens;
        }
        final List<String> tokens = new ArrayList<>(dictionaryAcceptTokens);
        tokens.remove(ContentCoding.DCZ.token());
        return tokens;
    }

    private static boolean isDczDictionaryCompatible(
            final CompressionDictionary dictionary) {
        return dictionary.getContentLength() >= 8
                && !dictionary.contentStartsWith(
                        (byte) 0x37, (byte) 0xa4, (byte) 0x30, (byte) 0xec);
    }

    private CompressionDictionary prepareDictionaryNegotiation(
            final HttpRequest request,
            final boolean enabled,
            final CompressionDictionary candidate,
            final List<String> requestDictionaryAcceptTokens) {
        if (!enabled) {
            return null;
        }
        // A caller-provided Accept-Encoding is honoured verbatim: the client neither rewrites it
        // nor negotiates a dictionary on top of an explicit choice.
        if (request.containsHeader(HttpHeaders.ACCEPT_ENCODING)) {
            return null;
        }

        if (candidate == null || requestDictionaryAcceptTokens.isEmpty()) {
            request.addHeader(MessageSupport.headerOfTokens(
                    HttpHeaders.ACCEPT_ENCODING, acceptTokens));
            return null;
        }
        final List<String> tokens = new ArrayList<>(
                acceptTokens.size() + requestDictionaryAcceptTokens.size());
        tokens.addAll(acceptTokens);
        tokens.addAll(requestDictionaryAcceptTokens);
        request.addHeader(MessageSupport.headerOfTokens(
                HttpHeaders.ACCEPT_ENCODING, tokens));

        addDictionaryHeaders(request, candidate);
        return candidate;
    }
    private static void addDictionaryHeaders(
            final HttpRequest request,
            final CompressionDictionary dictionary) {
        request.addHeader(
                CompressionDictionaryHeaderSupport.AVAILABLE_DICTIONARY,
                CompressionDictionaryHeaderSupport.formatAvailableDictionary(
                        dictionary.getSha256()));
        if (!dictionary.getId().isEmpty()) {
            request.addHeader(
                    CompressionDictionaryHeaderSupport.DICTIONARY_ID,
                    CompressionDictionaryHeaderSupport.formatDictionaryId(
                            dictionary.getId()));
        }
    }

    private CompressionDictionary findDictionary(
            final HttpRequest request,
            final URI requestUri,
            final CookieStore privacyPartition) {
        if (compressionDictionaryStore == null
                || compressionDictionaryMatcher == null
                || privacyPartition == null
                || requestUri == null
                || !URIScheme.HTTPS.same(requestUri.getScheme())
                || request.containsHeader(CompressionDictionaryHeaderSupport.AVAILABLE_DICTIONARY)
                || request.containsHeader(CompressionDictionaryHeaderSupport.DICTIONARY_ID)) {
            return null;
        }

        return compressionDictionaryMatcher.match(
                requestUri,
                null,
                compressionDictionaryStore.getByOrigin(privacyPartition, requestUri));
    }

    private UseAsDictionary parseUseAsDictionary(
            final HttpResponse response,
            final URI requestUri,
            final CookieStore privacyPartition) {
        if (compressionDictionaryStore == null
                || privacyPartition == null
                || requestUri == null
                || !URIScheme.HTTPS.same(requestUri.getScheme())) {
            return null;
        }

        try {
            final UseAsDictionary useAsDictionary = UseAsDictionary.parse(
                    response, CompressionDictionaryHeaderSupport.USE_AS_DICTIONARY);
            if (useAsDictionary == null
                    || !useAsDictionary.isSupported()
                    || !new DefaultCompressionDictionaryUrlPatternMatcher().isValid(
                    useAsDictionary.getMatch(), requestUri)) {
                return null;
            }
            return useAsDictionary;
        } catch (final ProtocolException | RuntimeException ex) {
            return null;
        }
    }

    private CookieStore getPrivacyPartition(final HttpClientContext context) throws HttpException {
        final CookieStore cookieStore = context.getCookieStore();
        if (cookieStore instanceof CompressionDictionaryCookieStore
                && ((CompressionDictionaryCookieStore) cookieStore)
                .isBoundTo(compressionDictionaryStore)) {
            return cookieStore;
        }
        if (compressionDictionaryStore != null) {
            throw new HttpException(
                    "Compression Dictionary Transport requires its managed cookie store");
        }
        return null;
    }

    private static boolean containsToken(
            final Map<String, ?> map,
            final String expected) {
        for (final String token : map.keySet()) {
            if (expected.equalsIgnoreCase(token)) {
                return true;
            }
        }
        return false;
    }

    private static URI resolveRequestUri(final HttpRequest request) {
        try {
            final URI requestUri = request.getUri();
            return requestUri.isAbsolute() ? requestUri : null;
        } catch (final URISyntaxException ex) {
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
