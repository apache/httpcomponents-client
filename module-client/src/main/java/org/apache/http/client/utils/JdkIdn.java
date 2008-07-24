package org.apache.http.client.utils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Uses the java.net.IDN class through reflection.
 * 
 * @author Ortwin Glück
 */
public class JdkIdn implements Idn {
    private Method toUnicode;

    /**
     * 
     * @throws ClassNotFoundException if java.net.IDN is not available
     */
    public JdkIdn() throws ClassNotFoundException {
        Class clazz = Class.forName("java.net.IDN");
        try {
            toUnicode = clazz.getMethod("toUnicode", String.class);
        } catch (SecurityException e) {
            // doesn't happen
            throw new IllegalStateException(e.getMessage(), e);
        } catch (NoSuchMethodException e) {
            // doesn't happen
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    public String toUnicode(String punycode) {
        try {
            return (String) toUnicode.invoke(null, punycode);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e.getMessage(), e);
        } catch (InvocationTargetException e) {
            Throwable t = e.getCause();
            throw new RuntimeException(t.getMessage(), t);
        }
    }
    
}