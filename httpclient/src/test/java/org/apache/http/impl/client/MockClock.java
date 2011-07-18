package org.apache.http.impl.client;

public class MockClock implements Clock {

    private long t = System.currentTimeMillis();
    
    public long getCurrentTime() {
        return t;
    }
    
    public void setCurrentTime(long now) {
        t = now;
    }

}
