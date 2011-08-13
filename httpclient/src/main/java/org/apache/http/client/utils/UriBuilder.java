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
 *
 * ====================================================================
 */

package org.apache.http.client.utils;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Set;

public class UriBuilder {
    private String scheme;
    private String schemeSpecificPart;
    private String authority;
    private String userInfo;
    private String host;
    private int port;
    private String path;
    private String query;
    private String fragment;
    private URI uri;
    private String enc;
    private boolean encOn;
    private Set<String> supportedEncoding;

    public UriBuilder() {
        init();
    }

    public UriBuilder(String string) throws URISyntaxException {
        init();
        URI uri = new URI(string);
        from(uri);
    }

    public UriBuilder(URI uri) {
        this.init();
        from(uri);
    }

    public UriBuilder addParameter(String param, Object value)
            throws URISyntaxException {
        return this.addParameter(param, value.toString());
    }

    /**
     * add a parameter-value pair into URI query
     *
     * @param param
     * @param value
     * @throws URISyntaxException
     */
    public UriBuilder addParameter(String param, String value)
            throws URISyntaxException {
        StringBuffer sb = this.query == null ? new StringBuffer()
                : new StringBuffer(this.query);
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '&')
            sb.append('&');
        sb.append(encode(param)).append('=').append(encode(value));
        return setQuery(sb.toString());
    }

    /**
     * build a URI instance from pre-provided information
     *
     * @throws RuntimeException
     */
    public URI build() throws RuntimeException {
        if (uri != null)
            return uri;
        else
            throw new IllegalStateException("Not enough information to build URI");
    }

    private void digestURI(URI uri, boolean raw) {
        scheme = uri.getScheme();
        host = uri.getHost();
        port = uri.getPort();
        if (raw) {
            schemeSpecificPart = uri.getRawSchemeSpecificPart();
            authority = uri.getRawAuthority();
            userInfo = uri.getRawUserInfo();
            path = uri.getRawPath();
            query = uri.getRawQuery();
            fragment = uri.getRawFragment();
        } else {
            schemeSpecificPart = uri.getSchemeSpecificPart();
            authority = uri.getAuthority();
            userInfo = uri.getUserInfo();
            path = uri.getPath();
            query = uri.getQuery();
            fragment = uri.getFragment();
        }
    }

    public UriBuilder encodingOff() {
        this.encOn = false;
        return this;
    }

    public UriBuilder encodingOn() {
        try {
            encodingOn(enc);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return this;
    }

    public UriBuilder encodingOn(String enc)
            throws UnsupportedEncodingException {
        if (enc == null)
            throw new IllegalArgumentException(
                    "Encoding scheme cannot be null.");

        // check encoding is supported
        if (!supportedEncoding.contains(enc))
            throw new UnsupportedEncodingException();
        this.enc = enc;
        this.encOn = true;
        return this;
    }

    /**
     * copy the uri into builder
     *
     * @param uri
     *            String value of a URI instance
     * @throws URISyntaxException
     *             if uri is invalid
     */
    public UriBuilder from(final String uri) throws URISyntaxException {
        URI u = new URI(uri);
        from(u);
        return this;
    }

    /**
     * copy uri into builder
     *
     * @param uri
     *            the URI source to clone
     */
    public UriBuilder from(final URI uri) {
        digestURI(uri, false);
        this.uri = uri;
        return this;
    }

    private void init() {
        port = -1;
        encOn = false;
        enc = "UTF-8";
        supportedEncoding = Charset.availableCharsets().keySet();
    }

    /**
     * set URI fragment
     *
     * @param fragment
     * @throws URISyntaxException
     */
    public UriBuilder setFragment(final String fragment)
            throws URISyntaxException {
        this.fragment = encode(fragment);
        update();
        return this;
    }

    private String encode(String string) {

        try {
            if (encOn) {
                String encodedString = URLEncoder.encode(string, enc);
                return encodedString;
            } else {
                return URLDecoder.decode(string, enc);
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return string;
    }

    /**
     * set URI host
     *
     * @param host
     * @throws URISyntaxException
     *             if uri is invalid
     */
    public UriBuilder setHost(final String host) throws URISyntaxException {
        this.host = encode(host);
        update();
        return this;
    }

    /**
     * set URI path
     *
     * @param path
     *            a String represent the path of a URI, e.g., "/path"
     * @throws URISyntaxException
     */
    public UriBuilder setPath(final String path) throws URISyntaxException {
        this.path = encode(path);
        update();
        return this;
    }

    /**
     * set URI port
     *
     * @param port
     * @throws URISyntaxException
     */
    public UriBuilder setPort(final int port) throws URISyntaxException {
        this.port = port < 0 ? -1 : port;
        update();
        return this;
    }

    /**
     * set URI query by parameter-value pairs
     *
     * @param paramMap
     * @throws URISyntaxException
     */
    public UriBuilder setQuery(final Map<String, String> paramMap)
            throws URISyntaxException {
        StringBuffer sb = new StringBuffer();
        for (String key : paramMap.keySet())
            sb.append(encode(key)).append('=')
                    .append(encode(paramMap.get(key))).append('&');
        if (sb.charAt(sb.length() - 1) == '&')
            sb.deleteCharAt(sb.length() - 1);
        return setQuery(sb.toString());
    }

    /**
     * set URI query
     *
     * @param query
     * @throws URISyntaxException
     */
    public UriBuilder setQuery(final String query) throws URISyntaxException {
        this.query = query;
        update();
        return this;
    }

    /**
     * set URI scheme
     *
     * @param scheme
     * @throws URISyntaxException
     *             if uri is invalid
     */
    public UriBuilder setScheme(final String scheme) throws URISyntaxException {
        this.scheme = encode(scheme);
        update();
        return this;
    }

    /**
     * set URI user-info
     *
     * @param userInfo
     *            a String represents the user-info, e.g., "username:password"
     * @throws URISyntaxException
     */
    public UriBuilder setUserInfo(final String userInfo)
            throws URISyntaxException {
        this.userInfo = userInfo;
        update();
        return this;
    }

    /**
     * set URI user-info
     *
     * @param username
     * @param password
     * @throws URISyntaxException
     */
    public UriBuilder setUserInfo(final String username, final String password)
            throws URISyntaxException {
        return setUserInfo(username + ':' + password);
    }

    public UriBuilder removeQuery() {
        this.query = null;
        return this;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        URI uri = build();
        sb.append(uri.toString()).append('\n');
        sb.append("scheme   : ").append(scheme).append('\n');
        sb.append("sspart   : ").append(schemeSpecificPart).append('\n');
        sb.append("authority: ").append(authority).append('\n');
        sb.append("user-info: ").append(userInfo).append('\n');
        sb.append("host     : ").append(host).append('\n');
        sb.append("port     : ").append(port).append('\n');
        sb.append("path     : ").append(path).append('\n');
        sb.append("query    : ").append(query).append('\n');
        sb.append("fragment : ").append(fragment);
        return sb.toString();
    }

    private void update() throws URISyntaxException {
        if (scheme != null && host != null)
            try {
                uri = new URI(scheme, userInfo, host, port, path, query,
                        fragment);

                // StringBuffer sb = new StringBuffer();
                // sb.append(scheme).append("://");
                // if(userInfo != null)
                // sb.append(userInfo).append("@");
                // sb.append(host);
                // if(path != null)
                // sb.append(path);
                // if(query != null)
                // sb.append('?').append(query);
                // if(fragment != null)
                // sb.append('#').append(fragment);
                // uri = new URI(sb.toString());
                digestURI(uri, false);
            } catch (URISyntaxException e) {
                // roll back
                digestURI(uri, false);
                throw e;
            }
    }
}
