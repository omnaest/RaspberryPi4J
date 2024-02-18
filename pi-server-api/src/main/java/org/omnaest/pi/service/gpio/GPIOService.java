/* 

	Copyright 2017 Danny Kunz

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

		http://www.apache.org/licenses/LICENSE-2.0

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.
	

*/
package org.omnaest.pi.service.gpio;

import java.util.function.Consumer;

public interface GPIOService
{
    public void enableGPIOPort(int port, boolean active);

    public void enableGPIOPortForDigitalOutput(int port);

    void enableGPIOPortForPWM(int port);

    void setGPIOPortPWMValue(int port, int value);

    public DigitalOutputGPIOPort getDigitalOutputGPIOPort(int port);

    public DigitalInputGPIOPort getDigitalInputGPIOPort(int port);

    public PwmGPIOPort getPwmGPIOPort(int port);

    public static interface DigitalGPIOPort
    {
        /**
         * Enables the given port for digital use. This function is idempotent, which means it can be safely called twice.
         * 
         * @return
         */
        public DigitalGPIOPort enable();

        /**
         * Disables the given port for digital use.
         * 
         * @return
         */
        public DigitalGPIOPort disable();
    }

    public static interface DigitalInputGPIOPort extends DigitalGPIOPort
    {
        /**
         * Enables
         */
        @Override
        public DigitalInputGPIOPort enable();

        @Override
        public DigitalInputGPIOPort disable();

        /**
         * Returns true for high signal and false for a low signal
         * 
         * @return
         */
        public boolean getState();

        /**
         * Disables any resistance. Call this before {@link #enable()} otherwise this will throw an {@link IllegalArgumentException}.
         * 
         * @return
         */
        public DigitalInputGPIOPort withNoPullResistance();

        /**
         * Enables the pull down resistance. Call this before {@link #enable()} otherwise this will throw an {@link IllegalArgumentException}.
         * 
         * @return
         */
        public DigitalInputGPIOPort withPullDownResistance();

        /**
         * Enables the pull up resistance. Call this before {@link #enable()} otherwise this will throw an {@link IllegalArgumentException}.
         * <br>
         * <br>
         * This is enabled by default.
         * 
         * @return
         */
        public DigitalInputGPIOPort withPullUpResistance();

        public DigitalInputGPIOPort addStateChangeListener(Consumer<DigitalInputPinStateChange> stateChangeListener);

        public boolean isEnabled();

        public static class DigitalInputPinStateChange
        {
            private boolean previous;
            private boolean current;

            public boolean getPrevious()
            {
                return this.previous;
            }

            public boolean getCurrent()
            {
                return this.current;
            }

            /**
             * @see #isRaisingEdge()
             * @return
             */
            public boolean isFallingEdge()
            {
                return this.previous && !this.current;
            }

            /**
             * @see #isFallingEdge()
             * @return
             */
            public boolean isRaisingEdge()
            {
                return !this.previous && this.current;
            }

            public DigitalInputPinStateChange(boolean previous, boolean current)
            {
                super();
                this.previous = previous;
                this.current = current;
            }

            @Override
            public String toString()
            {
                return "DigitalInputPinStateChange [previous=" + previous + ", current=" + current + "]";
            }

        }
    }

    public static interface DigitalOutputGPIOPort extends DigitalGPIOPort
    {
        @Override
        public DigitalOutputGPIOPort enable();

        @Override
        public DigitalOutputGPIOPort disable();

        /**
         * Returns true for high signal and false for a low signal
         * 
         * @return
         */
        public boolean getState();

        /**
         * True sets the output signal to high/active, false to low
         * 
         * @param active
         * @return
         */
        public DigitalOutputGPIOPort setState(boolean active);
    }

    public static interface PwmGPIOPort
    {

        public PwmGPIOPort enable();

        public PwmGPIOPort disable();

        /**
         * returns the state of the pwm signal. Is between 0.0 and 1.0
         * 
         * @return
         */
        public double getState();

        /**
         * Sets the state of the pwm signal. Can be between 0.0 and 1.0
         * 
         * @param value
         * @return
         */
        public PwmGPIOPort setState(double value);
    }
}
