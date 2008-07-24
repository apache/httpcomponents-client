package org.apache.http.client.utils;

/**
 * Abstraction of international domain name (IDN) conversion.
 * 
 * @author Ortwin Glück
 */
public interface Idn {
    /**
     * Converts a name from its punycode representation to Unicode.
     * The name may be a single hostname or a dot-separated qualified domain name.
     * @param punycode the Punycode representation
     * @return the Unicode domain name
     */
    String toUnicode(String punycode);
}