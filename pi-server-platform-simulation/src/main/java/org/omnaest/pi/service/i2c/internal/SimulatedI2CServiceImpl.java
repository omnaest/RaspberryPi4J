package org.omnaest.pi.service.i2c.internal;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.omnaest.pi.service.i2c.AbstractAddressConnector;
import org.omnaest.pi.service.i2c.I2CService;
import org.omnaest.pi.service.i2c.I2CSimulationControl;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Simulated, in-memory {@link I2CService} implementation used under the {@code simulation} Spring profile. Holds a
 * sparse register store per device (keyed by bus number + device address) so that tests and an interactive
 * {@code simulation} boot can exercise I2C consumers without any pi4j hardware dependency.
 * <p>
 * Any local address that was never written (or preset via {@link I2CSimulationControl}) defaults to {@code 0} on
 * read - this mirrors freshly powered-on hardware registers being zeroed and keeps the sparse map small.
 *
 * @author Danny Kunz
 */
@Service
@Profile("simulation")
public class SimulatedI2CServiceImpl implements I2CService, I2CSimulationControl
{
    private final Map<DeviceKey, ConcurrentHashMap<Integer, Byte>> deviceToRegisters     = new ConcurrentHashMap<>();

    private final Map<Integer, I2CBusControl>                      busNumberToBusControl = new ConcurrentHashMap<>();

    @Override
    public Optional<I2CBusControl> provision(BusNumber busNumber)
    {
        return this.provision(busNumber.ordinal());
    }

    @Override
    public Optional<I2CBusControl> provision(int busNumber)
    {
        return Optional.of(this.busNumberToBusControl.computeIfAbsent(busNumber, bn -> new I2CBusControl() {
            private Map<Integer, AddressConnector> addressToConnector = new ConcurrentHashMap<>();

            @Override
            public Optional<AddressConnector> connectTo(int deviceAddress)
            {
                return Optional.of(this.addressToConnector.computeIfAbsent(deviceAddress, address -> new SimulatedAddressConnector(bn, address)));
            }
        }));
    }

    @Override
    public I2CSimulationControl presetRegister(int busNumber, int deviceAddress, int localAddress, byte... data)
    {
        this.writeBytes(busNumber, deviceAddress, localAddress, data);
        return this;
    }

    @Override
    public Optional<byte[]> readRegister(int busNumber, int deviceAddress, int localAddress, int size)
    {
        // The sparse store treats every (busNumber, deviceAddress) pair as implicitly provisioned - an unset local
        // address simply defaults to 0, so there is no "unprovisioned device" state to report as empty; this always
        // returns a present (possibly zero-filled) array.
        return Optional.of(this.readBytes(busNumber, deviceAddress, localAddress, size)
                               .get());
    }

    @Override
    public I2CSimulationControl reset()
    {
        this.deviceToRegisters.clear();
        return this;
    }

    @Override
    public I2CSimulationControl reset(int busNumber, int deviceAddress)
    {
        this.deviceToRegisters.remove(new DeviceKey(busNumber, deviceAddress));
        return this;
    }

    private Map<Integer, Byte> registersOf(int busNumber, int deviceAddress)
    {
        return this.deviceToRegisters.computeIfAbsent(new DeviceKey(busNumber, deviceAddress), key -> new ConcurrentHashMap<>());
    }

    private ByteArray readBytes(int busNumber, int deviceAddress, int localAddress, int size)
    {
        Map<Integer, Byte> registers = this.registersOf(busNumber, deviceAddress);
        byte[] result = new byte[size];
        for (int i = 0; i < size; i++)
        {
            result[i] = registers.getOrDefault(localAddress + i, (byte) 0);
        }
        return new ByteArray(result);
    }

    private void writeBytes(int busNumber, int deviceAddress, int localAddress, byte... data)
    {
        Map<Integer, Byte> registers = this.registersOf(busNumber, deviceAddress);
        for (int i = 0; i < data.length; i++)
        {
            registers.put(localAddress + i, data[i]);
        }
    }

    /**
     * Key identifying a single simulated device on a single simulated bus.
     */
    private static record DeviceKey(int busNumber, int deviceAddress) {
    }

    private class SimulatedAddressConnector extends AbstractAddressConnector
    {
        private final int busNumber;
        private final int deviceAddress;

        public SimulatedAddressConnector(int busNumber, int deviceAddress)
        {
            this.busNumber = busNumber;
            this.deviceAddress = deviceAddress;
        }

        @Override
        public Optional<ByteArray> read(int localAddress, int start, int size)
        {
            return Optional.of(readBytes(this.busNumber, this.deviceAddress, localAddress + start, size));
        }

        @Override
        public Optional<ByteArray> read(int start, int size)
        {
            return this.read(0, start, size);
        }

        @Override
        public AddressConnector write(int localAddress, byte... data)
        {
            writeBytes(this.busNumber, this.deviceAddress, localAddress, data);
            return this;
        }

        @Override
        public AddressConnector write(byte... data)
        {
            return this.write(0, data);
        }
    }
}
