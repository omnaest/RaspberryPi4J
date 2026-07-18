package org.omnaest.pi.service.sensor.pressure.internal;

import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE;

import java.util.concurrent.TimeUnit;

import org.omnaest.pi.client.domain.pressure.LPS28Definition;
import org.omnaest.pi.service.i2c.I2CService;
import org.omnaest.pi.service.i2c.I2CService.AddressConnector;
import org.omnaest.pi.service.i2c.I2CService.Register;
import org.omnaest.pi.service.i2c.I2CService.RegisterBit;
import org.omnaest.pi.service.i2c.I2CService.RegisterBits;
import org.omnaest.pi.service.sensor.pressure.LPS28PressureService;
import org.omnaest.pi.service.utils.ThreadUtils;
import org.omnaest.utils.bitset.Bits;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import lombok.Data;

@Service
@Scope(SCOPE_PROTOTYPE)
public class LPS28PressureServiceImpl implements LPS28PressureService
{
    @Autowired
    private I2CService    i2cService;

    private int           bus           = 1;
    private int           address       = 0x5C;
    private PressureScale pressureScale = PressureScale._4060HPA;

    @Override
    public double readPressure()
    {
        LPS28Accessor accessor = this.createLPS28Accessor();

        this.initialize(accessor);

        this.waitForRegisterBitToBecomeTrueOrThrowIllegalStateException(1000, TimeUnit.MILLISECONDS, "Timeout waiting for pressure data",
                                                                        accessor.getPressureReadyRegisterBit());

        double rawValue = accessor.getPressureRegisterBits()
                                  .readAsLittleEndianUnsignedInteger();

        return rawValue / (this.isHighPressureScale() ? 2048.0 : 4096.0);
    }

    @Override
    public double readTemperature()
    {
        LPS28Accessor accessor = this.createLPS28Accessor();

        this.initialize(accessor);

        this.waitForRegisterBitToBecomeTrueOrThrowIllegalStateException(1000, TimeUnit.MILLISECONDS, "Timeout waiting for pressure data",
                                                                        accessor.getTemperatureReadyRegisterBit());

        double rawValue = accessor.getTemperatureRegisterBits()
                                  .readAsLittleEndianUnsignedInteger();

        return rawValue / 100.0;
    }

    private void initialize(LPS28Accessor accessor)
    {
        accessor.getSwResetRegisterBit()
                .writeValue(true);
        ThreadUtils.sleep(10, TimeUnit.MILLISECONDS);

        boolean isHighPressureScale = this.isHighPressureScale();
        Bits ctrlRegisterValue = Bits.of(0x30)
                                     .setLength(8)
                                     .setIndex(6, isHighPressureScale); // true = 4060hPa , false = 1260hPa
        accessor.getControl2RegisterBits()
                .write(ctrlRegisterValue); // Write control register

        accessor.getFullScaleModeRegisterBit()
                .writeValue(isHighPressureScale);
        accessor.getPolarityRegisterBit()
                .writeValue(true);
        accessor.getOpenDrainRegisterBit()
                .writeValue(false);

        accessor.getDataRateRegisterBits()
                .write(0b1000);

        accessor.getDataReadyPulseRegisterBit()
                .writeValue(true);
    }

    private boolean isHighPressureScale()
    {
        return PressureScale._4060HPA.equals(this.pressureScale);
    }

    private LPS28Accessor createLPS28Accessor()
    {
        AddressConnector connector = this.i2cService.provision(this.bus)
                                                    .orElseThrow(() -> new IllegalArgumentException("Unable to provision bus " + this.bus))
                                                    .connectTo(this.address)
                                                    .orElseThrow(() -> new IllegalArgumentException("Unable to access I2C address " + Bits.of(this.address)
                                                                                                                                          .toHexDigits()
                                                                                                                                          .toUpperCaseString()));

        return new LPS28Accessor(connector);
    }

    @Data
    private static class LPS28Accessor
    {

        private final RegisterBits temperatureRegisterBits;
        private final RegisterBits pressureRegisterBits;
        private final RegisterBit  pressureReadyRegisterBit;
        private final RegisterBit  temperatureReadyRegisterBit;
        private final RegisterBit  swResetRegisterBit;
        private final RegisterBits control2RegisterBits;
        private final RegisterBit  dataReadyPulseRegisterBit;
        private final RegisterBits dataRateRegisterBits;
        private final RegisterBit  fullScaleModeRegisterBit;
        private final RegisterBit  polarityRegisterBit;
        private final RegisterBit  openDrainRegisterBit;

        public LPS28Accessor(AddressConnector connector)
        {
            super();

            this.temperatureRegisterBits = connector.accessRegister(0x2B)
                                                    .accessBits(16);
            this.pressureRegisterBits = connector.accessRegister(0x28)
                                                 .accessBits(24);

            Register control1Register = connector.accessRegister(0x10);
            this.dataRateRegisterBits = control1Register.accessBits(3, 4);
            Register control2Register = connector.accessRegister(0x11);
            this.swResetRegisterBit = control2Register.accessBit(2);
            this.fullScaleModeRegisterBit = control2Register.accessBit(6);
            this.control2RegisterBits = control2Register.accessBits(8);

            Register control3Register = connector.accessRegister(0x12);
            this.polarityRegisterBit = control3Register.accessBit(3);
            this.openDrainRegisterBit = control3Register.accessBit(1);
            Register control4Register = connector.accessRegister(0x13);
            this.dataReadyPulseRegisterBit = control4Register.accessBit(6);

            Register statusRegister = connector.accessRegister(0x27);
            this.pressureReadyRegisterBit = statusRegister.accessBit(0);
            this.temperatureReadyRegisterBit = statusRegister.accessBit(1);
        }

    }

    @Override
    public LPS28Definition usingBus(int bus)
    {
        this.bus = bus;
        return this;
    }

    @Override
    public LPS28Definition usingPrimaryAddress()
    {
        return this.usingAddress(0x5C);
    }

    @Override
    public LPS28Definition usingSecondaryAddress()
    {
        return this.usingAddress(0x5D);
    }

    @Override
    public LPS28Definition usingAddress(int address)
    {
        this.address = address;
        return this;
    }

    @Override
    public LPS28Definition withPressureScale(PressureScale pressureScale)
    {
        this.pressureScale = pressureScale;
        return this;
    }

    private void waitForRegisterBitToBecomeTrueOrThrowIllegalStateException(int duration, TimeUnit timeUnit, String message, RegisterBit registerBit)
    {
        ThreadUtils.sleepWhile(duration, timeUnit, () -> !registerBit.readValue(), () ->
        {
            throw new IllegalStateException(message);
        });
    }

}
