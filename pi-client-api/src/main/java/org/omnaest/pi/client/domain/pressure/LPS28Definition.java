package org.omnaest.pi.client.domain.pressure;

/**
 * API for the LPS28 pressure sensor that communicates over I2C
 * 
 * @author omnaest
 */
public interface LPS28Definition
{
    public LPS28Definition usingBus(int bus);

    public LPS28Definition withPressureScale(PressureScale pressureScale);

    public double readPressure();

    public static enum PressureScale
    {
        _4060HPA, _1260HPA
    }
}
