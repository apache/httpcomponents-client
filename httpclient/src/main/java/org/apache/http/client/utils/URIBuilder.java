/*
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.client.utils;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.http.Consts;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

/**
 * {@link URI} builder for HTTP requests.
 * 
 * @since 4.2
 */
public class URIBuilder {

    private String scheme;
    private String schemeSpecificPart;
    private String authority;
    private String userInfo;
    private String host;
    private int port;
    private String path;
    private List<NameValuePair> queryParams;
    private String fragment;

    public URIBuilder() {
        super();
        this.port = -1;
    }

    public URIBuilder(final String string) throws URISyntaxException {
        super();
        digestURI(new URI(string));
    }

    public URIBuilder(final URI uri) {
        super();
        digestURI(uri);
    }

    private List <NameValuePair> parseQuery(final String query, final Charset charset) {
        if (query != null && query.length() > 0) {
            return URLEncodedUtils.parse(query, charset);
        }
        return null;
    }

    private String formatQuery(final List<NameValuePair> parameters, final Charset charset) {
        if (parameters == null) {
            return null;
        }
        return URLEncodedUtils.format(parameters, charset);
    }

    /**
     * Builds a {@link URI} instance.
     */
    public URI build() throws URISyntaxException {
        if (this.schemeSpecificPart != null) {
            return new URI(this.scheme, this.schemeSpecificPart, this.fragment);
        } else if (this.authority != null) {
            return new URI(this.scheme, this.authority,
                    this.path, formatQuery(this.queryParams, Consts.UTF_8), this.fragment);

        } else {
            return new URI(this.scheme, this.userInfo, this.host, this.port,
                    this.path, formatQuery(this.queryParams, Consts.UTF_8), this.fragment);
        }
    }

    private void digestURI(final URI uri) {
        this.scheme = uri.getScheme();
        this.schemeSpecificPart = uri.getSchemeSpecificPart();
        this.authority = uri.getAuthority();
        this.host = uri.getHost();
        this.port = uri.getPort();
        this.userInfo = uri.getUserInfo();
        this.path = uri.getPath();
        this.queryParams = parseQuery(uri.getRawQuery(), Consts.UTF_8);
        this.fragment = uri.getFragment();
    }

    public URIBuilder setScheme(final String scheme) {
        this.scheme = scheme;
        return this;
    }

    public URIBuilder setUserInfo(final String userInfo) {
        this.userInfo = userInfo;
        this.schemeSpecificPart = null;
        this.authority = null;
        return this;
    }

    public URIBuilder setUserInfo(final String username, final String password) {
        return setUserInfo(username + ':' + password);
    }

    public URIBuilder setHost(final String host) {
        this.host = host;
        this.schemeSpecificPart = null;
        this.authority = null;
        return this;
    }

    public URIBuilder setPort(final int port) {
        this.port = port < 0 ? -1 : port;
        this.schemeSpecificPart = null;
        this.authority = null;
        return this;
    }

    public URIBuilder setPath(final String path) {
        this.path = path;
        this.schemeSpecificPart = null;
        return this;
    }

    public URIBuilder removeQuery() {
        this.queryParams = null;
        this.schemeSpecificPart = null;
        return this;
    }

    public URIBuilder setQuery(final String query) {
        this.queryParams = parseQuery(query, Consts.UTF_8);
        this.schemeSpecificPart = null;
        return this;
    }

    public URIBuilder addParameter(final String param, final String value) {
        if (this.queryParams == null) {
            this.queryParams = new ArrayList<NameValuePair>();
        }
        this.queryParams.add(new BasicNameValuePair(param, value));
        this.schemeSpecificPart = null;
        return this;
    }

    public URIBuilder setParameter(final String param, final String value) {
        if (this.queryParams == null) {
            this.queryParams = new ArrayList<NameValuePair>();
        }
        if (!this.queryParams.isEmpty()) {
            for (Iterator<NameValuePair> it = this.queryParams.iterator(); it.hasNext(); ) {
                NameValuePair nvp = it.next();
                if (nvp.getName().equals(param)) {
                    it.remove();
                }
            }
        }
        this.queryParams.add(new BasicNameValuePair(param, value));
        this.schemeSpecificPart = null;
        return this;
    }

    public URIBuilder setFragment(final String fragment) {
        this.fragment = fragment;
        return this;
    }

    public String getScheme() {
        return this.scheme;
    }

    public String getUserInfo() {
        return this.userInfo;
    }

    public String getHost() {
        return this.host;
    }

    public int getPort() {
        return this.port;
    }

    public String getPath() {
        return this.path;
    }

    public List<NameValuePair> getQueryParams() {
        if (this.queryParams != null) {
            return new ArrayList<NameValuePair>(this.queryParams);
        } else {
            return new ArrayList<NameValuePair>();
        }
    }

    public String getFragment() {
        return this.fragment;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("URI [scheme=").append(this.scheme)
                .append(", userInfo=").append(this.userInfo).append(", host=").append(this.host)
                .append(", port=").append(this.port).append(", path=").append(this.path)
                .append(", queryParams=").append(this.queryParams).append(", fragment=")
                .append(this.fragment).append("]");
        return builder.toString();
    }

}
