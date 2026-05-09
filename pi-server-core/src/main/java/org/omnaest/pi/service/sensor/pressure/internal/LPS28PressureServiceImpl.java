package org.omnaest.pi.service.sensor.pressure.internal;

import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE;

import org.omnaest.pi.client.domain.pressure.LPS28Definition;
import org.omnaest.pi.service.i2c.I2CService;
import org.omnaest.pi.service.i2c.I2CService.AddressConnector;
import org.omnaest.pi.service.sensor.pressure.LPS28PressureService;
import org.omnaest.utils.bitset.Bits;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

@Service
@Scope(SCOPE_PROTOTYPE)
public class LPS28PressureServiceImpl implements LPS28PressureService
{
    @Autowired
    private I2CService i2cService;

    private int           bus           = 1;
    private int           address       = 0x5C;
    private PressureScale pressureScale = PressureScale._4060HPA;

    @Override
    public double readPressure()
    {
        AddressConnector connector = this.i2cService.provision(this.bus)
                                                    .orElseThrow(() -> new IllegalArgumentException("Unable to provision bus " + this.bus))
                                                    .connectTo(this.address)
                                                    .orElseThrow(() -> new IllegalArgumentException("Unable to access I2C address " + Bits.of(this.address)
                                                                                                                                          .toHexDigits()
                                                                                                                                          .toUpperCaseString()));

        boolean isHighPressureScale = PressureScale._4060HPA.equals(this.pressureScale);
        Bits ctrlRegisterValue = Bits.of(0x30)
                                     .setLength(8)
                                     .setIndex(6, isHighPressureScale); // true = 4060hPa , false = 1260hPa
        connector.accessRegister(0x11)
                 .accessBits(8)
                 .write(ctrlRegisterValue); // Write control register

        double rawValue = connector.accessRegister(0x28)
                                   .accessBits(24)
                                   .readAsLittleEndianUnsignedInteger();

        return rawValue / (isHighPressureScale ? 2048.0 : 4096.0);
    }

    @Override
    public LPS28Definition usingBus(int bus)
    {
        this.bus = bus;
        return this;
    }

    @Override
    public LPS28Definition withPressureScale(PressureScale pressureScale)
    {
        this.pressureScale = pressureScale;
        return this;
    }

}
