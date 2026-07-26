package org.omnaest.pi.adapter.mcp;

import java.util.List;
import java.util.Map;

import org.omnaest.pi.domain.CameraSnapshotOptions;
import org.omnaest.pi.service.CameraService;
import org.springframework.stereotype.Component;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;

/**
 * Builds and exposes MCP tool specifications for the {@code camera} bounded context — mirrors {@code DataController}'s
 * {@code CameraService} endpoint.
 *
 * <p>Tools owned by this group:
 * <ul>
 * <li>{@code camera_snapshot}</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class CameraTools
{

    private final CameraService  cameraService;
    private final McpToolSupport support;

    /**
     * Returns the {@link McpServerFeatures.SyncToolSpecification}s for all camera tools. Called once at server
     * startup by {@link McpServerConfig}.
     */
    public List<McpServerFeatures.SyncToolSpecification> specs()
    {
        return List.of(cameraSnapshotSpec());
    }

    // ---- handlers ----

    private McpServerFeatures.SyncToolSpecification cameraSnapshotSpec()
    {
        return new McpServerFeatures.SyncToolSpecification(
                                                           cameraSnapshotTool(),
                                                           (exchange, args) -> support.handle("camera_snapshot", () -> cameraService.takeSnapshot(new CameraSnapshotOptions())));
    }

    // ---- tool schemas ----

    private static McpSchema.Tool cameraSnapshotTool()
    {
        return McpSchema.Tool.builder()
                             .name("camera_snapshot")
                             .description("Takes a camera snapshot. Returns the image data (base64-encoded) or an error message.")
                             .inputSchema(new McpSchema.JsonSchema(
                                                                   "object",
                                                                   Map.of(),
                                                                   null, null, null, null))
                             .build();
    }
}
