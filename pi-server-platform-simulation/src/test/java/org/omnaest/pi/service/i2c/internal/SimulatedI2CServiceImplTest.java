package org.omnaest.pi.service.i2c.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.omnaest.pi.service.i2c.I2CService.AddressConnector;
import org.omnaest.pi.service.i2c.I2CService.BusNumber;
import org.omnaest.pi.service.i2c.I2CService.ByteArray;
import org.omnaest.pi.service.i2c.I2CService.Register;

public class SimulatedI2CServiceImplTest
{
    private SimulatedI2CServiceImpl simulation;

    @BeforeEach
    public void setUp()
    {
        this.simulation = new SimulatedI2CServiceImpl();
    }

    @Test
    public void testPresetAndReadRegisterRoundTrip()
    {
        this.simulation.presetRegister(0, 0x40, 2, (byte) 0x11, (byte) 0x22);

        byte[] result = this.simulation.readRegister(0, 0x40, 2, 2)
                                       .orElseThrow();
        assertArrayEquals(new byte[] {0x11, 0x22}, result);
    }

    @Test
    public void testUnsetLocalAddressDefaultsToZero()
    {
        // never preset - must default to 0 rather than being reported as absent
        Optional<byte[]> result = this.simulation.readRegister(0, 0x40, 5, 3);
        assertTrue(result.isPresent());
        assertArrayEquals(new byte[] {0, 0, 0}, result.get());
    }

    @Test
    public void testConnectorWriteVisibleThroughControlSurface()
    {
        AddressConnector connector = this.simulation.provision(BusNumber.BUS_1)
                                                    .orElseThrow()
                                                    .connectTo(0x50)
                                                    .orElseThrow();

        connector.write(3, (byte) 0x7A);

        byte[] viaControl = this.simulation.readRegister(BusNumber.BUS_1.ordinal(), 0x50, 3, 1)
                                           .orElseThrow();
        assertEquals((byte) 0x7A, viaControl[0]);
    }

    @Test
    public void testControlPresetVisibleThroughConnector()
    {
        AddressConnector connector = this.simulation.provision(BusNumber.BUS_1)
                                                    .orElseThrow()
                                                    .connectTo(0x50)
                                                    .orElseThrow();

        this.simulation.presetRegister(BusNumber.BUS_1.ordinal(), 0x50, 4, (byte) 0x5C);

        ByteArray read = connector.read(4, 0, 1)
                                  .orElseThrow();
        assertEquals((byte) 0x5C, read.getFirstByte()
                                      .orElseThrow());
    }

    @Test
    public void testDefaultAddressReadAndWritePrimitives()
    {
        AddressConnector connector = this.simulation.provision(0)
                                                    .orElseThrow()
                                                    .connectTo(0x11)
                                                    .orElseThrow();

        connector.write((byte) 0x09);

        ByteArray result = connector.read(0, 1)
                                    .orElseThrow();
        assertEquals((byte) 0x09, result.getFirstByte()
                                        .orElseThrow());
    }

    @Test
    public void testAccessRegisterByteAndBitManipulation()
    {
        AddressConnector connector = this.simulation.provision(0)
                                                    .orElseThrow()
                                                    .connectTo(0x68)
                                                    .orElseThrow();
        Register register = connector.accessRegister(10);

        // writeByte / readByte
        register.writeByte((byte) 0b10110010);
        assertEquals((byte) 0b10110010, register.readByte());

        // writeBit / readBit
        register.writeBit(0, true);
        assertTrue(register.readBit(0));
        register.writeBit(0, false);
        assertFalse(register.readBit(0));
        // untouched bits of the byte must survive the single-bit write
        assertEquals((byte) 0b10110010, register.readByte());

        // accessBit
        register.accessBit(3)
                .writeValue(true);
        assertTrue(register.accessBit(3)
                           .readValue());
        register.accessBit(3)
                .writeValue(0);
        assertFalse(register.accessBit(3)
                            .readValue());
    }

    @Test
    public void testAccessBitsWriteAndReadRoundTrip()
    {
        AddressConnector connector = this.simulation.provision(0)
                                                    .orElseThrow()
                                                    .connectTo(0x68)
                                                    .orElseThrow();
        Register register = connector.accessRegister(20);

        register.accessBits(0, 16)
                .write(0x1234);

        // the raw bytes are stored in the low-address-first (little-endian) bit order the write() primitive uses -
        // the low byte of the written value lands at localAddress, the high byte at localAddress + 1
        byte[] rawBytes = this.simulation.readRegister(0, 0x68, 20, 2)
                                         .orElseThrow();
        assertArrayEquals(new byte[] {0x34, 0x12}, rawBytes);

        // reading back with the matching (little-endian) accessor round-trips to the exact written value
        assertEquals(0x1234, register.accessBits(0, 16)
                                     .readAsLittleEndianUnsignedInteger());

        // reading the same bytes as big-endian (address order = decreasing significance) yields the byte-swapped value
        assertEquals(0x3412, register.accessBits(0, 16)
                                     .readAsBigEndianUnsignedInteger());
    }

    @Test
    public void testWaitUntilBitIsTrueReturnsImmediatelyWhenPreset()
    {
        AddressConnector connector = this.simulation.provision(0)
                                                    .orElseThrow()
                                                    .connectTo(0x68)
                                                    .orElseThrow();

        // isolate bit 0 with mask=1 and preset it to 1 so the predicate ((value & mask) == 1) is true right away
        this.simulation.presetRegister(0, 0x68, 30, (byte) 0b00000001);

        AddressConnector result = connector.waitUntilBitIsTrue(30, (byte) 1);
        assertEquals(connector, result);
    }

    @Test
    public void testResetClearsAllDevices()
    {
        this.simulation.presetRegister(0, 0x40, 0, (byte) 0x11);
        this.simulation.presetRegister(1, 0x50, 0, (byte) 0x22);

        this.simulation.reset();

        assertArrayEquals(new byte[] {0}, this.simulation.readRegister(0, 0x40, 0, 1)
                                                         .orElseThrow());
        assertArrayEquals(new byte[] {0}, this.simulation.readRegister(1, 0x50, 0, 1)
                                                         .orElseThrow());
    }

    @Test
    public void testResetSingleDeviceLeavesOthersUntouched()
    {
        this.simulation.presetRegister(0, 0x40, 0, (byte) 0x11);
        this.simulation.presetRegister(0, 0x41, 0, (byte) 0x22);

        this.simulation.reset(0, 0x40);

        assertArrayEquals(new byte[] {0}, this.simulation.readRegister(0, 0x40, 0, 1)
                                                         .orElseThrow());
        assertArrayEquals(new byte[] {0x22}, this.simulation.readRegister(0, 0x41, 0, 1)
                                                            .orElseThrow());
    }
}
