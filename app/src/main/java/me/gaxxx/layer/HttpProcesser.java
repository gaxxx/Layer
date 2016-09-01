package me.gaxxx.layer;

import com.github.gaxxx.layer_cache.Layer;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/**
 * Created by siwu on 7/11/16.
 */
public abstract class HttpProcesser<T> implements Layer.Proccessor<T> {
    @Override
    public T get(String key) {
        Map<String,T> ret = mget(new TreeSet<String>(Collections.singletonList(key)));
        if (ret != null) {
            return ret.get(key);
        }
        return null;
    }

    @Override
    abstract  public Map<String,T> mget(Set<String> keys);

    @Override
    public int msave(Map<String, T> toSave) {
        return 0;
    }

    @Override
    public int mremove(Set<String> keys) {
        return 0;
    }
}
