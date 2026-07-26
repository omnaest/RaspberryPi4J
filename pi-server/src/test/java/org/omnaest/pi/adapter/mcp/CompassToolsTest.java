package org.omnaest.pi.adapter.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.omnaest.pi.Application;
import org.omnaest.pi.service.compass.CompassService.Module;
import org.omnaest.pi.service.i2c.I2CSimulationControl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

/**
 * Seam test for {@link CompassTools}: drives {@code compass_read_angle}'s {@code call()} handler directly against
 * the REAL {@code CompassService} bean wired under the {@code simulation} profile, presetting registers via
 * {@link I2CSimulationControl} - the same register layout and hand-derived expected angle as
 * {@code SimulationProfileSmokeTest}.
 */
@SpringBootTest(classes = Application.class, properties = "spring.profiles.active=simulation")
class CompassToolsTest
{

    @Autowired
    private CompassTools         compassTools;

    @Autowired
    private I2CSimulationControl i2cSimulationControl;

    @Test
    void readAngle_withExplicitBusAndModule_matchesHandDerivedAngle()
    {
        int bus = 1;
        int deviceAddress = Module.QMC5883L.getAddress();

        this.i2cSimulationControl.presetRegister(bus, deviceAddress, 0x06, (byte) 0x01);
        this.i2cSimulationControl.presetRegister(bus, deviceAddress, 0x00, (byte) 0x03, (byte) 0xE8, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00);
        int expectedAngle = 180;

        SyncToolSpecification spec = specOf("compass_read_angle");
        CallToolResult result = spec.call()
                                    .apply(null, Map.of("bus", bus, "module", "QMC5883L"));

        assertThat(result.isError()).isFalse();
        assertThat(textOf(result)).isEqualTo(String.valueOf(expectedAngle));
    }

    @Test
    void readAngle_withOmittedArgs_defaultsToBus1AndQmc5883L()
    {
        int deviceAddress = Module.QMC5883L.getAddress();

        // y = 1000, x = 0 -> north-relative angle differs from the x=1000,y=0 case above, proving the default
        // bus/module actually resolved to the same device this test presets (bus 1, QMC5883L 0x0D).
        this.i2cSimulationControl.presetRegister(1, deviceAddress, 0x06, (byte) 0x01);
        this.i2cSimulationControl.presetRegister(1, deviceAddress, 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x03, (byte) 0xE8, (byte) 0x00, (byte) 0x00);
        int expectedAngle = (int) Math.round((-Math.atan2(1000, 0) * 180.0 / Math.PI) + 180);

        SyncToolSpecification spec = specOf("compass_read_angle");
        CallToolResult result = spec.call()
                                    .apply(null, Map.of());

        assertThat(result.isError()).isFalse();
        assertThat(textOf(result)).isEqualTo(String.valueOf(expectedAngle));
    }

    private SyncToolSpecification specOf(String toolName)
    {
        List<SyncToolSpecification> specs = this.compassTools.specs();
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
