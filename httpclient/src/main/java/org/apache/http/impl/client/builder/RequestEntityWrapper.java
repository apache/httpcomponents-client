package org.apache.http.impl.client.builder;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.http.HttpEntity;
import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.entity.HttpEntityWrapper;

/**
 * A wrapper class for {@link HttpEntity} enclosed in a request message.
 *
 * @since 4.3
 */
@NotThreadSafe
class RequestEntityWrapper extends HttpEntityWrapper {

    private boolean consumed = false;

    RequestEntityWrapper(final HttpEntity entity) {
        super(entity);
    }

    @Override
    public boolean isRepeatable() {
        if (!this.consumed) {
            return true;
        } else {
            return super.isRepeatable();
        }
    }

    @Deprecated
    @Override
    public void consumeContent() throws IOException {
        consumed = true;
        super.consumeContent();
    }

    @Override
    public void writeTo(final OutputStream outstream) throws IOException {
        consumed = true;
        super.writeTo(outstream);
    }

    public boolean isConsumed() {
        return consumed;
    }

}