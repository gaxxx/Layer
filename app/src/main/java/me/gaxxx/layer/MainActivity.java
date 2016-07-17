package me.gaxxx.layer;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import rx.Scheduler;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Layer<Integer> layer = new Layer.Builder<Integer>().processor(new MemoryProcesser<Integer>()).next().processor(new HttpProcesser()).build();
        Set<String> keys = new HashSet<>();
        for (int i=0;i<50;i++) {
            keys.add(String.valueOf(i));
        }
        layer.mget(keys).subscribe(
                new Action1<Map<String, Integer>>() {
                                       @Override
                                       public void call(Map<String, Integer> stringIntegerMap) {

                                       }
                                   }, new Action1<Throwable>() {
                                       @Override
                                       public void call(Throwable throwable) {
                                           throwable.printStackTrace();
                                       }
                                   }
        );
        layer.mremove(keys,true);
        for (int i=0;i<1000;i++) {
            final int finalI = i % 100;
            layer.get(String.valueOf(finalI)).subscribe(
                    new Action1<Integer>() {
                        @Override
                        public void call(Integer integer) {
                            Log.d(MainActivity.class.getName(), String.format("get %d => %d", finalI, integer));
                        }
                    },
                    new Action1<Throwable>() {
                        @Override
                        public void call(Throwable throwable) {
                            throwable.printStackTrace();
                        }
                    }
            );
        }
    }
}
