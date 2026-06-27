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

    public double readTemperature();

    /**
     * @see #usingPrimaryAddress()
     * @see #usingSecondaryAddress()
     * @param address
     * @return
     */
    public LPS28Definition usingAddress(int address);

    /**
     * Using 0x5D as address
     * 
     * @see #usingPrimaryAddress()
     * @see #usingAddress(int)
     * @return
     */
    public LPS28Definition usingSecondaryAddress();

    /**
     * Using 0x5C as address
     * 
     * @see #usingSecondaryAddress()
     * @see #usingAddress(int)
     * @return
     */
    public LPS28Definition usingPrimaryAddress();

    public static enum PressureScale
    {
        _4060HPA, _1260HPA
    }
}
