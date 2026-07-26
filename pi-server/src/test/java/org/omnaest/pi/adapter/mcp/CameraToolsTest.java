package org.omnaest.pi.adapter.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.omnaest.pi.Application;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

/**
 * Seam test for {@link CameraTools}: drives {@code camera_snapshot}'s {@code call()} handler directly against the
 * REAL {@code CameraService} bean ({@code CameraServicePI}), which is unconditionally unimplemented
 * ({@code NotImplementedException}) on every profile - the same behavior REST callers of {@code POST /snapshot} see
 * today. This doubles as one of the required error-path tests proving the {@code McpToolSupport.handle} try/catch ->
 * {@code isError()} wiring.
 */
@SpringBootTest(classes = Application.class, properties = "spring.profiles.active=simulation")
class CameraToolsTest
{

    @Autowired
    private CameraTools cameraTools;

    @Test
    void snapshot_surfacesUnimplementedCameraAsMcpError()
    {
        SyncToolSpecification spec = specOf("camera_snapshot");

        CallToolResult result = spec.call()
                                    .apply(null, Map.of());

        assertThat(result.isError()).isTrue();
    }

    private SyncToolSpecification specOf(String toolName)
    {
        List<SyncToolSpecification> specs = this.cameraTools.specs();
        return specs.stream()
                    .filter(spec -> toolName.equals(spec.tool()
                                                        .name()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Tool spec not found: " + toolName));
    }
}
