package org.apache.http.impl.client.cache;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ConsumableInputStream extends InputStream {

    private ByteArrayInputStream buf;
    private boolean closed = false;
    
    public ConsumableInputStream(ByteArrayInputStream buf) {
        this.buf = buf;
    }
    
    public int read() throws IOException {
        return buf.read();
    }
    
    @Override
    public void close() {
        closed = true;
        try {
            buf.close();
        } catch (IOException e) {
        }
    }

    public boolean wasClosed() {
        return closed;
    }
}
