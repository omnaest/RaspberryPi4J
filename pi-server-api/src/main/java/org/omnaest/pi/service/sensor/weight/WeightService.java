package org.omnaest.pi.service.sensor.weight;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;

public interface WeightService
{
    /**
     * Reads the raw (uncalibrated) value of the NAU7802 sensor using the given {@link HX711PortConfiguration}
     * 
     * @param bus
     * @return
     */
    public int readValueFromNau7802(int bus);

    @Value
    @Builder
    public static class NAU7802Configuration
    {
        private final int bus;
    }

    /**
     * Reads the raw (uncalibrated) value of the HX711 sensor using the given {@link HX711PortConfiguration}
     * 
     * @param portConfiguration
     * @return
     */
    public long readValueFromHX711(HX711PortConfiguration portConfiguration);

    @Value
    @Builder
    public static class HX711PortConfiguration
    {
        private final int dataPort;

        private final int clockPort;

        @Default
        private final Gain gain = Gain.CHANNEL_A_HIGH;
    }

    @Getter
    @RequiredArgsConstructor
    public static enum Gain
    {
        CHANNEL_A_HIGH(128, 0), CHANNEL_A_NORMAL(64, 2), CHANNEL_B_LOW(32, 1);

        private final int value;
        private final int additionalBits;
    }
}
