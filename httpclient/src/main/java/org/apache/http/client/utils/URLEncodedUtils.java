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

package org.apache.http.client.utils;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

import org.apache.http.annotation.Immutable;
import org.apache.http.entity.ContentType;

import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicHeaderValueParser;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.message.ParserCursor;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.CharArrayBuffer;
import org.apache.http.util.EntityUtils;

/**
 * A collection of utilities for encoding URLs.
 *
 * @since 4.0
 */
@Immutable
public class URLEncodedUtils {

    public static final String CONTENT_TYPE = "application/x-www-form-urlencoded";
    private static final String PARAMETER_SEPARATOR = "&";
    private static final String NAME_VALUE_SEPARATOR = "=";

    /**
     * Returns a list of {@link NameValuePair NameValuePairs} as built from the
     * URI's query portion. For example, a URI of
     * http://example.org/path/to/file?a=1&b=2&c=3 would return a list of three
     * NameValuePairs, one for a=1, one for b=2, and one for c=3.
     * <p>
     * This is typically useful while parsing an HTTP PUT.
     *
     * @param uri
     *            uri to parse
     * @param encoding
     *            encoding to use while parsing the query
     */
    public static List <NameValuePair> parse (final URI uri, final String encoding) {
        final String query = uri.getRawQuery();
        if (query != null && query.length() > 0) {
            List<NameValuePair> result = new ArrayList<NameValuePair>();
            Scanner scanner = new Scanner(query);
            parse(result, scanner, encoding);
            return result;
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Returns a list of {@link NameValuePair NameValuePairs} as parsed from an
     * {@link HttpEntity}. The encoding is taken from the entity's
     * Content-Encoding header.
     * <p>
     * This is typically used while parsing an HTTP POST.
     *
     * @param entity
     *            The entity to parse
     * @throws IOException
     *             If there was an exception getting the entity's data.
     */
    public static List <NameValuePair> parse (
            final HttpEntity entity) throws IOException {
        ContentType contentType = ContentType.get(entity);
        if (contentType != null && contentType.getMimeType().equalsIgnoreCase(CONTENT_TYPE)) {
            String content = EntityUtils.toString(entity, Consts.ASCII);
            if (content != null && content.length() > 0) {
                Charset charset = contentType != null ? contentType.getCharset() : null;
                if (charset == null) {
                    charset = HTTP.DEF_CONTENT_CHARSET;
                }
                return parse(content, charset);
            }
        }
        return Collections.emptyList();
    }

    /**
     * Returns true if the entity's Content-Type header is
     * <code>application/x-www-form-urlencoded</code>.
     */
    public static boolean isEncoded (final HttpEntity entity) {
        Header h = entity.getContentType();
        if (h != null) {
            HeaderElement[] elems = h.getElements();
            if (elems.length > 0) {
                String contentType = elems[0].getName();
                return contentType.equalsIgnoreCase(CONTENT_TYPE);
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    /**
     * Adds all parameters within the Scanner to the list of
     * <code>parameters</code>, as encoded by <code>encoding</code>. For
     * example, a scanner containing the string <code>a=1&b=2&c=3</code> would
     * add the {@link NameValuePair NameValuePairs} a=1, b=2, and c=3 to the
     * list of parameters.
     *
     * @param parameters
     *            List to add parameters to.
     * @param scanner
     *            Input that contains the parameters to parse.
     * @param charset
     *            Encoding to use when decoding the parameters.
     */
    public static void parse (
            final List <NameValuePair> parameters,
            final Scanner scanner,
            final String charset) {
        scanner.useDelimiter(PARAMETER_SEPARATOR);
        while (scanner.hasNext()) {
            String name = null;
            String value = null;
            String token = scanner.next();
            int i = token.indexOf(NAME_VALUE_SEPARATOR);
            if (i != -1) {
                name = decode(token.substring(0, i).trim(), charset);
                value = decode(token.substring(i + 1).trim(), charset);
            } else {
                name = decode(token.trim(), charset);
            }
            parameters.add(new BasicNameValuePair(name, value));
        }
    }

    private static final char[] DELIM = new char[] { '&' };

    /**
     * Returns a list of {@link NameValuePair NameValuePairs} as parsed from the given string
     * using the given character encoding.
     *
     * @param s
     *            text to parse.
     * @param charset
     *            Encoding to use when decoding the parameters.
     *
     * @since 4.2
     */
    public static List<NameValuePair> parse (final String s, final Charset charset) {
        if (s == null) {
            return Collections.emptyList();
        }
        BasicHeaderValueParser parser = BasicHeaderValueParser.DEFAULT;
        CharArrayBuffer buffer = new CharArrayBuffer(s.length());
        buffer.append(s);
        ParserCursor cursor = new ParserCursor(0, buffer.length());
        List<NameValuePair> list = new ArrayList<NameValuePair>();
        while (!cursor.atEnd()) {
            NameValuePair nvp = parser.parseNameValuePair(buffer, cursor, DELIM);
            if (nvp.getName().length() > 0) {
                list.add(new BasicNameValuePair(
                        decode(nvp.getName(), charset),
                        decode(nvp.getValue(), charset)));
            }
        }
        return list;
    }

    /**
     * Returns a String that is suitable for use as an <code>application/x-www-form-urlencoded</code>
     * list of parameters in an HTTP PUT or HTTP POST.
     *
     * @param parameters  The parameters to include.
     * @param encoding The encoding to use.
     */
    public static String format (
            final List <? extends NameValuePair> parameters,
            final String encoding) {
        final StringBuilder result = new StringBuilder();
        for (final NameValuePair parameter : parameters) {
            final String encodedName = encode(parameter.getName(), encoding);
            final String encodedValue = encode(parameter.getValue(), encoding);
            if (result.length() > 0) {
                result.append(PARAMETER_SEPARATOR);
            }
            result.append(encodedName);
            if (encodedValue != null) {
                result.append(NAME_VALUE_SEPARATOR);
                result.append(encodedValue);
            }
        }
        return result.toString();
    }

    /**
     * Returns a String that is suitable for use as an <code>application/x-www-form-urlencoded</code>
     * list of parameters in an HTTP PUT or HTTP POST.
     *
     * @param parameters  The parameters to include.
     * @param charset The encoding to use.
     *
     * @since 4.2
     */
    public static String format (
            final Iterable<? extends NameValuePair> parameters,
            final Charset charset) {
        final StringBuilder result = new StringBuilder();
        for (final NameValuePair parameter : parameters) {
            final String encodedName = encode(parameter.getName(), charset);
            final String encodedValue = encode(parameter.getValue(), charset);
            if (result.length() > 0) {
                result.append(PARAMETER_SEPARATOR);
            }
            result.append(encodedName);
            if (encodedValue != null) {
                result.append(NAME_VALUE_SEPARATOR);
                result.append(encodedValue);
            }
        }
        return result.toString();
    }

    private static final BitSet UNRESERVED   = new BitSet(256);
    private static final BitSet PUNCT        = new BitSet(256);
    private static final BitSet SAFE         = new BitSet(256);
    private static final BitSet PATHSAFE     = new BitSet(256);

    static {
        // unreserved chars
        // alpha characters
        for (int i = 'a'; i <= 'z'; i++) {
            UNRESERVED.set(i);
        }
        for (int i = 'A'; i <= 'Z'; i++) {
            UNRESERVED.set(i);
        }
        // numeric characters
        for (int i = '0'; i <= '9'; i++) {
            UNRESERVED.set(i);
        }
        UNRESERVED.set('_');
        UNRESERVED.set('-');
        UNRESERVED.set('!');
        UNRESERVED.set('.');
        UNRESERVED.set('~');
        UNRESERVED.set('\'');
        UNRESERVED.set('(');
        UNRESERVED.set(')');
        UNRESERVED.set('*');
        // punct chats
        PUNCT.set(',');
        PUNCT.set(';');
        PUNCT.set(':');
        PUNCT.set('$');
        PUNCT.set('&');
        PUNCT.set('+');
        PUNCT.set('=');
        // URL path safe
        SAFE.or(UNRESERVED);
        SAFE.or(PUNCT);
        // URL path safe
        PATHSAFE.or(UNRESERVED);
        PATHSAFE.or(PUNCT);
        PATHSAFE.set('/');
        PATHSAFE.set('@');
    }

    private static final int RADIX = 16;

    private static String urlencode(
            final String content, final Charset charset, final BitSet safechars) {
        if (content == null) {
            return null;
        }
        StringBuilder buf = new StringBuilder();
        ByteBuffer bb = charset.encode(content);
        while (bb.hasRemaining()) {
            int b = bb.get() & 0xff;
            if (safechars.get(b)) {
                buf.append((char) b);
            } else {
                buf.append("%");
                char hex1 = Character.toUpperCase(Character.forDigit((b >> 4) & 0xF, RADIX));
                char hex2 = Character.toUpperCase(Character.forDigit(b & 0xF, RADIX));
                buf.append(hex1);
                buf.append(hex2);
            }
        }
        return buf.toString();
    }

    private static String urldecode(
            final String content, final Charset charset) {
        if (content == null) {
            return null;
        }
        ByteBuffer bb = ByteBuffer.allocate(content.length());
        CharBuffer cb = CharBuffer.wrap(content);
        while (cb.hasRemaining()) {
            char c = cb.get();
            if (c == '%' && cb.remaining() >= 2) {
                char uc = cb.get();
                char lc = cb.get();
                int u = Character.digit(uc, 16);
                int l = Character.digit(lc, 16);
                if (u != -1 && l != -1) {
                    bb.put((byte) ((u << 4) + l));
                } else {
                    bb.put((byte) '%');
                    bb.put((byte) uc);
                    bb.put((byte) lc);
                }
            } else {
                bb.put((byte) c);
            }
        }
        bb.flip();
        return charset.decode(bb).toString();
    }

    private static String decode (final String content, final String charset) {
        if (content == null) {
            return null;
        }
        return urldecode(content, charset != null ? Charset.forName(charset) : Consts.UTF_8);
    }

    private static String decode (final String content, final Charset charset) {
        if (content == null) {
            return null;
        }
        return urldecode(content, charset != null ? charset : Consts.UTF_8);
    }

    private static String encode(final String content, final String charset) {
        if (content == null) {
            return null;
        }
        return urlencode(content, charset != null ? Charset.forName(charset) : Consts.UTF_8, UNRESERVED);
    }

    private static String encode(final String content, final Charset charset) {
        if (content == null) {
            return null;
        }
        return urlencode(content, charset != null ? charset : Consts.UTF_8, UNRESERVED);
    }

    static String enc(final String content, final Charset charset) {
        return urlencode(content, charset, SAFE);
    }

    static String encPath(final String content, final Charset charset) {
        return urlencode(content, charset, PATHSAFE);
    }

}
