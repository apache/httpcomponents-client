package org.apache.http.impl.client.cache;

import org.apache.http.client.cache.HttpCacheEntry;

/** Records a set of information describing a cached variant. */
class Variant {

	private final String variantKey;
	private final String cacheKey;
	private final HttpCacheEntry entry;

	public Variant(String variantKey, String cacheKey, HttpCacheEntry entry) {
		this.variantKey = variantKey;
		this.cacheKey = cacheKey;
		this.entry = entry;
	}

	public String getVariantKey() {
		return variantKey;
	}
	
	public String getCacheKey() {
		return cacheKey;
	}
	
	public HttpCacheEntry getEntry() {
		return entry;
	}
}
