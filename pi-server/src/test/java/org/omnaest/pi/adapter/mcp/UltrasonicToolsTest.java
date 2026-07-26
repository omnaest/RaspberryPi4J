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

/**
 * Seam test for {@link UltrasonicTools}: drives each tool's {@code call()} handler directly against the REAL
 * {@code UltrasonicService} bean (built atop {@code GPIOService}) wired under the {@code simulation} profile, and
 * asserts observable state via {@link GPIOSimulationControl}.
 *
 * <p>Timeouts are kept in the low-millisecond range so the (real, busy-waiting) timeout paths in
 * {@code UltrasonicServiceImpl} complete quickly and deterministically without needing to simulate echo-pulse
 * timing.
 */
@SpringBootTest(classes = Application.class, properties = "spring.profiles.active=simulation")
class UltrasonicToolsTest
{

    private static final int      SHORT_TIMEOUT_NANOS = 2_000_000; // 2ms

    @Autowired
    private UltrasonicTools       ultrasonicTools;

    @Autowired
    private GPIOSimulationControl gpioSimulationControl;

    @Test
    void init_enablesEchoInputAndTriggerOutputPorts()
    {
        int index = 1;
        int echoPort = 40;
        int triggerPort = 41;

        CallToolResult result = specOf("ultrasonic_init").call()
                                                         .apply(null, Map.of(
                                                                             "index", index,
                                                                             "echoPort", echoPort,
                                                                             "triggerPort", triggerPort,
                                                                             "pingTimeout", SHORT_TIMEOUT_NANOS,
                                                                             "signalTimeout", SHORT_TIMEOUT_NANOS));

        assertThat(result.isError()).isFalse();
        assertThat(this.gpioSimulationControl.isDigitalInputEnabled(echoPort)).isTrue();
        assertThat(this.gpioSimulationControl.isDigitalOutputEnabled(triggerPort)).isTrue();
    }

    @Test
    void readDistance_withoutInit_surfacesIllegalStateAsMcpError()
    {
        int index = 2;

        CallToolResult result = specOf("ultrasonic_read_distance").call()
                                                                  .apply(null, Map.of("index", index));

        assertThat(result.isError()).isTrue();
    }

    @Test
    void init_withOmittedSignals_usesClassDefaultWithoutError()
    {
        int index = 3;

        CallToolResult initResult = specOf("ultrasonic_init").call()
                                                             .apply(null, Map.of(
                                                                                 "index", index,
                                                                                 "echoPort", 42,
                                                                                 "triggerPort", 43,
                                                                                 "pingTimeout", SHORT_TIMEOUT_NANOS,
                                                                                 "signalTimeout", SHORT_TIMEOUT_NANOS));
        assertThat(initResult.isError()).isFalse();

        // sendSignal() iterates over the (defaulted) signals array before the timeout busy-wait; a null array from
        // a wrong "omitted means null-and-unhandled" implementation would NPE here instead of timing out cleanly.
        CallToolResult readResult = specOf("ultrasonic_read_distance").call()
                                                                      .apply(null, Map.of("index", index));
        assertThat(readResult.isError()).isFalse();
    }

    @Test
    void init_withExplicitSignals_overridesClassDefaultWithoutError()
    {
        int index = 4;

        CallToolResult initResult = specOf("ultrasonic_init").call()
                                                             .apply(null, Map.of(
                                                                                 "index", index,
                                                                                 "echoPort", 44,
                                                                                 "triggerPort", 45,
                                                                                 "pingTimeout", SHORT_TIMEOUT_NANOS,
                                                                                 "signalTimeout", SHORT_TIMEOUT_NANOS,
                                                                                 "signals", List.of(3, 10, 0)));
        assertThat(initResult.isError()).isFalse();

        CallToolResult readResult = specOf("ultrasonic_read_distance").call()
                                                                      .apply(null, Map.of("index", index));
        assertThat(readResult.isError()).isFalse();
    }

    private SyncToolSpecification specOf(String toolName)
    {
        List<SyncToolSpecification> specs = this.ultrasonicTools.specs();
        return specs.stream()
                    .filter(s -> toolName.equals(s.tool()
                                                  .name()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Tool spec not found: " + toolName));
    }
}
