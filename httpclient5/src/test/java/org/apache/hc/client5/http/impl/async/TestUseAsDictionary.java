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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hc.core5.http.ParseException;
import org.junit.jupiter.api.Test;

class TestUseAsDictionary {

    private static String repeat(final char ch, final int count) {
        final StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(ch);
        }
        return builder.toString();
    }

    @Test
    void parseWellFormedValue() throws ParseException {
        final UseAsDictionary dictionary = UseAsDictionary.parse("match=\"/app/*\", id=\"v1\", type=raw");
        assertEquals("/app/*", dictionary.getMatch());
        assertEquals("v1", dictionary.getId());
        assertTrue(dictionary.isSupported());
    }

    @Test
    void defaultTypeIsRawWhenAbsent() throws ParseException {
        final UseAsDictionary dictionary = UseAsDictionary.parse("match=\"/app/*\", id=\"v1\"");
        assertEquals("/app/*", dictionary.getMatch());
        assertEquals("v1", dictionary.getId());
        assertTrue(dictionary.isSupported());
    }

    @Test
    void typeOtherIsNotSupported() throws ParseException {
        final UseAsDictionary dictionary = UseAsDictionary.parse("match=\"/app/*\", type=other");
        assertEquals("/app/*", dictionary.getMatch());
        assertFalse(dictionary.isSupported());
    }

    @Test
    void typeTokenIsCaseSensitive() throws ParseException {
        final UseAsDictionary dictionary = UseAsDictionary.parse("match=\"/app/*\", type=RAW");
        assertFalse(dictionary.isSupported());
    }

    @Test
    void idDefaultsToEmpty() throws ParseException {
        final UseAsDictionary dictionary = UseAsDictionary.parse("match=\"/app/*\"");
        assertEquals("", dictionary.getId());
        assertTrue(dictionary.isSupported());
    }

    @Test
    void missingMatchThrows() {
        assertThrows(ParseException.class, () -> UseAsDictionary.parse("id=\"v1\", type=raw"));
    }

    @Test
    void emptyMatchStringThrows() {
        assertThrows(ParseException.class, () -> UseAsDictionary.parse("match=\"\""));
    }

    @Test
    void nullValueThrows() {
        assertThrows(NullPointerException.class, () -> UseAsDictionary.parse(null));
    }

    @Test
    void blankValueThrows() {
        assertThrows(IllegalArgumentException.class, () -> UseAsDictionary.parse("   "));
    }

    @Test
    void emptyValueThrows() {
        assertThrows(IllegalArgumentException.class, () -> UseAsDictionary.parse(""));
    }

    @Test
    void idExactly1024CharactersIsAccepted() throws ParseException {
        final String id = repeat('a', 1024);
        final UseAsDictionary dictionary = UseAsDictionary.parse("match=\"/x\", id=\"" + id + "\"");
        assertEquals(1024, dictionary.getId().length());
    }

    @Test
    void idExceeding1024CharactersThrows() {
        final String id = repeat('a', 1025);
        assertThrows(ParseException.class, () -> UseAsDictionary.parse("match=\"/x\", id=\"" + id + "\""));
    }

    @Test
    void escapedQuoteInsideStringIsUnescaped() throws ParseException {
        final UseAsDictionary dictionary = UseAsDictionary.parse("match=\"a\\\"b\"");
        assertEquals("a\"b", dictionary.getMatch());
    }

    @Test
    void escapedBackslashInsideStringIsUnescaped() throws ParseException {
        final UseAsDictionary dictionary = UseAsDictionary.parse("match=\"a\\\\b\"");
        assertEquals("a\\b", dictionary.getMatch());
    }

    @Test
    void invalidEscapeSequenceThrows() {
        assertThrows(ParseException.class, () -> UseAsDictionary.parse("match=\"a\\b\""));
    }

    @Test
    void controlCharacterInsideStringThrows() {
        assertThrows(ParseException.class, () -> UseAsDictionary.parse("match=\"a\tb\""));
    }

    @Test
    void nonAsciiCharacterInsideStringThrows() {
        final String value = "match=\"" + 'é' + "\"";
        assertThrows(ParseException.class, () -> UseAsDictionary.parse(value));
    }

    @Test
    void unquotedMatchThrows() {
        assertThrows(ParseException.class, () -> UseAsDictionary.parse("match=raw"));
    }

    @Test
    void unbalancedQuoteThrows() {
        assertThrows(ParseException.class, () -> UseAsDictionary.parse("match=\"abc"));
    }

    @Test
    void unbalancedOpenParenThrows() {
        assertThrows(ParseException.class, () -> UseAsDictionary.parse("match=\"/x\", dummy=(a"));
    }

    @Test
    void unbalancedCloseParenThrows() {
        assertThrows(ParseException.class, () -> UseAsDictionary.parse("match=\"/x\", dummy=a)"));
    }

    @Test
    void commaInsideQuotesIsNotSeparator() throws ParseException {
        final UseAsDictionary dictionary = UseAsDictionary.parse("match=\"a,b\", id=\"c\"");
        assertEquals("a,b", dictionary.getMatch());
        assertEquals("c", dictionary.getId());
    }

    @Test
    void commaInsideInnerListIsInvalidStructuredFieldSyntax() {
        assertThrows(ParseException.class,
                () -> UseAsDictionary.parse("dummy=(a, b), match=\"/x\", type=raw"));
    }

    @Test
    void matchDestParsesInnerListOfStrings() throws ParseException {
        final UseAsDictionary dictionary = UseAsDictionary.parse(
                "match=\"/x\", match-dest=(\"document\" \"script\")");
        assertEquals(2, dictionary.getMatchDest().size());
        assertEquals("document", dictionary.getMatchDest().get(0));
        assertEquals("script", dictionary.getMatchDest().get(1));
    }

    @Test
    void memberWithoutEqualsIsIgnored() throws ParseException {
        final UseAsDictionary dictionary = UseAsDictionary.parse("foo, match=\"/x\"");
        assertEquals("/x", dictionary.getMatch());
    }

    @Test
    void parametersAfterSemicolonAreIgnored() throws ParseException {
        final UseAsDictionary dictionary = UseAsDictionary.parse("match=\"/x\", type=raw;foo=bar");
        assertTrue(dictionary.isSupported());
    }

    @Test
    void spaceAfterParameterSemicolonIsAccepted() throws ParseException {
        final UseAsDictionary dictionary = UseAsDictionary.parse("match=\"/x\"; foo=bar");
        assertEquals("/x", dictionary.getMatch());
    }

    @Test
    void parametersAfterSemicolonAreIgnoredForUnsupportedType() throws ParseException {
        final UseAsDictionary dictionary = UseAsDictionary.parse("match=\"/x\", type=other;foo=bar");
        assertFalse(dictionary.isSupported());
    }

    @Test
    void emptyTypeTokenThrows() {
        assertThrows(ParseException.class, () -> UseAsDictionary.parse("match=\"/x\", type="));
    }

    @Test
    void invalidTypeTokenCharacterThrows() {
        assertThrows(ParseException.class, () -> UseAsDictionary.parse("match=\"/x\", type=a b"));
    }

    @Test
    void integerLongerThanFifteenDigitsInvalidatesWholeField() {
        assertThrows(ParseException.class,
                () -> UseAsDictionary.parse("match=\"/x\", extension=1234567890123456"));
    }

    @Test
    void decimalIntegerPartLongerThanTwelveDigitsInvalidatesWholeField() {
        assertThrows(ParseException.class,
                () -> UseAsDictionary.parse("match=\"/x\", extension=1234567890123.1"));
    }

    @Test
    void dateLongerThanFifteenDigitsInvalidatesWholeField() {
        assertThrows(ParseException.class,
                () -> UseAsDictionary.parse("match=\"/x\", extension=@1234567890123456"));
    }

    @Test
    void malformedUtf8DisplayStringInvalidatesWholeField() {
        assertThrows(ParseException.class,
                () -> UseAsDictionary.parse("match=\"/x\", extension=%\"%ff\""));
    }

    @Test
    void validUtf8DisplayStringIsAcceptedAsAnExtension() throws ParseException {
        final UseAsDictionary dictionary = UseAsDictionary.parse(
                "match=\"/x\", extension=%\"caf%c3%a9\"");
        assertEquals("/x", dictionary.getMatch());
    }

    @Test
    void leadingTabIsNotAccepted() {
        assertThrows(ParseException.class, () -> UseAsDictionary.parse("\tmatch=\"/x\""));
    }
}
