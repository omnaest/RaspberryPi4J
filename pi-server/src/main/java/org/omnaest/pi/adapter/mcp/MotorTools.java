package org.omnaest.pi.adapter.mcp;

import java.util.List;
import java.util.Map;

import org.omnaest.pi.client.domain.motor.L298nMotorControlDefinition;
import org.omnaest.pi.client.domain.motor.MotorMovementDirection;
import org.omnaest.pi.service.motor.MotorControlService;
import org.omnaest.pi.service.motor.MotorControlService.MotorControl;
import org.springframework.stereotype.Component;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;

/**
 * Builds and exposes MCP tool specifications for the {@code motor} bounded context — mirrors {@code DataController}'s
 * {@code MotorControlService} endpoints.
 *
 * <p>Tools owned by this group:
 * <ul>
 * <li>{@code motor_define}</li>
 * <li>{@code motor_move}</li>
 * <li>{@code motor_stop}</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class MotorTools
{

    private final MotorControlService motorControlService;
    private final McpToolSupport      support;

    /**
     * Returns the {@link McpServerFeatures.SyncToolSpecification}s for all motor tools. Called once at server
     * startup by {@link McpServerConfig}.
     */
    public List<McpServerFeatures.SyncToolSpecification> specs()
    {
        return List.of(
                       motorDefineSpec(),
                       motorMoveSpec(),
                       motorStopSpec());
    }

    // ---- handlers ----

    private McpServerFeatures.SyncToolSpecification motorDefineSpec()
    {
        return new McpServerFeatures.SyncToolSpecification(
                                                           motorDefineTool(),
                                                           (exchange, args) -> support.handle("motor_define", () ->
                                                           {
                                                               int forwardPort = McpArgs.requiredInt(args, "forwardPort");
                                                               int backwardPort = McpArgs.requiredInt(args, "backwardPort");
                                                               int pwmPort = McpArgs.requiredInt(args, "pwmPort");
                                                               L298nMotorControlDefinition definition = L298nMotorControlDefinition.builder()
                                                                                                                                   .forwardPort(forwardPort)
                                                                                                                                   .backwardPort(backwardPort)
                                                                                                                                   .pwmPort(pwmPort)
                                                                                                                                   .build();
                                                               return motorControlService.defineMotorControl(definition)
                                                                                         .getId();
                                                           }));
    }

    private McpServerFeatures.SyncToolSpecification motorMoveSpec()
    {
        return new McpServerFeatures.SyncToolSpecification(
                                                           motorMoveTool(),
                                                           (exchange, args) -> support.handle("motor_move", () ->
                                                           {
                                                               String id = McpArgs.string(args, "id");
                                                               MotorMovementDirection direction = McpArgs.requiredEnum(args, "direction", MotorMovementDirection.class);
                                                               double speed = McpArgs.requiredDouble(args, "speed");
                                                               motorControlService.getMotorControl(id)
                                                                                  .ifPresent(motor ->
                                                                                  {
                                                                                      if (speed > 0.001)
                                                                                      {
                                                                                          motor.move(direction, speed);
                                                                                      }
                                                                                      else
                                                                                      {
                                                                                          motor.stop();
                                                                                      }
                                                                                  });
                                                               return Map.of("id", id, "direction", direction.name(), "speed", speed);
                                                           }));
    }

    private McpServerFeatures.SyncToolSpecification motorStopSpec()
    {
        return new McpServerFeatures.SyncToolSpecification(
                                                           motorStopTool(),
                                                           (exchange, args) -> support.handle("motor_stop", () ->
                                                           {
                                                               String id = McpArgs.string(args, "id");
                                                               motorControlService.getMotorControl(id)
                                                                                  .ifPresent(MotorControl::stop);
                                                               return Map.of("id", id);
                                                           }));
    }

    // ---- tool schemas ----

    private static McpSchema.Tool motorDefineTool()
    {
        return McpSchema.Tool.builder()
                             .name("motor_define")
                             .description("Defines (or returns the existing id for) an L298n motor driver control using the given "
                                          + "forward/backward digital output ports and PWM port. Returns the motor control id.")
                             .inputSchema(new McpSchema.JsonSchema(
                                                                   "object",
                                                                   Map.of(
                                                                          "forwardPort", Map.of("type", "integer"),
                                                                          "backwardPort", Map.of("type", "integer"),
                                                                          "pwmPort", Map.of("type", "integer")),
                                                                   List.of("forwardPort", "backwardPort", "pwmPort"),
                                                                   null, null, null))
                             .build();
    }

    private static McpSchema.Tool motorMoveTool()
    {
        return McpSchema.Tool.builder()
                             .name("motor_move")
                             .description("Moves a previously defined motor in the given direction at the given speed (0.0-1.0). "
                                          + "A speed of 0.001 or less stops the motor instead. Unknown motor ids are silently ignored. "
                                          + "WARNING: this causes immediate physical actuation on the connected hardware with no confirmation step.")
                             .inputSchema(new McpSchema.JsonSchema(
                                                                   "object",
                                                                   Map.of(
                                                                          "id", Map.of("type", "string"),
                                                                          "direction", Map.of("type", "string",
                                                                                              "description", "One of: FORWARDS, BACKWARDS"),
                                                                          "speed", Map.of("type", "number",
                                                                                          "description", "0.0-1.0; values <= 0.001 stop the motor")),
                                                                   List.of("id", "direction", "speed"),
                                                                   null, null, null))
                             .build();
    }

    private static McpSchema.Tool motorStopTool()
    {
        return McpSchema.Tool.builder()
                             .name("motor_stop")
                             .description("Stops a previously defined motor. Unknown motor ids are silently ignored.")
                             .inputSchema(new McpSchema.JsonSchema(
                                                                   "object",
                                                                   Map.of("id", Map.of("type", "string")),
                                                                   List.of("id"),
                                                                   null, null, null))
                             .build();
    }
}
