/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
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

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;

import org.apache.http.HttpHost;
import org.apache.http.NameValuePair;
import org.apache.http.protocol.HTTP;

/**
 * The home for utility methods that handle various URL encoding tasks.
 * 
 * @author Michael Becke
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 * 
 * @since 4.0
 */
public class URLUtils {

    /** Default content encoding chatset */
    private static final String DEFAULT_CHARSET = "ISO-8859-1";
    private static final String PARAMETER_SEPARATOR = "&";
    private static final String NAME_VALUE_SEPARATOR = "=";

    /**
     * Form-urlencoding routine.
     *
     * The default encoding for all forms is `application/x-www-form-urlencoded'. 
     * A form data set is represented in this media type as follows:
     *
     * The form field names and values are escaped: space characters are replaced 
     * by `+', and then reserved characters are escaped as per [URL]; that is, 
     * non-alphanumeric characters are replaced by `%HH', a percent sign and two 
     * hexadecimal digits representing the ASCII code of the character. Line breaks, 
     * as in multi-line text field values, are represented as CR LF pairs, i.e. `%0D%0A'.
     * 
     * <p>
     * if the given charset is not supported, ISO-8859-1 is used instead.
     * </p>
     * 
     * @param pairs the values to be encoded
     * @param charset the character set of pairs to be encoded
     * 
     * @return the urlencoded pairs
     */
     public static String simpleFormUrlEncode(
             final NameValuePair[] pairs, 
             final String charset) {
        try {
            return formUrlEncode(pairs, charset);
        } catch (UnsupportedEncodingException e) {
            try {
                return formUrlEncode(pairs, DEFAULT_CHARSET);
            } catch (UnsupportedEncodingException fatal) {
                // Should never happen. ISO-8859-1 must be supported on all JVMs
                throw new Error("HttpClient requires " + DEFAULT_CHARSET + " support");
            }
        }
    }

    /**
     * Form-urlencoding routine.
     *
     * The default encoding for all forms is `application/x-www-form-urlencoded'. 
     * A form data set is represented in this media type as follows:
     *
     * The form field names and values are escaped: space characters are replaced 
     * by `+', and then reserved characters are escaped as per [URL]; that is, 
     * non-alphanumeric characters are replaced by `%HH', a percent sign and two 
     * hexadecimal digits representing the ASCII code of the character. Line breaks, 
     * as in multi-line text field values, are represented as CR LF pairs, i.e. `%0D%0A'.
     * 
     * @param pairs the values to be encoded
     * @param charset the character set of pairs to be encoded
     * 
     * @return the urlencoded pairs
     * @throws UnsupportedEncodingException if charset is not supported
     */
     public static String formUrlEncode(
             final NameValuePair[] pairs, 
             final String charset) throws UnsupportedEncodingException {
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < pairs.length; i++) {
            NameValuePair pair = pairs[i];
            if (pair.getName() != null) {
                if (i > 0) {
                    buf.append("&");
                }
                buf.append(URLEncoder.encode(pair.getName(), charset));
                buf.append("=");
                if (pair.getValue() != null) {
                    buf.append(URLEncoder.encode(pair.getValue(), charset));
                }
            }
        }
        return buf.toString();
    }
    
     public static Map <String, List <String>> parse (
             final URI uri, 
             String charset) throws UnsupportedEncodingException {
         Map <String, List <String>> result = Collections.emptyMap();
         final String query = uri.getRawQuery();
         if (query != null && query.length() > 0) {
             result = new TreeMap <String, List <String>>();
             parse(result, new Scanner(query), charset);
         }
         return result;
     }

     public static void parse (
             final Map <String, List <String>> result, 
             final Scanner scanner, String charset) throws UnsupportedEncodingException {
         if (charset == null) {
             charset = HTTP.DEFAULT_CONTENT_CHARSET;
         }
         scanner.useDelimiter(PARAMETER_SEPARATOR);
         while (scanner.hasNext()) {
             final String[] nameValue = scanner.next().split(NAME_VALUE_SEPARATOR);
             if (nameValue.length == 0 || nameValue.length > 2)
                 throw new IllegalArgumentException("bad parameter");
             final String name = URLDecoder.decode(nameValue[0], charset);
             if (nameValue.length == 2) {
                 if (!result.containsKey(name))
                     result.put(name, new LinkedList <String>());
                 String value = null;
                 final List <String> values = result.get(name);
                 value = URLDecoder.decode(nameValue[1], charset);
                 values.add(value);
             }
         }
     }

     public static String format (
             final Map <String, List <String>> parameters, 
             String charset) throws UnsupportedEncodingException {
         if (charset == null) {
             charset = HTTP.DEFAULT_CONTENT_CHARSET;
         }
         final StringBuilder result = new StringBuilder(64);
         for (final String name : parameters.keySet()) {
             final List <? extends String> values = parameters.get(name);
             if (values != null) {
                 final String encodedName = URLEncoder.encode(name, charset);
                 for (final String value : values) {
                     if (result.length() > 0)
                         result.append(PARAMETER_SEPARATOR);
                     final String encodedValue = URLEncoder.encode(value, charset);
                     result.append(encodedName);
                     result.append(NAME_VALUE_SEPARATOR);
                     result.append(encodedValue);
                 }
             }
         }
         return result.toString();
     }

     public static URI createURI(
            final String scheme,
            final String host,
            int port,
            final String path,
            final String query,
            final String fragment) throws URISyntaxException {
        
        StringBuilder buffer = new StringBuilder();
        if (host != null) {
            if (scheme != null) {
                buffer.append(scheme);
                buffer.append("://");
            }
            buffer.append(host);
            if (port > 0) {
                buffer.append(":");
                buffer.append(port);
            }
        }
        if (path == null || !path.startsWith("/")) {
            buffer.append("/");
        }
        if (path != null) {
            buffer.append(path);
        }
        if (query != null) {
            buffer.append("?");
            buffer.append(query);
        }
        if (fragment != null) {
            buffer.append("#");
            buffer.append(fragment);
        }
        return new URI(buffer.toString());
    }

    public static URI rewriteURI(
            final URI uri, 
            final HttpHost target,
            boolean dropFragment) throws URISyntaxException {
        if (uri == null) {
            throw new IllegalArgumentException("URI may nor be null");
        }
        if (target != null) {
            return URLUtils.createURI(
                    target.getSchemeName(), 
                    target.getHostName(), 
                    target.getPort(), 
                    uri.getRawPath(), 
                    uri.getRawQuery(), 
                    dropFragment ? null : uri.getRawFragment());
        } else {
            return URLUtils.createURI(
                    null, 
                    null, 
                    -1, 
                    uri.getRawPath(), 
                    uri.getRawQuery(), 
                    dropFragment ? null : uri.getRawFragment());
        }
    }
    
    public static URI rewriteURI(
            final URI uri, 
            final HttpHost target) throws URISyntaxException {
        return rewriteURI(uri, target, false);
    }
    
    /**
     * Resolves a URI reference against a base URI. Work-around for bug in
     * java.net.URI (<http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4708535>)
     *
     * @param baseURI the base URI
     * @param reference the URI reference
     * @return the resulting URI
     */
    public static URI resolve(final URI baseURI, final String reference) {
        return URLUtils.resolve(baseURI, URI.create(reference));
    }

    /**
     * Resolves a URI reference against a base URI. Work-around for bug in
     * java.net.URI (<http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4708535>)
     *
     * @param baseURI the base URI
     * @param reference the URI reference
     * @return the resulting URI
     */
    public static URI resolve(final URI baseURI, URI reference){
        if (baseURI == null) {
            throw new IllegalArgumentException("Base URI may nor be null");
        }
        if (reference == null) {
            throw new IllegalArgumentException("Reference URI may nor be null");
        }
        boolean emptyReference = reference.toString().length() == 0;
        if (emptyReference) {
            reference = URI.create("#");
        }
        URI resolved = baseURI.resolve(reference);
        if (emptyReference) {
            String resolvedString = resolved.toString();
            resolved = URI.create(resolvedString.substring(0,
                resolvedString.indexOf('#')));
        }
        return resolved;
    }

    /**
     * This class should not be instantiated.
     */
    private URLUtils() {
    }

}
