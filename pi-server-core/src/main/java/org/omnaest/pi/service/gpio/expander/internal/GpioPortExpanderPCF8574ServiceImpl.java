package org.omnaest.pi.service.gpio.expander.internal;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.omnaest.pi.client.domain.gpio.expander.GpioPortExpanderAddress;
import org.omnaest.pi.client.domain.gpio.expander.GpioPortExpanderPort;
import org.omnaest.pi.service.gpio.expander.GpioPortExpanderPCF8574Service;
import org.omnaest.pi.service.i2c.I2CService;
import org.omnaest.pi.service.i2c.I2CService.AddressConnector;
import org.omnaest.pi.service.i2c.I2CService.BusNumber;
import org.omnaest.pi.service.i2c.I2CService.ByteArray;
import org.omnaest.utils.bitset.Bits;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.Builder;
import lombok.Data;

@Service
public class GpioPortExpanderPCF8574ServiceImpl implements GpioPortExpanderPCF8574Service
{
    @Autowired
    private I2CService i2cService;

    private final Map<GpioPortExpanderAddress, PCF8574Address> addressToAccessor = new ConcurrentHashMap<>();

    private PCF8574Address initializeAddress(GpioPortExpanderAddress address)
    {
        return new PCF8574Address(this.i2cService.provision(BusNumber.BUS_1)
                                                 .orElseThrow(() -> new IllegalStateException("Unable to provision bus 0"))
                                                 .connectTo(address.getAddress())
                                                 .orElseThrow(() -> new IllegalStateException("Unable to connect to address 0x20"))).write(false, false, false,
                                                                                                                                           false, false, false,
                                                                                                                                           false, false);
    }

    @Data
    @Builder
    private static class PCF8574Address implements GPIOsAccessor
    {
        private final AddressConnector address;
        private final Bits             bits = Bits.of(false, false, false, false, false, false, false, false);

        @Override
        public PCF8574Address write(boolean... values)
        {
            this.bits.set(Bits.of(values));
            this.writeCurrentBits();
            return this;
        }

        @Override
        public PCF8574Address write(GpioPortExpanderPort gpioPort, boolean value)
        {
            int port = gpioPort.getPort();
            this.validateGpioPort(port);
            this.bits.setIndex(port, value);
            this.writeCurrentBits();
            return this;
        }

        private void writeCurrentBits()
        {
            this.address.write(this.bits.toBytes()[0]);
        }

        @Override
        public boolean[] read()
        {
            return this.address.read(0, 1)
                               .map(ByteArray::get)
                               .map(Bits::of)
                               .orElse(this.bits)
                               .toBooleanArray();
        }

        @Override
        public boolean read(GpioPortExpanderPort gpioPort)
        {
            int port = gpioPort.getPort();
            this.validateGpioPort(port);
            return this.read()[port];
        }

        private void validateGpioPort(int gpioPort)
        {
            if (gpioPort < 0 || gpioPort > 7)
            {
                throw new IllegalArgumentException("GPIO port must be between 0 and 7 (both inclusive) but was: " + gpioPort);
            }
        }

    }

    @Override
    public GPIOsAccessor access(GpioPortExpanderAddress address)
    {
        return this.addressToAccessor.computeIfAbsent(address, this::initializeAddress);
    }
}
