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
package org.omnaest.pi.service.servo;

/**
 * Control surface for a simulated {@link ServoDriverService} implementation. Allows tests and an interactive
 * {@code simulation} profile boot to inspect and configure the PWM state of a simulated servo driver chip
 * without any pi4j hardware dependency.
 *
 * @author Danny Kunz
 */
public interface ServoDriverSimulationControl
{
    /**
     * Returns the current [on,off] pwm values of the given channel.
     *
     * @param channel
     * @return
     */
    public int[] getPwmOnOffValues(int channel);

    public boolean isAlwaysOn(int channel);

    public boolean isAlwaysOff(int channel);

    /**
     * Returns the period duration in microseconds the simulated chip currently operates with.
     *
     * @return
     */
    public int getPeriodDurationMicros();

    /**
     * Sets the period duration in microseconds the simulated chip should operate with.
     *
     * @param micros
     * @return
     */
    public ServoDriverSimulationControl setPeriodDurationMicros(int micros);

    /**
     * Resets all simulated channel state back to its initial state.
     *
     * @return
     */
    public ServoDriverSimulationControl reset();
}
