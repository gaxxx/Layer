package me.gaxxx.layer;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;

import rx.functions.Action1;
import rx.schedulers.Schedulers;

public class MainActivity extends Activity {

    int httpGetCount = 0;
    int httpGetTimes = 0;
    private Layer<Integer> layer;

    private void httpStatisShow() {
        Toast.makeText(this,String.format("http get times:%d, total:%d",httpGetCount,httpGetTimes),Toast.LENGTH_SHORT).show();
    }

    private void httpStaticReset() {
        httpGetCount = httpGetTimes = 0;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        HttpProcesser<Integer> httpProcesser = new HttpProcesser<Integer>(){

            @Override
            public Map<String, Integer> mget(Set<String> keys) {
                httpGetTimes++;
                httpGetCount += keys.size();
                try {
                    Thread.sleep(100l);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                HashMap<String, Integer> ret = new HashMap<>();
                for (String s : keys) {
                    ret.put(s,100);
                }
                return ret;
            }
        };

        layer = new Layer.Builder<Integer>()
                .processor(new MemoryProcesser<Integer>()).schedule(Schedulers.from(Executors.newFixedThreadPool(3)))
                .next().processor(httpProcesser).schedule(Schedulers.from(Executors.newFixedThreadPool(3)))
                .build();

        Button get_1000_key_sep = (Button) findViewById(R.id.get_1000_key);
        get_1000_key_sep.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final CountDownLatch ct = new CountDownLatch(1000);
                Set<String> toRemove = new HashSet<String>();
                for (int i=0;i<1000;i++) {
                    toRemove.add(String.valueOf(i));
                    final int finalI = i;
                    layer.get(String.valueOf(i)).subscribe(
                            new Action1<Integer>() {
                                @Override
                                public void call(Integer integer) {
                                    ct.countDown();
                                }
                            },
                            new Action1<Throwable>() {
                                @Override
                                public void call(Throwable throwable) {
                                    throwable.printStackTrace();
                                    ct.countDown();
                                }
                            }
                    );

                }
                try {
                    ct.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                httpStatisShow();
                layer.mremove(toRemove,true,true);
                httpStaticReset();
            }
        });

        Button get_batch_100 = (Button)findViewById(R.id.get_batch_100);
        get_batch_100.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final CountDownLatch ct = new CountDownLatch(1);
                Set<String> toGet = new HashSet<String>();

                for (int i=0;i<100;i++) {
                    toGet.add(String.valueOf(i));
                }
                layer.mget(toGet).subscribe(
                        new Action1<Map<String, Integer>>() {
                            @Override
                            public void call(Map<String, Integer> stringIntegerMap) {
                                ct.countDown();
                            }
                        },
                        new Action1<Throwable>() {
                            @Override
                            public void call(Throwable throwable) {
                                ct.countDown();
                            }
                        }
                );

                try {
                    ct.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                httpStatisShow();
                layer.mremove(toGet,true,true);
                httpStaticReset();

            }
        });

        Button random_get = (Button) findViewById(R.id.random_get);
        random_get.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Random rd = new Random(System.currentTimeMillis());
                final CountDownLatch ct = new CountDownLatch(10);
                Set<String> totalToGet = new HashSet<String>();
                for (int i=0;i<10;i++) {
                    int begin = rd.nextInt(1000);
                    Set<String> toGet = new HashSet<>();
                    for (int j=begin;j<begin+50;j++) {
                        toGet.add(String.valueOf(j));
                        totalToGet.add(String.valueOf(j));
                    }
                    layer.mget(toGet).subscribe(
                            new Action1<Map<String, Integer>>() {
                                @Override
                                public void call(Map<String, Integer> stringIntegerMap) {
                                    ct.countDown();
                                }
                            },
                            new Action1<Throwable>() {
                                @Override
                                public void call(Throwable throwable) {
                                    ct.countDown();
                                }
                            }
                    );

                }

                try {
                    ct.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Toast.makeText(MainActivity.this,String.format("total get is %d for 10 times",totalToGet.size()),Toast.LENGTH_SHORT).show();
                httpStatisShow();
                httpStaticReset();
                layer.mremove(totalToGet,true,true);


            }
        });


    }
}
