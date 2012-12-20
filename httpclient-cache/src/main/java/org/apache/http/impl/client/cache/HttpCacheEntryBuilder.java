package org.apache.http.impl.client.cache;

import org.apache.http.Header;
import org.apache.http.StatusLine;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.Resource;

import java.util.Date;
import java.util.Map;

public class HttpCacheEntryBuilder {

    private Date requestDate;
    private Date responseDate;
    private StatusLine statusLine;
    private Header[] allHeaders;
    private Resource resource;
    private Map<String, String> variantMap;
    private int errorCount;

    public HttpCacheEntryBuilder() {}

    public HttpCacheEntryBuilder(HttpCacheEntry entry) {
        this.requestDate = entry.getRequestDate();
        this.responseDate = entry.getResponseDate();
        this.statusLine = entry.getStatusLine();
        this.allHeaders = entry.getAllHeaders();
        this.resource = entry.getResource();
        this.variantMap = entry.getVariantMap();
        this.errorCount = entry.getErrorCount();
    }

    public HttpCacheEntry build() {
        return new HttpCacheEntry(requestDate, responseDate, statusLine, allHeaders, resource, variantMap, errorCount);
    }

    public HttpCacheEntryBuilder setRequestDate(Date requestDate) {
        this.requestDate = requestDate;
        return this;
    }

    public HttpCacheEntryBuilder setResponseDate(Date responseDate) {
        this.responseDate = responseDate;
        return this;
    }

    public HttpCacheEntryBuilder setStatusLine(StatusLine statusLine) {
        this.statusLine = statusLine;
        return this;
    }

    public HttpCacheEntryBuilder setAllHeaders(Header[] allHeaders) {
        this.allHeaders = allHeaders;
        return this;
    }

    public HttpCacheEntryBuilder setResource(Resource resource) {
        this.resource = resource;
        return this;
    }

    public HttpCacheEntryBuilder setVariantMap(Map<String, String> variantMap) {
        this.variantMap = variantMap;
        return this;
    }

    public HttpCacheEntryBuilder setErrorCount(int errorCount) {
        this.errorCount = errorCount;
        return this;
    }
}
