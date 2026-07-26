package org.omnaest.pi.adapter.mcp;

import java.util.List;
import java.util.Map;

import org.omnaest.pi.service.sensor.weight.WeightService;
import org.omnaest.pi.service.sensor.weight.WeightService.Gain;
import org.omnaest.pi.service.sensor.weight.WeightService.HX711PortConfiguration;
import org.springframework.stereotype.Component;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;

/**
 * Builds and exposes MCP tool specifications for the {@code weight} bounded context — mirrors
 * {@code DataController}'s {@code WeightService} HX711 endpoint. The Nau7802 path is deliberately excluded (see
 * plan-59).
 *
 * <p>Tools owned by this group:
 * <ul>
 * <li>{@code weight_read_hx711}</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class WeightTools
{

    private final WeightService  weightService;
    private final McpToolSupport support;

    /**
     * Returns the {@link McpServerFeatures.SyncToolSpecification}s for all weight tools. Called once at server
     * startup by {@link McpServerConfig}.
     */
    public List<McpServerFeatures.SyncToolSpecification> specs()
    {
        return List.of(weightReadHx711Spec());
    }

    // ---- handlers ----

    private McpServerFeatures.SyncToolSpecification weightReadHx711Spec()
    {
        return new McpServerFeatures.SyncToolSpecification(
                                                           weightReadHx711Tool(),
                                                           (exchange, args) -> support.handle("weight_read_hx711", () ->
                                                           {
                                                               int dataPort = McpArgs.requiredInt(args, "dataPort");
                                                               int clockPort = McpArgs.requiredInt(args, "clockPort");
                                                               String gainName = McpArgs.string(args, "gain");

                                                               HX711PortConfiguration.HX711PortConfigurationBuilder builder = HX711PortConfiguration.builder()
                                                                                                                                                    .dataPort(dataPort)
                                                                                                                                                    .clockPort(clockPort);
                                                               // gain is optional - if omitted, leave the builder field untouched so the domain's own
                                                               // @Builder.Default (Gain.CHANNEL_A_HIGH) applies, rather than overriding it with null.
                                                               if (gainName != null)
                                                               {
                                                                   builder.gain(Gain.valueOf(gainName));
                                                               }

                                                               return weightService.readValueFromHX711(builder.build());
                                                           }));
    }

    // ---- tool schemas ----

    private static McpSchema.Tool weightReadHx711Tool()
    {
        return McpSchema.Tool.builder()
                             .name("weight_read_hx711")
                             .description("Reads the raw (uncalibrated) value of the HX711 load-cell amplifier connected on the given "
                                          + "data/clock ports. Optional gain (one of CHANNEL_A_HIGH, CHANNEL_A_NORMAL, CHANNEL_B_LOW; "
                                          + "default: CHANNEL_A_HIGH).")
                             .inputSchema(new McpSchema.JsonSchema(
                                                                   "object",
                                                                   Map.of(
                                                                          "dataPort", Map.of("type", "integer"),
                                                                          "clockPort", Map.of("type", "integer"),
                                                                          "gain", Map.of("type", "string",
                                                                                         "description",
                                                                                         "Optional, one of CHANNEL_A_HIGH, CHANNEL_A_NORMAL, CHANNEL_B_LOW (default: CHANNEL_A_HIGH)")),
                                                                   List.of("dataPort", "clockPort"),
                                                                   null, null, null))
                             .build();
    }
}
