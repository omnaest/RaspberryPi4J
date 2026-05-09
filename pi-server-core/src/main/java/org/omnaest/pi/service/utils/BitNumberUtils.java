package org.omnaest.pi.service.utils;

import java.util.Optional;

import org.omnaest.utils.bitset.Bits;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Utility to translate bits into numbers.
 * 
 * @author omnaest
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BitNumberUtils
{
    public static int mapBitsFromMsbToLsbAsSignedInteger(Bits bits)
    {
        int unsignedInteger = mapBitsFromMsbToLsbAsUnsignedInteger(bits);

        Bits unsignedIntegerBits = Bits.of(unsignedInteger);

        int shiftSize = Integer.SIZE - bits.getLength();
        int divisor = Bits.of(true)
                          .setLength(shiftSize + 1)
                          .toInt() << shiftSize;
        return unsignedIntegerBits.shiftLeft(shiftSize)
                                  .toInt()
                / divisor;
    }

    public static int mapBitsFromMsbToLsbAsUnsignedInteger(Bits bits)
    {
        return Optional.ofNullable(bits)
                       .map(BitNumberUtils::asLongFromMsbToLsb)
                       .map(Long::intValue)
                       .orElse(0);
    }

    public static int mapBitsFromLsbToMsbAsUnsignedInteger(Bits bits)
    {
        return Optional.ofNullable(bits)
                       .map(BitNumberUtils::asLongFromLsbToMsb)
                       .map(Long::intValue)
                       .orElse(0);
    }

    public static long asLongFromMsbToLsb(Bits bits)
    {
        long result = 0;
        for (Bits byteBits : bits.partition(8)
                                 .toList())
        {
            for (boolean bitValue : byteBits.toReverseBooleanArray())
            {
                result = result * 2 + (bitValue ? 1 : 0);
            }
        }
        return result;
    }

    public static long asLongFromLsbToMsb(Bits bits)
    {
        return bits.toLong();
    }
}
