package org.omnaest.pi.adapter.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.omnaest.pi.Application;
import org.omnaest.pi.service.i2c.I2CSimulationControl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

/**
 * Seam test for {@link SensorTools}: drives each tool's {@code call()} handler directly against the REAL
 * {@code RotaryEncoderService}/{@code GyroscopeService}/{@code FlowSensorService}/{@code PressureSensorMS5837Service}
 * beans wired under the {@code simulation} profile, presetting/inspecting simulated I2C register state where
 * applicable via {@link I2CSimulationControl}.
 */
@SpringBootTest(classes = Application.class, properties = "spring.profiles.active=simulation")
class SensorToolsTest
{

    @Autowired
    private SensorTools          sensorTools;

    @Autowired
    private I2CSimulationControl i2cSimulationControl;

    @Autowired
    private ObjectMapper         objectMapper;

    @Test
    void rotaryEncoderRead_freshEncoderStartsAtZero()
    {
        CallToolResult result = specOf("rotary_encoder_read").call()
                                                             .apply(null, Map.of("clkPort", 60, "dtPort", 61, "swPort", 62));

        assertThat(result.isError()).isFalse();
        assertThat(textOf(result)).isEqualTo("0");
    }

    @Test
    void gyroscopeReadOrientation_decodesPresetRegisters()
    {
        int bus = 1;
        int deviceAddress = 0x68;
        // GyroscopeServiceImpl.ReadRegister high addresses: GYROSCOPE_X=67, GYROSCOPE_Y=69, GYROSCOPE_Z=71, each
        // read as 2 bytes MSB-first unsigned via ByteArray.asIntFromMsbToLsb(0).
        this.i2cSimulationControl.presetRegister(bus, deviceAddress, 67, (byte) 0x00, (byte) 0x0A); // x = 10
        this.i2cSimulationControl.presetRegister(bus, deviceAddress, 69, (byte) 0x00, (byte) 0x14); // y = 20
        this.i2cSimulationControl.presetRegister(bus, deviceAddress, 71, (byte) 0x00, (byte) 0x1E); // z = 30

        CallToolResult result = specOf("gyroscope_read_orientation").call()
                                                                    .apply(null, Map.of("numberOfSamplings", 1));

        assertThat(result.isError()).isFalse();
        JsonNode node = readJson(result);
        assertThat(node.get("x").asDouble()).isEqualTo(10.0);
        assertThat(node.get("y").asDouble()).isEqualTo(20.0);
        assertThat(node.get("z").asDouble()).isEqualTo(30.0);
    }

    @Test
    void flowSensorEnableThenReadThenDisable_reflectsContextLifecycle()
    {
        int port = 70;

        // before enabling, no context exists.
        CallToolResult beforeEnable = specOf("flow_sensor_read_rate").call()
                                                                     .apply(null, Map.of("port", port));
        assertThat(beforeEnable.isError()).isFalse();
        assertThat(textOf(beforeEnable)).containsIgnoringCase("nan");

        CallToolResult enableResult = specOf("flow_sensor_enable").call()
                                                                  .apply(null, Map.of("port", port));
        assertThat(enableResult.isError()).isFalse();

        // the periodic flow-rate computation is scheduled with a 10s initial delay, so immediately after enabling
        // the context exists but the rate is still its initial 0.0 - proving the port reached the real service.
        CallToolResult afterEnable = specOf("flow_sensor_read_rate").call()
                                                                    .apply(null, Map.of("port", port));
        assertThat(afterEnable.isError()).isFalse();
        assertThat(textOf(afterEnable)).isEqualTo("0.0");

        CallToolResult disableResult = specOf("flow_sensor_disable").call()
                                                                    .apply(null, Map.of("port", port));
        assertThat(disableResult.isError()).isFalse();

        CallToolResult afterDisable = specOf("flow_sensor_read_rate").call()
                                                                     .apply(null, Map.of("port", port));
        assertThat(afterDisable.isError()).isFalse();
        assertThat(textOf(afterDisable)).containsIgnoringCase("nan");
    }

    @Test
    void pressureMs5837EnableThenReadThenDisable_reflectsSensorLifecycle()
    {
        CallToolResult enableResult = specOf("pressure_ms5837_enable").call()
                                                                      .apply(null, Map.of("model", "MS5837_02BA"));
        assertThat(enableResult.isError()).isFalse();
        String sensorId = unquote(textOf(enableResult));

        // all MS5837 registers default to 0 under simulation, so the calibration/decode formula is deterministic:
        // pressureAbsolute=0.0, pressureRelative=0.0, temperature=20.0 for MS5837_02BA (see PressureSensorMS5837ServiceImpl).
        CallToolResult readResult = specOf("pressure_ms5837_read").call()
                                                                  .apply(null, Map.of("sensorId", sensorId));
        assertThat(readResult.isError()).isFalse();
        JsonNode node = readJson(readResult);
        assertThat(node.get("temperature").asDouble()).isEqualTo(20.0);
        assertThat(node.get("pressureAbsolute").asDouble()).isEqualTo(0.0);
        assertThat(node.get("pressureRelative").asDouble()).isEqualTo(0.0);

        CallToolResult disableResult = specOf("pressure_ms5837_disable").call()
                                                                        .apply(null, Map.of("sensorId", sensorId));
        assertThat(disableResult.isError()).isFalse();

        CallToolResult readAfterDisable = specOf("pressure_ms5837_read").call()
                                                                        .apply(null, Map.of("sensorId", sensorId));
        assertThat(readAfterDisable.isError()).isFalse();
        assertThat(textOf(readAfterDisable)).isEqualTo("null");
    }

    @Test
    void pressureMs5837Read_unknownSensorIdReturnsNull()
    {
        CallToolResult result = specOf("pressure_ms5837_read").call()
                                                              .apply(null, Map.of("sensorId", "does-not-exist"));

        assertThat(result.isError()).isFalse();
        assertThat(textOf(result)).isEqualTo("null");
    }

    private SyncToolSpecification specOf(String toolName)
    {
        List<SyncToolSpecification> specs = this.sensorTools.specs();
        return specs.stream()
                    .filter(spec -> toolName.equals(spec.tool()
                                                        .name()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Tool spec not found: " + toolName));
    }

    private JsonNode readJson(CallToolResult result)
    {
        try
        {
            return this.objectMapper.readTree(textOf(result));
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private static String textOf(CallToolResult result)
    {
        return ((TextContent) result.content()
                                    .get(0)).text();
    }

    private static String unquote(String jsonQuotedString)
    {
        return jsonQuotedString.substring(1, jsonQuotedString.length() - 1);
    }
}
