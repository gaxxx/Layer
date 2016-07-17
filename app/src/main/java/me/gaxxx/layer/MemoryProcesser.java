package me.gaxxx.layer;

import android.util.LruCache;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by siwu on 7/11/16.
 */
public class MemoryProcesser<T> implements Layer.Proccessor<T> {
    private LruCache<String,T> cache = new LruCache<>(500);

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
    public synchronized void batchSave(HashMap<String, T> toSave) {
        for (Map.Entry<String,T> m : toSave.entrySet()) {
            cache.put(m.getKey(),m.getValue());
        }

    }

    @Override
    public synchronized void batchRemove(Set<String> keys) {
        for (String k :keys) {
            cache.remove(k);
        }
    }
}
