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
 * Seam test for {@link MotorTools}: drives each tool's {@code call()} handler directly against the REAL
 * {@code MotorControlService} bean (built atop {@code GPIOService}) wired under the {@code simulation} profile, and
 * asserts observable state via {@link GPIOSimulationControl}.
 */
@SpringBootTest(classes = Application.class, properties = "spring.profiles.active=simulation")
class MotorToolsTest
{

    @Autowired
    private MotorTools            motorTools;

    @Autowired
    private GPIOSimulationControl gpioSimulationControl;

    @Test
    void define_thenMove_drivesForwardPortAndPwm()
    {
        int forwardPort = 21;
        int backwardPort = 22;
        int pwmPort = 23;

        String id = defineMotor(forwardPort, backwardPort, pwmPort);

        SyncToolSpecification moveSpec = specOf("motor_move");
        CallToolResult moveResult = moveSpec.call()
                                            .apply(null, Map.of("id", id, "direction", "FORWARDS", "speed", 0.75));

        assertThat(moveResult.isError()).isFalse();
        assertThat(this.gpioSimulationControl.getDigitalOutputState(forwardPort)).isTrue();
        assertThat(this.gpioSimulationControl.getDigitalOutputState(backwardPort)).isFalse();
        assertThat(this.gpioSimulationControl.getPwmOutputState(pwmPort)).isEqualTo(0.75);
    }

    @Test
    void move_withSpeedAtOrBelowThreshold_stopsInstead()
    {
        int forwardPort = 24;
        int backwardPort = 25;
        int pwmPort = 26;

        String id = defineMotor(forwardPort, backwardPort, pwmPort);

        // first drive it forward so stop-vs-move is actually observable.
        specOf("motor_move").call()
                            .apply(null, Map.of("id", id, "direction", "FORWARDS", "speed", 0.5));
        assertThat(this.gpioSimulationControl.getDigitalOutputState(forwardPort)).isTrue();

        CallToolResult result = specOf("motor_move").call()
                                                    .apply(null, Map.of("id", id, "direction", "FORWARDS", "speed", 0.0005));

        assertThat(result.isError()).isFalse();
        assertThat(this.gpioSimulationControl.getDigitalOutputState(forwardPort)).isFalse();
        assertThat(this.gpioSimulationControl.getDigitalOutputState(backwardPort)).isFalse();
        assertThat(this.gpioSimulationControl.getPwmOutputState(pwmPort)).isEqualTo(0.0);
    }

    @Test
    void stop_zeroesAllPorts()
    {
        int forwardPort = 27;
        int backwardPort = 28;
        int pwmPort = 29;

        String id = defineMotor(forwardPort, backwardPort, pwmPort);
        specOf("motor_move").call()
                            .apply(null, Map.of("id", id, "direction", "BACKWARDS", "speed", 0.6));
        assertThat(this.gpioSimulationControl.getDigitalOutputState(backwardPort)).isTrue();

        CallToolResult result = specOf("motor_stop").call()
                                                    .apply(null, Map.of("id", id));

        assertThat(result.isError()).isFalse();
        assertThat(this.gpioSimulationControl.getDigitalOutputState(backwardPort)).isFalse();
        assertThat(this.gpioSimulationControl.getDigitalOutputState(forwardPort)).isFalse();
        assertThat(this.gpioSimulationControl.getPwmOutputState(pwmPort)).isEqualTo(0.0);
    }

    private String defineMotor(int forwardPort, int backwardPort, int pwmPort)
    {
        SyncToolSpecification defineSpec = specOf("motor_define");
        CallToolResult defineResult = defineSpec.call()
                                                .apply(null, Map.of("forwardPort", forwardPort, "backwardPort", backwardPort, "pwmPort", pwmPort));
        assertThat(defineResult.isError()).isFalse();
        String id = textOf(defineResult);
        // motor_define returns the id JSON-quoted ("id-value") - strip the quotes.
        return id.substring(1, id.length() - 1);
    }

    private SyncToolSpecification specOf(String toolName)
    {
        List<SyncToolSpecification> specs = this.motorTools.specs();
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
