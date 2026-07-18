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

import java.util.concurrent.TimeUnit;

/**
 * Control surface for a simulated {@link GPIOService} implementation. Allows tests and an interactive
 * {@code simulation} profile boot to inject and inspect digital input/output and PWM port state without any
 * pi4j hardware dependency.
 *
 * @author Danny Kunz
 */
public interface GPIOSimulationControl
{
    /**
     * Sets the state of a digital input port as if it was driven externally.
     *
     * @param port
     * @param active
     * @return
     */
    public GPIOSimulationControl setDigitalInputState(int port, boolean active);

    /**
     * Returns the current simulated state of the given digital input port.
     *
     * @param port
     * @return
     */
    public boolean getDigitalInputState(int port);

    public boolean isDigitalInputEnabled(int port);

    /**
     * Returns the current simulated state of the given digital output port.
     *
     * @param port
     * @return
     */
    public boolean getDigitalOutputState(int port);

    public boolean isDigitalOutputEnabled(int port);

    /**
     * Returns the current simulated PWM output state of the given port. Is between 0.0 and 1.0.
     *
     * @param port
     * @return
     */
    public double getPwmOutputState(int port);

    public boolean isPwmOutputEnabled(int port);

    /**
     * Schedules a future state change of the given digital input port, e.g. to simulate an incoming echo signal.
     *
     * @param port
     * @param active
     * @param delay
     * @param timeUnit
     * @return
     */
    public GPIOSimulationControl scheduleDigitalInputState(int port, boolean active, long delay, TimeUnit timeUnit);

    /**
     * Resets all simulated port state back to its initial state.
     *
     * @return
     */
    public GPIOSimulationControl reset();
}
