package org.omnaest.pi.adapter.mcp;

import java.util.List;
import java.util.Map;

import org.omnaest.pi.domain.UltrasonicSensorConfiguration;
import org.omnaest.pi.service.UltrasonicService;
import org.springframework.stereotype.Component;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;

/**
 * Builds and exposes MCP tool specifications for the {@code ultrasonic} bounded context — mirrors
 * {@code DataController}'s {@code UltrasonicService} endpoints.
 *
 * <p>Tools owned by this group:
 * <ul>
 * <li>{@code ultrasonic_init}</li>
 * <li>{@code ultrasonic_read_distance}</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class UltrasonicTools
{

    private final UltrasonicService ultrasonicService;
    private final McpToolSupport    support;

    /**
     * Returns the {@link McpServerFeatures.SyncToolSpecification}s for all ultrasonic tools. Called once at server
     * startup by {@link McpServerConfig}.
     */
    public List<McpServerFeatures.SyncToolSpecification> specs()
    {
        return List.of(
                       ultrasonicInitSpec(),
                       ultrasonicReadDistanceSpec());
    }

    // ---- handlers ----

    private McpServerFeatures.SyncToolSpecification ultrasonicInitSpec()
    {
        return new McpServerFeatures.SyncToolSpecification(
                                                           ultrasonicInitTool(),
                                                           (exchange, args) -> support.handle("ultrasonic_init", () ->
                                                           {
                                                               int index = McpArgs.requiredInt(args, "index");
                                                               int echoPort = McpArgs.requiredInt(args, "echoPort");
                                                               int triggerPort = McpArgs.requiredInt(args, "triggerPort");
                                                               int pingTimeout = McpArgs.requiredInt(args, "pingTimeout");
                                                               int signalTimeout = McpArgs.requiredInt(args, "signalTimeout");
                                                               int[] signals = McpArgs.intArray(args, "signals");

                                                               UltrasonicSensorConfiguration configuration = new UltrasonicSensorConfiguration();
                                                               configuration.setEchoPort(echoPort);
                                                               configuration.setTriggerPort(triggerPort);
                                                               configuration.setPingTimeout(pingTimeout);
                                                               configuration.setSignalTimeout(signalTimeout);
                                                               // signals is optional - if omitted, UltrasonicSensorConfiguration's own field default
                                                               // {5, 20, 0} applies, so it is only overridden when explicitly supplied.
                                                               if (signals != null)
                                                               {
                                                                   configuration.setSignals(signals);
                                                               }

                                                               ultrasonicService.getInstance(index)
                                                                                .init(configuration);
                                                               return Map.of("index", index, "echoPort", echoPort, "triggerPort", triggerPort);
                                                           }));
    }

    private McpServerFeatures.SyncToolSpecification ultrasonicReadDistanceSpec()
    {
        return new McpServerFeatures.SyncToolSpecification(
                                                           ultrasonicReadDistanceTool(),
                                                           (exchange, args) -> support.handle("ultrasonic_read_distance", () ->
                                                           {
                                                               int index = McpArgs.requiredInt(args, "index");
                                                               return ultrasonicService.getInstance(index)
                                                                                       .getDistance();
                                                           }));
    }

    // ---- tool schemas ----

    private static McpSchema.Tool ultrasonicInitTool()
    {
        return McpSchema.Tool.builder()
                             .name("ultrasonic_init")
                             .description("Initializes the ultrasonic distance sensor at the given index with the given echo/trigger "
                                          + "ports and timeouts. Idempotent - a sensor already initialized is left unchanged. "
                                          + "Optional signals (array of pulse durations in microseconds; default: [5, 20, 0]).")
                             .inputSchema(new McpSchema.JsonSchema(
                                                                   "object",
                                                                   Map.of(
                                                                          "index", Map.of("type", "integer"),
                                                                          "echoPort", Map.of("type", "integer"),
                                                                          "triggerPort", Map.of("type", "integer"),
                                                                          "pingTimeout", Map.of("type", "integer", "description", "Timeout in nanoseconds"),
                                                                          "signalTimeout", Map.of("type", "integer", "description", "Timeout in nanoseconds"),
                                                                          "signals", Map.of("type", "array", "items", Map.of("type", "integer"),
                                                                                            "description", "Optional trigger pulse durations in microseconds (default: [5, 20, 0])")),
                                                                   List.of("index", "echoPort", "triggerPort", "pingTimeout", "signalTimeout"),
                                                                   null, null, null))
                             .build();
    }

    private static McpSchema.Tool ultrasonicReadDistanceTool()
    {
        return McpSchema.Tool.builder()
                             .name("ultrasonic_read_distance")
                             .description("Returns the distance in millimeters measured by the ultrasonic sensor at the given index. "
                                          + "Throws if the sensor was not previously initialized via ultrasonic_init.")
                             .inputSchema(new McpSchema.JsonSchema(
                                                                   "object",
                                                                   Map.of("index", Map.of("type", "integer")),
                                                                   List.of("index"),
                                                                   null, null, null))
                             .build();
    }
}
