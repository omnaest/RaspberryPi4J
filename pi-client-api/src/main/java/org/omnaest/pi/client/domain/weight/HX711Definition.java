package org.omnaest.pi.client.domain.weight;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

public interface HX711Definition
{
    HX711Definition usingDataPort(int dataPort);

    HX711Definition usingClockPort(int clockPort);

    /**
     * Defines the {@link Gain}. Default is {@link Gain#CHANNEL_A_HIGH}
     * 
     * @param gain
     * @return
     */
    HX711Definition usingGain(Gain gain);

    long readValue();

    @Getter
    @RequiredArgsConstructor
    public static enum Gain
    {
        CHANNEL_A_HIGH(128), CHANNEL_A_NORMAL(64), CHANNEL_B_LOW(32);

        private final int value;
    }
}
