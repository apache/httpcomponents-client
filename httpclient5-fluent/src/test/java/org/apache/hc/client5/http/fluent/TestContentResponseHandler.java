package org.apache.hc.client5.http.fluent;

import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class TestContentResponseHandler {

    private static final String URI_LARGE_GIF = "https://www.apache.org/images/asf_logo_wide.gif";

    @Test
    public void testLargeContent() {
        try {
            final Content content = Request.get(URI_LARGE_GIF)
                    .execute()
                    .returnContent();
            assertEquals(ContentType.IMAGE_GIF.getMimeType(), content.getType().getMimeType());
            assertEquals(7051, content.asBytes().length);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
