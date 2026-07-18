package org.omnaest.pi.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.omnaest.pi.domain.UltrasonicSensorConfiguration;
import org.omnaest.pi.service.UltrasonicServiceImpl.UltrasonicDistanceSensorImpl;
import org.omnaest.pi.service.gpio.internal.SimulatedGPIOServiceImpl;

/**
 * Drives {@link UltrasonicDistanceSensorImpl} against a real {@link SimulatedGPIOServiceImpl} (no mocks - the
 * simulated GPIO service IS the test double). {@link UltrasonicDistanceSensorImpl#getDistance()} busy-waits on
 * nanosecond timing on its own thread, so this test is inherently real-time: a driver thread observes the trigger
 * pulse via {@link SimulatedGPIOServiceImpl#getDigitalOutputState(int)} and then drives the echo pin via
 * {@link SimulatedGPIOServiceImpl#setDigitalInputState(int, boolean)} - bounded, generous polling is used
 * throughout; the race itself is accepted (plan-56 open risk), not eliminated.
 */
public class UltrasonicServiceImplTest
{
    private static final int         TRIGGER_PORT = 17;
    private static final int         ECHO_PORT    = 27;

    private SimulatedGPIOServiceImpl simulatedGpioService;

    @AfterEach
    public void tearDown()
    {
        if (this.simulatedGpioService != null)
        {
            this.simulatedGpioService.destroy();
        }
    }

    @Test
    public void testGetDistanceReturnsPlausibleValueForSimulatedEcho() throws Exception
    {
        this.simulatedGpioService = new SimulatedGPIOServiceImpl();

        UltrasonicDistanceSensorImpl sensor = new UltrasonicDistanceSensorImpl(this.simulatedGpioService);

        UltrasonicSensorConfiguration configuration = new UltrasonicSensorConfiguration();
        configuration.setTriggerPort(TRIGGER_PORT);
        configuration.setEchoPort(ECHO_PORT);
        configuration.setPingTimeout(TimeUnit.SECONDS.toNanos(2));
        configuration.setSignalTimeout(TimeUnit.SECONDS.toNanos(2));
        // widen the trigger-high phase (default {5,20,0}us is too narrow to reliably observe from a polling
        // thread) - sendSignal()'s busy-wait/toggle algorithm itself is untouched, only its input configuration
        // is widened for test reliability.
        configuration.setSignals(new int[] {200, 5000, 0});

        sensor.init(configuration);

        CompletableFuture<Double> distanceFuture = CompletableFuture.supplyAsync(sensor::getDistance);

        boolean triggerObservedHigh = waitUntil(() -> this.simulatedGpioService.getDigitalOutputState(TRIGGER_PORT), 3000);
        assertTrue(triggerObservedHigh, "trigger pulse never observed high - wiring bug between UltrasonicServiceImpl and GPIOService");

        // wait for the trigger pulse to fully complete (back to low) BEFORE driving the echo pin - sendSignal()
        // only returns (and the sensor thread only enters its echo-detection busy-wait) once the trigger is low
        // again, so this ordering guarantees the echo-high window below cannot be missed by a sensor thread that
        // hasn't started polling yet.
        boolean triggerPulseCompleted = waitUntil(() -> !this.simulatedGpioService.getDigitalOutputState(TRIGGER_PORT), 3000);
        assertTrue(triggerPulseCompleted, "trigger pulse never completed (returned low) - sendSignal() may be stuck");

        // simulate a ~5ms echo pulse (a known, plausible travel time)
        this.simulatedGpioService.setDigitalInputState(ECHO_PORT, true);
        busySleepNanos(TimeUnit.MILLISECONDS.toNanos(5));
        this.simulatedGpioService.setDigitalInputState(ECHO_PORT, false);

        double distance = distanceFuture.get(5, TimeUnit.SECONDS);

        assertTrue(distance > 0, "expected a positive simulated distance but got " + distance);
        assertTrue(distance < 10000, "expected a plausible (sub-10m) simulated distance but got " + distance);
    }

    private static boolean waitUntil(BooleanSupplier condition, long timeoutMillis)
    {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline)
        {
            if (condition.getAsBoolean())
            {
                return true;
            }
        }
        return condition.getAsBoolean();
    }

    private static void busySleepNanos(long nanos) throws InterruptedException
    {
        Thread.sleep(nanos / 1_000_000, (int) (nanos % 1_000_000));
    }
}
