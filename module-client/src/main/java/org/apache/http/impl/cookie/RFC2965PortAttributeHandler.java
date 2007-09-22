package org.apache.http.impl.cookie;

import java.util.StringTokenizer;

import org.apache.http.cookie.ClientCookie;
import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.CookieAttributeHandler;
import org.apache.http.cookie.CookieOrigin;
import org.apache.http.cookie.MalformedCookieException;
import org.apache.http.cookie.SetCookie;
import org.apache.http.cookie.SetCookie2;

/**
 * <tt>"Port"</tt> cookie attribute handler for RFC 2965 cookie spec.
 */
public class RFC2965PortAttributeHandler implements CookieAttributeHandler {

    /**
     * @param spec
     */
    public RFC2965PortAttributeHandler() {
        super();
    }

    /**
     * Parses the given Port attribute value (e.g. "8000,8001,8002")
     * into an array of ports.
     *
     * @param portValue port attribute value
     * @return parsed array of ports
     * @throws MalformedCookieException if there is a problem in
     *          parsing due to invalid portValue.
     */
    private static int[] parsePortAttribute(final String portValue)
            throws MalformedCookieException {
        StringTokenizer st = new StringTokenizer(portValue, ",");
        int[] ports = new int[st.countTokens()];
        try {
            int i = 0;
            while(st.hasMoreTokens()) {
                ports[i] = Integer.parseInt(st.nextToken().trim());
                if (ports[i] < 0) {
                  throw new MalformedCookieException ("Invalid Port attribute.");
                }
                ++i;
            }
        } catch (NumberFormatException e) {
            throw new MalformedCookieException ("Invalid Port "
                                                + "attribute: " + e.getMessage());
        }
        return ports;
    }

    /**
     * Returns <tt>true</tt> if the given port exists in the given
     * ports list.
     *
     * @param port port of host where cookie was received from or being sent to.
     * @param ports port list
     * @return true returns <tt>true</tt> if the given port exists in
     *         the given ports list; <tt>false</tt> otherwise.
     */
    private static boolean portMatch(int port, int[] ports) {
        boolean portInList = false;
        for (int i = 0, len = ports.length; i < len; i++) {
            if (port == ports[i]) {
                portInList = true;
                break;
            }
        }
        return portInList;
    }

    /**
     * Parse cookie port attribute.
     */
    public void parse(final SetCookie cookie, final String portValue)
            throws MalformedCookieException {
        if (cookie == null) {
            throw new IllegalArgumentException("Cookie may not be null");
        }
        if (cookie instanceof SetCookie2) {
            SetCookie2 cookie2 = (SetCookie2) cookie;
            if (portValue != null && !portValue.equals("")) {
                int[] ports = parsePortAttribute(portValue);
                cookie2.setPorts(ports);
            }
        }
    }

    /**
     * Validate cookie port attribute. If the Port attribute was specified
     * in header, the request port must be in cookie's port list.
     */
    public void validate(final Cookie cookie, final CookieOrigin origin)
            throws MalformedCookieException {
        if (cookie == null) {
            throw new IllegalArgumentException("Cookie may not be null");
        }
        if (origin == null) {
            throw new IllegalArgumentException("Cookie origin may not be null");
        }
        int port = origin.getPort();
        if (cookie instanceof ClientCookie 
                && ((ClientCookie) cookie).containsAttribute(ClientCookie.PORT_ATTR)) {
            if (!portMatch(port, cookie.getPorts())) {
                throw new MalformedCookieException(
                        "Port attribute violates RFC 2965: "
                        + "Request port not found in cookie's port list.");
            }
        }
    }

    /**
     * Match cookie port attribute. If the Port attribute is not specified
     * in header, the cookie can be sent to any port. Otherwise, the request port
     * must be in the cookie's port list.
     */
    public boolean match(final Cookie cookie, final CookieOrigin origin) {
        if (cookie == null) {
            throw new IllegalArgumentException("Cookie may not be null");
        }
        if (origin == null) {
            throw new IllegalArgumentException("Cookie origin may not be null");
        }
        int port = origin.getPort();
        if (cookie instanceof ClientCookie 
                && ((ClientCookie) cookie).containsAttribute(ClientCookie.PORT_ATTR)) {
            if (cookie.getPorts() == null) {
                // Invalid cookie state: port not specified
                return false;
            }
            if (!portMatch(port, cookie.getPorts())) {
                return false;
            }
        }
        return true;
    }

}