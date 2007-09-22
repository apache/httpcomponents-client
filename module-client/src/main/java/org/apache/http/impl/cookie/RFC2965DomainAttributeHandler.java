package org.apache.http.impl.cookie;

import org.apache.http.cookie.ClientCookie;
import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.CookieAttributeHandler;
import org.apache.http.cookie.CookieOrigin;
import org.apache.http.cookie.MalformedCookieException;
import org.apache.http.cookie.SetCookie;

/**
 * <tt>"Domain"</tt> cookie attribute handler for RFC 2965 cookie spec.
 * 
 * @author jain.samit@gmail.com (Samit Jain)
 *
 * @since 3.1
 */
public class RFC2965DomainAttributeHandler implements CookieAttributeHandler {

    public RFC2965DomainAttributeHandler() {
        super();
    }

    /**
     * Parse cookie domain attribute.
     */
    public void parse(final SetCookie cookie, String domain)
            throws MalformedCookieException {
        if (cookie == null) {
            throw new IllegalArgumentException("Cookie may not be null");
        }
        if (domain == null) {
            throw new MalformedCookieException(
                    "Missing value for domain attribute");
        }
        if (domain.trim().equals("")) {
            throw new MalformedCookieException(
                    "Blank value for domain attribute");
        }
        domain = domain.toLowerCase();
        if (!domain.startsWith(".")) {
            // Per RFC 2965 section 3.2.2
            // "... If an explicitly specified value does not start with
            // a dot, the user agent supplies a leading dot ..."
            // That effectively implies that the domain attribute 
            // MAY NOT be an IP address of a host name
            domain = "." + domain;
        }
        cookie.setDomain(domain);
    }

    /**
     * Performs domain-match as defined by the RFC2965.
     * <p>
     * Host A's name domain-matches host B's if
     * <ol>
     *   <ul>their host name strings string-compare equal; or</ul>
     *   <ul>A is a HDN string and has the form NB, where N is a non-empty
     *       name string, B has the form .B', and B' is a HDN string.  (So,
     *       x.y.com domain-matches .Y.com but not Y.com.)</ul>
     * </ol>
     *
     * @param host host name where cookie is received from or being sent to.
     * @param domain The cookie domain attribute.
     * @return true if the specified host matches the given domain.
     */
    public boolean domainMatch(String host, String domain) {
        boolean match = host.equals(domain)
                        || (domain.startsWith(".") && host.endsWith(domain));

        return match;
    }

    /**
     * Validate cookie domain attribute.
     */
    public void validate(final Cookie cookie, final CookieOrigin origin)
            throws MalformedCookieException {
        if (cookie == null) {
            throw new IllegalArgumentException("Cookie may not be null");
        }
        if (origin == null) {
            throw new IllegalArgumentException("Cookie origin may not be null");
        }
        String host = origin.getHost().toLowerCase();
        if (cookie.getDomain() == null) {
            throw new MalformedCookieException("Invalid cookie state: " +
                                               "domain not specified");
        }
        String cookieDomain = cookie.getDomain().toLowerCase();

        if (cookie instanceof ClientCookie 
                && ((ClientCookie) cookie).containsAttribute(ClientCookie.DOMAIN_ATTR)) {
            // Domain attribute must start with a dot
            if (!cookieDomain.startsWith(".")) {
                throw new MalformedCookieException("Domain attribute \"" +
                    cookie.getDomain() + "\" violates RFC 2109: domain must start with a dot");
            }

            // Domain attribute must contain at least one embedded dot,
            // or the value must be equal to .local.
            int dotIndex = cookieDomain.indexOf('.', 1);
            if (((dotIndex < 0) || (dotIndex == cookieDomain.length() - 1))
                && (!cookieDomain.equals(".local"))) {
                throw new MalformedCookieException(
                        "Domain attribute \"" + cookie.getDomain()
                        + "\" violates RFC 2965: the value contains no embedded dots "
                        + "and the value is not .local");
            }

            // The effective host name must domain-match domain attribute.
            if (!domainMatch(host, cookieDomain)) {
                throw new MalformedCookieException(
                        "Domain attribute \"" + cookie.getDomain()
                        + "\" violates RFC 2965: effective host name does not "
                        + "domain-match domain attribute.");
            }

            // effective host name minus domain must not contain any dots
            String effectiveHostWithoutDomain = host.substring(
                    0, host.length() - cookieDomain.length());
            if (effectiveHostWithoutDomain.indexOf('.') != -1) {
                throw new MalformedCookieException("Domain attribute \""
                                                   + cookie.getDomain() + "\" violates RFC 2965: "
                                                   + "effective host minus domain may not contain any dots");
            }
        } else {
            // Domain was not specified in header. In this case, domain must
            // string match request host (case-insensitive).
            if (!cookie.getDomain().equals(host)) {
                throw new MalformedCookieException("Illegal domain attribute: \""
                                                   + cookie.getDomain() + "\"."
                                                   + "Domain of origin: \""
                                                   + host + "\"");
            }
        }
    }

    /**
     * Match cookie domain attribute.
     */
    public boolean match(final Cookie cookie, final CookieOrigin origin) {
        if (cookie == null) {
            throw new IllegalArgumentException("Cookie may not be null");
        }
        if (origin == null) {
            throw new IllegalArgumentException("Cookie origin may not be null");
        }
        String host = origin.getHost().toLowerCase();
        String cookieDomain = cookie.getDomain();

        // The effective host name MUST domain-match the Domain
        // attribute of the cookie.
        if (!domainMatch(host, cookieDomain)) {
            return false;
        }
        // effective host name minus domain must not contain any dots
        String effectiveHostWithoutDomain = host.substring(
                0, host.length() - cookieDomain.length());
        if (effectiveHostWithoutDomain.indexOf('.') != -1) {
            return false;
        }
        return true;
    }

}