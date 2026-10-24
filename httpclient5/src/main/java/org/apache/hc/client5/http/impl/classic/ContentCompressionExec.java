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

package org.apache.hc.client5.http.impl.classic;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecChainHandler;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.entity.compress.CompressionDictionary;
import org.apache.hc.client5.http.entity.compress.CompressionDictionaryStore;
import org.apache.hc.client5.http.entity.compress.ContentCodecRegistry;
import org.apache.hc.client5.http.entity.compress.ContentCoding;
import org.apache.hc.client5.http.entity.compress.DecompressingEntity;
import org.apache.hc.client5.http.impl.Brotli4jRuntime;
import org.apache.hc.client5.http.impl.CompressionDictionaryCookieStore;
import org.apache.hc.client5.http.impl.CompressionDictionaryFreshness;
import org.apache.hc.client5.http.impl.CompressionDictionaryHeaderSupport;
import org.apache.hc.client5.http.impl.CompressionDictionaryMatcher;
import org.apache.hc.client5.http.impl.ContentCodingSupport;
import org.apache.hc.client5.http.impl.DefaultCompressionDictionaryMatcher;
import org.apache.hc.client5.http.impl.DefaultCompressionDictionaryUrlPatternMatcher;
import org.apache.hc.client5.http.impl.UseAsDictionary;
import org.apache.hc.client5.http.impl.ZstdRuntime;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.util.Args;

/**
 * Request execution handler in the classic request execution chain
 * that is responsible for automatic response content decompression.
 * <p>
 * Further responsibilities such as communication with the opposite
 * endpoint is delegated to the next executor in the request execution
 * chain.
 * </p>
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.STATELESS)
@Internal
public final class ContentCompressionExec implements ExecChainHandler {

    private static final int DEFAULT_MAX_DICTIONARY_SIZE = 16 * 1024 * 1024;

    private final List<String> acceptTokens;
    private final Lookup<UnaryOperator<HttpEntity>> decoderRegistry;
    private final List<String> dictionaryAcceptTokens;
    private final int maxCodecListLen;
    private final CompressionDictionaryStore compressionDictionaryStore;
    private final CompressionDictionaryMatcher compressionDictionaryMatcher;

    public ContentCompressionExec(
            final List<String> acceptEncoding,
            final Lookup<UnaryOperator<HttpEntity>> decoderRegistry,
            final int maxCodecListLen,
            final CompressionDictionaryStore compressionDictionaryStore) {
        this.acceptTokens = new ArrayList<>(Args.notEmpty(acceptEncoding, "Encoding list"));
        Args.check(!containsToken(this.acceptTokens, ContentCoding.DCB.token()),
                "The dcb content coding is managed by Compression Dictionary Transport");
        Args.check(!containsToken(this.acceptTokens, ContentCoding.DCZ.token()),
                "The dcz content coding is managed by Compression Dictionary Transport");
        this.decoderRegistry = Args.notNull(decoderRegistry, "Decoder register");
        this.dictionaryAcceptTokens = createDictionaryAcceptTokens(compressionDictionaryStore);
        this.maxCodecListLen = maxCodecListLen;
        this.compressionDictionaryStore = compressionDictionaryStore;
        this.compressionDictionaryMatcher = compressionDictionaryStore != null
                ? new DefaultCompressionDictionaryMatcher()
                : null;
    }

    public ContentCompressionExec(
            final List<String> acceptEncoding,
            final Lookup<UnaryOperator<HttpEntity>> decoderRegistry,
            final int maxCodecListLen) {
        this(acceptEncoding, decoderRegistry, maxCodecListLen, null);
    }

    public ContentCompressionExec(
            final List<String> acceptEncoding,
            final Lookup<UnaryOperator<HttpEntity>> decoderRegistry,
            final CompressionDictionaryStore compressionDictionaryStore) {
        this(acceptEncoding, decoderRegistry, ContentCodingSupport.MAX_CODEC_LIST_LEN,
                compressionDictionaryStore);
    }

    public ContentCompressionExec(
            final List<String> acceptEncoding,
            final Lookup<UnaryOperator<HttpEntity>> decoderRegistry) {
        this(acceptEncoding, decoderRegistry, ContentCodingSupport.MAX_CODEC_LIST_LEN, null);
    }

    public ContentCompressionExec(
            final int maxCodecListLen,
            final CompressionDictionaryStore compressionDictionaryStore) {
        final Map<ContentCoding, UnaryOperator<HttpEntity>> decoderMap =
                new EnumMap<>(ContentCoding.class);
        for (final ContentCoding c : ContentCoding.values()) {
            final UnaryOperator<HttpEntity> d = ContentCodecRegistry.decoder(c);
            if (d != null) {
                decoderMap.put(c, d);
            }
        }

        final RegistryBuilder<UnaryOperator<HttpEntity>> builder = RegistryBuilder.create();
        final List<String> acceptList = new ArrayList<>(decoderMap.size() + 1);
        decoderMap.forEach((coding, decoder) -> {
            acceptList.add(coding.token());
            builder.register(coding.token(), decoder);
        });
        /* x-gzip alias */
        if (decoderMap.containsKey(ContentCoding.GZIP)) {
            acceptList.add(ContentCoding.X_GZIP.token());
            builder.register(ContentCoding.X_GZIP.token(), decoderMap.get(ContentCoding.GZIP));
        }
        this.acceptTokens = acceptList;
        this.decoderRegistry = builder.build();
        this.dictionaryAcceptTokens = createDictionaryAcceptTokens(compressionDictionaryStore);
        this.maxCodecListLen = maxCodecListLen;
        this.compressionDictionaryStore = compressionDictionaryStore;
        this.compressionDictionaryMatcher = compressionDictionaryStore != null
                ? new DefaultCompressionDictionaryMatcher()
                : null;
    }

    public ContentCompressionExec(final int maxCodecListLen) {
        this(maxCodecListLen, null);
    }

    public ContentCompressionExec(final CompressionDictionaryStore compressionDictionaryStore) {
        this(ContentCodingSupport.MAX_CODEC_LIST_LEN, compressionDictionaryStore);
    }

    public ContentCompressionExec() {
        this(ContentCodingSupport.MAX_CODEC_LIST_LEN);
    }

    @Override
    public ClassicHttpResponse execute(
            final ClassicHttpRequest request,
            final ExecChain.Scope scope,
            final ExecChain chain) throws IOException, HttpException {
        Args.notNull(request, "HTTP request");
        Args.notNull(scope, "Scope");

        final HttpClientContext clientContext = scope.clientContext;
        final RequestConfig requestConfig = clientContext.getRequestConfigOrDefault();
        final boolean enabled = requestConfig.isContentCompressionEnabled();
        final URI requestUri = resolveRequestUri(request);
        final Instant requestTime = Instant.now();
        final CookieStore privacyPartition = getPrivacyPartition(clientContext);

        final CompressionDictionary candidate = enabled
                ? findDictionary(request, requestUri, privacyPartition)
                : null;
        final List<String> requestDictionaryAcceptTokens =
                selectDictionaryAcceptTokens(candidate);
        final CompressionDictionary dictionary = prepareDictionaryNegotiation(
                request, enabled, candidate, requestDictionaryAcceptTokens);

        final ClassicHttpResponse response = chain.proceed(request, scope);
        try {
            final HttpEntity entity = response.getEntity();
            if (enabled && entity != null) {
                final UseAsDictionary useAsDictionary =
                        parseUseAsDictionary(response, requestUri, privacyPartition);
                final Instant responseTime = Instant.now();
                final Instant storedAt = useAsDictionary != null ? responseTime : null;
                final Instant validUntil = storedAt != null
                        ? CompressionDictionaryFreshness.determineValidUntil(
                                request, response, requestTime, responseTime)
                        : null;

                // Preserve the historical classic behaviour for explicitly zero-length entities.
                if (entity.getContentLength() != 0) {
                    final List<String> codecs = ContentCodingSupport.parseContentCodecs(entity);
                    ContentCodingSupport.validate(codecs, maxCodecListLen);
                    if (!codecs.isEmpty()) {
                        for (int i = codecs.size() - 1; i >= 0; i--) {
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
                                response.setEntity(new DecompressingEntity(
                                        response.getEntity(),
                                        input -> new DictionaryBrotliInputStream(input, dictionary)));
                            } else if (ContentCoding.DCZ.token().equalsIgnoreCase(codec)) {
                                if (!requestDictionaryAcceptTokens.contains(ContentCoding.DCZ.token())) {
                                    throw new HttpException("Unsupported Content-Encoding: " + codec);
                                }
                                response.setEntity(new DecompressingEntity(
                                        response.getEntity(),
                                        input -> new DictionaryZstdInputStream(input, dictionary)));
                            } else {
                                final UnaryOperator<HttpEntity> decoder = decoderRegistry.lookup(codec);
                                if (decoder != null) {
                                    response.setEntity(decoder.apply(response.getEntity()));
                                } else {
                                    throw new HttpException("Unsupported Content-Encoding: " + codec);
                                }
                            }
                        }
                    }
                }

                if (useAsDictionary != null && validUntil != null) {
                    response.setEntity(new DictionaryCapturingEntity(
                            response.getEntity(),
                            compressionDictionaryStore,
                            privacyPartition,
                            requestUri,
                            useAsDictionary,
                            storedAt,
                            validUntil,
                            DEFAULT_MAX_DICTIONARY_SIZE));
                }
            }
            return response;
        } catch (final RuntimeException | HttpException ex) {
            response.close();
            throw ex;
        }
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

    private List<String> selectDictionaryAcceptTokens(final CompressionDictionary dictionary) {
        if (dictionary == null
                || !dictionaryAcceptTokens.contains(ContentCoding.DCZ.token())
                || isDczDictionaryCompatible(dictionary)) {
            return dictionaryAcceptTokens;
        }
        final List<String> tokens = new ArrayList<>(dictionaryAcceptTokens);
        tokens.remove(ContentCoding.DCZ.token());
        return tokens;
    }

    private static boolean isDczDictionaryCompatible(final CompressionDictionary dictionary) {
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
        // Honour a caller-provided Accept-Encoding verbatim; do not negotiate a dictionary on top.
        if (request.containsHeader(HttpHeaders.ACCEPT_ENCODING)) {
            return null;
        }

        if (candidate == null || requestDictionaryAcceptTokens.isEmpty()) {
            request.addHeader(MessageSupport.headerOfTokens(HttpHeaders.ACCEPT_ENCODING, acceptTokens));
            return null;
        }
        final List<String> tokens = new ArrayList<>(
                acceptTokens.size() + requestDictionaryAcceptTokens.size());
        tokens.addAll(acceptTokens);
        tokens.addAll(requestDictionaryAcceptTokens);
        request.addHeader(MessageSupport.headerOfTokens(HttpHeaders.ACCEPT_ENCODING, tokens));
        addDictionaryHeaders(request, candidate);
        return candidate;
    }

    private static void addDictionaryHeaders(
            final HttpRequest request,
            final CompressionDictionary dictionary) {
        request.addHeader(
                CompressionDictionaryHeaderSupport.AVAILABLE_DICTIONARY,
                CompressionDictionaryHeaderSupport.formatAvailableDictionary(dictionary.getSha256()));
        if (!dictionary.getId().isEmpty()) {
            request.addHeader(
                    CompressionDictionaryHeaderSupport.DICTIONARY_ID,
                    CompressionDictionaryHeaderSupport.formatDictionaryId(dictionary.getId()));
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
            final Collection<String> tokens,
            final String expected) {
        for (final String token : tokens) {
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
}
