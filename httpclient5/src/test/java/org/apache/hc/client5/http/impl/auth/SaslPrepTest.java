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

package org.apache.hc.client5.http.impl.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hc.client5.http.impl.ScramException;
import org.junit.jupiter.api.Test;


class SaslPrepTest {

    @Test
    void testValidString() throws ScramException {
        // A valid string with no prohibited or unassigned characters
        final String validInput = "ValidTestString123";
        final String result = SaslPrep.INSTANCE.prepAsStoredString(validInput);
        assertEquals(validInput, result, "Valid string should pass without modifications.");
    }

    @Test
    void testStringWithUnassignedCodePoint() {
        // A string with an unassigned Unicode code point
        final String invalidInput = "Invalid\u0221String";
        final ScramException exception = assertThrows(ScramException.class, () -> {
            SaslPrep.INSTANCE.prepAsStoredString(invalidInput);
        });
        assertTrue(exception.getMessage().contains("unassigned code point"), "Exception should indicate unassigned code point.");
    }

    @Test
    void testStringWithProhibitedCharacter() {
        // A string with prohibited characters
        final String invalidInput = "Invalid\u0000String";
        final ScramException exception = assertThrows(ScramException.class, () -> SaslPrep.INSTANCE.prepAsQueryString(invalidInput));
        assertTrue(exception.getMessage().contains("prohibited character"), "Exception should indicate prohibited character.");
    }

    @Test
    void testStringWithBidiViolation() {
        // A string with bidirectional rule violation
        final String invalidInput = "\u0627InvalidString\u0041"; // RandALCat + LCat
        final ScramException exception = assertThrows(ScramException.class, () -> SaslPrep.INSTANCE.prepAsQueryString(invalidInput));
        assertTrue(exception.getMessage().contains("RandALCat"), "Exception should indicate Bidi rule violation.");
    }

    @Test
    void testUnicodeNormalizationFailure() {
        // Invalid characters for SASLprep (e.g., unassigned Unicode code points)
        final String invalidUsername = "\uFFF9"; // U+FFF9 (interlinear annotation anchor)
        final String invalidPassword = "\uFFFA"; // U+FFFA (interlinear annotation separator)

        assertThrows(ScramException.class,
                () -> SaslPrep.INSTANCE.prepAsQueryString(invalidUsername), "Invalid username should throw an exception");

        assertThrows(ScramException.class,
                () -> SaslPrep.INSTANCE.prepAsStoredString(invalidPassword), "Invalid password should throw an exception");
    }


    @Test
    void testMixedRandALCatAndLCat() {
        // A string with both RandALCat and LCat characters
        final String invalidRTLString = "\u0627Invalid\u0041"; // RandALCat + LCat
        final ScramException exception = assertThrows(ScramException.class, () -> SaslPrep.INSTANCE.prepAsQueryString(invalidRTLString));
        assertTrue(exception.getMessage().contains("RandALCat"), "Exception should indicate mixed RandALCat and LCat.");
    }

    @Test
    void testNotEndRandALCat() {
        // A string with both RandALCat and LCat characters
        final String invalidRTLString = "\u0627\u06270";
        final ScramException exception = assertThrows(ScramException.class, () -> SaslPrep.INSTANCE.prepAsQueryString(invalidRTLString));
        assertTrue(exception.getMessage().contains("RandALCat"), "Exception should indicate mixed RandALCat and LCat.");
    }

}