package com.thinkaurelius.titan.diskstorage;

import com.thinkaurelius.titan.diskstorage.util.RecordIterator;

import java.nio.ByteBuffer;
import java.util.Iterator;

/**
 * (c) Matthias Broecheler (me@matthiasb.com)
 */

public interface ScanKeyColumnValueStore {

    /**
     * Returns an iterator over all keys in this store. The keys may be
     * ordered but not necessarily.
     *
     * @return An iterator over all keys in this store.
     */
    public RecordIterator<ByteBuffer> getKeys(TransactionHandle txh) throws StorageException;
    
}
