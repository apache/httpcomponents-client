package org.apache.http.mockup;

import java.io.IOException;

import org.apache.http.HttpConnection;

/**
 * {@link HttpConnection} mockup implementation.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 */
public class HttpConnectionMockup implements HttpConnection {

    private boolean open = true;
    
    public HttpConnectionMockup() {
        super();
    }
    
    public void close() throws IOException {
        this.open = false;
    }
    
    public void shutdown() throws IOException {
        this.open = false;
    }
    
    public int getSocketTimeout() throws IOException {
        return 0;
    }
    
    public boolean isOpen() {
        return this.open;
    }
    
    public boolean isStale() {
        return false;
    }

    public void setSocketTimeout(int timeout) throws IOException {
    }
}
