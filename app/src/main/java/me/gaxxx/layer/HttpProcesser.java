package me.gaxxx.layer;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Created by siwu on 7/11/16.
 */
public class HttpProcesser implements Layer.Proccessor<Integer> {

    Random seed = new Random(System.currentTimeMillis());
    @Override
    public Integer get(String key) {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return seed.nextInt();
    }

    @Override
    public Map<String, Integer> mget(Set<String> keys) {
         try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        HashMap<String, Integer> ret = new HashMap<String, Integer>();
        for (String k : keys) {
                ret.put(k, seed.nextInt());
        }
        return ret;
    }

    @Override
    public void batchSave(HashMap<String, Integer> toSave) {
    }

    @Override
    public void batchRemove(Set<String> keys) {
    }
}
