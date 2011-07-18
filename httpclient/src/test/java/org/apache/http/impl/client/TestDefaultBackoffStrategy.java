package org.apache.http.impl.client;

import static org.junit.Assert.*;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.client.ConnectionBackoffStrategy;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.message.BasicHttpResponse;
import org.junit.Before;
import org.junit.Test;


public class TestDefaultBackoffStrategy {

    private DefaultBackoffStrategy impl;

    @Before
    public void setUp() {
        impl = new DefaultBackoffStrategy();
    }
    
    @Test
    public void isABackoffStrategy() {
        assertTrue(impl instanceof ConnectionBackoffStrategy);
    }
    
    @Test
    public void backsOffForSocketTimeouts() {
        assertTrue(impl.shouldBackoff(new SocketTimeoutException()));
    }
    
    @Test
    public void backsOffForConnectionTimeouts() {
        assertTrue(impl.shouldBackoff(new ConnectException()));
    }
    
    @Test
    public void doesNotBackOffForConnectionManagerTimeout() {
        assertFalse(impl.shouldBackoff(new ConnectionPoolTimeoutException()));
    }
    
    @Test
    public void backsOffForServiceUnavailable() {
        HttpResponse resp = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_SERVICE_UNAVAILABLE, "Service Unavailable");
        assertTrue(impl.shouldBackoff(resp));
    }
    
    @Test
    public void doesNotBackOffForNon503StatusCodes() {
        for(int i = 100; i <= 599; i++) {
            if (i == HttpStatus.SC_SERVICE_UNAVAILABLE) continue;
            HttpResponse resp = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                    i, "Foo");
            assertFalse(impl.shouldBackoff(resp));
        }
    }
}
