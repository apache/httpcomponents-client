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
package org.apache.hc.client5.http.examples.fluent;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.client5.http.protocol.ClientProtocolException;
import org.apache.hc.client5.http.protocol.HttpResponseException;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.ResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * This example demonstrates how the HttpClient fluent API can be used to handle HTTP responses
 * without buffering content body in memory.
 */
public class FluentResponseHandling {

    public static void main(String[] args)throws Exception {
        Document result = Request.Get("http://somehost/content")
                .execute().handleResponse(new ResponseHandler<Document>() {

            @Override
            public Document handleResponse(final ClassicHttpResponse response) throws IOException {
                int status = response.getCode();
                HttpEntity entity = response.getEntity();
                if (status >= HttpStatus.SC_REDIRECTION) {
                    throw new HttpResponseException(status, response.getReasonPhrase());
                }
                if (entity == null) {
                    throw new ClientProtocolException("Response contains no content");
                }
                DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();
                try {
                    DocumentBuilder docBuilder = dbfac.newDocumentBuilder();
                    ContentType contentType = EntityUtils.getContentTypeOrDefault(entity);
                    if (!contentType.equals(ContentType.APPLICATION_XML)) {
                        throw new ClientProtocolException("Unexpected content type:" + contentType);
                    }
                    Charset charset = contentType.getCharset();
                    if (charset == null) {
                        charset = StandardCharsets.ISO_8859_1;
                    }
                    return docBuilder.parse(entity.getContent(), charset.name());
                } catch (ParserConfigurationException ex) {
                    throw new IllegalStateException(ex);
                } catch (SAXException ex) {
                    throw new ClientProtocolException("Malformed XML document", ex);
                }
            }

            });
        // Do something useful with the result
        System.out.println(result);
    }

}
