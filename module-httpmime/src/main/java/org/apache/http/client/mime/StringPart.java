package org.apache.http.client.mime;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;

import org.apache.commons.io.IOUtils;
import org.apache.http.protocol.HTTP;
import org.apache.james.mime4j.message.AbstractBody;
import org.apache.james.mime4j.message.TextBody;

public class StringPart extends AbstractBody implements TextBody {

    private final String text;
    private final String charset;
    
    public StringPart(final String text, String charset) {
        super();
        if (text == null) {
            throw new IllegalArgumentException("Text may not be null");
        }
        if (charset == null) {
            charset = HTTP.UTF_8;
        }
        this.text = text;
        this.charset = charset;
    }
    
    public StringPart(final String text) {
        this(text, null);
    }
    
    public Reader getReader() throws IOException {
        return new StringReader(this.text);
    }

    public void writeTo(final OutputStream out) throws IOException {
        if (out == null) {
            throw new IllegalArgumentException("Output stream may not be null");
        }
        IOUtils.copy(getReader(), out, this.charset);
    }
    
}
