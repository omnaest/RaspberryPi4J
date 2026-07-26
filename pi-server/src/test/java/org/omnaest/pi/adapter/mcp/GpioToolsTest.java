package org.omnaest.pi.adapter.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.omnaest.pi.Application;
import org.omnaest.pi.client.domain.gpio.expander.GpioPortExpanderAddress;
import org.omnaest.pi.client.domain.gpio.expander.GpioPortExpanderPort;
import org.omnaest.pi.service.gpio.GPIOSimulationControl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

/**
 * Seam test for {@link GpioTools}: drives each tool's {@code call()} handler directly against the REAL
 * {@code GPIOService}/{@code GpioPortExpanderPCF8574Service} beans wired under the {@code simulation} profile, and
 * asserts observable state via {@link GPIOSimulationControl} (the legitimate external-boundary fake) - never
 * mocking the wrapped service interfaces.
 */
@SpringBootTest(classes = Application.class, properties = "spring.profiles.active=simulation")
class GpioToolsTest
{

    @Autowired
    private GpioTools             gpioTools;

    @Autowired
    private GPIOSimulationControl gpioSimulationControl;

    @Test
    void digitalOutputEnable_enablesPort()
    {
        int port = 11;
        SyncToolSpecification spec = specOf("gpio_digital_output_enable");

        CallToolResult result = spec.call()
                                    .apply(null, Map.of("port", port));

        assertThat(result.isError()).isFalse();
        assertThat(this.gpioSimulationControl.isDigitalOutputEnabled(port)).isTrue();
    }

    @Test
    void digitalInputRead_returnsPresetState()
    {
        int port = 12;
        this.gpioSimulationControl.setDigitalInputState(port, true);
        SyncToolSpecification spec = specOf("gpio_digital_input_read");

        CallToolResult result = spec.call()
                                    .apply(null, Map.of("port", port));

        assertThat(result.isError()).isFalse();
        assertThat(textOf(result)).isEqualTo("true");
        assertThat(this.gpioSimulationControl.isDigitalInputEnabled(port)).isTrue();
    }

    @Test
    void digitalOutputWrite_setsStateAndEnables()
    {
        int port = 13;
        SyncToolSpecification spec = specOf("gpio_digital_output_write");

        CallToolResult result = spec.call()
                                    .apply(null, Map.of("port", port, "state", true));

        assertThat(result.isError()).isFalse();
        assertThat(this.gpioSimulationControl.isDigitalOutputEnabled(port)).isTrue();
        assertThat(this.gpioSimulationControl.getDigitalOutputState(port)).isTrue();
    }

    @Test
    void pwmEnable_enablesPwmPort()
    {
        int port = 14;
        SyncToolSpecification spec = specOf("gpio_pwm_enable");

        CallToolResult result = spec.call()
                                    .apply(null, Map.of("port", port));

        assertThat(result.isError()).isFalse();
        assertThat(this.gpioSimulationControl.isPwmOutputEnabled(port)).isTrue();
    }

    @Test
    void portEnable_setsDigitalOutputStateDirectly()
    {
        int port = 15;
        SyncToolSpecification spec = specOf("gpio_port_enable");

        CallToolResult result = spec.call()
                                    .apply(null, Map.of("port", port, "active", true));

        assertThat(result.isError()).isFalse();
        assertThat(this.gpioSimulationControl.getDigitalOutputState(port)).isTrue();
    }

    @Test
    void pwmWrite_setsClampedPwmValue()
    {
        int port = 16;
        SyncToolSpecification spec = specOf("gpio_pwm_write");

        CallToolResult result = spec.call()
                                    .apply(null, Map.of("port", port, "value", 50));

        assertThat(result.isError()).isFalse();
        assertThat(this.gpioSimulationControl.getPwmOutputState(port)).isEqualTo(0.5);
    }

    @Test
    void expanderWriteThenRead_roundTripsThroughSimulatedI2CBus()
    {
        GpioPortExpanderAddress address = GpioPortExpanderAddress.A21;
        GpioPortExpanderPort port = GpioPortExpanderPort.P3;
        SyncToolSpecification writeSpec = specOf("gpio_expander_pcf8574_write");
        SyncToolSpecification readSpec = specOf("gpio_expander_pcf8574_read");

        CallToolResult writeResult = writeSpec.call()
                                              .apply(null, Map.of("address", address.name(), "port", port.name(), "value", true));
        assertThat(writeResult.isError()).isFalse();

        CallToolResult readResult = readSpec.call()
                                            .apply(null, Map.of("address", address.name(), "port", port.name()));
        assertThat(readResult.isError()).isFalse();
        assertThat(textOf(readResult)).isEqualTo("true");

        // a sibling port on the same address must remain unaffected by the single-bit write.
        CallToolResult siblingReadResult = readSpec.call()
                                                   .apply(null, Map.of("address", address.name(), "port", GpioPortExpanderPort.P4.name()));
        assertThat(textOf(siblingReadResult)).isEqualTo("false");
    }

    private SyncToolSpecification specOf(String toolName)
    {
        List<SyncToolSpecification> specs = this.gpioTools.specs();
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
