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
package org.omnaest.pi.service.i2c;

import java.util.Optional;

/**
 * Control surface for a simulated {@link I2CService} implementation. Allows tests and an interactive
 * {@code simulation} profile boot to preset and inspect register state on a simulated I2C bus without any
 * pi4j hardware dependency.
 *
 * @author Danny Kunz
 */
public interface I2CSimulationControl
{
    /**
     * Presets the given register of a simulated device to the given data.
     *
     * @param busNumber
     * @param deviceAddress
     * @param localAddress
     * @param data
     * @return
     */
    public I2CSimulationControl presetRegister(int busNumber, int deviceAddress, int localAddress, byte... data);

    /**
     * Reads the current simulated register data of the given size, if present.
     *
     * @param busNumber
     * @param deviceAddress
     * @param localAddress
     * @param size
     * @return
     */
    public Optional<byte[]> readRegister(int busNumber, int deviceAddress, int localAddress, int size);

    /**
     * Resets all simulated register state back to its initial state.
     *
     * @return
     */
    public I2CSimulationControl reset();

    /**
     * Resets the simulated register state of a single device back to its initial state.
     *
     * @param busNumber
     * @param deviceAddress
     * @return
     */
    public I2CSimulationControl reset(int busNumber, int deviceAddress);
}
