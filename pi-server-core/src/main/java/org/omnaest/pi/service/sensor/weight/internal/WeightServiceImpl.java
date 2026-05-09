package org.omnaest.pi.service.sensor.weight.internal;

import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.apache.commons.lang3.BooleanUtils;
import org.omnaest.pi.client.domain.weight.Nau7802Definition;
import org.omnaest.pi.service.gpio.GPIOService;
import org.omnaest.pi.service.gpio.GPIOService.DigitalInputGPIOPort;
import org.omnaest.pi.service.gpio.GPIOService.DigitalOutputGPIOPort;
import org.omnaest.pi.service.i2c.I2CService;
import org.omnaest.pi.service.i2c.I2CService.AddressConnector;
import org.omnaest.pi.service.i2c.I2CService.I2CBusControl;
import org.omnaest.pi.service.i2c.I2CService.RegisterBit;
import org.omnaest.pi.service.i2c.I2CService.RegisterBits;
import org.omnaest.pi.service.sensor.weight.Nau7802Service;
import org.omnaest.pi.service.sensor.weight.WeightService;
import org.omnaest.pi.service.utils.ThreadUtils;
import org.omnaest.utils.bitset.Bits;
import org.omnaest.utils.duration.DurationCapture;
import org.omnaest.utils.duration.DurationCapture.DurationMeasurement;
import org.omnaest.utils.duration.DurationCapture.MeasurementResult;
import org.omnaest.utils.math.MathUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@Configuration
public class WeightServiceImpl implements WeightService
{
    @Autowired
    private GPIOService gpioService;

    @Autowired
    private I2CService i2cService;

    @Bean
    public Nau7802Service createNau7802Service()
    {
        return new Nau7802ServiceImpl();
    }

    @Override
    public int readValueFromNau7802(int bus)
    {
        Nau7802I2CAccessor accessor = this.createNau7802I2CAccessor(bus);

        this.resetNau7802(accessor);

        this.enableNau7802(accessor);

        this.setNau7802Voltage(accessor);

        accessor.getPULDOSourceBit()
                .writeValue(1);

        accessor.getGainsBits()
                .write(Bits.of(0x7)
                           .setLength(3)); // 128

        accessor.getConversionRateBits()
                .write(Bits.of(0x0)
                           .setLength(3));

        accessor.getADCChopClockBits()
                .write(Bits.of(0x3) // disable
                           .setLength(2));

        accessor.getPGALDOModeBit()
                .writeValue(0); // low ESR capacitors

        accessor.getPCCapEnableBit()
                .writeValue(1); // pga stabilization

        this.enableNau7802(accessor);

        this.selectNau7802Channel(accessor);

        double[] dataPoints = IntStream.range(0, 10)
                                       .map(i ->
                                       {
                                           int result = this.readAdcValueNau7802(accessor);
                                           log.debug("NAU7802 single value: " + result);
                                           return result;
                                       })
                                       .mapToDouble(value -> value)
                                       .toArray();
        int adcValueNau7802 = (int) Math.round(MathUtils.analyze()
                                                        .data(dataPoints)
                                                        .splitByLowerAndUpperPercentile(80)
                                                        .included()
                                                        .calculateAverage());

        log.debug("NAU7802 average value: " + adcValueNau7802);

        this.disableNau7802(accessor);

        return adcValueNau7802;

    }

    private void selectNau7802Channel(Nau7802I2CAccessor accessor)
    {
        this.readAdcValueNau7802(accessor);
        accessor.getChannelSelectBit()
                .writeValue(0);
        this.waitForRegisterBitToBecomeTrueOrThrowIllegalStateException(1000, TimeUnit.MILLISECONDS, "Unable to select channel", accessor.getPUCycleReadyBit());
    }

    public void calibrateNau7802(int bus)
    {
        this.calibrateNau7802(this.createNau7802I2CAccessor(bus));
    }

    private Nau7802I2CAccessor createNau7802I2CAccessor(int bus)
    {
        I2CBusControl i2cBus = this.i2cService.provision(bus)
                                              .orElseThrow(() -> new IllegalStateException("Unable to provision I2C bus " + bus));

        AddressConnector addressConnector = i2cBus.connectTo(0x2A)
                                                  .orElseThrow(() -> new IllegalStateException("Unable to provision I2C device address 0x2A"));

        RegisterBit resetBit = addressConnector.accessRegister(0x00)
                                               .accessBit(0);
        RegisterBit puDigitalBit = addressConnector.accessRegister(0x00)
                                                   .accessBit(1);
        RegisterBit puAnalogBit = addressConnector.accessRegister(0x00)
                                                  .accessBit(2);
        RegisterBit puReadyBit = addressConnector.accessRegister(0x00)
                                                 .accessBit(3);
        RegisterBit puStartBit = addressConnector.accessRegister(0x00)
                                                 .accessBit(4);
        RegisterBit puCycleReadyBit = addressConnector.accessRegister(0x00)
                                                      .accessBit(5);
        RegisterBit puLdoSource = addressConnector.accessRegister(0x00)
                                                  .accessBit(7);

        RegisterBits ctrl1Voltage = addressConnector.accessRegister(0x01)
                                                    .accessBits(3, 3);
        RegisterBits ctrl1Gains = addressConnector.accessRegister(0x01)
                                                  .accessBits(0, 3);

        RegisterBits calibrationMode = addressConnector.accessRegister(0x02)
                                                       .accessBits(2);
        RegisterBit calibrationStart = addressConnector.accessRegister(0x02)
                                                       .accessBit(2);
        RegisterBit calibrationError = addressConnector.accessRegister(0x02)
                                                       .accessBit(3);
        RegisterBits conversionRate = addressConnector.accessRegister(0x02)
                                                      .accessBits(4, 3);
        RegisterBit channelSelect = addressConnector.accessRegister(0x02)
                                                    .accessBit(7);

        RegisterBits adcRegisterBits = addressConnector.accessRegister(0x12)
                                                       .accessBits(24);

        RegisterBits adcChopClockBits = addressConnector.accessRegister(0x15)
                                                        .accessBits(4, 2);

        RegisterBit pgaLdoMode = addressConnector.accessRegister(0x1B)
                                                 .accessBit(6);

        RegisterBit pcCapEnable = addressConnector.accessRegister(0x1C)
                                                  .accessBit(7);

        Nau7802I2CAccessor accessor = new Nau7802I2CAccessor()
        {

            @Override
            public RegisterBits getCalibrationModeBits()
            {
                return calibrationMode;
            }

            @Override
            public RegisterBits getVoltageBits()
            {
                return ctrl1Voltage;
            }

            @Override
            public RegisterBits getGainsBits()
            {
                return ctrl1Gains;
            }

            @Override
            public RegisterBits getConversionRateBits()
            {
                return conversionRate;
            }

            @Override
            public RegisterBit getCalibrationStartBit()
            {
                return calibrationStart;
            }

            @Override
            public RegisterBit getCalibrationErrorBit()
            {
                return calibrationError;
            }

            @Override
            public RegisterBit getResetBit()
            {
                return resetBit;
            }

            @Override
            public RegisterBit getPUDigitalBit()
            {
                return puDigitalBit;
            }

            @Override
            public RegisterBit getPUAnalogBit()
            {
                return puAnalogBit;
            }

            @Override
            public RegisterBit getPUReadyBit()
            {
                return puReadyBit;
            }

            @Override
            public RegisterBit getPUStartBit()
            {
                return puStartBit;
            }

            @Override
            public RegisterBit getPULDOSourceBit()
            {
                return puLdoSource;
            }

            @Override
            public RegisterBit getPUCycleReadyBit()
            {
                return puCycleReadyBit;
            }

            @Override
            public RegisterBit getPGALDOModeBit()
            {
                return pgaLdoMode;
            }

            @Override
            public RegisterBit getChannelSelectBit()
            {
                return channelSelect;
            }

            @Override
            public RegisterBits getADCRegisterBits()
            {
                return adcRegisterBits;
            }

            @Override
            public RegisterBits getADCChopClockBits()
            {
                return adcChopClockBits;
            }

            @Override
            public RegisterBit getPCCapEnableBit()
            {
                return pcCapEnable;
            }
        };
        return accessor;
    }

    private static interface Nau7802I2CAccessor
    {
        public RegisterBits getCalibrationModeBits();

        public RegisterBit getChannelSelectBit();

        public RegisterBit getPCCapEnableBit();

        public RegisterBit getPGALDOModeBit();

        public RegisterBits getADCChopClockBits();

        public RegisterBits getConversionRateBits();

        public RegisterBits getGainsBits();

        public RegisterBit getPULDOSourceBit();

        public RegisterBits getVoltageBits();

        public RegisterBits getADCRegisterBits();

        public RegisterBit getPUCycleReadyBit();

        public RegisterBit getPUAnalogBit();

        public RegisterBit getPUStartBit();

        public RegisterBit getPUReadyBit();

        public RegisterBit getResetBit();

        public RegisterBit getPUDigitalBit();

        public RegisterBit getCalibrationStartBit();

        public RegisterBit getCalibrationErrorBit();
    }

    private void setNau7802Voltage(Nau7802I2CAccessor accessor)
    {
        RegisterBits ctrl1Voltage = accessor.getVoltageBits();
        ctrl1Voltage.write(Bits.of(0x5)
                               .setLength(3));
    }

    private int readAdcValueNau7802(Nau7802I2CAccessor accessor)
    {
        this.waitForRegisterBitToBecomeTrueOrThrowIllegalStateException(1000, TimeUnit.MILLISECONDS, "Unable to read NAU7802 ADC register",
                                                                        accessor.getPUCycleReadyBit());

        log.info("Cycle bit before read: " + accessor.getPUCycleReadyBit()
                                                     .readValue());

        RegisterBits registerBits = accessor.getADCRegisterBits();

        log.info("Register adc bits: " + registerBits.read()
                                                     .toBinaryString());

        log.info("Cycle bit after read: " + accessor.getPUCycleReadyBit()
                                                    .readValue());

        return registerBits.readAsSignedInteger();
    }

    private void enableNau7802(Nau7802I2CAccessor accessor)
    {
        RegisterBit puDigitalBit = accessor.getPUDigitalBit();
        RegisterBit puAnalogBit = accessor.getPUAnalogBit();
        RegisterBit puReadyBit = accessor.getPUReadyBit();
        RegisterBit puStartBit = accessor.getPUStartBit();

        puDigitalBit.writeValue(1);
        puAnalogBit.writeValue(1);
        ThreadUtils.sleep(750, TimeUnit.MILLISECONDS);
        puStartBit.writeValue(1);

        this.waitForRegisterBitToBecomeTrueOrThrowIllegalStateException(1000, TimeUnit.MILLISECONDS, "Unable to start the NAU7802 chip", puReadyBit);
    }

    private void disableNau7802(Nau7802I2CAccessor accessor)
    {
        RegisterBit puDigitalBit = accessor.getPUDigitalBit();
        RegisterBit puAnalogBit = accessor.getPUAnalogBit();

        puDigitalBit.writeValue(0);
        puAnalogBit.writeValue(0);
        ThreadUtils.sleep(10, TimeUnit.MILLISECONDS);
    }

    private void resetNau7802(Nau7802I2CAccessor accessor)
    {
        RegisterBit resetBit = accessor.getResetBit();
        RegisterBit puDigitalBit = accessor.getPUDigitalBit();
        RegisterBit puReadyBit = accessor.getPUReadyBit();

        resetBit.writeValue(1);
        ThreadUtils.sleep(100, TimeUnit.MILLISECONDS);
        resetBit.writeValue(0);

        puDigitalBit.writeValue(1);
        ThreadUtils.sleep(750, TimeUnit.MILLISECONDS);

        this.waitForRegisterBitToBecomeTrueOrThrowIllegalStateException(1000, TimeUnit.MILLISECONDS, "Unable to reset the NAU7802 chip", puReadyBit);
    }

    private void calibrateNau7802(Nau7802I2CAccessor accessor)
    {
        RegisterBits calibrationMode = accessor.getCalibrationModeBits();
        RegisterBit calibrationStart = accessor.getCalibrationStartBit();
        RegisterBit calibrationError = accessor.getCalibrationErrorBit();

        calibrationMode.write(Bits.of(0x0)); // INTERNAL
        calibrationStart.writeValue(1);
        ThreadUtils.sleep(10, TimeUnit.MILLISECONDS);
        calibrationStart.writeValue(0);
        log.info("NAU7802 internal calibration error: " + calibrationError.readValue());

        calibrationMode.write(Bits.of(0x2)); // OFFSET
        calibrationStart.writeValue(1);
        ThreadUtils.sleep(10, TimeUnit.MILLISECONDS);
        calibrationStart.writeValue(0);
        log.info("NAU7802 offset calibration error: " + calibrationError.readValue());
    }

    private void waitForRegisterBitToBecomeTrueOrThrowIllegalStateException(int duration, TimeUnit timeUnit, String message, RegisterBit registerBit)
    {
        ThreadUtils.sleepWhile(duration, timeUnit, () -> !registerBit.readValue(), () ->
        {
            throw new IllegalStateException(message);
        });
    }

    @Override
    public long readValueFromHX711(HX711PortConfiguration portConfiguration)
    {
        DigitalInputGPIOPort dataPort = this.gpioService.getDigitalInputGPIOPort(portConfiguration.getDataPort())
                                                        .withNoPullResistance()
                                                        .enable();
        DigitalOutputGPIOPort clockPort = this.gpioService.getDigitalOutputGPIOPort(portConfiguration.getClockPort())
                                                          .enable()
                                                          .setState(false);

        try
        {
            Gain gain = portConfiguration.getGain();

            clockPort.setState(false);
            this.waitUntilDataPortIsReady(dataPort);
            this.readRawData(gain, dataPort, clockPort); // dummy read to set the gain correctly

            this.sleep(1, TimeUnit.MILLISECONDS);
            clockPort.setState(false);
            this.waitUntilDataPortIsReady(dataPort);
            long value = this.readRawData(gain, dataPort, clockPort);

            log.info("Weight value read from sensor on (" + dataPort + ") : " + value);

            this.sleep(1, TimeUnit.MILLISECONDS);
            return value;
        }
        finally
        {
            dataPort.disable();
            clockPort.disable();
        }
    }

    private void sleep(int duration, TimeUnit timeUnit)
    {
        long nanos = timeUnit.toNanos(duration);
        if (nanos <= TimeUnit.MICROSECONDS.toNanos(10))
        {
            long start = System.nanoTime();
            while (System.nanoTime() - start < nanos)
            {
                // do nothing
            }
        }
        else
        {
            ThreadUtils.sleep(duration, timeUnit);
        }
    }

    private long readRawData(Gain gain, DigitalInputGPIOPort dataPort, DigitalOutputGPIOPort clockPort)
    {
        Bits bits = Bits.newInstance();

        int numberOfDataBits = 24 + gain.getAdditionalBits();
        for (int i = 0; i < numberOfDataBits; i++)
        {
            clockPort.setState(true);
            this.sleep(200, TimeUnit.NANOSECONDS);
            clockPort.setState(false);
            this.sleep(200, TimeUnit.NANOSECONDS);

            bits.setIndex(i, dataPort.getState());
        }

        clockPort.setState(true);
        this.sleep(200, TimeUnit.NANOSECONDS);
        clockPort.setState(false);

        log.info("Received HX711 sensor data on (" + dataPort + ") : " + bits.toBinaryDigits()
                                                                             .toUpperCaseString());

        long result = 0;
        for (boolean value : bits.toBooleanArray())
        {
            result = result << 1;
            result += BooleanUtils.toInteger(value);
        }

        return result ^ 0x800000;
    }

    private void waitUntilDataPortIsReady(DigitalInputGPIOPort dataPort)
    {
        DurationMeasurement durationMeasurement = DurationCapture.newInstance()
                                                                 .start();
        this.sleep(1, TimeUnit.MILLISECONDS);
        while (dataPort.getState())
        {
            this.sleep(1, TimeUnit.MILLISECONDS);

            MeasurementResult durationMeasurementResult = durationMeasurement.stop();
            if (durationMeasurementResult.getDuration(TimeUnit.SECONDS) >= 5)
            {
                throw new IllegalStateException("Waiting for data port to be ready timed out after " + durationMeasurementResult.getDurationAsCanonicalString()
                        + ". Port = " + dataPort);
            }
        }
    }

    public class Nau7802ServiceImpl implements Nau7802Service
    {
        private int bus;

        @Override
        public Nau7802Definition usingBus(int bus)
        {
            this.bus = bus;
            return this;
        }

        @Override
        public int readValue()
        {
            return WeightServiceImpl.this.readValueFromNau7802(this.bus);
        }

        @Override
        public void calibrate()
        {
            WeightServiceImpl.this.calibrateNau7802(this.bus);
        }
    }

}
