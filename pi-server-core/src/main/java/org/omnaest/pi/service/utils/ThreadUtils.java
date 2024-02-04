package org.omnaest.pi.service.utils;

import java.util.concurrent.TimeUnit;

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
}
