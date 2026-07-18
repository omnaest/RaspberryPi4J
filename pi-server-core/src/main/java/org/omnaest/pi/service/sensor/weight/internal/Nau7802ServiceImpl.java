package org.omnaest.pi.service.sensor.weight.internal;

import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE;

import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.omnaest.pi.client.domain.weight.Nau7802Definition;
import org.omnaest.pi.service.i2c.I2CService;
import org.omnaest.pi.service.i2c.I2CService.AddressConnector;
import org.omnaest.pi.service.i2c.I2CService.I2CBusControl;
import org.omnaest.pi.service.i2c.I2CService.RegisterBit;
import org.omnaest.pi.service.i2c.I2CService.RegisterBits;
import org.omnaest.pi.service.sensor.weight.Nau7802Service;
import org.omnaest.pi.service.utils.ThreadUtils;
import org.omnaest.utils.bitset.Bits;
import org.omnaest.utils.math.MathUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Scope(SCOPE_PROTOTYPE)
@Slf4j
public class Nau7802ServiceImpl implements Nau7802Service
{
    @Autowired
    private I2CService i2cService;

    private int        bus;

    @Override
    public Nau7802Definition usingBus(int bus)
    {
        this.bus = bus;
        return this;
    }

    @Override
    public int readValue()
    {
        Nau7802I2CAccessor accessor = this.createNau7802I2CAccessor(this.bus);

        this.reset(accessor);

        this.enable(accessor);

        this.setVoltage(accessor);

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

        this.enable(accessor);

        this.selectChannel(accessor);

        double[] dataPoints = IntStream.range(0, 10)
                                       .map(i ->
                                       {
                                           int result = this.readAdcRegisterValue(accessor);
                                           log.debug("NAU7802 single value: " + result);
                                           return result;
                                       })
                                       .mapToDouble(value -> value)
                                       .toArray();
        int result = (int) Math.round(MathUtils.analyze()
                                               .data(dataPoints)
                                               .splitByLowerAndUpperPercentile(80)
                                               .included()
                                               .calculateAverage());

        log.debug("NAU7802 average value: " + result);

        this.disable(accessor);

        return result;
    }

    @Override
    public void calibrate()
    {
        this.calibrateNau7802(this.bus);
    }

    private void selectChannel(Nau7802I2CAccessor accessor)
    {
        this.readAdcRegisterValue(accessor);
        accessor.getChannelSelectBit()
                .writeValue(0);
        this.waitForRegisterBitToBecomeTrueOrThrowIllegalStateException(1000, TimeUnit.MILLISECONDS, "Unable to select channel", accessor.getPUCycleReadyBit());
    }

    private void calibrateNau7802(int bus)
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

        Nau7802I2CAccessor accessor = new Nau7802I2CAccessor() {

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

    private void setVoltage(Nau7802I2CAccessor accessor)
    {
        RegisterBits ctrl1Voltage = accessor.getVoltageBits();
        ctrl1Voltage.write(Bits.of(0x5)
                               .setLength(3));
    }

    private int readAdcRegisterValue(Nau7802I2CAccessor accessor)
    {
        this.waitForRegisterBitToBecomeTrueOrThrowIllegalStateException(1000, TimeUnit.MILLISECONDS, "Unable to read NAU7802 ADC register",
                                                                        accessor.getPUCycleReadyBit());

        RegisterBits registerBits = accessor.getADCRegisterBits();

        log.debug("Register adc bits: " + registerBits.read()
                                                      .toBinaryString());

        return registerBits.readAsBigEndianSignedInteger();
    }

    private void enable(Nau7802I2CAccessor accessor)
    {
        RegisterBit puDigitalBit = accessor.getPUDigitalBit();
        RegisterBit puAnalogBit = accessor.getPUAnalogBit();
        RegisterBit puReadyBit = accessor.getPUReadyBit();
        RegisterBit puStartBit = accessor.getPUStartBit();

        puDigitalBit.writeValue(1);
        puAnalogBit.writeValue(1);
        ThreadUtils.sleep(500, TimeUnit.MILLISECONDS);
        puStartBit.writeValue(1);

        this.waitForRegisterBitToBecomeTrueOrThrowIllegalStateException(1000, TimeUnit.MILLISECONDS, "Unable to start the NAU7802 chip", puReadyBit);
    }

    private void disable(Nau7802I2CAccessor accessor)
    {
        RegisterBit puDigitalBit = accessor.getPUDigitalBit();
        RegisterBit puAnalogBit = accessor.getPUAnalogBit();

        puDigitalBit.writeValue(0);
        puAnalogBit.writeValue(0);
        ThreadUtils.sleep(10, TimeUnit.MILLISECONDS);
    }

    private void reset(Nau7802I2CAccessor accessor)
    {
        RegisterBit resetBit = accessor.getResetBit();
        RegisterBit puDigitalBit = accessor.getPUDigitalBit();
        RegisterBit puReadyBit = accessor.getPUReadyBit();

        resetBit.writeValue(1);
        ThreadUtils.sleep(100, TimeUnit.MILLISECONDS);
        resetBit.writeValue(0);

        puDigitalBit.writeValue(1);
        ThreadUtils.sleep(400, TimeUnit.MILLISECONDS);

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
        log.debug("NAU7802 internal calibration error: " + calibrationError.readValue());

        calibrationMode.write(Bits.of(0x2)); // OFFSET
        calibrationStart.writeValue(1);
        ThreadUtils.sleep(10, TimeUnit.MILLISECONDS);
        calibrationStart.writeValue(0);
        log.debug("NAU7802 offset calibration error: " + calibrationError.readValue());
    }

    private void waitForRegisterBitToBecomeTrueOrThrowIllegalStateException(int duration, TimeUnit timeUnit, String message, RegisterBit registerBit)
    {
        ThreadUtils.sleepWhile(duration, timeUnit, () -> !registerBit.readValue(), () ->
        {
            throw new IllegalStateException(message);
        });
    }
}
