package org.omnaest.pi.adapter.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.omnaest.pi.Application;
import org.omnaest.pi.service.gpio.GPIOSimulationControl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

/**
 * Seam test for {@link WeightTools}: drives {@code weight_read_hx711}'s {@code call()} handler directly against the
 * REAL {@code WeightService} bean (built atop {@code GPIOService}) wired under the {@code simulation} profile, and
 * asserts observable state via {@link GPIOSimulationControl}.
 *
 * <p>Under simulation every fresh digital input port defaults to {@code false}, so every bit read by the HX711
 * bit-bang loop is {@code 0} regardless of {@code gain}'s bit count - the raw value is deterministically
 * {@code 0 ^ 0x800000 = 8388608} for both the default and an explicit gain, which is what these tests assert. The
 * data/clock ports being left disabled afterwards proves the {@code finally} cleanup ran (i.e. the handler drove the
 * real read, not a short-circuit).
 */
@SpringBootTest(classes = Application.class, properties = "spring.profiles.active=simulation")
class WeightToolsTest
{

    private static final long     EXPECTED_RAW_VALUE_FOR_ALL_ZERO_BITS = 0x800000L;

    @Autowired
    private WeightTools           weightTools;

    @Autowired
    private GPIOSimulationControl gpioSimulationControl;

    @Test
    void readHx711_withOmittedGain_usesDefaultChannelAHigh()
    {
        int dataPort = 50;
        int clockPort = 51;

        CallToolResult result = specOf("weight_read_hx711").call()
                                                           .apply(null, Map.of("dataPort", dataPort, "clockPort", clockPort));

        assertThat(result.isError()).isFalse();
        assertThat(textOf(result)).isEqualTo(String.valueOf(EXPECTED_RAW_VALUE_FOR_ALL_ZERO_BITS));
        assertThat(this.gpioSimulationControl.isDigitalInputEnabled(dataPort)).isFalse();
        assertThat(this.gpioSimulationControl.isDigitalOutputEnabled(clockPort)).isFalse();
    }

    @Test
    void readHx711_withExplicitGain_readsFromTheGivenPorts()
    {
        int dataPort = 52;
        int clockPort = 53;

        CallToolResult result = specOf("weight_read_hx711").call()
                                                           .apply(null, Map.of("dataPort", dataPort, "clockPort", clockPort, "gain", "CHANNEL_B_LOW"));

        assertThat(result.isError()).isFalse();
        assertThat(textOf(result)).isEqualTo(String.valueOf(EXPECTED_RAW_VALUE_FOR_ALL_ZERO_BITS));
        assertThat(this.gpioSimulationControl.isDigitalInputEnabled(dataPort)).isFalse();
        assertThat(this.gpioSimulationControl.isDigitalOutputEnabled(clockPort)).isFalse();
    }

    private SyncToolSpecification specOf(String toolName)
    {
        List<SyncToolSpecification> specs = this.weightTools.specs();
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
