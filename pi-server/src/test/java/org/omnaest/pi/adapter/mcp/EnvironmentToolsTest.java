package org.omnaest.pi.adapter.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.omnaest.pi.Application;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

/**
 * Seam test for {@link EnvironmentTools}: drives {@code environment_bmp180_read}'s {@code call()} handler directly
 * against the REAL {@code EnvironmentService} bean wired under the {@code simulation} profile.
 *
 * <p>Note: the simulated I2C register store ({@code SimulatedI2CServiceImpl}) defaults every unset local address to
 * {@code 0} rather than reporting "absent", so an un-preset BMP180 device does not throw here (calibration and
 * measurement both complete, with NaN-ish derived values from zeroed calibration constants) - the mandatory
 * error-path coverage for {@code McpToolSupport.handle} is instead provided by {@code CameraToolsTest}, where
 * {@code CameraServicePI} unconditionally throws.
 */
@SpringBootTest(classes = Application.class, properties = "spring.profiles.active=simulation")
class EnvironmentToolsTest
{

    @Autowired
    private EnvironmentTools environmentTools;

    @Autowired
    private ObjectMapper     objectMapper;

    @Test
    void read_returnsMeasurementShapeWithoutError() throws Exception
    {
        SyncToolSpecification spec = specOf("environment_bmp180_read");

        CallToolResult result = spec.call()
                                    .apply(null, Map.of());

        assertThat(result.isError()).isFalse();
        JsonNode node = this.objectMapper.readTree(textOf(result));
        assertThat(node.has("altitude")).isTrue();
        assertThat(node.has("pressure")).isTrue();
        assertThat(node.has("temperature")).isTrue();
    }

    private SyncToolSpecification specOf(String toolName)
    {
        List<SyncToolSpecification> specs = this.environmentTools.specs();
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
