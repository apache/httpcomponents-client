package org.apache.http.impl.client.cache;

import java.lang.reflect.Proxy;

import org.apache.http.HttpResponse;
import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.Args;

/**
 * Proxies for HTTP message objects.
 *
 * @since 4.3
 */
@NotThreadSafe
class Proxies {

    public static CloseableHttpResponse enhanceResponse(final HttpResponse original) {
        Args.notNull(original, "HTTP response");
        if (original instanceof CloseableHttpResponse) {
            return (CloseableHttpResponse) original;
        } else {
            return (CloseableHttpResponse) Proxy.newProxyInstance(
                    ResponseProxyHandler.class.getClassLoader(),
                    new Class<?>[] { CloseableHttpResponse.class },
                    new ResponseProxyHandler(original));
        }
    }

}