package org.omnaest.pi.service.rotary;

import java.util.function.LongSupplier;

public interface RotaryEncoderService
{
    public RotaryEncoder getRotaryEncoderByPin(int clkPort, int dtPort, int swPort);

    public static interface RotaryEncoder extends LongSupplier
    {
        /**
         * Sets the maximum value (inclusive) which the encoder can reach.
         * 
         * @param maximum
         * @return
         */
        public RotaryEncoder withMaximum(int maximum);

        /**
         * Sets the minumum value which the encoder can reach.
         * 
         * @param minimum
         * @return
         */
        public RotaryEncoder withMinimum(int minimum);

        /**
         * Similar to {@link #withCircularRotation(boolean)} with a true value
         * 
         * @return
         */
        public RotaryEncoder withCircularRotation();

        /**
         * Enables or disables the circular calculation of the value, which means, when the maximum is hit that the value starts again from the minimum and the
         * other way.
         * 
         * @param circularRotation
         * @return
         */
        public RotaryEncoder withCircularRotation(boolean circularRotation);

        /**
         * Resets the encoder value to the minimum value
         * 
         * @see #withMinimum(int)
         * @return
         */
        public RotaryEncoder reset();

        /**
         * Returns the value of the rotary encoder
         */
        @Override
        public long getAsLong();

    }
}
