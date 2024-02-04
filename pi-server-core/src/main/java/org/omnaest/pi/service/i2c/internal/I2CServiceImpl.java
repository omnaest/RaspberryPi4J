package org.omnaest.pi.service.i2c.internal;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.omnaest.pi.service.i2c.I2CService;
import org.omnaest.pi.service.utils.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;

@Service
public class I2CServiceImpl implements I2CService
{
    private static final Logger LOG = LoggerFactory.getLogger(I2CServiceImpl.class);

    private Map<Integer, I2CBusControl> busNumberToBusControl = new ConcurrentHashMap<>();

    @Override
    public Optional<I2CBusControl> provision(BusNumber busNumber)
    {
        return this.provision(busNumber.ordinal());
    }

    @Override
    public Optional<I2CBusControl> provision(int busNumber)
    {
        return Optional.ofNullable(this.busNumberToBusControl.computeIfAbsent(busNumber, bn ->
        {
            try
            {
                I2CBus bus = I2CFactory.getInstance(bn);
                return new I2CBusControl()
                {
                    private Map<Integer, AddressConnector> addressToConnector = new ConcurrentHashMap<>();

                    @Override
                    public Optional<AddressConnector> connectTo(int address)
                    {
                        return Optional.ofNullable(this.addressToConnector.computeIfAbsent(address, a ->
                        {

                            try
                            {
                                I2CDevice device = bus.getDevice(address);
                                return new AddressConnectorImpl(device);
                            }
                            catch (IOException e)
                            {
                                LOG.error("Error connecting to I2C address " + address + " for bus " + busNumber, e);
                                return null;
                            }
                        }));
                    }
                };
            }
            catch (Exception e)
            {
                LOG.error("Error provisioning to I2C bus " + busNumber, e);
                return null;
            }
        }));

    }

    private static class AddressConnectorImpl implements AddressConnector
    {
        private I2CDevice device;

        public AddressConnectorImpl(I2CDevice device)
        {
            this.device = device;
        }

        @Override
        public Optional<ByteArray> read(int address, int start, int size)
        {
            try
            {
                byte[] result = new byte[size];
                this.device.read(address, result, start, size);
                return Optional.of(new ByteArray(result));
            }
            catch (IOException e)
            {
                LOG.error("Failed to read from address: " + address + " start: " + start, e);
                return Optional.empty();
            }
        }

        @Override
        public Optional<ByteArray> read(int start, int size)
        {
            try
            {
                byte[] result = new byte[size];
                this.device.read(result, start, size);
                return Optional.of(new ByteArray(result));
            }
            catch (IOException e)
            {
                LOG.error("Failed to read from start: " + start, e);
                return Optional.empty();
            }
        }

        @Override
        public AddressConnector write(int address, byte... data)
        {
            try
            {
                this.device.write(address, data);
            }
            catch (IOException e)
            {
                throw new IllegalStateException(e);
            }
            return this;
        }

        @Override
        public AddressConnector wait(int duration, TimeUnit timeUnit)
        {
            ThreadUtils.sleep(duration, timeUnit);
            return this;
        }

        @Override
        public AddressConnector waitUntil(Predicate<AddressConnector> predicate)
        {
            while (!predicate.test(this))
            {
                this.wait(10, TimeUnit.MILLISECONDS);
            }
            return this;
        }

        @Override
        public AddressConnector waitUntilBitIsTrue(int address, byte mask)
        {
            return this.waitUntil(connector -> connector.read(address, 0, 1)
                                                        .map(value -> (value.getFirstByte()
                                                                            .orElse((byte) 0)
                                                                & mask) == 1)
                                                        .orElse(false));
        }

    }
}
