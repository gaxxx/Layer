package com.github.gaxxx.layer_cache;

import android.util.LruCache;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by siwu on 7/11/16.
 */
public class MemoryProcesser<T> implements Layer.Proccessor<T> {
    private LruCache<String,T> cache;

    public MemoryProcesser(int max) {
        this.cache = new LruCache<>(max);
    }

    @Override
    public synchronized T get(String key) {
        try {
            return cache.get(key);
        }catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        return  null;
    }

    @Override
    public synchronized Map<String, T> mget(Set<String> keys) {
        Map<String,T> m = new HashMap<>();
        for (String k : keys) {
            T v = cache.get(k);
            if (v != null) {
                m.put(k,v);
            }
        }

        return m;
    }

    @Override
    public synchronized int msave(Map<String, T> toSave) {
        for (Map.Entry<String,T> m : toSave.entrySet()) {
            cache.put(m.getKey(),m.getValue());
        }
        return toSave.size();
    }

    @Override
    public synchronized int mremove(Set<String> keys) {
        int i = 0;
        for (String k :keys) {
            if (cache.remove(k) != null) {
                i++;
            }
        }
        return i;
    }
}
