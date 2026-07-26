package org.omnaest.pi.adapter.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.omnaest.pi.Application;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * Verifies that the Spring application context starts (under the {@code simulation} profile, see
 * {@code SimulationProfileSmokeTest}) with the MCP server and transport {@link RouterFunction} beans properly wired,
 * and that exactly the 35 planned tools are registered (plan-59).
 */
@SpringBootTest(classes = Application.class, properties = "spring.profiles.active=simulation")
class McpContextLoadTest
{

    @Autowired
    private McpSyncServer                  mcpSyncServer;

    @Autowired
    private RouterFunction<ServerResponse> mcpRouterFunction;

    @Test
    void contextLoads_allMcpBeansPresent()
    {
        assertThat(this.mcpSyncServer).isNotNull();
        assertThat(this.mcpRouterFunction).isNotNull();
    }

    @Test
    void exactlyThirtyFiveToolsRegistered_noInteractionTool()
    {
        List<McpSchema.Tool> tools = this.mcpSyncServer.listTools();
        assertThat(tools).hasSize(35);

        List<String> names = tools.stream()
                                  .map(McpSchema.Tool::name)
                                  .toList();
        assertThat(names).doesNotContain("interaction");
        assertThat(names).containsExactlyInAnyOrder(
                                                    "gpio_digital_output_enable",
                                                    "gpio_digital_input_read",
                                                    "gpio_digital_output_write",
                                                    "gpio_pwm_enable",
                                                    "gpio_port_enable",
                                                    "gpio_pwm_write",
                                                    "gpio_expander_pcf8574_write",
                                                    "gpio_expander_pcf8574_read",
                                                    "motor_define",
                                                    "motor_move",
                                                    "motor_stop",
                                                    "servo_set_angle",
                                                    "servo_set_speed",
                                                    "servo_pin_enable",
                                                    "servo_pin_set_pwm",
                                                    "servo_pin_disable",
                                                    "servo_set_duration_maximum",
                                                    "servo_set_duration_minimum",
                                                    "servo_set_duration_neutral",
                                                    "camera_snapshot",
                                                    "environment_bmp180_read",
                                                    "compass_read_angle",
                                                    "i2c_read_byte",
                                                    "i2c_write_byte",
                                                    "ultrasonic_init",
                                                    "ultrasonic_read_distance",
                                                    "weight_read_hx711",
                                                    "rotary_encoder_read",
                                                    "gyroscope_read_orientation",
                                                    "flow_sensor_enable",
                                                    "flow_sensor_read_rate",
                                                    "flow_sensor_disable",
                                                    "pressure_ms5837_enable",
                                                    "pressure_ms5837_read",
                                                    "pressure_ms5837_disable");
    }

    @Test
    void safetyCriticalToolsCarryImmediateActuationWarning()
    {
        List<McpSchema.Tool> tools = this.mcpSyncServer.listTools();
        List<String> safetyCriticalToolNames = List.of(
                                                       "motor_move",
                                                       "servo_set_angle",
                                                       "servo_set_speed",
                                                       "gpio_digital_output_write",
                                                       "gpio_pwm_write");

        for (String toolName : safetyCriticalToolNames)
        {
            McpSchema.Tool tool = tools.stream()
                                       .filter(candidate -> toolName.equals(candidate.name()))
                                       .findFirst()
                                       .orElseThrow(() -> new AssertionError("Tool not found: " + toolName));
            assertThat(tool.description()).as("safety warning on %s", toolName)
                                          .containsIgnoringCase("immediate")
                                          .containsIgnoringCase("physical")
                                          .containsIgnoringCase("no confirmation");
        }
    }
}
