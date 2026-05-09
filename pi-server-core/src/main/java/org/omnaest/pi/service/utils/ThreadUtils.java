package org.omnaest.pi.service.utils;

import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

public class ThreadUtils
{
    public static void sleep(int duration, TimeUnit timeUnit)
    {
        try
        {
            timeUnit.sleep(duration);
        }
        catch (InterruptedException e)
        {
            throw new IllegalStateException(e);
        }
    }

    public static void sleepWhile(int duration, TimeUnit timeUnit, BooleanSupplier condition, Runnable onTimeout)
    {
        int counter = 0;
        int durationInMilliseconds = (int) timeUnit.toMillis(duration);
        while (condition.getAsBoolean())
        {
            int delay = Math.min(Math.max(1, counter / 2), Math.max(1, Math.abs(durationInMilliseconds - counter) / 2));
            counter += delay;
            if (counter > durationInMilliseconds)
            {
                onTimeout.run();
            }
            ThreadUtils.sleep(delay, TimeUnit.MILLISECONDS);
        }
    }

}
