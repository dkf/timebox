package com.ning.timebox;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;

public class TimeBox
{
    private final SortedMap<Integer, Handler> handlers = new TreeMap<Integer, Handler>(new Comparator<Integer>()
    {
        public int compare(Integer first, Integer second)
        {
            return first.compareTo(second) * -1;
        }
    });

    private final Semaphore flag = new Semaphore(0);
    private final int highestPriority;
    private final ExecutorService service;

    public TimeBox(Factory factory, Object handler) {
    	this(Executors.newCachedThreadPool(), factory, handler);
    }
	
    public TimeBox(ExecutorService service, Factory factory, Object handler)
    {
        this.service = service;
        int hp = Integer.MIN_VALUE;
        final Method[] methods = handler.getClass().getDeclaredMethods();
        for (Method method : methods) {
            if (Modifier.isPublic(method.getModifiers()) && method.isAnnotationPresent(Priority.class)) {
                int priority = method.getAnnotation(Priority.class).value();
                if (priority > hp) {
                    hp = priority;
                }
                Handler h = new Handler(factory, handler, method);
                if (handlers.containsKey(priority)) {
                    throw new IllegalArgumentException(format("multiple reactor methods have priority %d", priority));
                }
                handlers.put(priority, h);
            }
        }
        highestPriority = hp;
    }

    public TimeBox(Object handler)
    {
        this(new DefaultFactory(), handler);
    }

    public synchronized void provide(Object value)
    {
        provide(value, 0);
    }

    public synchronized void provide(Object value, int authority)
    {
        assert authority > Long.MIN_VALUE;

        final Class type = value.getClass();
        for (Map.Entry<Integer, Handler> entry : handlers.entrySet()) {
            entry.getValue().provide(type, value, authority);
            if (entry.getKey() == highestPriority && entry.getValue().isSatisfied()) {
                flag.release();
                return; // we satisfied highest priority, short circuit
            }
        }
    }

    public TimeBox providing(final Callable<?> callable)
    {
        return providing(callable, 0);
    }

    public TimeBox providing(final Callable<?> callable, final int authority)
    {
        service.submit(new Runnable() {
            @Override
            public void run()
            {
                try {
                    provide(callable.call(), authority);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
        return this;
    }

    public boolean react(long number, TimeUnit unit) throws InterruptedException, InvocationTargetException, IllegalAccessException
    {
        if (flag.tryAcquire(number, unit)) {
            // flag will only be avail *if* highest priority handler is triggered
            for (Handler handler : handlers.values()) {
                if (handler.isSatisfied()) {
                    handler.handle();
                    return true; // satisfied highest priority so short circuit and return
                }
            }
        }

        for (Handler handler : handlers.values()) {
            if (handler.isSatisfied()) {
                handler.handle();
                return true;
            }
        }
        
        return false;
    }
}
