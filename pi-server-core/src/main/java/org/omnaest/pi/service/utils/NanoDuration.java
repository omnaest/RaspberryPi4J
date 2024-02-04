package org.omnaest.pi.service.utils;

public class NanoDuration
{
    private long start = System.nanoTime();

    private NanoDuration()
    {
        super();
    }

    public long stop()
    {
        return System.nanoTime() - this.start;
    }

    public static NanoDuration start()
    {
        return new NanoDuration();
    }

    @Override
    public String toString()
    {
        return "NanoDuration [start=" + this.start + "]";
    }

}
