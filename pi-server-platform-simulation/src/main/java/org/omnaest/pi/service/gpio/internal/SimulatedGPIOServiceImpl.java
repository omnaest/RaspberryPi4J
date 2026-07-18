package org.omnaest.pi.service.gpio.internal;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.annotation.PreDestroy;

import org.omnaest.pi.service.gpio.GPIOService;
import org.omnaest.pi.service.gpio.GPIOService.DigitalInputGPIOPort.DigitalInputPinStateChange;
import org.omnaest.pi.service.gpio.GPIOSimulationControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Simulated, in-memory {@link GPIOService} implementation used under the {@code simulation} Spring profile. Mirrors
 * {@code GPIOServiceImpl}'s three port maps (digital output / digital input / pwm) but backs each entry with plain
 * in-memory state instead of pi4j pin handles, so tests and an interactive {@code simulation} boot can exercise GPIO
 * consumers without any hardware dependency.
 * <p>
 * The {@link DigitalOutputGPIOPort}/{@link DigitalInputGPIOPort}/{@link PwmGPIOPort} accessors returned by this
 * service read and write the SAME per-port state held in the maps below, so a value set via
 * {@link GPIOSimulationControl} is visible through the port accessor and vice-versa.
 *
 * @author Danny Kunz
 */
@Service
@Profile("simulation")
public class SimulatedGPIOServiceImpl implements GPIOService, GPIOSimulationControl
{
    private static final Logger                 LOG                      = LoggerFactory.getLogger(SimulatedGPIOServiceImpl.class);

    /*
     * The simulated pwm range mirrors GPIOServiceImpl's PWM_DEFAULT_RANGE (100), which is the range the real
     * PwmGPIOPort accessor applies via pin.setPwmRange(100). Simulation has no hardware-specific range concept, so
     * the same default is reused for both setGPIOPortPWMValue(port, int) and the PwmGPIOPort/GPIOSimulationControl
     * 0.0-1.0 surface, keeping the two consistent with each other.
     */
    private static final double                 PWM_DEFAULT_RANGE        = 100.0;

    private final Map<Integer, OutputPortState> portToDigitalOutputState = new ConcurrentHashMap<>();
    private final Map<Integer, InputPortState>  portToDigitalInputState  = new ConcurrentHashMap<>();
    private final Map<Integer, PwmPortState>    portToPwmState           = new ConcurrentHashMap<>();

    private final ScheduledExecutorService      executorService          = Executors.newSingleThreadScheduledExecutor();

    @PreDestroy
    public void destroy()
    {
        this.executorService.shutdownNow();
    }

    // ---- GPIOService ----

    @Override
    public void enableGPIOPortForDigitalOutput(int port)
    {
        this.outputStateOf(port).enabled = true;
        LOG.info("Enabled port " + port + " for digital output");
    }

    @Override
    public void enableGPIOPortForPWM(int port)
    {
        this.pwmStateOf(port).enabled = true;
        LOG.info("Enabled port " + port + " for pwm output");
    }

    @Override
    public void enableGPIOPort(int port, boolean active)
    {
        this.outputStateOf(port).state = active;
        LOG.info("Set state of port " + port + " to " + active);
    }

    @Override
    public void setGPIOPortPWMValue(int port, int value)
    {
        this.pwmStateOf(port).state = this.clamp(value / PWM_DEFAULT_RANGE);
        LOG.info("Set pwm value of port " + port + " to " + value);
    }

    @Override
    public DigitalOutputGPIOPort getDigitalOutputGPIOPort(int port)
    {
        return new SimulatedDigitalOutputGPIOPort(port);
    }

    @Override
    public DigitalInputGPIOPort getDigitalInputGPIOPort(int port)
    {
        return new SimulatedDigitalInputGPIOPort(port);
    }

    @Override
    public PwmGPIOPort getPwmGPIOPort(int port)
    {
        return new SimulatedPwmGPIOPort(port);
    }

    // ---- GPIOSimulationControl ----

    @Override
    public GPIOSimulationControl setDigitalInputState(int port, boolean active)
    {
        InputPortState state = this.inputStateOf(port);
        boolean previous;
        boolean current;
        synchronized (state)
        {
            previous = state.state;
            current = active;
            state.state = current;
        }

        if (previous != current)
        {
            DigitalInputPinStateChange stateChange = new DigitalInputPinStateChange(previous, current);
            LOG.info("Digital input port " + port + " changed state: " + stateChange);
            state.listeners.forEach(listener -> listener.accept(stateChange));
        }
        return this;
    }

    @Override
    public boolean getDigitalInputState(int port)
    {
        return this.inputStateOf(port).state;
    }

    @Override
    public boolean isDigitalInputEnabled(int port)
    {
        return this.inputStateOf(port).enabled;
    }

    @Override
    public boolean getDigitalOutputState(int port)
    {
        return this.outputStateOf(port).state;
    }

    @Override
    public boolean isDigitalOutputEnabled(int port)
    {
        return this.outputStateOf(port).enabled;
    }

    @Override
    public double getPwmOutputState(int port)
    {
        return this.pwmStateOf(port).state;
    }

    @Override
    public boolean isPwmOutputEnabled(int port)
    {
        return this.pwmStateOf(port).enabled;
    }

    @Override
    public GPIOSimulationControl scheduleDigitalInputState(int port, boolean active, long delay, TimeUnit timeUnit)
    {
        this.executorService.schedule(() -> this.setDigitalInputState(port, active), delay, timeUnit);
        return this;
    }

    @Override
    public GPIOSimulationControl reset()
    {
        this.portToDigitalOutputState.clear();
        this.portToDigitalInputState.clear();
        this.portToPwmState.clear();
        return this;
    }

    // ---- shared per-port state lookup (backs both GPIOService accessors and GPIOSimulationControl) ----

    private OutputPortState outputStateOf(int port)
    {
        return this.portToDigitalOutputState.computeIfAbsent(port, p -> new OutputPortState());
    }

    private InputPortState inputStateOf(int port)
    {
        return this.portToDigitalInputState.computeIfAbsent(port, p -> new InputPortState());
    }

    private PwmPortState pwmStateOf(int port)
    {
        return this.portToPwmState.computeIfAbsent(port, p -> new PwmPortState());
    }

    private double clamp(double value)
    {
        return Math.max(0.0, Math.min(1.0, value));
    }

    // ---- per-port in-memory state holders ----

    private static class OutputPortState
    {
        private volatile boolean enabled = false;
        private volatile boolean state   = false;
    }

    private static class InputPortState
    {
        private volatile boolean                                                 enabled   = false;
        private volatile boolean                                                 state     = false;

        private final CopyOnWriteArrayList<Consumer<DigitalInputPinStateChange>> listeners = new CopyOnWriteArrayList<>();
    }

    private static class PwmPortState
    {
        private volatile boolean enabled = false;
        private volatile double  state   = 0.0;
    }

    // ---- port accessors sharing the maps above ----

    private class SimulatedDigitalOutputGPIOPort implements DigitalOutputGPIOPort
    {
        private final int port;

        public SimulatedDigitalOutputGPIOPort(int port)
        {
            this.port = port;
        }

        @Override
        public DigitalOutputGPIOPort enable()
        {
            SimulatedGPIOServiceImpl.this.outputStateOf(this.port).enabled = true;
            LOG.info("Enabled port " + this.port + " for digital output");
            return this;
        }

        @Override
        public DigitalOutputGPIOPort disable()
        {
            SimulatedGPIOServiceImpl.this.outputStateOf(this.port).enabled = false;
            LOG.info("Disabled port " + this.port + " for digital output");
            return this;
        }

        @Override
        public boolean getState()
        {
            return SimulatedGPIOServiceImpl.this.outputStateOf(this.port).state;
        }

        @Override
        public DigitalOutputGPIOPort setState(boolean active)
        {
            SimulatedGPIOServiceImpl.this.outputStateOf(this.port).state = active;
            LOG.info("Sets the state for digital output port " + this.port + " to " + active);
            return this;
        }

        @Override
        public boolean isEnabled()
        {
            return SimulatedGPIOServiceImpl.this.outputStateOf(this.port).enabled;
        }

        @Override
        public String toString()
        {
            return "SimulatedDigitalOutputGPIOPort [port=" + this.port + ", enabled=" + this.isEnabled() + "]";
        }
    }

    private class SimulatedDigitalInputGPIOPort implements DigitalInputGPIOPort
    {
        private final int port;

        public SimulatedDigitalInputGPIOPort(int port)
        {
            this.port = port;
        }

        @Override
        public DigitalInputGPIOPort enable()
        {
            SimulatedGPIOServiceImpl.this.inputStateOf(this.port).enabled = true;
            LOG.info("Enabled port " + this.port + " for digital input");
            return this;
        }

        @Override
        public DigitalInputGPIOPort disable()
        {
            SimulatedGPIOServiceImpl.this.inputStateOf(this.port).enabled = false;
            LOG.info("Disabled port " + this.port + " for digital input");
            return this;
        }

        @Override
        public boolean getState()
        {
            return SimulatedGPIOServiceImpl.this.inputStateOf(this.port).state;
        }

        // pull-resistance selection has no in-memory-simulation equivalent - kept as fluent no-ops so callers
        // (e.g. RotaryEncoderServiceImpl, UltrasonicServiceImpl) can chain them unchanged under either profile.
        @Override
        public DigitalInputGPIOPort withNoPullResistance()
        {
            return this;
        }

        @Override
        public DigitalInputGPIOPort withPullDownResistance()
        {
            return this;
        }

        @Override
        public DigitalInputGPIOPort withPullUpResistance()
        {
            return this;
        }

        @Override
        public DigitalInputGPIOPort addStateChangeListener(Consumer<DigitalInputPinStateChange> stateChangeListener)
        {
            SimulatedGPIOServiceImpl.this.inputStateOf(this.port).listeners.add(stateChangeListener);
            return this;
        }

        @Override
        public boolean isEnabled()
        {
            return SimulatedGPIOServiceImpl.this.inputStateOf(this.port).enabled;
        }

        @Override
        public String toString()
        {
            return "SimulatedDigitalInputGPIOPort [port=" + this.port + ", enabled=" + this.isEnabled() + "]";
        }
    }

    private class SimulatedPwmGPIOPort implements PwmGPIOPort
    {
        private final int port;

        public SimulatedPwmGPIOPort(int port)
        {
            this.port = port;
        }

        @Override
        public PwmGPIOPort enable()
        {
            SimulatedGPIOServiceImpl.this.pwmStateOf(this.port).enabled = true;
            LOG.info("Enabled port " + this.port + " for pwm output");
            return this;
        }

        @Override
        public PwmGPIOPort disable()
        {
            SimulatedGPIOServiceImpl.this.pwmStateOf(this.port).enabled = false;
            LOG.info("Disabled port " + this.port + " for pwm output");
            return this;
        }

        @Override
        public double getState()
        {
            return SimulatedGPIOServiceImpl.this.pwmStateOf(this.port).state;
        }

        @Override
        public PwmGPIOPort setState(double value)
        {
            SimulatedGPIOServiceImpl.this.pwmStateOf(this.port).state = SimulatedGPIOServiceImpl.this.clamp(value);
            LOG.info("Set pwm value of port " + this.port + " to " + value);
            return this;
        }
    }
}
