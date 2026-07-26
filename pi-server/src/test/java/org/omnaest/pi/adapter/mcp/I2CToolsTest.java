package org.omnaest.pi.adapter.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.omnaest.pi.Application;
import org.omnaest.pi.service.i2c.I2CSimulationControl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

/**
 * Seam test for {@link I2CTools}: drives each tool's {@code call()} handler directly against the REAL
 * {@code I2CService} bean wired under the {@code simulation} profile, and asserts observable state via
 * {@link I2CSimulationControl}.
 */
@SpringBootTest(classes = Application.class, properties = "spring.profiles.active=simulation")
class I2CToolsTest
{

    @Autowired
    private I2CTools             i2cTools;

    @Autowired
    private I2CSimulationControl i2cSimulationControl;

    @Test
    void readByte_readsFromLocalAddressPlusOffset_withExplicitAddress()
    {
        int bus = 1;
        int address = 0x40;
        int localaddress = 0x10;
        int offset = 2;
        // read(localaddress, offset, 1) resolves to logical register localaddress+offset.
        this.i2cSimulationControl.presetRegister(bus, address, localaddress + offset, (byte) 0x2A);

        SyncToolSpecification spec = specOf("i2c_read_byte");
        CallToolResult result = spec.call()
                                    .apply(null, Map.of("bus", bus, "address", address, "localaddress", localaddress, "offset", offset));

        assertThat(result.isError()).isFalse();
        assertThat(textOf(result)).isEqualTo(String.valueOf((byte) 0x2A));
    }

    @Test
    void readByte_withOmittedAddress_defaultsToZero()
    {
        int bus = 1;
        int localaddress = 0x05;
        int offset = 0;
        this.i2cSimulationControl.presetRegister(bus, 0, localaddress, (byte) 0x11);

        SyncToolSpecification spec = specOf("i2c_read_byte");
        CallToolResult result = spec.call()
                                    .apply(null, Map.of("bus", bus, "localaddress", localaddress, "offset", offset));

        assertThat(result.isError()).isFalse();
        assertThat(textOf(result)).isEqualTo(String.valueOf((byte) 0x11));
    }

    @Test
    void writeByte_writesToLocalAddress_ignoringOffset()
    {
        int bus = 1;
        int address = 0x41;
        int localaddress = 0x20;

        SyncToolSpecification spec = specOf("i2c_write_byte");
        CallToolResult result = spec.call()
                                    .apply(null, Map.of("bus", bus, "address", address, "localaddress", localaddress, "offset", 9, "value", 0x55));

        assertThat(result.isError()).isFalse();
        byte[] registered = this.i2cSimulationControl.readRegister(bus, address, localaddress, 1)
                                                     .orElseThrow();
        assertThat(registered[0]).isEqualTo((byte) 0x55);
    }

    private SyncToolSpecification specOf(String toolName)
    {
        List<SyncToolSpecification> specs = this.i2cTools.specs();
        return specs.stream()
                    .filter(spec -> toolName.equals(spec.tool()
                                                        .name()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Tool spec not found: " + toolName));
    }

    private static String textOf(CallToolResult result)
    {
        return ((TextContent) result.content()
                                    .get(0)).text();
    }
}
