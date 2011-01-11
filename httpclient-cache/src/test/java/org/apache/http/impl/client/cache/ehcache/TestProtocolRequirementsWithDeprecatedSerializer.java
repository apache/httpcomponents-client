package org.apache.http.impl.client.cache.ehcache;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Set;

import org.apache.http.Header;
import org.apache.http.StatusLine;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.HttpCacheStorage;
import org.apache.http.client.cache.HttpCacheEntrySerializer;
import org.apache.http.client.cache.Resource;
import org.apache.http.impl.client.cache.CachingHttpClient;
import org.apache.http.impl.client.cache.HeapResourceFactory;
import org.junit.Before;

public class TestProtocolRequirementsWithDeprecatedSerializer 
    extends TestEhcacheProtocolRequirements {

    private static class OldSerializer implements HttpCacheEntrySerializer {

        @SuppressWarnings("deprecation")
        public void writeTo(HttpCacheEntry entry, OutputStream os)
                throws IOException {
            ObjectOutputStream oos = new ObjectOutputStream(os);
            oos.writeObject(entry.getRequestDate());
            oos.writeObject(entry.getResponseDate());
            oos.writeObject(entry.getStatusLine());
            oos.writeObject(entry.getAllHeaders());
            oos.writeObject(entry.getResource());
            oos.writeObject(entry.getVariantURIs());
        }

        @SuppressWarnings({ "deprecation", "unchecked" })
        public HttpCacheEntry readFrom(InputStream is) throws IOException {
            ObjectInputStream ois = new ObjectInputStream(is);
            try {
                Date requestDate = (Date)ois.readObject();
                Date responseDate = (Date)ois.readObject();
                StatusLine statusLine = (StatusLine)ois.readObject();
                Header[] responseHeaders = (Header[])ois.readObject();
                Resource resource = (Resource)ois.readObject();
                Set<String> variants = (Set<String>)ois.readObject();
                return new HttpCacheEntry(requestDate, responseDate, statusLine,
                        responseHeaders, resource, variants);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            } finally {
                ois.close();
            }
        }
        
    }
    
    @Override
    @Before
    public void setUp() {
        super.setUp();
        HttpCacheStorage storage = new EhcacheHttpCacheStorage(CACHE_MANAGER.getCache(TEST_EHCACHE_NAME), params,
                new OldSerializer());
        impl = new CachingHttpClient(mockBackend, new HeapResourceFactory(), storage, params);
    }

}
