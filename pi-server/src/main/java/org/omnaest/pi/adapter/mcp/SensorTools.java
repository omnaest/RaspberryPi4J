package org.omnaest.pi.adapter.mcp;

import java.util.List;
import java.util.Map;

import org.omnaest.pi.client.domain.flow.FlowSensorDefinition;
import org.omnaest.pi.client.domain.pressure.MS5837Model;
import org.omnaest.pi.service.rotary.RotaryEncoderService;
import org.omnaest.pi.service.sensor.flow.FlowSensorService;
import org.omnaest.pi.service.sensor.gyro.GyroscopeService;
import org.omnaest.pi.service.sensor.pressure.PressureSensorMS5837Service;
import org.springframework.stereotype.Component;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;

/**
 * Builds and exposes MCP tool specifications for the remaining {@code sensor} bounded contexts (rotary encoder,
 * gyroscope, flow, MS5837 pressure) — mirrors {@code DataController}'s {@code RotaryEncoderService},
 * {@code GyroscopeService}, {@code FlowSensorService}, and {@code PressureSensorMS5837Service} endpoints.
 * {@code LPS28PressureService} is deliberately excluded (see plan-59).
 *
 * <p>Tools owned by this group:
 * <ul>
 * <li>{@code rotary_encoder_read}</li>
 * <li>{@code gyroscope_read_orientation}</li>
 * <li>{@code flow_sensor_enable}</li>
 * <li>{@code flow_sensor_read_rate}</li>
 * <li>{@code flow_sensor_disable}</li>
 * <li>{@code pressure_ms5837_enable}</li>
 * <li>{@code pressure_ms5837_read}</li>
 * <li>{@code pressure_ms5837_disable}</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class SensorTools
{

    private final RotaryEncoderService        rotaryEncoderService;
    private final GyroscopeService            gyroscopeService;
    private final FlowSensorService           flowSensorService;
    private final PressureSensorMS5837Service pressureSensorMS5837Service;
    private final McpToolSupport              support;

    /**
     * Returns the {@link McpServerFeatures.SyncToolSpecification}s for all sensor tools. Called once at server
     * startup by {@link McpServerConfig}.
     */
    public List<McpServerFeatures.SyncToolSpecification> specs()
    {
        return List.of(
                       rotaryEncoderReadSpec(),
                       gyroscopeReadOrientationSpec(),
                       flowSensorEnableSpec(),
                       flowSensorReadRateSpec(),
                       flowSensorDisableSpec(),
                       pressureMs5837EnableSpec(),
                       pressureMs5837ReadSpec(),
                       pressureMs5837DisableSpec());
    }

    // ---- handlers ----

    private McpServerFeatures.SyncToolSpecification rotaryEncoderReadSpec()
    {
        return new McpServerFeatures.SyncToolSpecification(
                                                           rotaryEncoderReadTool(),
                                                           (exchange, args) -> support.handle("rotary_encoder_read", () ->
                                                           {
                                                               int clkPort = McpArgs.requiredInt(args, "clkPort");
                                                               int dtPort = McpArgs.requiredInt(args, "dtPort");
                                                               int swPort = McpArgs.requiredInt(args, "swPort");
                                                               return rotaryEncoderService.getRotaryEncoderByPin(clkPort, dtPort, swPort)
                                                                                          .getAsLong();
                                                           }));
    }

    private McpServerFeatures.SyncToolSpecification gyroscopeReadOrientationSpec()
    {
        return new McpServerFeatures.SyncToolSpecification(
                                                           gyroscopeReadOrientationTool(),
                                                           (exchange, args) -> support.handle("gyroscope_read_orientation", () ->
                                                           {
                                                               // bus mirrors DataController.getGyroscopeOrientation's @RequestParam shape exactly - it is
                                                               // accepted but never passed to GyroscopeService (same dead parameter as the REST endpoint).
                                                               Integer numberOfSamplings = McpArgs.optInt(args, "numberOfSamplings");
                                                               return gyroscopeService.getOrientation(numberOfSamplings != null ? numberOfSamplings : 1);
                                                           }));
    }

    private McpServerFeatures.SyncToolSpecification flowSensorEnableSpec()
    {
        return new McpServerFeatures.SyncToolSpecification(
                                                           flowSensorEnableTool(),
                                                           (exchange, args) -> support.handle("flow_sensor_enable", () ->
                                                           {
                                                               int port = McpArgs.requiredInt(args, "port");
                                                               Double flowRateCoefficient = McpArgs.optDouble(args, "flowRateCoefficient");
                                                               FlowSensorDefinition definition = FlowSensorDefinition.builder()
                                                                                                                     .flowRateCoefficient(flowRateCoefficient != null ? flowRateCoefficient : 0.0)
                                                                                                                     .build();
                                                               flowSensorService.enableFlowSensor(port, definition);
                                                               return Map.of("port", port);
                                                           }));
    }

    private McpServerFeatures.SyncToolSpecification flowSensorReadRateSpec()
    {
        return new McpServerFeatures.SyncToolSpecification(
                                                           flowSensorReadRateTool(),
                                                           (exchange, args) -> support.handle("flow_sensor_read_rate", () ->
                                                           {
                                                               int port = McpArgs.requiredInt(args, "port");
                                                               return flowSensorService.getFlowRate(port);
                                                           }));
    }

    private McpServerFeatures.SyncToolSpecification flowSensorDisableSpec()
    {
        return new McpServerFeatures.SyncToolSpecification(
                                                           flowSensorDisableTool(),
                                                           (exchange, args) -> support.handle("flow_sensor_disable", () ->
                                                           {
                                                               int port = McpArgs.requiredInt(args, "port");
                                                               flowSensorService.disableFlowSensor(port);
                                                               return Map.of("port", port);
                                                           }));
    }

    private McpServerFeatures.SyncToolSpecification pressureMs5837EnableSpec()
    {
        return new McpServerFeatures.SyncToolSpecification(
                                                           pressureMs5837EnableTool(),
                                                           (exchange, args) -> support.handle("pressure_ms5837_enable", () ->
                                                           {
                                                               MS5837Model model = McpArgs.requiredEnum(args, "model", MS5837Model.class);
                                                               return pressureSensorMS5837Service.enableSensorAndGetSensorId(model);
                                                           }));
    }

    private McpServerFeatures.SyncToolSpecification pressureMs5837ReadSpec()
    {
        return new McpServerFeatures.SyncToolSpecification(
                                                           pressureMs5837ReadTool(),
                                                           (exchange, args) -> support.handle("pressure_ms5837_read", () ->
                                                           {
                                                               String sensorId = McpArgs.string(args, "sensorId");
                                                               return pressureSensorMS5837Service.readSensor(sensorId)
                                                                                                 .orElse(null);
                                                           }));
    }

    private McpServerFeatures.SyncToolSpecification pressureMs5837DisableSpec()
    {
        return new McpServerFeatures.SyncToolSpecification(
                                                           pressureMs5837DisableTool(),
                                                           (exchange, args) -> support.handle("pressure_ms5837_disable", () ->
                                                           {
                                                               String sensorId = McpArgs.string(args, "sensorId");
                                                               pressureSensorMS5837Service.disableSensor(sensorId);
                                                               return Map.of("sensorId", sensorId);
                                                           }));
    }

    // ---- tool schemas ----

    private static McpSchema.Tool rotaryEncoderReadTool()
    {
        return McpSchema.Tool.builder()
                             .name("rotary_encoder_read")
                             .description("Returns the current value of the rotary encoder wired to the given clk/dt/sw digital input ports.")
                             .inputSchema(new McpSchema.JsonSchema(
                                                                   "object",
                                                                   Map.of(
                                                                          "clkPort", Map.of("type", "integer"),
                                                                          "dtPort", Map.of("type", "integer"),
                                                                          "swPort", Map.of("type", "integer")),
                                                                   List.of("clkPort", "dtPort", "swPort"),
                                                                   null, null, null))
                             .build();
    }

    private static McpSchema.Tool gyroscopeReadOrientationTool()
    {
        return McpSchema.Tool.builder()
                             .name("gyroscope_read_orientation")
                             .description("Returns the averaged x/y/z gyroscope orientation over the given number of samplings "
                                          + "(default: 1). Optional bus is accepted for parity with the REST endpoint but has no effect "
                                          + "(the underlying service always uses I2C bus 1).")
                             .inputSchema(new McpSchema.JsonSchema(
                                                                   "object",
                                                                   Map.of(
                                                                          "bus", Map.of("type", "integer", "description", "Accepted but unused (default: 1)"),
                                                                          "numberOfSamplings", Map.of("type", "integer", "description", "Optional (default: 1)")),
                                                                   null, null, null, null))
                             .build();
    }

    private static McpSchema.Tool flowSensorEnableTool()
    {
        return McpSchema.Tool.builder()
                             .name("flow_sensor_enable")
                             .description("Enables the flow sensor on the given digital input port. Optional flowRateCoefficient "
                                          + "(pulses per liter/minute); a value <= 0.001 (including omitted) falls back to the sensor's "
                                          + "own default of 7.5.")
                             .inputSchema(new McpSchema.JsonSchema(
                                                                   "object",
                                                                   Map.of(
                                                                          "port", Map.of("type", "integer"),
                                                                          "flowRateCoefficient", Map.of("type", "number", "description", "Optional (default: 7.5)")),
                                                                   List.of("port"),
                                                                   null, null, null))
                             .build();
    }

    private static McpSchema.Tool flowSensorReadRateTool()
    {
        return McpSchema.Tool.builder()
                             .name("flow_sensor_read_rate")
                             .description("Returns the flow rate in L/min for the flow sensor on the given digital input port. "
                                          + "Returns NaN if the sensor was not previously enabled via flow_sensor_enable.")
                             .inputSchema(new McpSchema.JsonSchema(
                                                                   "object",
                                                                   Map.of("port", Map.of("type", "integer")),
                                                                   List.of("port"),
                                                                   null, null, null))
                             .build();
    }

    private static McpSchema.Tool flowSensorDisableTool()
    {
        return McpSchema.Tool.builder()
                             .name("flow_sensor_disable")
                             .description("Disables the flow sensor on the given digital input port.")
                             .inputSchema(new McpSchema.JsonSchema(
                                                                   "object",
                                                                   Map.of("port", Map.of("type", "integer")),
                                                                   List.of("port"),
                                                                   null, null, null))
                             .build();
    }

    private static McpSchema.Tool pressureMs5837EnableTool()
    {
        return McpSchema.Tool.builder()
                             .name("pressure_ms5837_enable")
                             .description("Enables an MS5837 pressure/temperature sensor of the given model on I2C bus 1 and returns "
                                          + "its generated sensor id.")
                             .inputSchema(new McpSchema.JsonSchema(
                                                                   "object",
                                                                   Map.of("model", Map.of("type", "string", "description", "One of: MS5837_02BA, MS5837_30BA")),
                                                                   List.of("model"),
                                                                   null, null, null))
                             .build();
    }

    private static McpSchema.Tool pressureMs5837ReadTool()
    {
        return McpSchema.Tool.builder()
                             .name("pressure_ms5837_read")
                             .description("Reads absolute/relative pressure and temperature from a previously enabled MS5837 sensor. "
                                          + "Returns null if the sensorId is unknown.")
                             .inputSchema(new McpSchema.JsonSchema(
                                                                   "object",
                                                                   Map.of("sensorId", Map.of("type", "string")),
                                                                   List.of("sensorId"),
                                                                   null, null, null))
                             .build();
    }

    private static McpSchema.Tool pressureMs5837DisableTool()
    {
        return McpSchema.Tool.builder()
                             .name("pressure_ms5837_disable")
                             .description("Disables (removes) a previously enabled MS5837 sensor by its sensorId.")
                             .inputSchema(new McpSchema.JsonSchema(
                                                                   "object",
                                                                   Map.of("sensorId", Map.of("type", "string")),
                                                                   List.of("sensorId"),
                                                                   null, null, null))
                             .build();
    }
}
