package org.apache.http.impl.client.cache.memcached;


/**
 * This is a {@link KeyHashingScheme} decorator that simply adds
 * a known prefix to the results of another <code>KeyHashingScheme</code>.
 * Primarily useful for namespacing a shared memcached cluster, for
 * example.
 */
public class PrefixKeyHashingScheme implements KeyHashingScheme {

    private String prefix;
    private KeyHashingScheme backingScheme;

    /**
     * Creates a new {@link KeyHashingScheme} that prepends the given
     * prefix to the results of hashes from the given backing scheme.
     * Users should be aware that memcached has a fixed maximum key
     * length, so the combination of this prefix plus the results of
     * the backing hashing scheme must still fit within these limits.
     * @param prefix
     * @param backingScheme
     */
    public PrefixKeyHashingScheme(String prefix, KeyHashingScheme backingScheme) {
        this.prefix = prefix;
        this.backingScheme = backingScheme;
    }

    public String hash(String storageKey) {
        return prefix + backingScheme.hash(storageKey);
    }

}
