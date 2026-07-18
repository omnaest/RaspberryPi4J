package org.omnaest.pi.service.servo.internal.chip;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class SimulatedPwmChipDriverTest
{
    @Test
    public void testGetChannelCount()
    {
        SimulatedPwmChipDriver driver = new SimulatedPwmChipDriver();
        assertEquals(16, driver.getChannelCount());
    }

    @Test
    public void testDefaultPeriodDurationMicros()
    {
        SimulatedPwmChipDriver driver = new SimulatedPwmChipDriver();
        // Derived from the real PCA9685's ~48.828 Hz operating frequency: round(1_000_000 / 48.828) = 20480
        assertEquals(20480, driver.getPeriodDurationMicros());
    }

    @Test
    public void testSetPeriodDurationMicrosRoundTrip()
    {
        SimulatedPwmChipDriver driver = new SimulatedPwmChipDriver();
        driver.setPeriodDurationMicros(1000);
        assertEquals(1000, driver.getPeriodDurationMicros());
    }

    @Test
    public void testUntouchedChannelDefaults()
    {
        SimulatedPwmChipDriver driver = new SimulatedPwmChipDriver();
        assertArrayEquals(new int[] {0, 0}, driver.getPwmOnOffValues(5));
        assertFalse(driver.isAlwaysOn(5));
        assertFalse(driver.isAlwaysOff(5));
    }

    @Test
    public void testSetPwmReflectsDurationAndClearsAlwaysFlags()
    {
        SimulatedPwmChipDriver driver = new SimulatedPwmChipDriver();
        int channel = 3;

        // Prime with an always-on state first, then confirm setPwm clears it.
        driver.setAlwaysOn(channel);
        assertTrue(driver.isAlwaysOn(channel));

        // period = 20480 (default); requested duration 1000us -> off = round(1000/20480*4096) = 200
        driver.setPwm(channel, 1000);

        assertArrayEquals(new int[] {0, 200}, driver.getPwmOnOffValues(channel));
        assertFalse(driver.isAlwaysOn(channel));
        assertFalse(driver.isAlwaysOff(channel));
    }

    @Test
    public void testSetPwmReflectsDurationAgainstCustomPeriod()
    {
        SimulatedPwmChipDriver driver = new SimulatedPwmChipDriver();
        int channel = 7;

        driver.setPeriodDurationMicros(1000);
        // duration 250us of a 1000us period -> off = round(250/1000*4096) = 1024
        driver.setPwm(channel, 250);

        assertArrayEquals(new int[] {0, 1024}, driver.getPwmOnOffValues(channel));
    }

    @Test
    public void testSetAlwaysOnAndSetAlwaysOffAreMutuallyExclusive()
    {
        SimulatedPwmChipDriver driver = new SimulatedPwmChipDriver();
        int channel = 9;

        driver.setAlwaysOn(channel);
        assertTrue(driver.isAlwaysOn(channel));
        assertFalse(driver.isAlwaysOff(channel));
        assertArrayEquals(new int[] {4096, 0}, driver.getPwmOnOffValues(channel));

        driver.setAlwaysOff(channel);
        assertFalse(driver.isAlwaysOn(channel));
        assertTrue(driver.isAlwaysOff(channel));
        assertArrayEquals(new int[] {0, 4096}, driver.getPwmOnOffValues(channel));
    }

    @Test
    public void testResetRestoresDefaults()
    {
        SimulatedPwmChipDriver driver = new SimulatedPwmChipDriver();
        int channel = 2;

        driver.setAlwaysOn(channel);
        driver.setPwm(channel, 500);
        driver.setAlwaysOff(11);

        driver.reset();

        assertArrayEquals(new int[] {0, 0}, driver.getPwmOnOffValues(channel));
        assertFalse(driver.isAlwaysOn(channel));
        assertFalse(driver.isAlwaysOff(channel));

        assertArrayEquals(new int[] {0, 0}, driver.getPwmOnOffValues(11));
        assertFalse(driver.isAlwaysOn(11));
        assertFalse(driver.isAlwaysOff(11));
    }
}
