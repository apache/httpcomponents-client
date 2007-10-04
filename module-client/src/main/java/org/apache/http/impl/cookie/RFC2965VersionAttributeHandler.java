package org.apache.http.impl.cookie;

import org.apache.http.cookie.ClientCookie;
import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.CookieAttributeHandler;
import org.apache.http.cookie.CookieOrigin;
import org.apache.http.cookie.MalformedCookieException;
import org.apache.http.cookie.SetCookie;

/**
 * <tt>"Version"</tt> cookie attribute handler for RFC 2965 cookie spec.
 */
public class RFC2965VersionAttributeHandler implements CookieAttributeHandler {

    public RFC2965VersionAttributeHandler() {
        super();
    }
    
    /**
     * Parse cookie version attribute.
     */
    public void parse(final SetCookie cookie, final String value)
            throws MalformedCookieException {
        if (cookie == null) {
            throw new IllegalArgumentException("Cookie may not be null");
        }
        if (value == null) {
            throw new MalformedCookieException(
                    "Missing value for version attribute");
        }
        int version = -1;
        try {
            version = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            version = -1;
        }
        if (version < 0) {
            throw new MalformedCookieException("Invalid cookie version.");
        }
        cookie.setVersion(version);
    }

    /**
     * validate cookie version attribute. Version attribute is REQUIRED.
     */
    public void validate(final Cookie cookie, final CookieOrigin origin)
            throws MalformedCookieException {
        if (cookie == null) {
            throw new IllegalArgumentException("Cookie may not be null");
        }
        if (cookie instanceof ClientCookie) {
            if (cookie instanceof ClientCookie 
                    && !((ClientCookie) cookie).containsAttribute(ClientCookie.VERSION_ATTR)) {
                throw new MalformedCookieException(
                        "Violates RFC 2965. Version attribute is required.");
            }
        }
    }

    public boolean match(final Cookie cookie, final CookieOrigin origin) {
        return true;
    }

}