package org.omnaest.pi.service.servo.internal;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.annotation.PreDestroy;

import org.omnaest.pi.service.servo.ServoDriverService;
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

@Service
public class ServoDriverServiceImpl implements ServoDriverService
{
    private static final Logger LOG = LoggerFactory.getLogger(ServoDriverServiceImpl.class);

    private List<Servo>         servos;
    private PCA9685GpioProvider provider;

    private void ensureInitialization()
    {
        if (this.provider == null)
        {
            try
            {
                I2CBus bus = this.newI2CBus();
                BigDecimal frequency = new BigDecimal("48.828");
                BigDecimal frequencyCorrectionFactor = new BigDecimal("1.0578");
                PCA9685GpioProvider provider = this.provider = new PCA9685GpioProvider(bus, 0x40, frequency, frequencyCorrectionFactor);
                GpioPinPwmOutput[] outputs = this.provisionPwmOutputs(provider);
                this.resetAll(provider);

                this.servos = IntStream.range(0, outputs.length)
                                       .mapToObj(index -> new ServoImpl(outputs, provider, index))
                                       .collect(Collectors.toList());
            }
            catch (Exception e)
            {
                LOG.warn("Unable to initialize I2C");
                LOG.debug("I2C error", e);
            }
        }
    }

    @PreDestroy
    public void destroy()
    {
        this.provider.shutdown();
    }

    @Override
    public Servo servo(int index)
    {
        this.ensureInitialization();
        return this.servos.get(index);
    }

    private void resetAll(PCA9685GpioProvider provider)
    {
        try
        {
            provider.reset();
        }
        catch (Exception e)
        {
            LOG.error("Failed to reset the provider", e);
        }
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

    private GpioPinPwmOutput[] provisionPwmOutputs(final PCA9685GpioProvider gpioProvider)
    {
        GpioController gpio = GpioFactory.getInstance();
        GpioPinPwmOutput myOutputs[] = { gpio.provisionPwmOutputPin(gpioProvider, PCA9685Pin.PWM_00, "Pulse 00"),
                                         gpio.provisionPwmOutputPin(gpioProvider, PCA9685Pin.PWM_01, "Pulse 01"),
                                         gpio.provisionPwmOutputPin(gpioProvider, PCA9685Pin.PWM_02, "Pulse 02"),
                                         gpio.provisionPwmOutputPin(gpioProvider, PCA9685Pin.PWM_03, "Pulse 03"),
                                         gpio.provisionPwmOutputPin(gpioProvider, PCA9685Pin.PWM_04, "Pulse 04"),
                                         gpio.provisionPwmOutputPin(gpioProvider, PCA9685Pin.PWM_05, "Pulse 05"),
                                         gpio.provisionPwmOutputPin(gpioProvider, PCA9685Pin.PWM_06, "Pulse 06"),
                                         gpio.provisionPwmOutputPin(gpioProvider, PCA9685Pin.PWM_07, "Pulse 07"),
                                         gpio.provisionPwmOutputPin(gpioProvider, PCA9685Pin.PWM_08, "Pulse 08"),
                                         gpio.provisionPwmOutputPin(gpioProvider, PCA9685Pin.PWM_09, "Pulse 09"),
                                         gpio.provisionPwmOutputPin(gpioProvider, PCA9685Pin.PWM_10, "Pulse 10"),
                                         gpio.provisionPwmOutputPin(gpioProvider, PCA9685Pin.PWM_11, "Pulse 11"),
                                         gpio.provisionPwmOutputPin(gpioProvider, PCA9685Pin.PWM_12, "Pulse 12"),
                                         gpio.provisionPwmOutputPin(gpioProvider, PCA9685Pin.PWM_13, "Pulse 13"),
                                         gpio.provisionPwmOutputPin(gpioProvider, PCA9685Pin.PWM_14, "Pulse 14"),
                                         gpio.provisionPwmOutputPin(gpioProvider, PCA9685Pin.PWM_15, "Pulse 15") };

        return myOutputs;
    }

    private static class ServoImpl implements Servo
    {
        private final GpioPinPwmOutput[]  outputs;
        private final PCA9685GpioProvider provider;
        private final int                 index;
        private int                       durationMinimum = 1;
        private int                       durationMaximum = 3200;
        private Supplier<Integer>         durationNeutral = () -> this.durationMaximum / 2;

        private ServoImpl(GpioPinPwmOutput[] outputs, PCA9685GpioProvider provider, int index)
        {
            this.outputs = outputs;
            this.provider = provider;
            this.index = index;
        }

        @Override
        public void applyAngle(int angle)
        {
            try
            {
                LOG.debug("Set servo " + this.index + " state to angle " + angle);

                this.logCurrentPWMStates(this.provider, this.outputs);

                this.provider.setPwm(this.determineServoPin(this.index), this.determineServoDuration(angle));

                LOG.debug("Current state (servo " + this.index + "): " + Arrays.toString(this.determineCurrentOnOffValues(this.index)));

                this.logCurrentPWMStates(this.provider, this.outputs);
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

                this.logCurrentPWMStates(this.provider, this.outputs);

                this.provider.setPwm(this.determineServoPin(this.index), this.determineServoDurationForSpeed(speed));

                LOG.debug("Current state (servo " + this.index + "): " + Arrays.toString(this.determineCurrentOnOffValues(this.index)));

                this.logCurrentPWMStates(this.provider, this.outputs);
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
            return this.provider.getPwmOnOffValues(this.outputs[servoIndex].getPin());
        }

        private void logCurrentPWMStates(PCA9685GpioProvider provider, GpioPinPwmOutput[] outputs)
        {
            if (LOG.isDebugEnabled())
            {
                for (GpioPinPwmOutput output : outputs)
                {
                    int[] onOffValues = provider.getPwmOnOffValues(output.getPin());

                    String pinName = output.getPin()
                                           .getName();
                    String name = output.getName();
                    LOG.debug(pinName + " (" + name + "): ON value [" + onOffValues[0] + "], OFF value [" + onOffValues[1] + "] ");
                }
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

    }

}
