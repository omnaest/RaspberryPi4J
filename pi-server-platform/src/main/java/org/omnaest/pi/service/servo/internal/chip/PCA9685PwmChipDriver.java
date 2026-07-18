package org.omnaest.pi.service.servo.internal.chip;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;

import javax.annotation.PreDestroy;

import org.omnaest.pi.service.servo.chip.PwmChipDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.pi4j.gpio.extension.pca.PCA9685GpioProvider;
import com.pi4j.gpio.extension.pca.PCA9685Pin;
import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CFactory;
import com.pi4j.io.i2c.I2CFactory.UnsupportedBusNumberException;

/**
 * Real, pi4j-backed {@link PwmChipDriver} talking to a PCA9685 PWM chip over I2C. Used under the default (real
 * hardware) profile - i.e. whenever the {@code simulation} Spring profile is NOT active.
 * <p>
 * The I2C bus discovery and {@link PCA9685GpioProvider} construction below are moved verbatim (same graceful
 * error-swallowing lazy-init behaviour) from the former {@code ServoDriverServiceImpl.ensureInitialization()}, so
 * that class no longer needs any pi4j dependency.
 *
 * @author Danny Kunz
 */
@Component
@Profile("!simulation")
public class PCA9685PwmChipDriver implements PwmChipDriver
{
    private static final Logger          LOG = LoggerFactory.getLogger(PCA9685PwmChipDriver.class);

    private volatile PCA9685GpioProvider provider;

    private void ensureInitialization()
    {
        if (this.provider == null)
        {
            synchronized (this)
            {
                if (this.provider == null)
                {
                    try
                    {
                        I2CBus bus = this.newI2CBus();
                        BigDecimal frequency = new BigDecimal("48.828");
                        BigDecimal frequencyCorrectionFactor = new BigDecimal("1.0578");
                        this.provider = new PCA9685GpioProvider(bus, 0x40, frequency, frequencyCorrectionFactor);
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
    public int getChannelCount()
    {
        return PCA9685Pin.ALL.length;
    }

    @Override
    public int getPeriodDurationMicros()
    {
        this.ensureInitialization();
        return this.provider.getPeriodDurationMicros();
    }

    @Override
    public void setPwm(int channel, int durationMicros)
    {
        this.ensureInitialization();
        this.provider.setPwm(PCA9685Pin.ALL[channel], durationMicros);
    }

    @Override
    public void setAlwaysOn(int channel)
    {
        this.ensureInitialization();
        this.provider.setAlwaysOn(PCA9685Pin.ALL[channel]);
    }

    @Override
    public void setAlwaysOff(int channel)
    {
        this.ensureInitialization();
        this.provider.setAlwaysOff(PCA9685Pin.ALL[channel]);
    }

    @Override
    public int[] getPwmOnOffValues(int channel)
    {
        this.ensureInitialization();
        return this.provider.getPwmOnOffValues(PCA9685Pin.ALL[channel]);
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
}
