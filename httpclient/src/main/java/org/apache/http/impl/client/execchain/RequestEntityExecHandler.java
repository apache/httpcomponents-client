package org.apache.http.impl.client.execchain;

import java.io.OutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.apache.http.HttpEntity;
import org.apache.http.annotation.NotThreadSafe;

/**
 * A wrapper class for {@link HttpEntity} enclosed in a request message.
 *
 * @since 4.3
 */
@NotThreadSafe
class RequestEntityExecHandler implements InvocationHandler  {

    private static final Method WRITE_TO_METHOD;

    static {
        try {
            WRITE_TO_METHOD = HttpEntity.class.getMethod("writeTo", OutputStream.class);
        } catch (NoSuchMethodException ex) {
            throw new Error(ex);
        }
    }

    private final HttpEntity original;
    private boolean consumed = false;

    RequestEntityExecHandler(final HttpEntity original) {
        super();
        this.original = original;
    }

    public HttpEntity getOriginal() {
        return original;
    }

    public boolean isConsumed() {
        return consumed;
    }

    public Object invoke(
            final Object proxy, final Method method, final Object[] args) throws Throwable {
        try {
            if (method.equals(WRITE_TO_METHOD)) {
                this.consumed = true;
            }
            return method.invoke(original, args);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause != null) {
                throw cause;
            } else {
                throw ex;
            }
        }
    }

}