package org.omnaest.pi.service.gpio.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.omnaest.pi.service.gpio.GPIOService.DigitalInputGPIOPort;
import org.omnaest.pi.service.gpio.GPIOService.DigitalInputGPIOPort.DigitalInputPinStateChange;
import org.omnaest.pi.service.gpio.GPIOService.DigitalOutputGPIOPort;
import org.omnaest.pi.service.gpio.GPIOService.PwmGPIOPort;

public class SimulatedGPIOServiceImplTest
{
    private SimulatedGPIOServiceImpl simulation;

    @BeforeEach
    public void setUp()
    {
        this.simulation = new SimulatedGPIOServiceImpl();
    }

    @AfterEach
    public void tearDown()
    {
        // Executors.newSingleThreadScheduledExecutor() creates non-daemon threads - shut every instance down so
        // repeated test runs (and, per plan-56, repeated Spring context reloads) never leak threads.
        this.simulation.destroy();
    }

    @Test
    public void testDigitalOutputSetViaPortVisibleThroughControlSurface()
    {
        DigitalOutputGPIOPort port = this.simulation.getDigitalOutputGPIOPort(4)
                                                    .enable();

        assertTrue(this.simulation.isDigitalOutputEnabled(4));
        assertFalse(this.simulation.getDigitalOutputState(4));

        port.setState(true);

        assertTrue(this.simulation.getDigitalOutputState(4));
        assertTrue(port.getState());

        port.disable();
        assertFalse(this.simulation.isDigitalOutputEnabled(4));
    }

    @Test
    public void testDigitalInputSetViaControlSurfaceVisibleThroughPort()
    {
        DigitalInputGPIOPort port = this.simulation.getDigitalInputGPIOPort(7)
                                                   .withPullDownResistance()
                                                   .enable();

        assertTrue(this.simulation.isDigitalInputEnabled(7));
        assertFalse(port.getState());

        this.simulation.setDigitalInputState(7, true);

        assertTrue(this.simulation.getDigitalInputState(7));
        assertTrue(port.getState());
    }

    @Test
    public void testEdgeFiringOnlyOnActualStateChange()
    {
        List<DigitalInputPinStateChange> observedChanges = new ArrayList<>();
        this.simulation.getDigitalInputGPIOPort(9)
                       .enable()
                       .addStateChangeListener(observedChanges::add);

        // rising edge false -> true: must fire exactly once
        this.simulation.setDigitalInputState(9, true);
        assertEquals(1, observedChanges.size());
        assertTrue(observedChanges.get(0)
                                  .isRaisingEdge());
        assertFalse(observedChanges.get(0)
                                   .getPrevious());
        assertTrue(observedChanges.get(0)
                                  .getCurrent());

        // repeating the same value (true -> true) is NOT an edge - must NOT fire again
        this.simulation.setDigitalInputState(9, true);
        assertEquals(1, observedChanges.size(), "no-op state re-assertion must not fire a listener");

        // falling edge true -> false: must fire exactly once more
        this.simulation.setDigitalInputState(9, false);
        assertEquals(2, observedChanges.size());
        assertTrue(observedChanges.get(1)
                                  .isFallingEdge());

        // repeating false -> false again must not fire
        this.simulation.setDigitalInputState(9, false);
        assertEquals(2, observedChanges.size(), "no-op state re-assertion must not fire a listener");
    }

    @Test
    public void testPwmValueAndControlSurface()
    {
        PwmGPIOPort port = this.simulation.getPwmGPIOPort(12)
                                          .enable();

        assertTrue(this.simulation.isPwmOutputEnabled(12));

        port.setState(0.5);

        assertEquals(0.5, this.simulation.getPwmOutputState(12));
        assertEquals(0.5, port.getState());

        // the raw GPIOService entry point uses the same 0-100 default range
        this.simulation.setGPIOPortPWMValue(12, 25);
        assertEquals(0.25, this.simulation.getPwmOutputState(12));
    }

    @Test
    public void testScheduleDigitalInputStateFlipsAfterDelayAndShutdownDoesNotHang()
    {
        this.simulation.getDigitalInputGPIOPort(15)
                       .enable();
        assertFalse(this.simulation.getDigitalInputState(15));

        this.simulation.scheduleDigitalInputState(15, true, 50, TimeUnit.MILLISECONDS);

        long deadline = System.currentTimeMillis() + 2000;
        boolean flipped = false;
        while (System.currentTimeMillis() < deadline)
        {
            if (this.simulation.getDigitalInputState(15))
            {
                flipped = true;
                break;
            }
        }
        assertTrue(flipped, "scheduled digital input state change never took effect within the bounded wait");

        // must shut down promptly, not hang, and be safe to call from a test teardown
        assertDoesNotHangOnDestroy();
    }

    private void assertDoesNotHangOnDestroy()
    {
        long start = System.currentTimeMillis();
        this.simulation.destroy();
        long elapsed = System.currentTimeMillis() - start;
        assertTrue(elapsed < 2000, "destroy() took too long, executor shutdown may be hanging: " + elapsed + "ms");
    }

    @Test
    public void testResetClearsStateAndListeners()
    {
        DigitalOutputGPIOPort outputPort = this.simulation.getDigitalOutputGPIOPort(1)
                                                          .enable();
        outputPort.setState(true);

        List<DigitalInputPinStateChange> observedChanges = new ArrayList<>();
        this.simulation.getDigitalInputGPIOPort(2)
                       .enable()
                       .addStateChangeListener(observedChanges::add);
        this.simulation.setDigitalInputState(2, true);
        assertEquals(1, observedChanges.size());

        this.simulation.getPwmGPIOPort(3)
                       .enable()
                       .setState(0.9);

        this.simulation.reset();

        assertFalse(this.simulation.isDigitalOutputEnabled(1));
        assertFalse(this.simulation.getDigitalOutputState(1));
        assertFalse(this.simulation.isDigitalInputEnabled(2));
        assertFalse(this.simulation.getDigitalInputState(2));
        assertFalse(this.simulation.isPwmOutputEnabled(3));
        assertEquals(0.0, this.simulation.getPwmOutputState(3));

        // a state change after reset must not reach the listener registered before reset - its port state was
        // cleared, so the listener list backing it is gone too
        this.simulation.setDigitalInputState(2, true);
        assertEquals(1, observedChanges.size(), "listener registered before reset must not fire after reset");
    }
}
