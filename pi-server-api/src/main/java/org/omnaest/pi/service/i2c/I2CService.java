package org.omnaest.pi.service.i2c;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import org.omnaest.utils.bitset.Bits;

public interface I2CService
{
    public static class ByteArray implements Supplier<byte[]>
    {
        private byte[] data;

        public ByteArray(byte[] data)
        {
            super();
            this.data = data;
        }

        @Override
        public byte[] get()
        {
            return this.data;
        }

        public Optional<Byte> getFirstByte()
        {
            return Optional.ofNullable(this.data)
                           .filter(data -> data.length >= 1)
                           .map(data -> data[0]);
        }

        @Override
        public String toString()
        {
            return "ByteArray [data=" + Arrays.toString(this.data) + "]";
        }

        public int asIntFromMsbToLsb(int startIndex)
        {
            return this.asIntFromMsbToLsb(startIndex, startIndex + 1);
        }

        public int asIntFromMsbToLsb(int startIndex, int endIndex)
        {
            return (int) this.asLongFromMsbToLsb(startIndex, endIndex);
        }

        public long asLongFromMsbToLsb(int startIndex)
        {
            return this.asLongFromMsbToLsb(startIndex, startIndex + 3);
        }

        public long asLongFromMsbToLsb(int startIndex, int endIndex)
        {
            long result = 0;
            for (int ii = startIndex; ii <= endIndex; ii++)
            {
                result = result * 256 + (this.data[ii] & 0xFF);
            }
            return result;
        }

        public int asIntFromMsbToLsb()
        {
            return this.asIntStreamFromMsbToLsb()
                       .findFirst()
                       .orElse(0);
        }

        public int[] asIntArrayFromMsbToLsb()
        {
            return IntStream.range(0, this.data.length / 2)
                            .map(startIndex -> startIndex * 2)
                            .map(startIndex -> this.asIntFromMsbToLsb(startIndex))
                            .toArray();
        }

        public IntStream asIntStreamFromMsbToLsb()
        {
            int[] values = this.asIntArrayFromMsbToLsb();
            return IntStream.range(0, values.length)
                            .map(i -> values[i]);
        }
    }

    public static interface AddressConnector
    {
        /**
         * Reads a given number of bytes from the given address and start offset
         * 
         * @param localAddress
         * @param start
         * @param size
         * @return
         */
        public Optional<ByteArray> read(int localAddress, int start, int size);

        /**
         * Similar to {@link #read(int, int, int)} from the default address
         * 
         * @param start
         * @param size
         * @return
         */
        public Optional<ByteArray> read(int start, int size);

        public AddressConnector write(int localAddress, byte... data);

        /**
         * Similar to {@link #write(int, byte...)} to local address 0
         * 
         * @param data
         * @return
         */
        public AddressConnector write(byte... data);

        public AddressConnector wait(int duration, TimeUnit timeUnit);

        public AddressConnector waitUntil(Predicate<AddressConnector> predicate);

        public AddressConnector waitUntilBitIsTrue(int localAddress, byte mask);

        public Register accessRegister(int localAddress);
    }

    public static interface Register
    {
        public boolean readBit(int index);

        public byte readByte();

        public Register writeBit(int index, boolean value);

        public Register writeByte(byte value);

        public RegisterBit accessBit(int index);

        public RegisterBits accessBits(int numberOfBits);

        public RegisterBits accessBits(int index, int numberOfBits);

    }

    public static interface RegisterBit
    {
        public boolean readValue();

        public void writeValue(boolean value);

        public void writeValue(int value);
    }

    public static interface RegisterBits
    {
        public Bits read();

        /**
         * Reads the {@link RegisterBits} as unsigned integer by most significant byte to less significant byte
         * 
         * @return
         */
        public int readAsUnsignedInteger();

        /**
         * Reads the {@link RegisterBits} as signed integer by most significant byte to less significant byte
         * 
         * @return
         */
        public int readAsSignedInteger();

        public RegisterBits write(Bits bits);
    }

    public static interface I2CBusControl
    {
        public Optional<AddressConnector> connectTo(int deviceAddress);
    }

    public static enum BusNumber
    {
        BUS_0, BUS_1, BUS_2, BUS_4
    }

    public Optional<I2CBusControl> provision(int busNumber);

    public Optional<I2CBusControl> provision(BusNumber busNumber);
}
