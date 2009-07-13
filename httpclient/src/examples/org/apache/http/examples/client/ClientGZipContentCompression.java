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

package org.apache.http.examples.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

/**
 * Demonstration of the use of protocol interceptors to transparently 
 * modify properties of HTTP messages sent / received by the HTTP client.
 * <p/>
 * In this particular case HTTP client is made capable of transparent content 
 * GZIP compression by adding two protocol interceptors: a request interceptor
 * that adds 'Accept-Encoding: gzip' header to all outgoing requests and
 * a response interceptor that automatically expands compressed response
 * entities by wrapping them with a uncompressing decorator class. The use of
 * protocol interceptors makes content compression completely transparent to 
 * the consumer of the {@link org.apache.http.client.HttpClient HttpClient}
 * interface.
 */
public class ClientGZipContentCompression {

    public final static void main(String[] args) throws Exception {
        DefaultHttpClient httpclient = new DefaultHttpClient();

        httpclient.addRequestInterceptor(new HttpRequestInterceptor() {
           
            public void process(
                    final HttpRequest request, 
                    final HttpContext context) throws HttpException, IOException {
                if (!request.containsHeader("Accept-Encoding")) {
                    request.addHeader("Accept-Encoding", "gzip");
                }
            }

        });
        
        httpclient.addResponseInterceptor(new HttpResponseInterceptor() {
           
            public void process(
                    final HttpResponse response, 
                    final HttpContext context) throws HttpException, IOException {
                HttpEntity entity = response.getEntity();
                Header ceheader = entity.getContentEncoding();
                if (ceheader != null) {
                    HeaderElement[] codecs = ceheader.getElements();
                    for (int i = 0; i < codecs.length; i++) {
                        if (codecs[i].getName().equalsIgnoreCase("gzip")) {
                            response.setEntity(
                                    new GzipDecompressingEntity(response.getEntity())); 
                            return;
                        }
                    }
                }
            }
            
        });
        
        HttpGet httpget = new HttpGet("http://www.apache.org/"); 
        
        // Execute HTTP request
        System.out.println("executing request " + httpget.getURI());
        HttpResponse response = httpclient.execute(httpget);

        System.out.println("----------------------------------------");
        System.out.println(response.getStatusLine());
        System.out.println(response.getLastHeader("Content-Encoding"));
        System.out.println(response.getLastHeader("Content-Length"));
        System.out.println("----------------------------------------");

        HttpEntity entity = response.getEntity();
        
        if (entity != null) {
            String content = EntityUtils.toString(entity);
            System.out.println(content);
            System.out.println("----------------------------------------");
            System.out.println("Uncompressed size: "+content.length());
        }

        // When HttpClient instance is no longer needed, 
        // shut down the connection manager to ensure
        // immediate deallocation of all system resources
        httpclient.getConnectionManager().shutdown();        
    }

    static class GzipDecompressingEntity extends HttpEntityWrapper {

        public GzipDecompressingEntity(final HttpEntity entity) {
            super(entity);
        }
    
        @Override
        public InputStream getContent()
            throws IOException, IllegalStateException {

            // the wrapped entity's getContent() decides about repeatability
            InputStream wrappedin = wrappedEntity.getContent();

            return new GZIPInputStream(wrappedin);
        }

        @Override
        public long getContentLength() {
            // length of ungzipped content is not known
            return -1;
        }

    } 
    
}

