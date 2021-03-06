package com.github.gaxxx.layer_cache;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executors;

import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.observables.ConnectableObservable;
import rx.schedulers.Schedulers;

/**
 * Created by siwu on 7/9/16.
 */
public class Layer<T> {
    public Proccessor<T> proccessor;
    public Layer<T> next;
    final Map<String,ConnectableObservable<T>> loading = new HashMap<>();
    final Map<String,Subscriber<? super  T>> merges = new HashMap<>();
    public Scheduler scheduler;

    public Observable<T> get(final String key) {
        return _get(key).subscribeOn(Schedulers.io());
    }

    public Observable<Map<String,T>> mget(final Set<String> keys) {
        return _mget(keys).subscribeOn(Schedulers.io());
    }

    public void update(final String key, final T value, boolean cursive, boolean sync) {
        HashMap<String,T> h = new HashMap<String,T>() {
            {
                put(key,value);
            }
        };
        mupdate(h,cursive,sync);
    }

    public void mupdate(final Map<String,T> obj,boolean cursive,boolean sync) {
        Observable<Void> ob = _mupdate(obj,cursive);
        if (!sync) {
            ob = ob.subscribeOn(Schedulers.io());
        }
        ob.doOnError(new Action1<Throwable>() {
                         @Override
                         public void call(Throwable throwable) {
                             throwable.printStackTrace();
                         }
                     }
        ).subscribe();
    }


    public Observable<Void> _mupdate(final Map<String,T> obj, final boolean cursive) {
        return Observable.create(new Observable.OnSubscribe<Void>() {
            @Override
            public void call(Subscriber<? super Void> subscriber) {
                proccessor.msave(obj);
                if (cursive && next != null) {
                    next._mupdate(obj,true).subscribeOn(scheduler).subscribe(subscriber);
                }else {
                    subscriber.onCompleted();
                }

            }
        });
    }

    public void remove(String key,boolean cursive,boolean sync) {
        Set<String> keys = new TreeSet<>(Collections.singletonList(key));
        mremove(keys,cursive,sync);
    }

    public void mremove(Set<String> keys,boolean cursive,boolean sync) {
        Observable<Void> ob = _mremove(keys,cursive);
        if (!sync) {
            ob = ob.subscribeOn(Schedulers.io());
        }
        ob.doOnError(new Action1<Throwable>() {
            @Override
            public void call(Throwable throwable) {
                throwable.printStackTrace();
            }
        }).subscribe();
    }

    private Observable<Void> _remove(String key,boolean cursive) {
        Set<String> keys = new TreeSet<>(Collections.singletonList(key));
        return _mremove(keys,cursive);
    }

    private Observable<Void> _mremove(final Set<String> keys,final boolean cursive) {
        return Observable.create(new Observable.OnSubscribe<Void>() {
            @Override
            public void call(Subscriber<? super Void> subscriber) {
                int size = proccessor.mremove(keys);
                if (size > 0) {
                    Log.d(proccessor.getClass().getName(),"mremove " + size);
                }
                if (cursive && next != null) {
                    next._mremove(keys,true).subscribeOn(scheduler).subscribe(subscriber);
                }else {
                    subscriber.onCompleted();
                }
            }
        });
    }


    private Observable<T> _get(final String key) {
        return Observable.create(new Observable.OnSubscribe<T>() {
            @Override
            public void call(Subscriber<? super T> subscriber) {
                Log.d(proccessor.getClass().getName(),"get " + key);
                T v = proccessor.get(key);
                if (v != null) {
                    if (!subscriber.isUnsubscribed()) {
                        subscriber.onNext(v);
                        subscriber.onCompleted();
                        return;
                    }
                }

                ConnectableObservable<T> future;
                synchronized (loading) {
                    future = loading.get(key);
                    if (future == null) {
                        future = Observable.create(new Observable.OnSubscribe<T>() {
                            @Override
                            public void call(Subscriber<? super T> subscriber) {
                                merge(key,subscriber);
                                trigger().subscribe();
                            }
                        }).publish();
                        loading.put(key,future);
                    }
                    future.autoConnect().subscribe(subscriber);
                }
            }
        });

    }



    private void merge(String key, Subscriber<? super T> subscriber) {
        synchronized (loading) {
            merges.put(key,subscriber);
        }
    }

    private void mergeTask() {
        final Map<String,Subscriber<? super  T>> todo = new HashMap<>();
        synchronized (loading) {
                todo.putAll(merges);
                merges.clear();
        }
        if (todo.size() == 0) {
            return;
        }
        Log.d(getClass().getName(),String.format("%x merge todo %s",this.hashCode(),todo));
        final HashMap<String, T> toSave = new HashMap<String, T>();
        final Action1<Throwable> onComplete = new Action1<Throwable>() {
            @Override
            public void call(Throwable throwable) {
                if (throwable != null) {
                    throwable.printStackTrace();
                }
                Log.d(proccessor.getClass().getName(),String.format("batch save %d",toSave.size()));
                proccessor.msave(toSave);
                for (Map.Entry<String, T> v : toSave.entrySet()) {
                    if (todo.containsKey(v.getKey())) {
                        Subscriber<? super T> s = todo.get(v.getKey());
                        todo.remove(v.getKey());
                        synchronized (loading) {
                            if (loading.containsKey(v.getKey())) {
                                loading.remove(v.getKey());
                            }
                        }
                        s.onNext(v.getValue());
                        s.onCompleted();
                    }
                }
                synchronized (loading) {
                    for (String k : todo.keySet()) {
                        if (loading.containsKey(k)) {
                            loading.remove(k);
                        }
                    }
                }
                for (Subscriber<? super T> s : todo.values()) {
                    s.onError(new RuntimeException("missing keys"));
                }

            }
        };
        next._mget(todo.keySet()).subscribe(
                new Action1<Map<String, T>>() {
                    @Override
                    public void call(Map<String, T> stringTMap) {
                        toSave.putAll(stringTMap);

                    }
                },
                new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        onComplete.call(throwable);
                    }
                },
                new Action0() {
                    @Override
                    public void call() {
                        onComplete.call(null);
                    }
                }
        );
    }

    private Observable<Map<String,T>> _mget(final Set<String> keys) {

        return Observable.create(new Observable.OnSubscribe<Map<String, T>>() {
            @Override
            public void call(final Subscriber<? super Map<String, T>> subscriber) {
                Log.d(proccessor.getClass().getName(),String.format("mget %s", keys));
                Map<String, T> v = proccessor.mget(keys);

                if (v != null && v.size() == keys.size()) {
                    if (!subscriber.isUnsubscribed()) {
                        subscriber.onNext(v);
                        subscriber.onCompleted();
                        return;
                    }
                }

                if (next == null) {
                    if (!subscriber.isUnsubscribed()) {
                        subscriber.onNext(v);
                        subscriber.onCompleted();
                        return;
                    }
                }

                TreeSet<String> missingKeys = new TreeSet<>(keys);
                List<String> toRemove = new ArrayList<>();
                if (v != null) {
                    for (String k : missingKeys) {
                        if (v.containsKey(k)) {
                            toRemove.add(k);
                        }
                    }
                }
                missingKeys.removeAll(toRemove);

                Observable<Map<String, T>> ret = Observable.just(v);
                synchronized (loading) {
                    for (final String k : missingKeys) {
                        ConnectableObservable<T> future = loading.get(k);
                        if (future == null) {
                            future = Observable.create(new Observable.OnSubscribe<T>() {
                                @Override
                                public void call(Subscriber<? super T> subscriber) {
                                    merge(k,subscriber);
                                }
                            }).publish();
                            loading.put(k,future);
                        }
                        ret = ret.mergeWith(future.autoConnect().map(new Func1<T, Map<String, T>>() {
                            @Override
                            public Map<String, T> call(final T t) {
                                return Collections.singletonMap(k, t);
                            }
                        }));
                    }
                    ret = ret.mergeWith(trigger());
                    final Map<String,T> toSave = new HashMap<String, T>();
                    final Action1<Throwable> onComplete = new Action1<Throwable>() {
                        @Override
                        public void call(Throwable throwable) {
                            if (throwable != null) {
                                throwable.printStackTrace();
                            }

                            if (!subscriber.isUnsubscribed()) {
                                subscriber.onNext(toSave);
                                subscriber.onCompleted();
                            }
                        }
                    };
                    ret.subscribe(
                            new Action1<Map<String, T>>() {
                                @Override
                                public void call(Map<String, T> stringTMap) {
                                    toSave.putAll(stringTMap);
                                }
                            },
                            new Action1<Throwable>() {
                                @Override
                                public void call(Throwable throwable) {
                                    onComplete.call(throwable);
                                }
                            },
                            new Action0() {
                                @Override
                                public void call() {
                                    onComplete.call(null);

                                }
                            }
                    );

                }
            }
        });
    }

    private Observable<? extends Map<String, T>> trigger() {
        return Observable.create(new Observable.OnSubscribe<Map<String, T>>() {
            @Override
            public void call(Subscriber<? super Map<String, T>> subscriber) {
                mergeTask();
                subscriber.onCompleted();
            }
        }).subscribeOn(scheduler);
    }

    public static final class Builder<T> {
        private Proccessor<T> processor;
        private Scheduler schedule;
        private Layer<T> next;
        private Builder<T> prev;


        public Builder() {
        }

        public Layer<T> build() {
            Layer<T> executor = new Layer<>();
            executor.proccessor = processor;
            executor.next = next;
            if (schedule == null) {
                executor.scheduler = getDefaultLevelService();
            }else {
                executor.scheduler = schedule;
            }
            if (prev == null) {
                return executor;
            }
            return prev.next(executor).build();
        }

        private Scheduler getDefaultLevelService() {
            return Schedulers.from(Executors.newFixedThreadPool(1));
        }

        public Builder<T> next(Layer<T> executor) {
            next = executor;
            return this;
        }

        public Builder<T> next() {
            Builder<T> b = new Builder<>();
            b.prev = this;
            return b;
        }

        public Builder<T> schedule(Scheduler schedule) {
            this.schedule = schedule;
            return this;
        }

        public Builder<T> processor(Proccessor<T> processor) {
            this.processor = processor;
            return this;
        }
    }

    public interface Proccessor<T> {
        T get(String key);
        Map<String,T> mget(Set<String> keys);
        int msave(Map<String, T> toSave);
        int mremove(Set<String> keys);
    }
}
