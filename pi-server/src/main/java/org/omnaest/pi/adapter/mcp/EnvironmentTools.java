package org.omnaest.pi.adapter.mcp;

import java.util.List;
import java.util.Map;

import org.omnaest.pi.service.EnvironmentService;
import org.springframework.stereotype.Component;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;

/**
 * Builds and exposes MCP tool specifications for the {@code environment} bounded context — mirrors
 * {@code DataController}'s {@code EnvironmentService} endpoint.
 *
 * <p>Tools owned by this group:
 * <ul>
 * <li>{@code environment_bmp180_read}</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class EnvironmentTools
{

    private final EnvironmentService environmentService;
    private final McpToolSupport     support;

    /**
     * Returns the {@link McpServerFeatures.SyncToolSpecification}s for all environment tools. Called once at server
     * startup by {@link McpServerConfig}.
     */
    public List<McpServerFeatures.SyncToolSpecification> specs()
    {
        return List.of(environmentBmp180ReadSpec());
    }

    // ---- handlers ----

    private McpServerFeatures.SyncToolSpecification environmentBmp180ReadSpec()
    {
        // .get() on the Optional chain is intentional: mirrors DataController.getBMP180TemperatureAndPressure()
        // exactly, so a missing sensor/measurement throws consistently with today's REST behavior, surfacing here
        // as an MCP isError() result via McpToolSupport.handle.
        return new McpServerFeatures.SyncToolSpecification(
                                                           environmentBmp180ReadTool(),
                                                           (exchange, args) -> support.handle("environment_bmp180_read",
                                                                                              () -> environmentService.getOrCreateBMP180SensorInstance()
                                                                                                                      .flatMap(sensor -> sensor.measure())
                                                                                                                      .get()));
    }

    // ---- tool schemas ----

    private static McpSchema.Tool environmentBmp180ReadTool()
    {
        return McpSchema.Tool.builder()
                             .name("environment_bmp180_read")
                             .description("Reads altitude, pressure, and temperature from the BMP180 sensor. "
                                          + "Throws (surfaced as an MCP error result) if the sensor/measurement is unavailable.")
                             .inputSchema(new McpSchema.JsonSchema(
                                                                   "object",
                                                                   Map.of(),
                                                                   null, null, null, null))
                             .build();
    }
}
