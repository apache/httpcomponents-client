package org.apache.http.impl.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.entity.HttpEntityWrapper;

/**
 * {@link HttpEntityWrapper} for handling gzip Content Coded responses.
 */
class GzipDecompressingEntity extends HttpEntityWrapper {

    /**
     * Creates a new {@link GzipDecompressingEntity} which will wrap the specified 
     * {@link HttpEntity}.
     *
     * @param entity
     *            the non-null {@link HttpEntity} to be wrapped
     */
    public GzipDecompressingEntity(final HttpEntity entity) {
        super(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStream getContent() throws IOException, IllegalStateException {

        // the wrapped entity's getContent() decides about repeatability
        InputStream wrappedin = wrappedEntity.getContent();

        return new GZIPInputStream(wrappedin);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Header getContentEncoding() {

        /* This HttpEntityWrapper has dealt with the Content-Encoding. */
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getContentLength() {

        /* length of ungzipped content is not known */
        return -1;
    }

}
