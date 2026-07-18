package org.omnaest.pi.service.utils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

public class ThreadUtilsTest
{

    @Test
    public void testSleepWhile()
    {
        AtomicInteger counter = new AtomicInteger();
        assertThrows(IllegalStateException.class, () ->
        {
            ThreadUtils.sleepWhile(100, TimeUnit.MILLISECONDS, () ->
            {
                counter.getAndIncrement();
                return true;
            }, () ->
            {
                throw new IllegalStateException();
            });
        });
        assertTrue(counter.get() < 50);
    }

}
