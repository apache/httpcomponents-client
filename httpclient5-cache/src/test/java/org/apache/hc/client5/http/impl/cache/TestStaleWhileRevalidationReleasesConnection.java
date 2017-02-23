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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.apache.hc.client5.http.cache.CacheResponseStatus;
import org.apache.hc.client5.http.cache.HttpCacheContext;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.io.BasicHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.sync.CloseableHttpClient;
import org.apache.hc.client5.http.protocol.ClientProtocolException;
import org.apache.hc.client5.http.sync.methods.HttpGet;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.MethodNotSupportedException;
import org.apache.hc.core5.http.config.SocketConfig;
import org.apache.hc.core5.http.impl.io.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.io.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Test that after background validation that a subsequent request for non cached
 * conent can be made.  This verifies that the connection has been release back to
 * the pool by the AsynchronousValidationRequest.
 */
public class TestStaleWhileRevalidationReleasesConnection {

    private static final EchoViaHeaderHandler cacheHandler = new EchoViaHeaderHandler();

    protected HttpServer localServer;
    private int port;
    private CloseableHttpClient client;
    private final String url = "/static/dom";
    private final String url2 = "2";


    @Before
    public void start() throws Exception  {
        this.localServer = ServerBootstrap.bootstrap()
                .setSocketConfig(SocketConfig.custom()
                        .setSoTimeout(5000)
                        .build())
                .registerHandler(url + "*", new EchoViaHeaderHandler())
                .create();
        this.localServer.start();

        port = this.localServer.getLocalPort();

        final CacheConfig cacheConfig = CacheConfig.custom()
                .setMaxCacheEntries(100)
                .setMaxObjectSize(15) //1574
                .setAsynchronousWorkerIdleLifetimeSecs(60)
                .setAsynchronousWorkersMax(1)
                .setAsynchronousWorkersCore(1)
                .setRevalidationQueueSize(100)
                .setSharedCache(true)
                .build();

        final RequestConfig config = RequestConfig.custom()
                .setSocketTimeout(10000)
                .setConnectTimeout(10000)
                .setConnectionRequestTimeout(1000)
                .build();

        client = CachingHttpClientBuilder.create()
                .setCacheConfig(cacheConfig)
                .setDefaultRequestConfig(config)
                .setConnectionManager(new BasicHttpClientConnectionManager())
                .build();
    }

    @After
    public void stop() {
        if (this.localServer != null) {
            try {
                this.localServer.stop();
            } catch(final Exception e) {
                e.printStackTrace();
            }
        }

        try {
            client.close();
        } catch(final IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testStaleWhileRevalidate() {
        final String urlToCall = "http://localhost:"+port + url;
        final HttpContext localContext = new BasicHttpContext();
        Exception requestException = null;

        // This will fetch from backend.
        requestException = sendRequest(client, localContext,urlToCall,null);
        assertNull(requestException);

        CacheResponseStatus responseStatus = (CacheResponseStatus) localContext.getAttribute(HttpCacheContext.CACHE_RESPONSE_STATUS);
        assertEquals(CacheResponseStatus.CACHE_MISS,responseStatus);

        try {
            Thread.sleep(1000);
        } catch (final Exception e) {

        }
        // These will be cached
        requestException = sendRequest(client, localContext,urlToCall,null);
        assertNull(requestException);

        responseStatus = (CacheResponseStatus) localContext.getAttribute(HttpCacheContext.CACHE_RESPONSE_STATUS);
        assertEquals(CacheResponseStatus.CACHE_HIT,responseStatus);

        requestException = sendRequest(client, localContext,urlToCall,null);
        assertNull(requestException);

        responseStatus = (CacheResponseStatus) localContext.getAttribute(HttpCacheContext.CACHE_RESPONSE_STATUS);
        assertEquals(CacheResponseStatus.CACHE_HIT,responseStatus);

        // wait, so that max-age is expired
        try {
            Thread.sleep(4000);
        } catch (final Exception e) {

        }

        // This will cause a revalidation to occur
        requestException = sendRequest(client, localContext,urlToCall,"This is new content that is bigger than cache limit");
        assertNull(requestException);

        responseStatus = (CacheResponseStatus) localContext.getAttribute(HttpCacheContext.CACHE_RESPONSE_STATUS);
        assertEquals(CacheResponseStatus.CACHE_HIT,responseStatus);

        try {
            Thread.sleep(1000);
        } catch (final Exception e) {

        }

        // fetch a different content This will hang due to connection leak in revalidation
        requestException = sendRequest(client, localContext,urlToCall+url2,null);
        if(requestException!=null) {
            requestException.printStackTrace();
        }
        assertNull(requestException);


    }

    static Exception sendRequest(final CloseableHttpClient cachingClient, final HttpContext localContext , final String url, final String content) {
        final HttpGet httpget = new HttpGet(url);
        if(content!=null) {
            httpget.setHeader(cacheHandler.getUserContentHeader(),content);
        }

        ClassicHttpResponse response = null;
        try {
            response = cachingClient.execute(httpget, localContext);
            return null;
        } catch (final ClientProtocolException e1) {
            return e1;
        } catch (final IOException e1) {
            return e1;
        } finally {
            if(response!=null) {
                final HttpEntity entity = response.getEntity();
                try {
                    IOUtils.consume(entity);
                } catch (final IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static class EchoViaHeaderHandler
            implements HttpRequestHandler {

        private final String CACHE_CONTROL_HEADER = "Cache-Control";

        private final byte[] DEFAULT_CONTENT;
        private final String DEFAULT_CLIENT_CONTROLLED_CONTENT_HEADER;
        private final String DEFAULT_RESPONSE_CACHE_HEADER;

        // public default constructor
        public EchoViaHeaderHandler() {
            this("ECHO-CONTENT","abc".getBytes(), "public, max-age=3, stale-while-revalidate=5");
        }

        public EchoViaHeaderHandler(final String contentHeader,final byte[] content,
                                    final String defaultResponseCacheHeader) {
            DEFAULT_CLIENT_CONTROLLED_CONTENT_HEADER = contentHeader;
            DEFAULT_CONTENT = content;
            DEFAULT_RESPONSE_CACHE_HEADER = defaultResponseCacheHeader;
        }


        /**
         * Return the header the user can set the content that will be returned by the server
         */
        public String getUserContentHeader() {
            return DEFAULT_CLIENT_CONTROLLED_CONTENT_HEADER;
        }

        /**
         * Handles a request by echoing the incoming request entity.
         * If there is no request entity, an empty document is returned.
         *
         * @param request   the request
         * @param response  the response
         * @param context   the context
         *
         * @throws org.apache.hc.core5.http.HttpException    in case of a problem
         * @throws java.io.IOException      in case of an IO problem
         */
        @Override
        public void handle(final ClassicHttpRequest request,
                           final ClassicHttpResponse response,
                           final HttpContext context)
                throws HttpException, IOException {

            final String method = request.getMethod().toUpperCase(Locale.ROOT);
            if (!"GET".equals(method) &&
                    !"POST".equals(method) &&
                    !"PUT".equals(method)
                    ) {
                throw new MethodNotSupportedException
                        (method + " not supported by " + getClass().getName());
            }

            response.setCode(org.apache.hc.core5.http.HttpStatus.SC_OK);
            response.addHeader("Cache-Control",getCacheContent(request));
            final byte[] content = getHeaderContent(request);
            final ByteArrayEntity bae = new ByteArrayEntity(content);
            response.setHeader("Connection","keep-alive");

            response.setEntity(bae);

        } // handle


        public byte[] getHeaderContent(final HttpRequest request) {
            final Header contentHeader = request.getFirstHeader(DEFAULT_CLIENT_CONTROLLED_CONTENT_HEADER);
            if(contentHeader!=null) {
                return contentHeader.getValue().getBytes(StandardCharsets.UTF_8);
            } else {
                return DEFAULT_CONTENT;
            }
        }

        public String getCacheContent(final HttpRequest request) {
            final Header contentHeader = request.getFirstHeader(CACHE_CONTROL_HEADER);
            if(contentHeader!=null) {
                return contentHeader.getValue();
            } else {
                return DEFAULT_RESPONSE_CACHE_HEADER;
            }
        }

    } // class EchoHandler

  }

