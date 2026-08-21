package org.apache.hc.client5.http.impl.async;

import java.net.URI;

interface CompressionDictionaryUrlPatternMatcher {

    boolean isValid(
            String pattern,
            URI dictionaryUri);

    boolean matches(
            String pattern,
            URI dictionaryUri,
            URI requestUri);
}