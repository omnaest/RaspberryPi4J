package org.omnaest.pi.adapter.mcp;

import java.util.List;
import java.util.Map;

import org.omnaest.pi.service.servo.ServoDriverService;
import org.springframework.stereotype.Component;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;

/**
 * Builds and exposes MCP tool specifications for the {@code servo} bounded context — mirrors {@code DataController}'s
 * {@code ServoDriverService} endpoints.
 *
 * <p>Tools owned by this group:
 * <ul>
 * <li>{@code servo_set_angle}</li>
 * <li>{@code servo_set_speed}</li>
 * <li>{@code servo_pin_enable}</li>
 * <li>{@code servo_pin_set_pwm}</li>
 * <li>{@code servo_pin_disable}</li>
 * <li>{@code servo_set_duration_maximum}</li>
 * <li>{@code servo_set_duration_minimum}</li>
 * <li>{@code servo_set_duration_neutral}</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ServoTools
{

    private final ServoDriverService servoDriverService;
    private final McpToolSupport     support;

    /**
     * Returns the {@link McpServerFeatures.SyncToolSpecification}s for all servo tools. Called once at server
     * startup by {@link McpServerConfig}.
     */
    public List<McpServerFeatures.SyncToolSpecification> specs()
    {
        return List.of(
                       servoSetAngleSpec(),
                       servoSetSpeedSpec(),
                       servoPinEnableSpec(),
                       servoPinSetPwmSpec(),
                       servoPinDisableSpec(),
                       servoSetDurationMaximumSpec(),
                       servoSetDurationMinimumSpec(),
                       servoSetDurationNeutralSpec());
    }

    // ---- handlers ----

    private McpServerFeatures.SyncToolSpecification servoSetAngleSpec()
    {
        return new McpServerFeatures.SyncToolSpecification(
                                                           servoSetAngleTool(),
                                                           (exchange, args) -> support.handle("servo_set_angle", () ->
                                                           {
                                                               int index = McpArgs.requiredInt(args, "index");
                                                               int angle = McpArgs.requiredInt(args, "angle");
                                                               servoDriverService.servo(index)
                                                                                 .applyAngle(angle);
                                                               return Map.of("index", index, "angle", angle);
                                                           }));
    }

    private McpServerFeatures.SyncToolSpecification servoSetSpeedSpec()
    {
        return new McpServerFeatures.SyncToolSpecification(
                                                           servoSetSpeedTool(),
                                                           (exchange, args) -> support.handle("servo_set_speed", () ->
                                                           {
                                                               int index = McpArgs.requiredInt(args, "index");
                                                               double speed = McpArgs.requiredDouble(args, "speed");
                                                               servoDriverService.servo(index)
                                                                                 .applySpeed(speed);
                                                               return Map.of("index", index, "speed", speed);
                                                           }));
    }

    private McpServerFeatures.SyncToolSpecification servoPinEnableSpec()
    {
        return new McpServerFeatures.SyncToolSpecification(
                                                           servoPinEnableTool(),
                                                           (exchange, args) -> support.handle("servo_pin_enable", () ->
                                                           {
                                                               int index = McpArgs.requiredInt(args, "index");
                                                               servoDriverService.pwmPin(index)
                                                                                 .enable();
                                                               return Map.of("index", index, "enabled", true);
                                                           }));
    }

    private McpServerFeatures.SyncToolSpecification servoPinSetPwmSpec()
    {
        return new McpServerFeatures.SyncToolSpecification(
                                                           servoPinSetPwmTool(),
                                                           (exchange, args) -> support.handle("servo_pin_set_pwm", () ->
                                                           {
                                                               int index = McpArgs.requiredInt(args, "index");
                                                               double value = McpArgs.requiredDouble(args, "value");
                                                               servoDriverService.pwmPin(index)
                                                                                 .setPwm(value);
                                                               return Map.of("index", index, "value", value);
                                                           }));
    }

    private McpServerFeatures.SyncToolSpecification servoPinDisableSpec()
    {
        return new McpServerFeatures.SyncToolSpecification(
                                                           servoPinDisableTool(),
                                                           (exchange, args) -> support.handle("servo_pin_disable", () ->
                                                           {
                                                               int index = McpArgs.requiredInt(args, "index");
                                                               servoDriverService.pwmPin(index)
                                                                                 .disable();
                                                               return Map.of("index", index, "enabled", false);
                                                           }));
    }

    private McpServerFeatures.SyncToolSpecification servoSetDurationMaximumSpec()
    {
        return new McpServerFeatures.SyncToolSpecification(
                                                           servoSetDurationMaximumTool(),
                                                           (exchange, args) -> support.handle("servo_set_duration_maximum", () ->
                                                           {
                                                               int index = McpArgs.requiredInt(args, "index");
                                                               int max = McpArgs.requiredInt(args, "max");
                                                               servoDriverService.servo(index)
                                                                                 .applyDurationMaximum(max);
                                                               return Map.of("index", index, "max", max);
                                                           }));
    }

    private McpServerFeatures.SyncToolSpecification servoSetDurationMinimumSpec()
    {
        return new McpServerFeatures.SyncToolSpecification(
                                                           servoSetDurationMinimumTool(),
                                                           (exchange, args) -> support.handle("servo_set_duration_minimum", () ->
                                                           {
                                                               int index = McpArgs.requiredInt(args, "index");
                                                               int min = McpArgs.requiredInt(args, "min");
                                                               servoDriverService.servo(index)
                                                                                 .applyDurationMinimum(min);
                                                               return Map.of("index", index, "min", min);
                                                           }));
    }

    private McpServerFeatures.SyncToolSpecification servoSetDurationNeutralSpec()
    {
        return new McpServerFeatures.SyncToolSpecification(
                                                           servoSetDurationNeutralTool(),
                                                           (exchange, args) -> support.handle("servo_set_duration_neutral", () ->
                                                           {
                                                               int index = McpArgs.requiredInt(args, "index");
                                                               int neutral = McpArgs.requiredInt(args, "neutral");
                                                               servoDriverService.servo(index)
                                                                                 .applyDurationNeutral(neutral);
                                                               return Map.of("index", index, "neutral", neutral);
                                                           }));
    }

    // ---- tool schemas ----

    private static McpSchema.Tool servoSetAngleTool()
    {
        return McpSchema.Tool.builder()
                             .name("servo_set_angle")
                             .description("Sets the angle (0-360 degrees) of the servo at the given channel index. "
                                          + "WARNING: this causes immediate physical actuation on the connected hardware with no confirmation step.")
                             .inputSchema(new McpSchema.JsonSchema(
                                                                   "object",
                                                                   Map.of(
                                                                          "index", Map.of("type", "integer"),
                                                                          "angle", Map.of("type", "integer")),
                                                                   List.of("index", "angle"),
                                                                   null, null, null))
                             .build();
    }

    private static McpSchema.Tool servoSetSpeedTool()
    {
        return McpSchema.Tool.builder()
                             .name("servo_set_speed")
                             .description("Sets the speed (-1.0 to 1.0, relative to the configured neutral position) of the "
                                          + "continuous-rotation servo at the given channel index. "
                                          + "WARNING: this causes immediate physical actuation on the connected hardware with no confirmation step.")
                             .inputSchema(new McpSchema.JsonSchema(
                                                                   "object",
                                                                   Map.of(
                                                                          "index", Map.of("type", "integer"),
                                                                          "speed", Map.of("type", "number")),
                                                                   List.of("index", "speed"),
                                                                   null, null, null))
                             .build();
    }

    private static McpSchema.Tool servoPinEnableTool()
    {
        return McpSchema.Tool.builder()
                             .name("servo_pin_enable")
                             .description("Sets the PWM pin at the given channel index to always-on.")
                             .inputSchema(new McpSchema.JsonSchema(
                                                                   "object",
                                                                   Map.of("index", Map.of("type", "integer")),
                                                                   List.of("index"),
                                                                   null, null, null))
                             .build();
    }

    private static McpSchema.Tool servoPinSetPwmTool()
    {
        return McpSchema.Tool.builder()
                             .name("servo_pin_set_pwm")
                             .description("Sets the PWM duty cycle (0.0-1.0) of the pin at the given channel index.")
                             .inputSchema(new McpSchema.JsonSchema(
                                                                   "object",
                                                                   Map.of(
                                                                          "index", Map.of("type", "integer"),
                                                                          "value", Map.of("type", "number")),
                                                                   List.of("index", "value"),
                                                                   null, null, null))
                             .build();
    }

    private static McpSchema.Tool servoPinDisableTool()
    {
        return McpSchema.Tool.builder()
                             .name("servo_pin_disable")
                             .description("Sets the PWM pin at the given channel index to always-off.")
                             .inputSchema(new McpSchema.JsonSchema(
                                                                   "object",
                                                                   Map.of("index", Map.of("type", "integer")),
                                                                   List.of("index"),
                                                                   null, null, null))
                             .build();
    }

    private static McpSchema.Tool servoSetDurationMaximumTool()
    {
        return McpSchema.Tool.builder()
                             .name("servo_set_duration_maximum")
                             .description("Sets the maximum pulse duration (microseconds) used to compute angle/speed pulses for the "
                                          + "servo at the given channel index.")
                             .inputSchema(new McpSchema.JsonSchema(
                                                                   "object",
                                                                   Map.of(
                                                                          "index", Map.of("type", "integer"),
                                                                          "max", Map.of("type", "integer")),
                                                                   List.of("index", "max"),
                                                                   null, null, null))
                             .build();
    }

    private static McpSchema.Tool servoSetDurationMinimumTool()
    {
        return McpSchema.Tool.builder()
                             .name("servo_set_duration_minimum")
                             .description("Sets the minimum pulse duration (microseconds) used to compute angle/speed pulses for the "
                                          + "servo at the given channel index.")
                             .inputSchema(new McpSchema.JsonSchema(
                                                                   "object",
                                                                   Map.of(
                                                                          "index", Map.of("type", "integer"),
                                                                          "min", Map.of("type", "integer")),
                                                                   List.of("index", "min"),
                                                                   null, null, null))
                             .build();
    }

    private static McpSchema.Tool servoSetDurationNeutralTool()
    {
        return McpSchema.Tool.builder()
                             .name("servo_set_duration_neutral")
                             .description("Sets the neutral pulse duration (microseconds) used as the speed=0 reference for the "
                                          + "servo at the given channel index.")
                             .inputSchema(new McpSchema.JsonSchema(
                                                                   "object",
                                                                   Map.of(
                                                                          "index", Map.of("type", "integer"),
                                                                          "neutral", Map.of("type", "integer")),
                                                                   List.of("index", "neutral"),
                                                                   null, null, null))
                             .build();
    }
}
