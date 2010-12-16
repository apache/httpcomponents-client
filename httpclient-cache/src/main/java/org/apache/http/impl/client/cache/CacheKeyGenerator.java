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
package org.apache.http.impl.client.cache;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.annotation.Immutable;
import org.apache.http.client.cache.HeaderConstants;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.protocol.HTTP;

/**
 * @since 4.1
 */
@Immutable
class CacheKeyGenerator {

    /**
     * For a given {@link HttpHost} and {@link HttpRequest} get a URI from the
     * pair that I can use as an identifier KEY into my HttpCache
     *
     * @param host The host for this request
     * @param req the {@link HttpRequest}
     * @return String the extracted URI
     */
    public String getURI(HttpHost host, HttpRequest req) {
        if (isRelativeRequest(req)) {
            return canonicalizeUri(String.format("%s%s", host.toString(), req.getRequestLine().getUri()));
        }
        return canonicalizeUri(req.getRequestLine().getUri());
    }

    public String canonicalizeUri(String uri) {
        try {
            URL u = new URL(uri);
            String protocol = u.getProtocol().toLowerCase();
            String hostname = u.getHost().toLowerCase();
            int port = canonicalizePort(u.getPort(), protocol);
            String path = canonicalizePath(u.getPath());
            if ("".equals(path)) path = "/";
            String query = u.getQuery();
            String file = (query != null) ? (path + "?" + query) : path;
            URL out = new URL(protocol, hostname, port, file);
            return out.toString();
        } catch (MalformedURLException e) {
            return uri;
        }
    }

    private String canonicalizePath(String path) {
        try {
            String decoded = URLDecoder.decode(path, "UTF-8");
            return (new URI(decoded)).getPath();
        } catch (UnsupportedEncodingException e) {
        } catch (URISyntaxException e) {
        }
        return path;
    }

    private int canonicalizePort(int port, String protocol) {
        if (port == -1 && "http".equalsIgnoreCase(protocol)) {
            return 80;
        } else if (port == -1 && "https".equalsIgnoreCase(protocol)) {
            return 443;
        }
        return port;
    }

    private boolean isRelativeRequest(HttpRequest req) {
        String requestUri = req.getRequestLine().getUri();
        return ("*".equals(requestUri) || requestUri.startsWith("/"));
    }

    protected String getFullHeaderValue(Header[] headers) {
        if (headers == null)
            return "";

        StringBuilder buf = new StringBuilder("");
        boolean first = true;
        for (Header hdr : headers) {
            if (!first) {
                buf.append(", ");
            }
            buf.append(hdr.getValue().trim());
            first = false;

        }
        return buf.toString();
    }

    /**
     * For a given {@link HttpHost} and {@link HttpRequest} if the request has a
     * VARY header - I need to get an additional URI from the pair of host and
     * request so that I can also store the variant into my HttpCache.
     *
     * @param host The host for this request
     * @param req the {@link HttpRequest}
     * @param entry the parent entry used to track the variants
     * @return String the extracted variant URI
     */
    public String getVariantURI(HttpHost host, HttpRequest req, HttpCacheEntry entry) {
        if (!entry.hasVariants()) return getURI(host, req);
        return getVariantKey(req, entry) + getURI(host, req);
    }

    /**
     * Compute a "variant key" from the headers of a given request that are
     * covered by the Vary header of a given cache entry. Any request whose
     * varying headers match those of this request should have the same
     * variant key. 
     * @param req originating request
     * @param entry cache entry in question that has variants
     * @return a <code>String</code> variant key
     */
    public String getVariantKey(HttpRequest req, HttpCacheEntry entry) {
        List<String> variantHeaderNames = new ArrayList<String>();
        for (Header varyHdr : entry.getHeaders(HeaderConstants.VARY)) {
            for (HeaderElement elt : varyHdr.getElements()) {
                variantHeaderNames.add(elt.getName());
            }
        }
        Collections.sort(variantHeaderNames);

        StringBuilder buf;
        try {
            buf = new StringBuilder("{");
            boolean first = true;
            for (String headerName : variantHeaderNames) {
                if (!first) {
                    buf.append("&");
                }
                buf.append(URLEncoder.encode(headerName, HTTP.UTF_8));
                buf.append("=");
                buf.append(URLEncoder.encode(getFullHeaderValue(req.getHeaders(headerName)),
                        HTTP.UTF_8));
                first = false;
            }
            buf.append("}");
        } catch (UnsupportedEncodingException uee) {
            throw new RuntimeException("couldn't encode to UTF-8", uee);
        }
        return buf.toString();
    }

}