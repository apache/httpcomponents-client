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
package org.apache.hc.client5.http.examples;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.Future;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import com.aayushatharva.brotli4j.Brotli4jLoader;
import com.aayushatharva.brotli4j.encoder.BrotliOutputStream;
import com.aayushatharva.brotli4j.encoder.Encoder;
import com.aayushatharva.brotli4j.encoder.PreparedDictionary;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.entity.compress.BasicCompressionDictionaryStore;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;

/**
 * End-to-end RFC 9842 Dictionary-Compressed Brotli ({@code dcb}) example.
 * <p>
 * A local HTTPS server first serves a resource marked with
 * {@code Use-As-Dictionary}. The asynchronous client stores that response
 * as a compression dictionary.
 * <p>
 * A subsequent matching request advertises the dictionary through
 * {@code Available-Dictionary}. The server then returns a real
 * Dictionary-Compressed Brotli response and the client transparently
 * decompresses it.
 * <p>
 * The TLS certificate embedded in this example is for localhost testing only.
 */
public final class AsyncClientServerDictionaryBrotli {

    private static final String DCB = "dcb";

    private static final byte[] DCB_MAGIC = {
            (byte) 0xff, 0x44, 0x43, 0x42
    };

    private static final byte[] DICTIONARY = (
            "{"
                    + "\"application\":\"Apache HttpClient\","
                    + "\"feature\":\"Compression Dictionary Transport\","
                    + "\"version\":1,"
                    + "\"message\":\"This document is used as a shared compression dictionary.\","
                    + "\"description\":\"Apache HttpClient supports transparent asynchronous "
                    + "HTTP content decompression using gzip, deflate, Brotli and Zstandard.\""
                    + "}"
    ).getBytes(StandardCharsets.UTF_8);

    private static final byte[] RESOURCE = (
            "{"
                    + "\"application\":\"Apache HttpClient\","
                    + "\"feature\":\"Compression Dictionary Transport\","
                    + "\"version\":2,"
                    + "\"message\":\"This document is compressed using the previous response "
                    + "as a shared compression dictionary.\","
                    + "\"description\":\"Apache HttpClient supports transparent asynchronous "
                    + "HTTP content decompression using gzip, deflate, Brotli and Zstandard.\""
                    + "}"
    ).getBytes(StandardCharsets.UTF_8);

    /*
     * Test-only localhost certificate.
     */
    private static final String CERTIFICATE =
            "-----BEGIN CERTIFICATE-----\n"
                    + "MIIDJTCCAg2gAwIBAgIUcY/j5gVTlIfg/yW+kjv4Pg7q8mYwDQYJKoZIhvcNAQEL\n"
                    + "BQAwFDESMBAGA1UEAwwJbG9jYWxob3N0MB4XDTI2MDgyMTExMTE1M1oXDTM2MDgx\n"
                    + "ODExMTE1M1owFDESMBAGA1UEAwwJbG9jYWxob3N0MIIBIjANBgkqhkiG9w0BAQEF\n"
                    + "AAOCAQ8AMIIBCgKCAQEApiAxQTbyBOrja4j73l2RqCDRODtg7+6rziV2T31UPhNF\n"
                    + "L3gcn6ApTJdp9roR89ndYHvN/OD49n3eryD5AEf/KcuMuHsFYUFodmUXhLk54ndl\n"
                    + "REw3iDl/4DTFcaRzRgAK/yVtlp6mYe6pRiEv7oR9oBXb7g3VzPvYpJ6ZenX1xuiz\n"
                    + "t5CDpZj4lmFoUHGxbcj02UOl4CCXTSqYIM6ibt9Y6EjdxxEzz9DfCkIw1z9lRpp5\n"
                    + "wkcvauZTokWQO9kmYQPsaHYwcWVYd7ahXo8d67sgDMXSiyqvUYgcog298iC8K+o0\n"
                    + "ZaAOxQqfFj+ZAamUVHwUlJPa7WLP17ev8EQDfBmi1wIDAQABo28wbTAdBgNVHQ4E\n"
                    + "FgQUESRWkI3fjUV4Yck000U1MlsPx1IwHwYDVR0jBBgwFoAUESRWkI3fjUV4Yck0\n"
                    + "00U1MlsPx1IwDwYDVR0TAQH/BAUwAwEB/zAaBgNVHREEEzARgglsb2NhbGhvc3SH\n"
                    + "BH8AAAEwDQYJKoZIhvcNAQELBQADggEBAHbtKlv5rZq3pu3yFj/apIIQyapQo2Gn\n"
                    + "8Qhw6IRFEZ+SYP/DVxYpEY8TlJy+XxSkSeDqpgGdYKG4Ljt762KF95rsxUAg+IcC\n"
                    + "6s/wDSpQ9GMTdGRGSeGd/hR1O0MuqCiIBijYK+MjutKZOOvMSsdE0wjhUCoWNIOS\n"
                    + "AlMzBJL1Np5dDs8Dqne/tNA8MZ/Dsx9gmo4Fv1JuIHWTCjG87CpZB78+G+QOhJWF\n"
                    + "6ubYTaNX/V/GW+cFX7XlF5iWy1o9TlBHMmbNos/za3FIRIEc45vrK4sM/0U1P8cJ\n"
                    + "eNR5rHEXdQX7C0AEHs2Xs29D0k7egK9AqoDWGihzTcwGItFwIf1+ZbI=\n"
                    + "-----END CERTIFICATE-----";

    private static final String PRIVATE_KEY =
            "-----BEGIN PRIVATE KEY-----\n"
                    + "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCmIDFBNvIE6uNr\n"
                    + "iPveXZGoINE4O2Dv7qvOJXZPfVQ+E0UveByfoClMl2n2uhHz2d1ge8384Pj2fd6v\n"
                    + "IPkAR/8py4y4ewVhQWh2ZReEuTnid2VETDeIOX/gNMVxpHNGAAr/JW2WnqZh7qlG\n"
                    + "IS/uhH2gFdvuDdXM+9iknpl6dfXG6LO3kIOlmPiWYWhQcbFtyPTZQ6XgIJdNKpgg\n"
                    + "zqJu31joSN3HETPP0N8KQjDXP2VGmnnCRy9q5lOiRZA72SZhA+xodjBxZVh3tqFe\n"
                    + "jx3ruyAMxdKLKq9RiByiDb3yILwr6jRloA7FCp8WP5kBqZRUfBSUk9rtYs/Xt6/w\n"
                    + "RAN8GaLXAgMBAAECggEAMRb2NxUrczSNu2shMlZoAkygRoOVY5Edh68eROL+D9HV\n"
                    + "8e8GVk0XpyBfGZ9mSq6ocihjeERqjTwon4uYyPJ9fjY+AQ2pS1Husn2w83Fgn4E0\n"
                    + "lXgIOOL03KX7ald0EM1Wcor21TlQZUQHFUgdR9gy3ylWcgP4l7gcDpknNT7CP+Jt\n"
                    + "rkNsifYFES2/lRYyFDbZBqV4Qjc2L+iik50qM+rj5B7DtjTns97+/Ck/ke1vGMyY\n"
                    + "waaqrOg0MH2O/LWXfHaqAUfEiq0AfQRqnqPKAofNbCTlFSEVKrH21bpRzF8kfNaR\n"
                    + "n62KVWSYN+7WbXAdmyoIiDw8McXW/0zFKppAoOWkAQKBgQDWwNxUyQz8XtNRoU6l\n"
                    + "aW0Z/lDxhNlDHwoRxTRKtnaLhaNJO/UoF2HGu/LDAiS29xUsyULyE3ViSVc/TgF9\n"
                    + "k74ou+KqfIOjNKN2xJ+NvDmfz4am7TmjM9LggPZii8yIMu9BXUJMOmDgkecm2Dzb\n"
                    + "EUbfTw0YcD2xpDl5jaU5V+QD1wKBgQDGCGBWV3n+ZUF2AkLc7OVek5XKA3VTZg8m\n"
                    + "CmN1qQ+ThoO94TXS+VtO1Rarhfz6Nhdggdder4VxuLgthmHtkQtox90GBE0VjJ0Z\n"
                    + "RTkGrUY6NJjpVhtsdZmB7gzrlXtmO0yp0oXdyXe6fc0jjsf5YmjYfy1/wk57wB7K\n"
                    + "jXUo6CN5AQKBgA+z2mh4qvJpHJqDaPS/WLLl3ZVLWXeG9X2HJeOwo8pf4yifsbVU\n"
                    + "wFl/tKh9p6GZP3se3D5HHfYp1q9STNmZy/W+hzxgDmAIoUs15VS/xpbg3b+m6Of+\n"
                    + "ChVQWLOr9TCgSM5Gu2pHen3xLS2x8gEyqjP528NFsb0jfPBeYw5mVs3RAoGBALlf\n"
                    + "9e5dDJGa72AsVbLA/yU9OiZUfmuHSf7uEpR9oVsTvBbuzpejXFm7FvGRB3KhV9i7\n"
                    + "MoQsAdqmc6IJ/XmJIQkArmGHfTEC47xYFD2vzeGGgu1J8Xnhy8TYtbeBwnW8ZNND\n"
                    + "gpROl4k3YeQ7L+6+tC6VPl4t4ZHuEeTB7j5Qr4QBAoGBAKpm9gLtvNYiCRvQjE2x\n"
                    + "vspx6yBa4z9E9Zzd98jXwIgWmbeN2oLuWXbHej4aBNCTlcoJhOof5wOlEUQhyjf9\n"
                    + "584Yj4SGVyXFWk3LOBJvr4iHfnm8uNxZxCZBqIwAZsK4e/02ExuT7Qpvh3kkn435\n"
                    + "wBYccyXfapxyL80kzfYp7bqb\n"
                    + "-----END PRIVATE KEY-----";

    static {
        Brotli4jLoader.ensureAvailability();
    }

    public static void main(final String[] args) throws Exception {
        final Certificate certificate = loadCertificate();
        final SSLContext serverSslContext =
                createServerSslContext(certificate, loadPrivateKey());
        final SSLContext clientSslContext =
                createClientSslContext(certificate);

        final byte[] dictionaryHash =
                MessageDigest.getInstance("SHA-256").digest(DICTIONARY);

        final HttpServer server = ServerBootstrap.bootstrap()
                .setLocalAddress(InetAddress.getLoopbackAddress())
                .setListenerPort(0)
                .setCanonicalHostName("localhost")
                .setSslContext(serverSslContext)
                .register("/dictionary", new DictionaryHandler())
                .register("/resource", new ResourceHandler(dictionaryHash))
                .create();

        server.start();

        final int port = server.getLocalPort();

        final URI dictionaryUri =
                URI.create("https://localhost:" + port + "/dictionary");

        final URI resourceUri =
                URI.create("https://localhost:" + port + "/resource");

        final BasicCompressionDictionaryStore dictionaryStore =
                new BasicCompressionDictionaryStore();

        final PoolingAsyncClientConnectionManager connectionManager =
                PoolingAsyncClientConnectionManagerBuilder.create()
                        .setTlsStrategy(new DefaultClientTlsStrategy(clientSslContext))
                        .build();

        try (final CloseableHttpAsyncClient client = HttpAsyncClients.custom()
                .setConnectionManager(connectionManager)
                .setCompressionDictionaryStore(dictionaryStore)
                .build()) {

            client.start();

            System.out.println("Fetching dictionary:");
            System.out.println("  " + dictionaryUri);

            final Message<HttpResponse, byte[]> dictionaryResponse =
                    execute(client, dictionaryUri);

            final HttpResponse dictionaryHead =
                    dictionaryResponse.getHead();

            final Header useAsDictionary =
                    dictionaryHead.getFirstHeader("Use-As-Dictionary");

            System.out.println("Status             : "
                    + dictionaryHead.getCode());

            System.out.println("Use-As-Dictionary  : "
                    + (useAsDictionary != null
                    ? useAsDictionary.getValue()
                    : "(none)"));

            System.out.println("Dictionary bytes   : "
                    + dictionaryResponse.getBody().length);

            System.out.println("Stored dictionaries: "
                    + dictionaryStore.getByOrigin(dictionaryUri).size());

            System.out.println();
            System.out.println("Fetching DCB resource:");
            System.out.println("  " + resourceUri);

            final Message<HttpResponse, byte[]> resourceResponse =
                    execute(client, resourceUri);

            final HttpResponse resourceHead =
                    resourceResponse.getHead();

            final Header contentEncoding =
                    resourceHead.getFirstHeader(HttpHeaders.CONTENT_ENCODING);

            final byte[] decoded = resourceResponse.getBody();

            System.out.println("Status             : "
                    + resourceHead.getCode());

            System.out.println("Content-Encoding   : "
                    + (contentEncoding != null
                    ? contentEncoding.getValue()
                    : "(none)"));

            System.out.println("Decoded bytes      : " + decoded.length);

            System.out.println("Response (plain)   : "
                    + new String(decoded, StandardCharsets.UTF_8));

            if (!MessageDigest.isEqual(RESOURCE, decoded)) {
                throw new IllegalStateException(
                        "Decoded DCB response does not match original resource");
            }

            if (contentEncoding == null
                    || !DCB.equalsIgnoreCase(contentEncoding.getValue())) {
                throw new IllegalStateException(
                        "Server did not return a DCB response");
            }

            System.out.println();
            System.out.println("DCB round-trip successful.");
        } finally {
            server.close(CloseMode.GRACEFUL);
        }
    }

    private static Message<HttpResponse, byte[]> execute(
            final CloseableHttpAsyncClient client,
            final URI uri) throws Exception {

        final SimpleHttpRequest request =
                SimpleRequestBuilder.get(uri).build();

        final Future<Message<HttpResponse, byte[]>> future =
                client.execute(
                        new BasicRequestProducer(request, null),
                        new BasicResponseConsumer<>(
                                new BasicAsyncEntityConsumer()),
                        null);

        return future.get();
    }

    private static final class DictionaryHandler
            implements HttpRequestHandler {

        @Override
        public void handle(
                final ClassicHttpRequest request,
                final ClassicHttpResponse response,
                final HttpContext context) {

            response.setCode(HttpStatus.SC_OK);

            response.addHeader(
                    HttpHeaders.CACHE_CONTROL,
                    "max-age=3600");

            response.addHeader(
                    "Use-As-Dictionary",
                    "match=\"/resource\", id=\"local-dcb-v1\"");

            response.setEntity(
                    new ByteArrayEntity(
                            DICTIONARY,
                            ContentType.APPLICATION_JSON));
        }
    }

    private static final class ResourceHandler
            implements HttpRequestHandler {

        private final byte[] dictionaryHash;
        private final String availableDictionary;

        ResourceHandler(final byte[] dictionaryHash) {
            this.dictionaryHash = dictionaryHash.clone();
            this.availableDictionary =
                    ":" + Base64.getEncoder()
                            .encodeToString(dictionaryHash) + ":";
        }

        @Override
        public void handle(
                final ClassicHttpRequest request,
                final ClassicHttpResponse response,
                final HttpContext context) throws IOException {

            final Header available =
                    request.getFirstHeader("Available-Dictionary");

            final Header acceptEncoding =
                    request.getFirstHeader(HttpHeaders.ACCEPT_ENCODING);

            System.out.println();
            System.out.println("Server received:");
            System.out.println("Available-Dictionary: "
                    + (available != null
                    ? available.getValue()
                    : "(none)"));
            System.out.println("Accept-Encoding      : "
                    + (acceptEncoding != null
                    ? acceptEncoding.getValue()
                    : "(none)"));

            final boolean dictionaryMatches =
                    available != null
                            && availableDictionary.equals(
                            available.getValue());

            final boolean acceptsDcb =
                    acceptEncoding != null
                            && containsToken(
                            acceptEncoding.getValue(), DCB);

            if (!dictionaryMatches || !acceptsDcb) {
                response.setCode(HttpStatus.SC_OK);
                response.setEntity(
                        new ByteArrayEntity(
                                RESOURCE,
                                ContentType.APPLICATION_JSON));
                return;
            }

            final byte[] compressed =
                    createDcb(
                            DICTIONARY,
                            dictionaryHash,
                            RESOURCE);

            response.setCode(HttpStatus.SC_OK);
            response.addHeader(
                    HttpHeaders.CONTENT_ENCODING,
                    DCB);

            response.addHeader(
                    HttpHeaders.VARY,
                    "Accept-Encoding, Available-Dictionary");

            response.setEntity(
                    new ByteArrayEntity(
                            compressed,
                            ContentType.APPLICATION_OCTET_STREAM));
        }
    }

    private static byte[] createDcb(
            final byte[] dictionary,
            final byte[] dictionaryHash,
            final byte[] content) throws IOException {

        final ByteBuffer dictionaryBuffer =
                ByteBuffer.allocateDirect(dictionary.length);

        dictionaryBuffer.put(dictionary);
        dictionaryBuffer.flip();

        /*
         * Shared dictionary type 0 is a raw LZ77 prefix dictionary.
         */
        final PreparedDictionary preparedDictionary =
                Encoder.prepareDictionary(dictionaryBuffer, 0);

        final ByteArrayOutputStream compressed =
                new ByteArrayOutputStream();

        try {
            final Encoder.Parameters parameters =
                    Encoder.Parameters.create(
                            6,
                            24,
                            Encoder.Mode.TEXT);

            try (final BrotliOutputStream out =
                         new BrotliOutputStream(
                                 compressed,
                                 parameters)) {

                out.attachDictionary(preparedDictionary);
                out.write(content);
            }
        } finally {
            if (preparedDictionary instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) preparedDictionary).close();
                } catch (final Exception ex) {
                    throw new IOException(
                            "Unable to release Brotli dictionary",
                            ex);
                }
            }
        }

        final ByteArrayOutputStream dcb =
                new ByteArrayOutputStream(
                        DCB_MAGIC.length
                                + dictionaryHash.length
                                + compressed.size());

        dcb.write(DCB_MAGIC);
        dcb.write(dictionaryHash);
        compressed.writeTo(dcb);

        return dcb.toByteArray();
    }

    private static boolean containsToken(
            final String value,
            final String token) {

        final String[] tokens = value.split(",");

        for (final String current : tokens) {
            if (token.equalsIgnoreCase(current.trim())) {
                return true;
            }
        }

        return false;
    }

    private static Certificate loadCertificate()
            throws Exception {

        final String value = CERTIFICATE
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replaceAll("\\s", "");

        final byte[] encoded =
                Base64.getDecoder().decode(value);

        final CertificateFactory factory =
                CertificateFactory.getInstance("X.509");

        return factory.generateCertificate(
                new ByteArrayInputStream(encoded));
    }

    private static PrivateKey loadPrivateKey()
            throws Exception {

        final String value = PRIVATE_KEY
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        final byte[] encoded =
                Base64.getDecoder().decode(value);

        return KeyFactory.getInstance("RSA")
                .generatePrivate(
                        new PKCS8EncodedKeySpec(encoded));
    }

    private static SSLContext createServerSslContext(
            final Certificate certificate,
            final PrivateKey privateKey) throws Exception {

        final char[] password = "changeit".toCharArray();

        final KeyStore keyStore =
                KeyStore.getInstance(KeyStore.getDefaultType());

        keyStore.load(null, null);

        keyStore.setKeyEntry(
                "localhost",
                privateKey,
                password,
                new Certificate[]{certificate});

        final KeyManagerFactory keyManagerFactory =
                KeyManagerFactory.getInstance(
                        KeyManagerFactory.getDefaultAlgorithm());

        keyManagerFactory.init(keyStore, password);

        final SSLContext sslContext =
                SSLContext.getInstance("TLS");

        sslContext.init(
                keyManagerFactory.getKeyManagers(),
                null,
                null);

        return sslContext;
    }

    private static SSLContext createClientSslContext(
            final Certificate certificate) throws Exception {

        final KeyStore trustStore =
                KeyStore.getInstance(KeyStore.getDefaultType());

        trustStore.load(null, null);

        trustStore.setCertificateEntry(
                "localhost",
                certificate);

        final TrustManagerFactory trustManagerFactory =
                TrustManagerFactory.getInstance(
                        TrustManagerFactory.getDefaultAlgorithm());

        trustManagerFactory.init(trustStore);

        final SSLContext sslContext =
                SSLContext.getInstance("TLS");

        sslContext.init(
                null,
                trustManagerFactory.getTrustManagers(),
                null);

        return sslContext;
    }
}