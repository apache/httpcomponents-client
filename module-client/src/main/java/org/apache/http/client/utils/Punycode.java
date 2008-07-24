package org.apache.http.client.utils;


/**
 * Facade that provides conversion between Unicode and Punycode domain names.
 * It will use an appropriate implementation.
 * 
 * @author Ortwin Glück
 */
public class Punycode {
    private static Idn impl;
    static {
        init();
    }
    
    public static String toUnicode(String punycode) {
        return impl.toUnicode(punycode);
    }
    
    private static void init() {
        try {
            impl = new JdkIdn();
        } catch (Exception e) {
            impl = new Rfc3492Idn();
        }
    }
}
