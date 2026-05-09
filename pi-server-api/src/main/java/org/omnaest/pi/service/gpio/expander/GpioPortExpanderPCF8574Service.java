package org.omnaest.pi.service.gpio.expander;

import org.omnaest.pi.client.domain.gpio.expander.GpioPortExpanderAddress;
import org.omnaest.pi.client.domain.gpio.expander.GpioPortExpanderPort;

public interface GpioPortExpanderPCF8574Service
{

    public GPIOsAccessor access(GpioPortExpanderAddress address);

    public static interface GPIOsAccessor
    {
        public boolean read(GpioPortExpanderPort gpioPort);

        public boolean[] read();

        public GPIOsAccessor write(GpioPortExpanderPort gpioPort, boolean value);

        public GPIOsAccessor write(boolean... values);
    }
}
