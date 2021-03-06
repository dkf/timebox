package com.ning.timebox;

import static com.ning.timebox.TimeBox.timebox;
import junit.framework.Assert;
import junit.framework.TestCase;
import com.ning.timebox.clojure.CLJ;
import com.ning.timebox.ruby.Rb;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TestTimeBox extends TestCase
{

    public void testFirstChoice() throws Exception
    {
        final AtomicInteger flag = new AtomicInteger(0);
        final TimeBox<Boolean> box = new TimeBox<Boolean>(new Tesseract<Boolean>()
        {
            @Priority(3)
            public void best(Dog dog, Cat cat)
            {
                setResult(true);
                flag.set(1);
            }

            @Priority(2)
            public void okay(Dog dog)
            {
                flag.set(2);
            }
        });

        final CountDownLatch latch = new CountDownLatch(1);
        new Thread(new Runnable()
        {
            public void run()
            {
                box.provide(new Dog());
                box.provide(new Cat());
                latch.countDown();
            }
        }).start();
        latch.await();

        assertTrue(box.react(100, TimeUnit.MILLISECONDS));
        assertEquals(1, flag.get());
    }

    public void testSecondChoice() throws Exception
    {
        final AtomicInteger flag = new AtomicInteger(0);
        final TimeBox<Boolean> box = new TimeBox<Boolean>(new Tesseract<Boolean>()
        {
            @Priority(3)
            public void best(Dog dog, Cat cat)
            {
                flag.set(1);
            }

            @Priority(2)
            public void okay(Dog dog)
            {
                setResult(true);
                flag.set(2);
            }
        });

        final CountDownLatch latch = new CountDownLatch(1);
        new Thread(new Runnable()
        {
            public void run()
            {
                box.provide(new Dog());
                latch.countDown();
            }
        }).start();
        latch.await();

        assertTrue(box.react(10, TimeUnit.MILLISECONDS));
        assertEquals(2, flag.get());
    }

    public void testFallback() throws Exception
    {
        final AtomicInteger flag = new AtomicInteger(0);
        final TimeBox<Boolean> box = timebox(new Tesseract<Boolean>()
        {
            @Priority(3)
            public void best(Dog dog, Cat cat)
            {
                flag.set(1);
            }

            @Priority(0)
            public void fallback()
            {
                setResult(true);
                flag.set(4);
            }
        });

        assertTrue(box.react(1, TimeUnit.NANOSECONDS));
        assertEquals(4, flag.get());
    }

    public void testMultipleProvides() throws Exception
    {
        final AtomicInteger flag = new AtomicInteger(0);
        final TimeBox<Boolean> box = timebox(new Tesseract<Boolean>()
        {
            @Priority(2)
            public void okay(Dog dog)
            {
                setResult(true);
                flag.set(dog.getName().equals("Bean") ? 1 : 2);
            }
        });

        final CountDownLatch latch = new CountDownLatch(1);
        new Thread(new Runnable()
        {
            public void run()
            {
                box.provide(new Dog("Bouncer"), 1);
                box.provide(new Dog("Bean"), 10);
                latch.countDown();
            }
        }).start();
        latch.await();

        assertTrue(box.react(10, TimeUnit.MILLISECONDS));
        assertEquals(1, flag.get());
    }

    public void testMinimumAuthority() throws Exception
    {
        final AtomicInteger flag = new AtomicInteger(0);
        final TimeBox<Boolean> box = timebox(new Tesseract<Boolean>()
        {
            @Priority(3)
            public void best(@Authority(10) Dog dog)
            {
                flag.set(1);
            }


            @Priority(2)
            public void okay(Dog dog)
            {
                setResult(true);
                flag.set(2);
            }
        });

        box.provide(new Dog("Bouncer"), 1);

        assertTrue(box.react(10, TimeUnit.MILLISECONDS));
        assertEquals(2, flag.get());
    }

    public void testMinimumAuthorityMet() throws Exception
    {
        final AtomicInteger flag = new AtomicInteger(0);
        final TimeBox<Boolean> box = timebox(new Tesseract<Boolean>()
        {
            @Priority(3)
            public void best(@Authority(10) Dog dog)
            {
                setResult(true);
                flag.set(1);
            }

            @Priority(2)
            public void okay(Dog dog)
            {
                flag.set(2);
            }
        });

        box.provide(new Dog("Bouncer"), 10);

        assertTrue(box.react(10, TimeUnit.MILLISECONDS));
        assertEquals(1, flag.get());
    }
    
    public void testProviding() throws Exception
    {
        final AtomicInteger flag = new AtomicInteger(0);
        assertTrue(timebox(new Tesseract<Boolean>() {
            @Priority(1)
            public void stuff(Dog dog)
            {
                setResult(true);
                flag.set(1);
            }
        }).providing(new Callable<Dog>() {
            @Override
            public Dog call() throws Exception
            {
                return new Dog("baz");
            }
        }).react(10, TimeUnit.MILLISECONDS));
        assertEquals(flag.get(), 1);
    }
    
    public void testFutureCleanUp() throws Exception
    {
        final AtomicInteger flag = new AtomicInteger(0);
        final CountDownLatch latch = new CountDownLatch(1);
        
        assertTrue(timebox(new Tesseract<Boolean>() {
            
            @Priority(1)
            public void dog(Dog dog) {
                setResult(true);
            }
            
            public void dogAndCat(Dog dog, Cat cat) {
                setResult(false);
            }
            
        }).providing(new Callable<Dog>() {
            @Override
            public Dog call() throws Exception
            {
                return new Dog("foo");
            }
        }).providing(new Callable<Cat>() {
            @Override
            public Cat call() throws Exception
            {
                latch.await();
                flag.set(1);
                return new Cat(42);
            }
        }).react(100, TimeUnit.MILLISECONDS));
        assertEquals(0, flag.get());
        
        latch.countDown();
        assertEquals(0, flag.get());
    }

}
