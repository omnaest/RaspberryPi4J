package org.omnaest.pi.service.servo.internal;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.annotation.PreDestroy;

import org.omnaest.pi.service.servo.ServoDriverService;
import org.omnaest.utils.element.cached.CachedElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.pi4j.gpio.extension.pca.PCA9685GpioProvider;
import com.pi4j.gpio.extension.pca.PCA9685Pin;
import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinPwmOutput;
import com.pi4j.io.gpio.Pin;
import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CFactory;
import com.pi4j.io.i2c.I2CFactory.UnsupportedBusNumberException;

import lombok.Builder;
import lombok.RequiredArgsConstructor;

@Service
public class ServoDriverServiceImpl implements ServoDriverService
{
    private static final Logger LOG = LoggerFactory.getLogger(ServoDriverServiceImpl.class);

    private List<ServoProvider> servos;
    private PCA9685GpioProvider provider;

    private void ensureInitialization()
    {
        if (this.servos == null)
        {
            synchronized (this)
            {
                if (this.servos == null)
                {
                    try
                    {
                        I2CBus bus = this.newI2CBus();
                        BigDecimal frequency = new BigDecimal("48.828");
                        BigDecimal frequencyCorrectionFactor = new BigDecimal("1.0578");
                        PCA9685GpioProvider provider = this.provider = new PCA9685GpioProvider(bus, 0x40, frequency, frequencyCorrectionFactor);

                        GpioController gpioController = GpioFactory.getInstance();
                        this.servos = Stream.of(PCA9685Pin.ALL)
                                            .map(pin -> ServoProvider.builder()
                                                                     .gpioController(gpioController)
                                                                     .gpioProvider(provider)
                                                                     .pin(pin)
                                                                     .build())
                                            .toList();

                    }
                    catch (Exception e)
                    {
                        LOG.warn("Unable to initialize I2C");
                        LOG.debug("I2C error", e);
                    }
                }
            }
        }
    }

    @PreDestroy
    public void destroy()
    {
        if (this.provider != null)
        {
            this.provider.shutdown();
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

    private I2CBus newI2CBus() throws UnsupportedBusNumberException, IOException
    {
        return Arrays.asList(I2CBus.BUS_1, I2CBus.BUS_0, I2CBus.BUS_2)
                     .stream()
                     .map(index ->
                     {
                         try
                         {
                             I2CBus i2cBus = I2CFactory.getInstance(index);
                             LOG.info("Created instance of bus " + index);
                             return i2cBus;
                         }
                         catch (Exception e)
                         {
                             LOG.debug("", e);
                             LOG.info("Tested bus " + index + " as not available");
                             return null;
                         }
                     })
                     .filter(factory -> factory != null)
                     .findFirst()
                     .orElseThrow(() -> new IllegalStateException("No I2C bus available"));
    }

    private static interface ServoAndPwm extends Servo, PwmPin
    {
    }

    @RequiredArgsConstructor
    private static class ServoAndPwmImpl implements ServoAndPwm
    {
        private final GpioPinPwmOutput    output;
        private final Pin                 pin;
        private final PCA9685GpioProvider provider;
        private final int                 index;
        private int                       durationMinimum = 1;
        private int                       durationMaximum = 3200;
        private Supplier<Integer>         durationNeutral = () -> this.durationMaximum / 2;

        @Override
        public void applyAngle(int angle)
        {
            try
            {
                LOG.debug("Set servo " + this.index + " state to angle " + angle);

                this.logCurrentPWMStates();

                this.provider.setPwm(this.determineServoPin(this.index), this.determineServoDuration(angle));

                LOG.debug("Current state (servo " + this.index + "): " + Arrays.toString(this.determineCurrentOnOffValues(this.index)));

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
                LOG.debug("Set servo " + this.index + " state to speed " + NumberFormat.getPercentInstance()
                                                                                       .format(speed));

                this.logCurrentPWMStates();

                this.provider.setPwm(this.determineServoPin(this.index), this.determineServoDurationForSpeed(speed));

                LOG.debug("Current state (servo " + this.index + "): " + Arrays.toString(this.determineCurrentOnOffValues(this.index)));

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

        private int[] determineCurrentOnOffValues(int servoIndex)
        {
            return this.provider.getPwmOnOffValues(this.output.getPin());
        }

        private void logCurrentPWMStates()
        {
            if (LOG.isDebugEnabled())
            {
                int[] onOffValues = this.provider.getPwmOnOffValues(this.output.getPin());

                String pinName = this.output.getPin()
                                            .getName();
                String name = this.output.getName();
                LOG.debug(pinName + " (" + name + "): ON value [" + onOffValues[0] + "], OFF value [" + onOffValues[1] + "] ");
            }
        }

        private Pin determineServoPin(int servoIndex)
        {
            return PCA9685Pin.ALL[servoIndex];
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
                this.provider.setAlwaysOn(this.pin);
            }
            else
            {
                this.provider.setAlwaysOff(this.pin);
            }
        }

        @Override
        public void setPwm(double value)
        {
            int periodDurationMicros = this.provider.getPeriodDurationMicros();
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
                this.provider.setPwm(this.pin, duration);
            }
        }

    }

    @Builder
    private static class ServoProvider implements Supplier<ServoAndPwm>
    {
        private final Pin                        pin;
        private final GpioController             gpioController;
        private final PCA9685GpioProvider        gpioProvider;
        private final CachedElement<ServoAndPwm> servo = CachedElement.of(this::createServoInstance);

        private ServoAndPwm createServoInstance()
        {
            GpioPinPwmOutput output = this.gpioController.provisionPwmOutputPin(this.gpioProvider, this.pin, this.pin.getName());

            try
            {
                this.gpioProvider.setAlwaysOff(this.pin);
            }
            catch (Exception e)
            {
                LOG.error("Failed to reset the pin: " + this.pin.getName(), e);
            }

            return new ServoAndPwmImpl(output, this.pin, this.gpioProvider, this.pin.getAddress());
        }

        @Override
        public ServoAndPwm get()
        {
            return this.servo.get();
        }

    }

}
