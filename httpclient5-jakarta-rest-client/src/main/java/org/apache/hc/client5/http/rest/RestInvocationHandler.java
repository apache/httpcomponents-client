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
package org.apache.hc.client5.http.rest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;

/**
 * {@link InvocationHandler} that translates interface method calls into HTTP requests
 * executed through the classic {@link HttpClient} transport. Each method is mapped to
 * its HTTP verb, URI template and parameter bindings at proxy creation time.
 */
final class RestInvocationHandler implements InvocationHandler {

    private static final int ERROR_STATUS_THRESHOLD = 300;

    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();
    private static final int BYTE_MASK = 0xFF;
    private static final int HI_NIBBLE_SHIFT = 4;
    private static final int LO_NIBBLE_MASK = 0x0F;

    private final HttpClient httpClient;
    private final String baseUriStr;
    private final Map<Method, MethodInvoker> invokerMap;

    RestInvocationHandler(final HttpClient client, final URI base,
                          final Map<Method, ClientResourceMethod> methods) {
        this.httpClient = client;
        this.baseUriStr = base.toString();
        this.invokerMap = buildInvokers(methods);
    }

    private static Map<Method, MethodInvoker> buildInvokers(
            final Map<Method, ClientResourceMethod> methods) {
        final Map<Method, MethodInvoker> result = new HashMap<>(methods.size());
        for (final Map.Entry<Method, ClientResourceMethod> entry : methods.entrySet()) {
            final ClientResourceMethod rm = entry.getValue();
            final String acceptHeader = rm.getProduces().length > 0 ? joinMediaTypes(rm.getProduces()) : null;
            final ContentType consumeType = rm.getConsumes().length > 0
                    ? ContentType.parse(rm.getConsumes()[0]) : null;
            result.put(entry.getKey(), new MethodInvoker(rm, acceptHeader, consumeType));
        }
        return result;
    }

    private static String joinMediaTypes(final String[] types) {
        if (types.length == 1) {
            return types[0];
        }
        final StringBuilder sb = new StringBuilder();
        for (final String type : types) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(type);
        }
        return sb.toString();
    }

    @Override
    public Object invoke(final Object proxy, final Method method,
                         final Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return handleObjectMethod(proxy, method, args);
        }
        final MethodInvoker invoker = invokerMap.get(method);
        if (invoker == null) {
            throw new UnsupportedOperationException(
                    "No Jakarta REST mapping for " + method.getName());
        }
        return executeRequest(invoker, args);
    }

    private Object executeRequest(final MethodInvoker invoker,
                                  final Object[] args) {
        final ClientResourceMethod rm = invoker.resourceMethod;
        final ClientResourceMethod.ParamInfo[] params = rm.getParameters();
        final Map<String, String> pathParams = rm.getPathParamCount() > 0
                ? new LinkedHashMap<>(rm.getPathParamCount()) : Collections.emptyMap();
        final Map<String, List<String>> queryParams = rm.getQueryParamCount() > 0
                ? new LinkedHashMap<>(rm.getQueryParamCount()) : Collections.emptyMap();
        final Map<String, String> headerParams = rm.getHeaderParamCount() > 0
                ? new LinkedHashMap<>(rm.getHeaderParamCount()) : Collections.emptyMap();
        Object bodyParam = null;

        if (args != null) {
            for (int i = 0; i < params.length; i++) {
                final ClientResourceMethod.ParamInfo pi = params[i];
                final Object val = args[i];
                final String strVal = val != null ? val.toString() : pi.getDefaultValue();
                switch (pi.getSource()) {
                    case PATH:
                        if (strVal != null) {
                            pathParams.put(pi.getName(), strVal);
                        }
                        break;
                    case QUERY:
                        if (strVal != null) {
                            queryParams.computeIfAbsent(pi.getName(),
                                    k -> new ArrayList<>()).add(strVal);
                        }
                        break;
                    case HEADER:
                        if (strVal != null) {
                            headerParams.put(pi.getName(), strVal);
                        }
                        break;
                    case BODY:
                        bodyParam = val;
                        break;
                    default:
                        break;
                }
            }
        }

        final String expandedPath = expandTemplate(rm.getPathTemplate(), pathParams);
        final String fullUri = buildUri(expandedPath, queryParams);
        final BasicClassicHttpRequest request =
                new BasicClassicHttpRequest(rm.getHttpMethod(), fullUri);

        if (invoker.acceptHeader != null) {
            request.addHeader(HttpHeaders.ACCEPT, invoker.acceptHeader);
        }
        for (final Map.Entry<String, String> entry : headerParams.entrySet()) {
            request.addHeader(entry.getKey(), entry.getValue());
        }
        if (bodyParam != null) {
            final ContentType ct = invoker.consumeType != null
                    ? invoker.consumeType
                    : bodyParam instanceof String
                            ? ContentType.create("text/plain", StandardCharsets.UTF_8)
                            : ContentType.APPLICATION_OCTET_STREAM;
            final byte[] bodyBytes = writeBody(bodyParam, ct.getCharset());
            request.setEntity(new ByteArrayEntity(bodyBytes, ct));
        }

        final Class<?> rawType = rm.getMethod().getReturnType();
        try {
            return httpClient.execute(request, response -> {
                final int status = response.getCode();
                if (status >= ERROR_STATUS_THRESHOLD) {
                    final byte[] body = response.getEntity() != null
                            ? EntityUtils.toByteArray(response.getEntity()) : null;
                    throw new RestClientResponseException(
                            status, response.getReasonPhrase(), body);
                }
                if (rawType == void.class || rawType == Void.class) {
                    EntityUtils.consume(response.getEntity());
                    return null;
                }
                if (response.getEntity() == null) {
                    return null;
                }
                if (rawType == byte[].class) {
                    return EntityUtils.toByteArray(response.getEntity());
                }
                if (rawType == String.class) {
                    return EntityUtils.toString(response.getEntity());
                }
                EntityUtils.consume(response.getEntity());
                throw new UnsupportedOperationException(
                        "Return type " + rawType.getName() + " is not supported;"
                        + " only void, String and byte[] are supported");
            });
        } catch (final IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static byte[] writeBody(final Object body, final Charset charset) {
        if (body instanceof byte[]) {
            return (byte[]) body;
        }
        if (body instanceof String) {
            return ((String) body).getBytes(charset != null ? charset : StandardCharsets.UTF_8);
        }
        throw new UnsupportedOperationException(
                "Cannot serialize " + body.getClass().getName()
                + "; only String and byte[] request bodies are supported");
    }

    private String buildUri(final String path, final Map<String, List<String>> query) {
        final int estimate = baseUriStr.length() + path.length() + query.size() * 32;
        final StringBuilder sb = new StringBuilder(estimate);
        if (baseUriStr.endsWith("/") && path.startsWith("/")) {
            sb.append(baseUriStr, 0, baseUriStr.length() - 1);
        } else {
            sb.append(baseUriStr);
        }
        sb.append(path);
        if (!query.isEmpty()) {
            sb.append('?');
            boolean first = true;
            for (final Map.Entry<String, List<String>> entry : query.entrySet()) {
                final String encodedKey = percentEncodeComponent(entry.getKey());
                for (final String value : entry.getValue()) {
                    if (!first) {
                        sb.append('&');
                    }
                    sb.append(encodedKey).append('=').append(percentEncodeComponent(value));
                    first = false;
                }
            }
        }
        return sb.toString();
    }

    /**
     * Expands URI template variables with percent-encoded values in a single pass.
     */
    static String expandTemplate(final String template,
                                 final Map<String, String> variables) {
        if (variables.isEmpty()) {
            return template;
        }
        final StringBuilder sb = new StringBuilder(template.length());
        int i = 0;
        while (i < template.length()) {
            final char c = template.charAt(i);
            if (c == '{') {
                final int close = template.indexOf('}', i);
                if (close < 0) {
                    sb.append(template, i, template.length());
                    break;
                }
                final String name = template.substring(i + 1, close);
                final String value = variables.get(name);
                if (value != null) {
                    sb.append(percentEncodeComponent(value));
                } else {
                    sb.append(template, i, close + 1);
                }
                i = close + 1;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    /**
     * Percent-encodes a value for use in a URI component per RFC 3986.
     */
    static String percentEncodeComponent(final String value) {
        final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        final StringBuilder sb = new StringBuilder(bytes.length);
        for (final byte b : bytes) {
            final int ch = b & BYTE_MASK;
            if (isUnreserved(ch)) {
                sb.append((char) ch);
            } else {
                sb.append('%');
                sb.append(HEX_DIGITS[ch >> HI_NIBBLE_SHIFT]);
                sb.append(HEX_DIGITS[ch & LO_NIBBLE_MASK]);
            }
        }
        return sb.toString();
    }

    private static boolean isUnreserved(final int ch) {
        return ch >= 'A' && ch <= 'Z'
                || ch >= 'a' && ch <= 'z'
                || ch >= '0' && ch <= '9'
                || ch == '-' || ch == '.' || ch == '_' || ch == '~';
    }

    private Object handleObjectMethod(final Object proxy, final Method method,
                                      final Object[] args) {
        final String name = method.getName();
        if ("toString".equals(name)) {
            return "RestProxy[" + baseUriStr + "]";
        }
        if ("hashCode".equals(name)) {
            return System.identityHashCode(proxy);
        }
        if ("equals".equals(name)) {
            return args[0] == proxy;
        }
        throw new UnsupportedOperationException(name);
    }

    static final class MethodInvoker {

        final ClientResourceMethod resourceMethod;
        final String acceptHeader;
        final ContentType consumeType;

        MethodInvoker(final ClientResourceMethod rm, final String accept,
                      final ContentType consume) {
            this.resourceMethod = rm;
            this.acceptHeader = accept;
            this.consumeType = consume;
        }
    }

}
