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
package org.apache.hc.client5.http.impl.async;

import java.net.IDN;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Validates and evaluates the URL pattern carried by a
 * {@code Use-As-Dictionary} response header.
 */
interface CompressionDictionaryUrlPatternMatcher {

    /**
     * Validates a pattern using the dictionary request URL as its base URL.
     *
     * @param pattern the URL pattern.
     * @param dictionaryUri the dictionary request URL.
     * @return whether the pattern is valid and confined to the dictionary origin.
     */
    boolean isValid(String pattern, URI dictionaryUri);

    /**
     * Evaluates a pattern using the outbound request URL as its base URL.
     *
     * @param pattern the URL pattern.
     * @param dictionaryUri the dictionary request URL.
     * @param requestUri the outbound request URL.
     * @return whether the outbound URL matches.
     */
    boolean matches(String pattern, URI dictionaryUri, URI requestUri);
}

/**
 * URLPattern matcher for RFC 9842. Regular-expression groups are rejected,
 * while wildcard, named and brace groups remain available.
 */
final class DefaultCompressionDictionaryUrlPatternMatcher
        implements CompressionDictionaryUrlPatternMatcher {

    @Override
    public boolean isValid(final String pattern, final URI dictionaryUri) {
        if (pattern == null || pattern.isEmpty() || !isAbsoluteHttpUri(dictionaryUri)) {
            return false;
        }
        try {
            UrlPattern.compile(pattern, dictionaryUri);
            return true;
        } catch (final IllegalArgumentException ex) {
            return false;
        }
    }

    @Override
    public boolean matches(
            final String pattern,
            final URI dictionaryUri,
            final URI requestUri) {
        if (!isValid(pattern, dictionaryUri)
                || !isAbsoluteHttpUri(requestUri)
                || !sameOrigin(dictionaryUri, requestUri)) {
            return false;
        }
        try {
            // RFC 9842 section 2.2.2 deliberately uses the outbound URL as baseURL.
            return UrlPattern.compile(pattern, requestUri).matches(requestUri);
        } catch (final IllegalArgumentException ex) {
            return false;
        }
    }

    private static boolean isAbsoluteHttpUri(final URI uri) {
        return uri != null
                && uri.isAbsolute()
                && uri.getHost() != null
                && ("http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme()));
    }

    private static boolean sameOrigin(final URI first, final URI second) {
        return first.getScheme().equalsIgnoreCase(second.getScheme())
                && normalizeHost(first.getHost()).equals(normalizeHost(second.getHost()))
                && effectivePort(first) == effectivePort(second);
    }

    private static int effectivePort(final URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static String normalizeHost(final String host) {
        final String value = host.length() > 1 && host.charAt(0) == '['
                && host.charAt(host.length() - 1) == ']'
                ? host.substring(1, host.length() - 1)
                : host;
        if (value.indexOf(':') >= 0) {
            return value.toLowerCase(Locale.ROOT);
        }
        return IDN.toASCII(value).toLowerCase(Locale.ROOT);
    }

    /**
     * Compiled URLPattern string. Constructor-string parsing and component
     * matching are intentionally kept out of the dictionary selection policy.
     */
    private static final class UrlPattern {

        private final Pattern protocol;
        private final Pattern username;
        private final Pattern password;
        private final Pattern hostname;
        private final Pattern port;
        private final Pattern pathname;
        private final Pattern search;
        private final Pattern hash;

        private UrlPattern(final ConstructorParts parts) {
            this.protocol = compileComponent(parts.protocol, Component.PROTOCOL);
            this.username = compileComponent(parts.username, Component.DEFAULT);
            this.password = compileComponent(parts.password, Component.DEFAULT);
            this.hostname = compileComponent(parts.hostname, Component.HOSTNAME);
            this.port = compileComponent(parts.port, Component.DEFAULT);
            this.pathname = compileComponent(parts.pathname, Component.PATHNAME);
            this.search = compileComponent(parts.search, Component.DEFAULT);
            this.hash = compileComponent(parts.hash, Component.DEFAULT);
        }

        static UrlPattern compile(final String input, final URI baseUri) {
            return new UrlPattern(ConstructorParts.parse(input, baseUri));
        }

        boolean matches(final URI uri) {
            final UserInfo userInfo = UserInfo.from(uri);
            return matches(protocol, uri.getScheme().toLowerCase(Locale.ROOT))
                    && matches(username, userInfo.username)
                    && matches(password, userInfo.password)
                    && matches(hostname, normalizeHost(uri.getHost()))
                    && matches(port, normalizedPort(uri))
                    && matches(pathname, rawPath(uri))
                    && matches(search, valueOrEmpty(uri.getRawQuery()))
                    && matches(hash, valueOrEmpty(uri.getRawFragment()));
        }

        private static boolean matches(final Pattern pattern, final String value) {
            return pattern == null || pattern.matcher(value).matches();
        }
    }

    /** URLPattern constructor-string components; {@code null} means wildcard. */
    private static final class ConstructorParts {

        private String protocol;
        private String username;
        private String password;
        private String hostname;
        private String port;
        private String pathname;
        private String search;
        private String hash;

        static ConstructorParts parse(final String input, final URI baseUri) {
            if (input == null || input.isEmpty() || baseUri == null || !baseUri.isAbsolute()) {
                throw new IllegalArgumentException("Invalid URL pattern");
            }
            rejectRegexpGroups(input);

            final ConstructorParts result = new ConstructorParts();
            final int authorityMarker = findAuthorityMarker(input);
            if (authorityMarker >= 0) {
                result.parseAbsolute(input, authorityMarker);
            } else if (input.startsWith("//")) {
                result.protocol = baseUri.getScheme().toLowerCase(Locale.ROOT);
                result.parseAuthorityAndTail(input, 2);
            } else {
                result.protocol = baseUri.getScheme().toLowerCase(Locale.ROOT);
                result.hostname = normalizeHost(baseUri.getHost());
                result.port = normalizedPort(baseUri);
                result.parseRelativeTail(input, baseUri);
            }
            return result;
        }

        private void parseAbsolute(final String input, final int authorityMarker) {
            final String scheme = input.substring(0, authorityMarker);
            if (scheme.isEmpty()
                    || !containsComponentPatternSyntax(scheme) && !isScheme(scheme)) {
                throw new IllegalArgumentException("Invalid URL pattern protocol");
            }
            protocol = scheme.toLowerCase(Locale.ROOT);
            parseAuthorityAndTail(input, authorityMarker + 3);
        }

        private void parseAuthorityAndTail(
                final String input,
                final int authorityStart) {
            final int bracedSlash = findBracedSlash(input, authorityStart);
            if (bracedSlash >= 0) {
                final String prefix = input.substring(authorityStart, bracedSlash);
                final int openBrace = prefix.lastIndexOf('{');
                parseAuthority(prefix.substring(0, openBrace)
                        + prefix.substring(openBrace + 1));
                pathname = null;
                search = null;
                hash = null;
                return;
            }
            final int tailStart = findTailStart(input, authorityStart);
            final String authority = input.substring(
                    authorityStart, tailStart >= 0 ? tailStart : input.length());
            parseAuthority(authority);

            if (tailStart < 0) {
                pathname = null;
                search = null;
                hash = null;
            } else {
                final String tail = input.substring(tailStart);
                if (tail.charAt(0) == '?') {
                    pathname = "/";
                    parseTail(tail, pathname);
                } else if (tail.charAt(0) == '#') {
                    pathname = "/";
                    search = "";
                    hash = tail.substring(1);
                } else {
                    parseTail(tail, null);
                }
            }
        }

        private static int findBracedSlash(
                final String value,
                final int start) {
            int braces = 0;
            boolean escaped = false;
            for (int i = start; i < value.length(); i++) {
                final char ch = value.charAt(i);
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '{') {
                    braces++;
                } else if (ch == '}') {
                    braces--;
                } else if (ch == '/' && braces > 0) {
                    return i;
                } else if (braces == 0
                        && (ch == '#' || ch == '?' && !isGroupModifier(value, i))) {
                    return -1;
                }
            }
            return -1;
        }

        private void parseAuthority(final String authority) {
            if (authority.isEmpty()) {
                throw new IllegalArgumentException("URL pattern has no authority");
            }

            String hostPort = authority;
            final int at = authority.lastIndexOf('@');
            if (at >= 0) {
                final String userInfo = authority.substring(0, at);
                hostPort = authority.substring(at + 1);
                final int colon = userInfoDelimiter(userInfo);
                if (colon >= 0) {
                    final String parsedUsername = userInfo.substring(0, colon);
                    username = parsedUsername.endsWith("\\")
                            ? parsedUsername.substring(0, parsedUsername.length() - 1)
                            : parsedUsername;
                    password = userInfo.substring(colon + 1);
                } else {
                    username = userInfo;
                    password = "";
                }
            }

            final HostPort parsed = HostPort.parse(hostPort, protocol);
            hostname = parsed.hostname;
            port = parsed.port;
        }

        private static int userInfoDelimiter(final String value) {
            boolean escaped = false;
            for (int i = 0; i < value.length(); i++) {
                final char ch = value.charAt(i);
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    if (i + 1 < value.length() && value.charAt(i + 1) == ':') {
                        return i + 1;
                    }
                    escaped = true;
                } else if (ch == ':') {
                    if (i == 0 && i + 1 < value.length()
                            && isNameStart(value.charAt(i + 1))) {
                        i++;
                        while (i + 1 < value.length()
                                && isNameChar(value.charAt(i + 1))) {
                            i++;
                        }
                    } else {
                        return i;
                    }
                }
            }
            return -1;
        }

        private void parseRelativeTail(final String input, final URI baseUri) {
            if (input.charAt(0) == '?') {
                pathname = rawPath(baseUri);
                parseTail(input, pathname);
            } else if (input.charAt(0) == '#') {
                pathname = rawPath(baseUri);
                search = valueOrEmpty(baseUri.getRawQuery());
                hash = input.substring(1);
            } else {
                parseTail(input, resolvePath(input, baseUri));
            }
        }

        private void parseTail(final String tail, final String resolvedPath) {
            final int hashIndex = findDelimiter(tail, '#');
            final String beforeHash = hashIndex >= 0 ? tail.substring(0, hashIndex) : tail;
            final int queryIndex = findQueryDelimiter(beforeHash);
            final boolean escapedQuery = queryIndex > 0
                    && beforeHash.charAt(queryIndex - 1) == '\\';

            final String path = queryIndex >= 0
                    ? beforeHash.substring(0, escapedQuery ? queryIndex - 1 : queryIndex)
                    : beforeHash;
            if (resolvedPath != null) {
                pathname = resolvedPath;
            } else if (!path.isEmpty()) {
                pathname = normalizePath(path);
            }
            if (queryIndex >= 0) {
                search = beforeHash.substring(queryIndex + 1);
            }
            if (hashIndex >= 0) {
                hash = tail.substring(hashIndex + 1);
            }
        }

        private static String resolvePath(final String input, final URI baseUri) {
            final int hashIndex = findDelimiter(input, '#');
            final String beforeHash = hashIndex >= 0 ? input.substring(0, hashIndex) : input;
            final int queryIndex = findQueryDelimiter(beforeHash);
            final String path = queryIndex >= 0 ? beforeHash.substring(0, queryIndex) : beforeHash;
            if (path.startsWith("/")) {
                return path;
            }
            final String basePath = rawPath(baseUri);
            final int slash = basePath.lastIndexOf('/');
            return normalizePath(
                    (slash >= 0 ? basePath.substring(0, slash + 1) : "/") + path);
        }
    }

    private static final class HostPort {
        final String hostname;
        final String port;

        HostPort(final String hostname, final String port) {
            this.hostname = hostname;
            this.port = port;
        }

        static HostPort parse(final String value, final String protocol) {
            if (value.isEmpty()) {
                throw new IllegalArgumentException("URL pattern has no hostname");
            }
            final String host;
            String port = "";
            if (value.charAt(0) == '[') {
                final int end = value.indexOf(']');
                if (end < 0) {
                    throw new IllegalArgumentException("Invalid IPv6 hostname");
                }
                host = value.substring(1, end);
                if (end + 1 < value.length()) {
                    if (value.charAt(end + 1) != ':') {
                        throw new IllegalArgumentException("Invalid authority");
                    }
                    port = value.substring(end + 2);
                }
            } else {
                final int colon = portDelimiter(value);
                if (colon >= 0) {
                    host = value.substring(0, colon);
                    port = value.substring(colon + 1);
                } else {
                    host = value;
                }
            }
            if (host.isEmpty()) {
                throw new IllegalArgumentException("URL pattern has no hostname");
            }
            if (!port.isEmpty() && !containsComponentPatternSyntax(port)) {
                final int number;
                try {
                    number = Integer.parseInt(port);
                } catch (final NumberFormatException ex) {
                    throw new IllegalArgumentException("Invalid URL pattern port", ex);
                }
                if (number < 0 || number > 65535) {
                    throw new IllegalArgumentException("Invalid URL pattern port");
                }
                if (number == defaultPort(protocol)) {
                    port = "";
                }
            }
            final String normalizedHost = containsComponentPatternSyntax(host)
                    ? host.toLowerCase(Locale.ROOT)
                    : normalizeHost(host);
            return new HostPort(normalizedHost, port);
        }

        private static int portDelimiter(final String value) {
            final int namedPort = value.indexOf("::");
            if (namedPort > 0) {
                return namedPort;
            }
            final int colon = value.lastIndexOf(':');
            if (colon <= 0 || colon + 1 >= value.length()) {
                return -1;
            }
            final char first = value.charAt(colon + 1);
            return isDigit(first) || first == '*' || first == '{'
                    ? colon
                    : -1;
        }
    }

    private enum Component {
        DEFAULT('\0', false),
        PROTOCOL('\0', true),
        HOSTNAME('.', true),
        PATHNAME('/', false);

        final char delimiter;
        final boolean lowerCase;

        Component(final char delimiter, final boolean lowerCase) {
            this.delimiter = delimiter;
            this.lowerCase = lowerCase;
        }
    }

    private static Pattern compileComponent(final String input, final Component component) {
        if (input == null) {
            return null;
        }
        try {
            final Set<String> names = new HashSet<>();
            final String regex = new ComponentCompiler(input, component, names).compile();
            return Pattern.compile("^(?:" + regex + ")$");
        } catch (final PatternSyntaxException ex) {
            throw new IllegalArgumentException("Invalid URL pattern", ex);
        }
    }

    /** Compiles URLPattern groups without accepting custom regular expressions. */
    private static final class ComponentCompiler {

        private final String input;
        private final Component component;
        private final Set<String> names;
        private int pos;

        ComponentCompiler(
                final String input,
                final Component component,
                final Set<String> names) {
            this.input = component.lowerCase ? input.toLowerCase(Locale.ROOT) : input;
            this.component = component;
            this.names = names;
        }

        String compile() {
            final String result = compileSequence(false);
            if (pos != input.length()) {
                throw new IllegalArgumentException("Unexpected URL pattern group terminator");
            }
            return result;
        }

        private String compileSequence(final boolean grouped) {
            final StringBuilder result = new StringBuilder();
            final StringBuilder literal = new StringBuilder();
            while (pos < input.length()) {
                final char ch = input.charAt(pos++);
                if (ch == '\\') {
                    if (pos >= input.length()) {
                        throw new IllegalArgumentException("Dangling URL pattern escape");
                    }
                    literal.append(input.charAt(pos++));
                } else if (ch == '}') {
                    if (!grouped) {
                        throw new IllegalArgumentException("Unexpected URL pattern group terminator");
                    }
                    appendLiteral(result, literal);
                    return result.toString();
                } else if (ch == '{') {
                    appendLiteral(result, literal);
                    final String body = compileSequence(true);
                    appendModified(result, body, modifier());
                } else if (ch == ':') {
                    final int start = pos;
                    while (pos < input.length() && isNameChar(input.charAt(pos))) {
                        pos++;
                    }
                    if (start == pos || !isNameStart(input.charAt(start))) {
                        throw new IllegalArgumentException("URL pattern group has no name");
                    }
                    final String name = input.substring(start, pos);
                    if (!names.add(name)) {
                        throw new IllegalArgumentException("Duplicate URL pattern group name");
                    }
                    if (pos < input.length() && input.charAt(pos) == '(') {
                        throw new IllegalArgumentException("Regular-expression groups are not permitted");
                    }
                    final char modifier = modifier();
                    final String prefix = detachPrefix(literal, modifier);
                    appendLiteral(result, literal);
                    final String body = prefix + defaultGroupRegex(component.delimiter);
                    appendModified(result, body, modifier);
                } else if (ch == '*') {
                    appendLiteral(result, literal);
                    appendModified(result, ".*", modifier());
                } else if (ch == '(' || ch == ')') {
                    throw new IllegalArgumentException("Regular-expression groups are not permitted");
                } else if (ch == '?' || ch == '+') {
                    throw new IllegalArgumentException("URL pattern modifier has no group");
                } else {
                    literal.append(ch);
                }
            }
            if (grouped) {
                throw new IllegalArgumentException("Unterminated URL pattern group");
            }
            appendLiteral(result, literal);
            return result.toString();
        }

        private char modifier() {
            if (pos < input.length()) {
                final char ch = input.charAt(pos);
                if (ch == '?' || ch == '*' || ch == '+') {
                    pos++;
                    return ch;
                }
            }
            return '\0';
        }

        private String detachPrefix(final StringBuilder literal, final char modifier) {
            if (modifier != '\0'
                    && component.delimiter != '\0'
                    && literal.length() > 0
                    && literal.charAt(literal.length() - 1) == component.delimiter) {
                literal.setLength(literal.length() - 1);
                return Pattern.quote(String.valueOf(component.delimiter));
            }
            return "";
        }

        private static void appendLiteral(
                final StringBuilder result,
                final StringBuilder literal) {
            if (literal.length() > 0) {
                result.append(Pattern.quote(literal.toString()));
                literal.setLength(0);
            }
        }

        private static void appendModified(
                final StringBuilder result,
                final String body,
                final char modifier) {
            result.append("(?:").append(body).append(')');
            if (modifier != '\0') {
                result.append(modifier);
            }
        }
    }

    private static String defaultGroupRegex(final char delimiter) {
        return delimiter == '\0'
                ? ".+?"
                : "[^" + delimiter + "]+?";
    }

    private static void rejectRegexpGroups(final String value) {
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            final char ch = value.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else if (ch == '(' || ch == ')') {
                throw new IllegalArgumentException("Regular-expression groups are not permitted");
            }
        }
        if (escaped) {
            throw new IllegalArgumentException("Dangling URL pattern escape");
        }
    }

    private static int findAuthorityMarker(final String value) {
        int braces = 0;
        boolean escaped = false;
        for (int i = 0; i + 2 < value.length(); i++) {
            final char ch = value.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else if (ch == '{') {
                braces++;
            } else if (ch == '}') {
                braces--;
            } else if (braces == 0
                    && ch == ':'
                    && value.charAt(i + 1) == '/'
                    && value.charAt(i + 2) == '/') {
                return i;
            }
        }
        return -1;
    }

    private static int findTailStart(final String value, final int start) {
        int braces = 0;
        boolean escaped = false;
        for (int i = start; i < value.length(); i++) {
            final char ch = value.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else if (ch == '{') {
                braces++;
            } else if (ch == '}') {
                braces--;
            } else if (braces == 0
                    && (ch == '/' || ch == '#'
                            || ch == '?' && !isGroupModifier(value, i))) {
                return i;
            }
        }
        return -1;
    }

    private static int findDelimiter(final String value, final char delimiter) {
        int braces = 0;
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            final char ch = value.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else if (ch == '{') {
                braces++;
            } else if (ch == '}') {
                braces--;
            } else if (braces == 0 && ch == delimiter) {
                return i;
            }
        }
        return -1;
    }

    private static int findQueryDelimiter(final String value) {
        int braces = 0;
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            final char ch = value.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (ch == '\\' && i + 1 < value.length()
                    && value.charAt(i + 1) == '?') {
                return i + 1;
            } else if (ch == '\\') {
                escaped = true;
            } else if (ch == '{') {
                braces++;
            } else if (ch == '}') {
                braces--;
            } else if (braces == 0 && ch == '?' && !isGroupModifier(value, i)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isGroupModifier(final String value, final int questionIndex) {
        if (questionIndex == 0) {
            return false;
        }
        final char previous = value.charAt(questionIndex - 1);
        if (previous == '}' || previous == '*') {
            return true;
        }
        int i = questionIndex - 1;
        while (i >= 0 && isNameChar(value.charAt(i))) {
            i--;
        }
        return i >= 0
                && value.charAt(i) == ':'
                && i + 1 < questionIndex
                && isNameStart(value.charAt(i + 1));
    }

    private static boolean containsComponentPatternSyntax(final String value) {
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            final char ch = value.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else if (ch == '*' || ch == ':' || ch == '{' || ch == '}'
                    || ch == '(' || ch == ')' || ch == '?' || ch == '+') {
                return true;
            }
        }
        return escaped;
    }

    private static boolean isScheme(final String value) {
        if (value.isEmpty() || !isAlpha(value.charAt(0))) {
            return false;
        }
        for (int i = 1; i < value.length(); i++) {
            final char ch = value.charAt(i);
            if (!isAlpha(ch) && !isDigit(ch) && ch != '+' && ch != '-' && ch != '.') {
                return false;
            }
        }
        return true;
    }

    private static boolean isNameChar(final char ch) {
        return isAlpha(ch) || isDigit(ch) || ch == '_';
    }

    private static boolean isNameStart(final char ch) {
        return isAlpha(ch) || ch == '_';
    }

    private static boolean isAlpha(final char ch) {
        return ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z';
    }

    private static boolean isDigit(final char ch) {
        return ch >= '0' && ch <= '9';
    }

    private static int defaultPort(final String scheme) {
        return "https".equalsIgnoreCase(scheme) ? 443
                : "http".equalsIgnoreCase(scheme) ? 80 : -1;
    }

    private static String normalizedPort(final URI uri) {
        return uri.getPort() < 0 || uri.getPort() == defaultPort(uri.getScheme())
                ? ""
                : Integer.toString(uri.getPort());
    }

    private static String rawPath(final URI uri) {
        final String value = URI.create(uri.toASCIIString()).normalize().getRawPath();
        return value == null || value.isEmpty() ? "/" : value;
    }

    private static String normalizePath(final String value) {
        final boolean absolute = value.startsWith("/");
        final String[] segments = value.split("/", -1);
        final List<String> normalized = new ArrayList<>();
        for (int i = absolute ? 1 : 0; i < segments.length; i++) {
            final String segment = segments[i];
            if (isSingleDot(segment)) {
                continue;
            }
            if (isDoubleDot(segment)) {
                if (!normalized.isEmpty()) {
                    normalized.remove(normalized.size() - 1);
                }
            } else {
                normalized.add(segment);
            }
        }
        final StringBuilder result = new StringBuilder(value.length());
        if (absolute) {
            result.append('/');
        }
        for (int i = 0; i < normalized.size(); i++) {
            if (i > 0) {
                result.append('/');
            }
            result.append(normalized.get(i));
        }
        return result.length() > 0 ? result.toString() : absolute ? "/" : "";
    }

    private static boolean isSingleDot(final String value) {
        return ".".equals(value) || "%2e".equalsIgnoreCase(value);
    }

    private static boolean isDoubleDot(final String value) {
        return "..".equals(value)
                || ".%2e".equalsIgnoreCase(value)
                || "%2e.".equalsIgnoreCase(value)
                || "%2e%2e".equalsIgnoreCase(value);
    }

    private static String valueOrEmpty(final String value) {
        return value != null ? value : "";
    }

    private static final class UserInfo {
        final String username;
        final String password;

        UserInfo(final String username, final String password) {
            this.username = username;
            this.password = password;
        }

        static UserInfo from(final URI uri) {
            final String value = uri.getRawUserInfo();
            if (value == null) {
                return new UserInfo("", "");
            }
            final int colon = value.indexOf(':');
            return colon >= 0
                    ? new UserInfo(value.substring(0, colon), value.substring(colon + 1))
                    : new UserInfo(value, "");
        }
    }
}
