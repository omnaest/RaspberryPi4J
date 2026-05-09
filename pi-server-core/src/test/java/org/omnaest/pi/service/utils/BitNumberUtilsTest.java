package org.omnaest.pi.service.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.omnaest.utils.bitset.Bits;

public class BitNumberUtilsTest
{

    @Test
    public void testMapBitsFromMsbToLsbAsSignedInteger()
    {
        int msb = 0b10000011; // address = 0    23:16
        int isb = 0b00000111; // address = 1    16:8
        int lsb = 0b00001111; // address = 2    7:0
        int value = (msb << 24 | isb << 16 | lsb << 8) / (1 << 8);
        assertEquals(value, BitNumberUtils.mapBitsFromMsbToLsbAsSignedInteger(Bits.of(new byte[] { (byte) msb, (byte) isb, (byte) lsb })));
    }

    @Test
    public void testMapBitsFromMsbToLsbAsUnsignedInteger()
    {
        byte msb = 0b00000011; // address = 0    23:16
        byte isb = 0b00000111; // address = 1    16:8
        byte lsb = 0b00001111; // address = 2    7:0
        int value = msb << 16 | isb << 8 | lsb;
        assertEquals(value, BitNumberUtils.mapBitsFromMsbToLsbAsUnsignedInteger(Bits.of(new byte[] { msb, isb, lsb })));
    }
}
