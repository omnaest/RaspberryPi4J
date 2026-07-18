package org.omnaest.pi.service.i2c;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.omnaest.pi.service.i2c.I2CService.AddressConnector;
import org.omnaest.pi.service.i2c.I2CService.ByteArray;
import org.omnaest.pi.service.i2c.I2CService.Register;
import org.omnaest.pi.service.i2c.I2CService.RegisterBit;
import org.omnaest.pi.service.i2c.I2CService.RegisterBits;
import org.omnaest.pi.service.utils.BitNumberUtils;
import org.omnaest.pi.service.utils.ThreadUtils;
import org.omnaest.utils.bitset.Bits;

/**
 * Base class holding the register/bit access and wait mechanics shared by every {@link AddressConnector}
 * implementation. The logic here is pure bit manipulation built only on top of the four raw primitives
 * ({@link #read(int, int, int)}, {@link #read(int, int)}, {@link #write(int, byte...)}, {@link #write(byte...)}) and
 * has no hardware dependency. Concrete subclasses only need to supply those four primitives.
 *
 * @author Danny Kunz
 */
public abstract class AbstractAddressConnector implements AddressConnector
{
    @Override
    public abstract Optional<ByteArray> read(int localAddress, int start, int size);

    @Override
    public abstract Optional<ByteArray> read(int start, int size);

    @Override
    public abstract AddressConnector write(int localAddress, byte... data);

    @Override
    public abstract AddressConnector write(byte... data);

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
        return new Register() {

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

                return new RegisterBits() {
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
                return new RegisterBit() {

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
