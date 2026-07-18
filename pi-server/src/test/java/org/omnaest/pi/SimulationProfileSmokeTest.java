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
package org.omnaest.pi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.omnaest.pi.service.compass.CompassService;
import org.omnaest.pi.service.compass.CompassService.Module;
import org.omnaest.pi.service.i2c.I2CSimulationControl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Cross-tier seam test for the {@code simulation} Spring profile wiring (plan-56, increment 5). Proves that under
 * the {@code simulation} profile:
 * <ul>
 * <li>the application context loads with the simulated hardware beans wired (no {@link UnsatisfiedLinkError}, no
 * failed {@code @PostConstruct})</li>
 * <li>a downstream sensor service ({@link CompassService}) resolves and functions end-to-end against the simulated
 * I2C bus - a register is preset via {@link I2CSimulationControl} and the sensor's decoded output is asserted
 * against the value hand-derived from those bytes</li>
 * </ul>
 * A green context-loads-only test would miss profile-wiring / bean-resolution defects (e.g. a missing
 * {@code @Profile} or the real pi4j-backed bean winning over the simulated one); this test drives the real
 * decode path instead.
 *
 * @author Danny Kunz
 */
@SpringBootTest(classes = Application.class, properties = "spring.profiles.active=simulation")
public class SimulationProfileSmokeTest
{
    @Autowired
    private CompassService       compassService;

    @Autowired
    private I2CSimulationControl i2cSimulationControl;

    @Test
    public void testCompassNorthDirectionAngleOverSimulatedI2CBus() throws Exception
    {
        int bus = 1;
        int deviceAddress = Module.QMC5883L.getAddress();

        // CompassServiceImpl.getNorthDirectionAngle() busy-waits on register 0x06 bit 0 ("data ready"); preset it
        // set so the read proceeds without blocking.
        this.i2cSimulationControl.presetRegister(bus, deviceAddress, 0x06, (byte) 0x01);

        // x = 1000 (0x03E8), y = 0, z = 0 as 6 big-endian bytes starting at local address 0x00 - matches
        // CompassServiceImpl's data.asIntFromMsbToLsb(0,1)/(2,3)/(4,5) decoding.
        this.i2cSimulationControl.presetRegister(bus, deviceAddress, 0x00, (byte) 0x03, (byte) 0xE8, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00);

        // Hand-derived expected value: angle = round(-atan2(y, x) * 180 / PI + 180) = round(-atan2(0, 1000) * 180 /
        // PI + 180) = round(0 + 180) = 180.
        int expectedAngle = 180;

        int actualAngle = this.compassService.onBus(bus)
                                             .withModule(Module.QMC5883L)
                                             .getNorthDirectionAngle();

        assertEquals(expectedAngle, actualAngle);
    }
}
