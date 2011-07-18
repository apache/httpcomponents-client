package org.apache.http.impl.client;

import static org.junit.Assert.*;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.message.BasicHttpResponse;
import org.junit.Before;
import org.junit.Test;


public class TestNullBackoffStrategy {

    private NullBackoffStrategy impl;

    @Before
    public void setUp() {
        impl = new NullBackoffStrategy();
    }
    
    @Test
    public void doesNotBackoffForThrowables() {
        assertFalse(impl.shouldBackoff(new Exception()));
    }
    
    @Test
    public void doesNotBackoffForResponses() {
        HttpResponse resp = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_SERVICE_UNAVAILABLE, "Service Unavailable");
        assertFalse(impl.shouldBackoff(resp));
    }
}
