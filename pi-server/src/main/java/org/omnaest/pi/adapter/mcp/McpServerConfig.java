package org.omnaest.pi.adapter.mcp;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcStreamableServerTransportProvider;

/**
 * Wires the MCP (Model Context Protocol) sync server over the WebMVC streamable-HTTP transport.
 *
 * <p>This class is registration-only: it collects {@link McpServerFeatures.SyncToolSpecification}s from each
 * hardware-context tool-group bean and registers them with the server. All tool logic lives in the tool groups:
 *
 * <ul>
 * <li>{@link GpioTools} — 8 tools</li>
 * <li>{@link MotorTools} — 3 tools</li>
 * <li>{@link ServoTools} — 8 tools</li>
 * <li>{@link CameraTools} — 1 tool</li>
 * <li>{@link EnvironmentTools} — 1 tool</li>
 * <li>{@link CompassTools} — 1 tool</li>
 * <li>{@link I2CTools} — 2 tools</li>
 * <li>{@link UltrasonicTools} — 2 tools</li>
 * <li>{@link WeightTools} — 1 tool</li>
 * <li>{@link SensorTools} — 8 tools</li>
 * </ul>
 *
 * <p>Total registered tools: 35 — a 1:1 mirror of every {@code DataController} hardware endpoint (the generic
 * reflection-based {@code /interaction} endpoint is deliberately excluded, see plan-59).
 *
 * <p>Ported near-verbatim from {@code ClaudeMemoryServer}'s {@code McpServerConfig} (package rename only).
 */
@Lazy(false)
@Configuration
public class McpServerConfig
{

    // ---- Transport ----

    @Bean
    public WebMvcStreamableServerTransportProvider mcpTransportProvider()
    {
        return new WebMvcStreamableServerTransportProvider.Builder()
                                                                    .mcpEndpoint("/mcp")
                                                                    .build();
    }

    @Bean
    public RouterFunction<ServerResponse> mcpRouterFunction(WebMvcStreamableServerTransportProvider transportProvider)
    {
        return transportProvider.getRouterFunction();
    }

    // ---- MCP Server ----

    @Bean
    public McpSyncServer mcpSyncServer(WebMvcStreamableServerTransportProvider transportProvider, GpioTools gpioTools, MotorTools motorTools, ServoTools servoTools, CameraTools cameraTools, EnvironmentTools environmentTools, CompassTools compassTools, I2CTools i2cTools, UltrasonicTools ultrasonicTools, WeightTools weightTools, SensorTools sensorTools)
    {
        List<McpServerFeatures.SyncToolSpecification> allSpecs = new ArrayList<>();
        allSpecs.addAll(gpioTools.specs());
        allSpecs.addAll(motorTools.specs());
        allSpecs.addAll(servoTools.specs());
        allSpecs.addAll(cameraTools.specs());
        allSpecs.addAll(environmentTools.specs());
        allSpecs.addAll(compassTools.specs());
        allSpecs.addAll(i2cTools.specs());
        allSpecs.addAll(ultrasonicTools.specs());
        allSpecs.addAll(weightTools.specs());
        allSpecs.addAll(sensorTools.specs());

        return McpServer.sync(transportProvider)
                        .serverInfo("pi-server", "0.0.1-SNAPSHOT")
                        .tools(allSpecs)
                        .build();
    }
}
