package org.omnaest.pi.adapter.mcp;

import java.util.List;
import java.util.Map;

import org.omnaest.pi.service.compass.CompassService;
import org.omnaest.pi.service.compass.CompassService.Module;
import org.springframework.stereotype.Component;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;

/**
 * Builds and exposes MCP tool specifications for the {@code compass} bounded context — mirrors
 * {@code DataController}'s {@code CompassService} endpoint.
 *
 * <p>Tools owned by this group:
 * <ul>
 * <li>{@code compass_read_angle}</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class CompassTools
{

    private final CompassService compassService;
    private final McpToolSupport support;

    /**
     * Returns the {@link McpServerFeatures.SyncToolSpecification}s for all compass tools. Called once at server
     * startup by {@link McpServerConfig}.
     */
    public List<McpServerFeatures.SyncToolSpecification> specs()
    {
        return List.of(compassReadAngleSpec());
    }

    // ---- handlers ----

    private McpServerFeatures.SyncToolSpecification compassReadAngleSpec()
    {
        return new McpServerFeatures.SyncToolSpecification(
                                                           compassReadAngleTool(),
                                                           (exchange, args) -> support.handle("compass_read_angle", () ->
                                                           {
                                                               Integer bus = McpArgs.optInt(args, "bus");
                                                               String moduleName = McpArgs.string(args, "module");
                                                               Module module = moduleName != null ? Module.valueOf(moduleName) : Module.QMC5883L;
                                                               return compassService.onBus(bus != null ? bus : 1)
                                                                                    .withModule(module)
                                                                                    .getNorthDirectionAngle();
                                                           }));
    }

    // ---- tool schemas ----

    private static McpSchema.Tool compassReadAngleTool()
    {
        return McpSchema.Tool.builder()
                             .name("compass_read_angle")
                             .description("Returns the angle towards north (clockwise, in degrees) from the GY-271 compass module. "
                                          + "Optional bus (default: 1) and module (default: QMC5883L; one of QMC5883L, HMC5883).")
                             .inputSchema(new McpSchema.JsonSchema(
                                                                   "object",
                                                                   Map.of(
                                                                          "bus", Map.of("type", "integer", "description", "Optional I2C bus number (default: 1)"),
                                                                          "module", Map.of("type", "string",
                                                                                           "description", "Optional compass module, one of QMC5883L, HMC5883 (default: QMC5883L)")),
                                                                   null, null, null, null))
                             .build();
    }
}
