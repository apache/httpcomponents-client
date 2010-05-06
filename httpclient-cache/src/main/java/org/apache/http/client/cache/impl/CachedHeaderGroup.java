package org.apache.http.client.cache.impl;

import java.io.Serializable;

import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.message.HeaderGroup;

/**
 */
@NotThreadSafe // because HeaderGroup is @NotThreadSafe
public class CachedHeaderGroup extends HeaderGroup implements Serializable {
    private static final long serialVersionUID = -4572663568087431896L;
}
