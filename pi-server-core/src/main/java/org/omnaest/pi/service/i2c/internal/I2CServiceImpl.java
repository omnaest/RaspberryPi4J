package org.omnaest.pi.service.i2c.internal;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.omnaest.pi.service.i2c.I2CService;
import org.omnaest.pi.service.utils.BitNumberUtils;
import org.omnaest.pi.service.utils.ThreadUtils;
import org.omnaest.utils.bitset.Bits;
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
        public AddressConnector write(byte... data)
        {
            try
            {
                this.device.write(data);
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

        @Override
        public Register accessRegister(int localAddress)
        {
            AddressConnector addressConnector = this;
            return new Register()
            {

                @Override
                public Register writeByte(byte value)
                {
                    addressConnector.write(localAddress, value);
                    return this;
                }

                @Override
                public Register writeBit(int index, boolean value)
                {
                    this.accessBit(index)
                        .writeValue(value);
                    return this;
                }

                @Override
                public byte readByte()
                {
                    return addressConnector.read(localAddress, 0, 1)
                                           .flatMap(ByteArray::getFirstByte)
                                           .orElse((byte) 0);
                }

                @Override
                public RegisterBits accessBits(int numberOfBits)
                {
                    return this.accessBits(0, numberOfBits);
                }

                @Override
                public RegisterBits accessBits(int bitIndex, int numberOfBits)
                {

                    return new RegisterBits()
                    {
                        @Override
                        public RegisterBits write(Bits bits)
                        {
                            Bits currentBits = this.readBytesAsBits();
                            currentBits.setIndex(bitIndex, bits.clone()
                                                               .setLength(numberOfBits));
                            addressConnector.write(localAddress, currentBits.toBytes());
                            return this;
                        }

                        @Override
                        public RegisterBits write(int value)
                        {
                            return this.write(Bits.of(value));
                        }

                        @Override
                        public int readAsBigEndianUnsignedInteger()
                        {
                            return BitNumberUtils.mapBitsFromMsbToLsbAsUnsignedInteger(this.read());
                        }

                        @Override
                        public int readAsBigEndianSignedInteger()
                        {
                            return BitNumberUtils.mapBitsFromMsbToLsbAsSignedInteger(this.read());
                        }

                        @Override
                        public Bits read()
                        {
                            return this.readBytesAsBits()
                                       .setLength(numberOfBits);
                        }

                        private Bits readBytesAsBits()
                        {
                            int numberOfBytes = determineNumberOfBytesFromBits(bitIndex + numberOfBits);
                            return addressConnector.read(localAddress, 0, numberOfBytes)
                                                   .map(ByteArray::get)
                                                   .map(Bits::of)
                                                   .orElse(Bits.newInstance()
                                                               .setLength(numberOfBytes * 8));
                        }

                        @Override
                        public int readAsLittleEndianUnsignedInteger()
                        {
                            return BitNumberUtils.mapBitsFromLsbToMsbAsUnsignedInteger(this.read());
                        }
                    };
                }

                private int determineNumberOfBytesFromBits(int numberOfBits)
                {
                    int numberOfFilledBytes = numberOfBits / 8;
                    int numberOfUnfilledBytes = (numberOfBits % 8 != 0) ? 1 : 0;
                    return numberOfFilledBytes + numberOfUnfilledBytes;
                }

                @Override
                public boolean readBit(int index)
                {
                    return this.accessBit(index)
                               .readValue();
                }

                @Override
                public RegisterBit accessBit(int index)
                {
                    return new RegisterBit()
                    {

                        @Override
                        public void writeValue(boolean value)
                        {
                            byte currentValue = readByte();
                            byte newValue = Bits.of(currentValue)
                                                .setLength(8)
                                                .setIndex(index, value)
                                                .toBytes()[0];
                            writeByte(newValue);
                        }

                        @Override
                        public boolean readValue()
                        {
                            byte value = readByte();
                            return Bits.of(value)
                                       .get(index);
                        }

                        @Override
                        public void writeValue(int value)
                        {
                            this.writeValue(value != 0);
                        }
                    };
                }
            };
        }

    }
}
