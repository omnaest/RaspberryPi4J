package org.omnaest.pi.service.servo.internal.chip;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.omnaest.pi.service.servo.ServoDriverSimulationControl;
import org.omnaest.pi.service.servo.chip.PwmChipDriver;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Simulated, in-memory {@link PwmChipDriver} implementation used under the {@code simulation} Spring profile. Holds
 * per-channel PWM state for 16 channels (mirroring the real PCA9685's channel count, see
 * {@link PCA9685PwmChipDriver#getChannelCount()}) so that tests and an interactive {@code simulation} boot can
 * exercise {@link org.omnaest.pi.service.servo.ServoDriverService} consumers without any pi4j hardware dependency.
 * <p>
 * <b>Tick encoding.</b> Each channel stores an [on,off] pair of 12-bit ticks (0-4095), the same tick model the real
 * PCA9685 chip uses for its LEDn_ON/LEDn_OFF counters, plus the reserved "full on"/"full off" indicator value
 * ({@link #TICKS_PER_PERIOD}, i.e. bit 12 set):
 * <ul>
 * <li>{@link #setPwm(int, int)} stores {@code on=0} and {@code off} proportional to the requested duration relative
 * to the current period ({@code round(durationMicros / periodMicros * 4096)}), and clears both always-on/off
 * flags.</li>
 * <li>{@link #setAlwaysOn(int)} stores the full-on convention ({@code on=4096, off=0}) and sets the always-on flag
 * (clearing always-off).</li>
 * <li>{@link #setAlwaysOff(int)} stores the full-off convention ({@code on=0, off=4096}) and sets the always-off
 * flag (clearing always-on).</li>
 * <li>An untouched channel defaults to {@code [0,0]} (mirrors freshly powered-on/reset hardware, which reports no
 * pulse), with both always-on/off flags {@code false}.</li>
 * </ul>
 *
 * @author Danny Kunz
 */
@Component
@Profile("simulation")
public class SimulatedPwmChipDriver implements PwmChipDriver, ServoDriverSimulationControl
{
    private static final int                 CHANNEL_COUNT                  = 16;                            // mirrors PCA9685Pin.ALL.length

    /*
     * The real PCA9685PwmChipDriver constructs its PCA9685GpioProvider with an operating frequency of 48.828 Hz
     * (see PCA9685PwmChipDriver). One PWM period at that frequency lasts 1_000_000 / 48.828 = 20480.05...
     * microseconds; rounded to the nearest microsecond that is 20480, used here as the simulated chip's default
     * period so duration/period ratios computed against the simulation match the real hardware's default
     * operating point.
     */
    private static final int                 DEFAULT_PERIOD_DURATION_MICROS = 20480;

    /*
     * 12-bit tick resolution (0-4095) mirrors the PCA9685's LEDn_ON/LEDn_OFF counters; 4096 is the reserved
     * "full on"/"full off" indicator value (bit 12 set) - see setAlwaysOn/setAlwaysOff.
     */
    private static final int                 TICKS_PER_PERIOD               = 4096;

    private final Map<Integer, ChannelState> channelToState                 = new ConcurrentHashMap<>();

    private volatile int                     periodDurationMicros           = DEFAULT_PERIOD_DURATION_MICROS;

    // ---- PwmChipDriver ----

    @Override
    public int getChannelCount()
    {
        return CHANNEL_COUNT;
    }

    @Override
    public int getPeriodDurationMicros()
    {
        return this.periodDurationMicros;
    }

    @Override
    public void setPwm(int channel, int durationMicros)
    {
        int off = (int) Math.round(durationMicros / (double) this.periodDurationMicros * TICKS_PER_PERIOD);
        ChannelState state = this.stateOf(channel);
        synchronized (state)
        {
            state.on = 0;
            state.off = off;
            state.alwaysOn = false;
            state.alwaysOff = false;
        }
    }

    @Override
    public void setAlwaysOn(int channel)
    {
        ChannelState state = this.stateOf(channel);
        synchronized (state)
        {
            state.on = TICKS_PER_PERIOD;
            state.off = 0;
            state.alwaysOn = true;
            state.alwaysOff = false;
        }
    }

    @Override
    public void setAlwaysOff(int channel)
    {
        ChannelState state = this.stateOf(channel);
        synchronized (state)
        {
            state.on = 0;
            state.off = TICKS_PER_PERIOD;
            state.alwaysOn = false;
            state.alwaysOff = true;
        }
    }

    @Override
    public int[] getPwmOnOffValues(int channel)
    {
        ChannelState state = this.stateOf(channel);
        synchronized (state)
        {
            return new int[] {state.on, state.off};
        }
    }

    // ---- ServoDriverSimulationControl ----

    @Override
    public boolean isAlwaysOn(int channel)
    {
        return this.stateOf(channel).alwaysOn;
    }

    @Override
    public boolean isAlwaysOff(int channel)
    {
        return this.stateOf(channel).alwaysOff;
    }

    @Override
    public ServoDriverSimulationControl setPeriodDurationMicros(int micros)
    {
        this.periodDurationMicros = micros;
        return this;
    }

    @Override
    public ServoDriverSimulationControl reset()
    {
        this.channelToState.clear();
        return this;
    }

    // ---- shared per-channel state lookup ----

    private ChannelState stateOf(int channel)
    {
        return this.channelToState.computeIfAbsent(channel, unusedChannel -> new ChannelState());
    }

    private static class ChannelState
    {
        private volatile int     on        = 0;
        private volatile int     off       = 0;
        private volatile boolean alwaysOn  = false;
        private volatile boolean alwaysOff = false;
    }
}
