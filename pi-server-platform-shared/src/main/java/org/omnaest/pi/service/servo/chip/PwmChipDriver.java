package org.omnaest.pi.service.servo.chip;

/**
 * Technology-neutral seam for a channel-indexed PWM chip driving servos/PWM outputs. Named for its role
 * ({@code chip}), not the concrete technology, so the PWM chip backing {@link org.omnaest.pi.service.servo.ServoDriverService}
 * can be swapped (e.g. real PCA9685 hardware vs. an in-memory simulation) without touching the servo service itself.
 * <p>
 * Channels are addressed by plain {@code int} index - no pi4j types appear anywhere on this interface.
 *
 * @author Danny Kunz
 */
public interface PwmChipDriver
{
    /**
     * Returns the number of PWM channels this chip exposes.
     *
     * @return
     */
    public int getChannelCount();

    /**
     * Returns the period duration in microseconds the chip currently operates with.
     *
     * @return
     */
    public int getPeriodDurationMicros();

    /**
     * Sets the pulse duration (in microseconds) for the given channel within the current PWM period.
     *
     * @param channel
     * @param durationMicros
     */
    public void setPwm(int channel, int durationMicros);

    /**
     * Sets the given channel to a permanent "always on" (fully high) state.
     *
     * @param channel
     */
    public void setAlwaysOn(int channel);

    /**
     * Sets the given channel to a permanent "always off" (fully low) state.
     *
     * @param channel
     */
    public void setAlwaysOff(int channel);

    /**
     * Returns the current [on,off] pwm tick values of the given channel.
     *
     * @param channel
     * @return
     */
    public int[] getPwmOnOffValues(int channel);
}
