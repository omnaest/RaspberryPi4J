package org.omnaest.pi.service.servo.internal;

import java.text.NumberFormat;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import org.omnaest.pi.service.servo.ServoDriverService;
import org.omnaest.pi.service.servo.chip.PwmChipDriver;
import org.omnaest.utils.element.cached.CachedElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.Builder;
import lombok.RequiredArgsConstructor;

@Service
public class ServoDriverServiceImpl implements ServoDriverService
{
    private static final Logger LOG = LoggerFactory.getLogger(ServoDriverServiceImpl.class);

    @Autowired
    private PwmChipDriver       chipDriver;

    private List<ServoProvider> servos;

    private void ensureInitialization()
    {
        if (this.servos == null)
        {
            synchronized (this)
            {
                if (this.servos == null)
                {
                    PwmChipDriver chipDriver = this.chipDriver;
                    this.servos = IntStream.range(0, chipDriver.getChannelCount())
                                           .mapToObj(channel -> ServoProvider.builder()
                                                                             .channel(channel)
                                                                             .chipDriver(chipDriver)
                                                                             .build())
                                           .toList();
                }
            }
        }
    }

    @Override
    public Servo servo(int index)
    {
        this.ensureInitialization();
        return this.servos.get(index)
                          .get();
    }

    @Override
    public PwmPin pwmPin(int index)
    {
        this.ensureInitialization();
        return this.servos.get(index)
                          .get();
    }

    private static interface ServoAndPwm extends Servo, PwmPin
    {
    }

    @RequiredArgsConstructor
    private static class ServoAndPwmImpl implements ServoAndPwm
    {
        private final int           channel;
        private final PwmChipDriver chipDriver;
        private int                 durationMinimum = 1;
        private int                 durationMaximum = 3200;
        private Supplier<Integer>   durationNeutral = () -> this.durationMaximum / 2;

        @Override
        public void applyAngle(int angle)
        {
            try
            {
                LOG.debug("Set servo " + this.channel + " state to angle " + angle);

                this.logCurrentPWMStates();

                this.chipDriver.setPwm(this.channel, this.determineServoDuration(angle));

                LOG.debug("Current state (servo " + this.channel + "): " + Arrays.toString(this.determineCurrentOnOffValues()));

                this.logCurrentPWMStates();
            }
            catch (Exception e)
            {
                LOG.error("Exception during servo state action", e);
            }

        }

        @Override
        public void applySpeed(double speed)
        {
            try
            {
                LOG.debug("Set servo " + this.channel + " state to speed " + NumberFormat.getPercentInstance()
                                                                                         .format(speed));

                this.logCurrentPWMStates();

                this.chipDriver.setPwm(this.channel, this.determineServoDurationForSpeed(speed));

                LOG.debug("Current state (servo " + this.channel + "): " + Arrays.toString(this.determineCurrentOnOffValues()));

                this.logCurrentPWMStates();
            }
            catch (Exception e)
            {
                LOG.error("Exception during servo state action", e);
            }

        }

        private int determineServoDuration(int value)
        {
            // Set 0.9ms pulse (R/C Servo minimum position)
            // Set 1.5ms pulse (R/C Servo neutral position)
            // Set 2.1ms pulse (R/C Servo maximum position)
            return (int) Math.round(this.durationMinimum + (this.durationMaximum - this.durationMinimum) * (value / 360.0));
        }

        private int determineServoDurationForSpeed(double value)
        {
            int result = 1;

            if (value >= 0)
            {
                int maxToNeutralDifference = Math.abs(this.durationMaximum - this.durationNeutral.get());
                result = (int) Math.round(this.durationNeutral.get() + maxToNeutralDifference * value);
            }
            else
            {
                int minToNeutralDifference = Math.abs(this.durationNeutral.get() - this.durationMinimum);
                result = (int) Math.round(this.durationNeutral.get() + minToNeutralDifference * value);
            }

            return result;
        }

        private int[] determineCurrentOnOffValues()
        {
            return this.chipDriver.getPwmOnOffValues(this.channel);
        }

        private void logCurrentPWMStates()
        {
            if (LOG.isDebugEnabled())
            {
                int[] onOffValues = this.chipDriver.getPwmOnOffValues(this.channel);

                LOG.debug("channel " + this.channel + ": ON value [" + onOffValues[0] + "], OFF value [" + onOffValues[1] + "] ");
            }
        }

        @Override
        public void applyDurationMinimum(int min)
        {
            this.durationMinimum = min;
        }

        @Override
        public void applyDurationMaximum(int max)
        {
            this.durationMaximum = max;
        }

        @Override
        public void applyDurationNeutral(int neutral)
        {
            this.durationNeutral = () -> neutral;
        }

        @Override
        public void set(boolean value)
        {
            if (value)
            {
                this.chipDriver.setAlwaysOn(this.channel);
            }
            else
            {
                this.chipDriver.setAlwaysOff(this.channel);
            }
        }

        @Override
        public void setPwm(double value)
        {
            int periodDurationMicros = this.chipDriver.getPeriodDurationMicros();
            int duration = (int) Math.round(value * periodDurationMicros);

            if (duration <= 0)
            {
                this.disable();
            }
            else if (duration >= periodDurationMicros)
            {
                this.enable();
            }
            else
            {
                this.chipDriver.setPwm(this.channel, duration);
            }
        }

    }

    @Builder
    private static class ServoProvider implements Supplier<ServoAndPwm>
    {
        private final int                        channel;
        private final PwmChipDriver              chipDriver;
        private final CachedElement<ServoAndPwm> servo = CachedElement.of(this::createServoInstance);

        private ServoAndPwm createServoInstance()
        {
            try
            {
                this.chipDriver.setAlwaysOff(this.channel);
            }
            catch (Exception e)
            {
                LOG.error("Failed to reset the channel: " + this.channel, e);
            }

            return new ServoAndPwmImpl(this.channel, this.chipDriver);
        }

        @Override
        public ServoAndPwm get()
        {
            return this.servo.get();
        }

    }

}
