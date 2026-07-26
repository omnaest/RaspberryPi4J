package org.omnaest.pi.adapter.mcp;

import java.util.List;
import java.util.Map;

import org.omnaest.pi.client.domain.gpio.expander.GpioPortExpanderAddress;
import org.omnaest.pi.client.domain.gpio.expander.GpioPortExpanderPort;
import org.omnaest.pi.service.gpio.GPIOService;
import org.omnaest.pi.service.gpio.expander.GpioPortExpanderPCF8574Service;
import org.springframework.stereotype.Component;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;

/**
 * Builds and exposes MCP tool specifications for the {@code gpio} bounded context — mirrors {@code DataController}'s
 * {@code GPIOService} and {@code GpioPortExpanderPCF8574Service} endpoints.
 *
 * <p>Tools owned by this group:
 * <ul>
 * <li>{@code gpio_digital_output_enable}</li>
 * <li>{@code gpio_digital_input_read}</li>
 * <li>{@code gpio_digital_output_write}</li>
 * <li>{@code gpio_pwm_enable}</li>
 * <li>{@code gpio_port_enable}</li>
 * <li>{@code gpio_pwm_write}</li>
 * <li>{@code gpio_expander_pcf8574_write}</li>
 * <li>{@code gpio_expander_pcf8574_read}</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class GpioTools
{

    private final GPIOService                    gpioService;
    private final GpioPortExpanderPCF8574Service gpioPortExpanderPCF8574Service;
    private final McpToolSupport                 support;

    /**
     * Returns the {@link McpServerFeatures.SyncToolSpecification}s for all gpio tools. Called once at server
     * startup by {@link McpServerConfig}.
     */
    public List<McpServerFeatures.SyncToolSpecification> specs()
    {
        return List.of(
                       gpioDigitalOutputEnableSpec(),
                       gpioDigitalInputReadSpec(),
                       gpioDigitalOutputWriteSpec(),
                       gpioPwmEnableSpec(),
                       gpioPortEnableSpec(),
                       gpioPwmWriteSpec(),
                       gpioExpanderPcf8574WriteSpec(),
                       gpioExpanderPcf8574ReadSpec());
    }

    // ---- handlers ----

    private McpServerFeatures.SyncToolSpecification gpioDigitalOutputEnableSpec()
    {
        return new McpServerFeatures.SyncToolSpecification(
                                                           gpioDigitalOutputEnableTool(),
                                                           (exchange, args) -> support.handle("gpio_digital_output_enable", () ->
                                                           {
                                                               int port = McpArgs.requiredInt(args, "port");
                                                               gpioService.enableGPIOPortForDigitalOutput(port);
                                                               return Map.of("port", port, "enabled", true);
                                                           }));
    }

    private McpServerFeatures.SyncToolSpecification gpioDigitalInputReadSpec()
    {
        return new McpServerFeatures.SyncToolSpecification(
                                                           gpioDigitalInputReadTool(),
                                                           (exchange, args) -> support.handle("gpio_digital_input_read", () ->
                                                           {
                                                               int port = McpArgs.requiredInt(args, "port");
                                                               return gpioService.getDigitalInputGPIOPort(port)
                                                                                 .enable()
                                                                                 .getState();
                                                           }));
    }

    private McpServerFeatures.SyncToolSpecification gpioDigitalOutputWriteSpec()
    {
        return new McpServerFeatures.SyncToolSpecification(
                                                           gpioDigitalOutputWriteTool(),
                                                           (exchange, args) -> support.handle("gpio_digital_output_write", () ->
                                                           {
                                                               int port = McpArgs.requiredInt(args, "port");
                                                               boolean state = McpArgs.requiredBoolean(args, "state");
                                                               gpioService.getDigitalOutputGPIOPort(port)
                                                                          .enable()
                                                                          .setState(state);
                                                               return Map.of("port", port, "state", state);
                                                           }));
    }

    private McpServerFeatures.SyncToolSpecification gpioPwmEnableSpec()
    {
        return new McpServerFeatures.SyncToolSpecification(
                                                           gpioPwmEnableTool(),
                                                           (exchange, args) -> support.handle("gpio_pwm_enable", () ->
                                                           {
                                                               int port = McpArgs.requiredInt(args, "port");
                                                               gpioService.enableGPIOPortForPWM(port);
                                                               return Map.of("port", port, "enabled", true);
                                                           }));
    }

    private McpServerFeatures.SyncToolSpecification gpioPortEnableSpec()
    {
        return new McpServerFeatures.SyncToolSpecification(
                                                           gpioPortEnableTool(),
                                                           (exchange, args) -> support.handle("gpio_port_enable", () ->
                                                           {
                                                               int port = McpArgs.requiredInt(args, "port");
                                                               boolean active = McpArgs.requiredBoolean(args, "active");
                                                               gpioService.enableGPIOPort(port, active);
                                                               return Map.of("port", port, "active", active);
                                                           }));
    }

    private McpServerFeatures.SyncToolSpecification gpioPwmWriteSpec()
    {
        return new McpServerFeatures.SyncToolSpecification(
                                                           gpioPwmWriteTool(),
                                                           (exchange, args) -> support.handle("gpio_pwm_write", () ->
                                                           {
                                                               int port = McpArgs.requiredInt(args, "port");
                                                               int value = McpArgs.requiredInt(args, "value");
                                                               gpioService.setGPIOPortPWMValue(port, value);
                                                               return Map.of("port", port, "value", value);
                                                           }));
    }

    private McpServerFeatures.SyncToolSpecification gpioExpanderPcf8574WriteSpec()
    {
        return new McpServerFeatures.SyncToolSpecification(
                                                           gpioExpanderPcf8574WriteTool(),
                                                           (exchange, args) -> support.handle("gpio_expander_pcf8574_write", () ->
                                                           {
                                                               GpioPortExpanderAddress address = McpArgs.requiredEnum(args, "address", GpioPortExpanderAddress.class);
                                                               GpioPortExpanderPort port = McpArgs.requiredEnum(args, "port", GpioPortExpanderPort.class);
                                                               boolean value = McpArgs.requiredBoolean(args, "value");
                                                               gpioPortExpanderPCF8574Service.access(address)
                                                                                             .write(port, value);
                                                               return Map.of("address", address.name(), "port", port.name(), "value", value);
                                                           }));
    }

    private McpServerFeatures.SyncToolSpecification gpioExpanderPcf8574ReadSpec()
    {
        return new McpServerFeatures.SyncToolSpecification(
                                                           gpioExpanderPcf8574ReadTool(),
                                                           (exchange, args) -> support.handle("gpio_expander_pcf8574_read", () ->
                                                           {
                                                               GpioPortExpanderAddress address = McpArgs.requiredEnum(args, "address", GpioPortExpanderAddress.class);
                                                               GpioPortExpanderPort port = McpArgs.requiredEnum(args, "port", GpioPortExpanderPort.class);
                                                               return gpioPortExpanderPCF8574Service.access(address)
                                                                                                    .read(port);
                                                           }));
    }

    // ---- tool schemas ----

    private static McpSchema.Tool gpioDigitalOutputEnableTool()
    {
        return McpSchema.Tool.builder()
                             .name("gpio_digital_output_enable")
                             .description("Enables the given GPIO port for digital output use. Idempotent.")
                             .inputSchema(new McpSchema.JsonSchema(
                                                                   "object",
                                                                   Map.of("port", Map.of("type", "integer")),
                                                                   List.of("port"),
                                                                   null, null, null))
                             .build();
    }

    private static McpSchema.Tool gpioDigitalInputReadTool()
    {
        return McpSchema.Tool.builder()
                             .name("gpio_digital_input_read")
                             .description("Enables the given GPIO port for digital input use (idempotent) and returns its current "
                                          + "state: true for high signal, false for low signal.")
                             .inputSchema(new McpSchema.JsonSchema(
                                                                   "object",
                                                                   Map.of("port", Map.of("type", "integer")),
                                                                   List.of("port"),
                                                                   null, null, null))
                             .build();
    }

    private static McpSchema.Tool gpioDigitalOutputWriteTool()
    {
        return McpSchema.Tool.builder()
                             .name("gpio_digital_output_write")
                             .description("Enables the given GPIO port for digital output use and sets its output signal. "
                                          + "WARNING: this causes immediate physical actuation on the connected hardware with no confirmation step.")
                             .inputSchema(new McpSchema.JsonSchema(
                                                                   "object",
                                                                   Map.of(
                                                                          "port", Map.of("type", "integer"),
                                                                          "state", Map.of("type", "boolean",
                                                                                          "description", "true sets the output signal to high/active, false to low")),
                                                                   List.of("port", "state"),
                                                                   null, null, null))
                             .build();
    }

    private static McpSchema.Tool gpioPwmEnableTool()
    {
        return McpSchema.Tool.builder()
                             .name("gpio_pwm_enable")
                             .description("Enables the given GPIO port for PWM output use. Idempotent.")
                             .inputSchema(new McpSchema.JsonSchema(
                                                                   "object",
                                                                   Map.of("port", Map.of("type", "integer")),
                                                                   List.of("port"),
                                                                   null, null, null))
                             .build();
    }

    private static McpSchema.Tool gpioPortEnableTool()
    {
        return McpSchema.Tool.builder()
                             .name("gpio_port_enable")
                             .description("Enables or disables the given GPIO port for general active use.")
                             .inputSchema(new McpSchema.JsonSchema(
                                                                   "object",
                                                                   Map.of(
                                                                          "port", Map.of("type", "integer"),
                                                                          "active", Map.of("type", "boolean")),
                                                                   List.of("port", "active"),
                                                                   null, null, null))
                             .build();
    }

    private static McpSchema.Tool gpioPwmWriteTool()
    {
        return McpSchema.Tool.builder()
                             .name("gpio_pwm_write")
                             .description("Sets the PWM value (0-100) of the given GPIO port. "
                                          + "WARNING: this causes immediate physical actuation on the connected hardware with no confirmation step.")
                             .inputSchema(new McpSchema.JsonSchema(
                                                                   "object",
                                                                   Map.of(
                                                                          "port", Map.of("type", "integer"),
                                                                          "value", Map.of("type", "integer")),
                                                                   List.of("port", "value"),
                                                                   null, null, null))
                             .build();
    }

    private static McpSchema.Tool gpioExpanderPcf8574WriteTool()
    {
        return McpSchema.Tool.builder()
                             .name("gpio_expander_pcf8574_write")
                             .description("Writes a single port's output value on a PCF8574 I2C GPIO expander at the given address.")
                             .inputSchema(new McpSchema.JsonSchema(
                                                                   "object",
                                                                   Map.of(
                                                                          "address", Map.of("type", "string",
                                                                                            "description", "PCF8574 I2C address, one of: A20, A21, A22, A23, A24, A25, A26, A27"),
                                                                          "port", Map.of("type", "string",
                                                                                         "description", "Expander port, one of: P0, P1, P2, P3, P4, P5, P6, P7"),
                                                                          "value", Map.of("type", "boolean")),
                                                                   List.of("address", "port", "value"),
                                                                   null, null, null))
                             .build();
    }

    private static McpSchema.Tool gpioExpanderPcf8574ReadTool()
    {
        return McpSchema.Tool.builder()
                             .name("gpio_expander_pcf8574_read")
                             .description("Reads a single port's current state on a PCF8574 I2C GPIO expander at the given address.")
                             .inputSchema(new McpSchema.JsonSchema(
                                                                   "object",
                                                                   Map.of(
                                                                          "address", Map.of("type", "string",
                                                                                            "description", "PCF8574 I2C address, one of: A20, A21, A22, A23, A24, A25, A26, A27"),
                                                                          "port", Map.of("type", "string",
                                                                                         "description", "Expander port, one of: P0, P1, P2, P3, P4, P5, P6, P7")),
                                                                   List.of("address", "port"),
                                                                   null, null, null))
                             .build();
    }
}
