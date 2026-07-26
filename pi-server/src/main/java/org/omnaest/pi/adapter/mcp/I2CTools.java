package org.omnaest.pi.adapter.mcp;

import java.util.List;
import java.util.Map;

import org.omnaest.pi.service.i2c.I2CService;
import org.omnaest.pi.service.i2c.I2CService.ByteArray;
import org.springframework.stereotype.Component;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;

/**
 * Builds and exposes MCP tool specifications for the {@code i2c} bounded context — mirrors {@code DataController}'s
 * {@code I2CService} endpoints. Both the REST GET and PUT for {@code /i2c/...} map through {@code getI2CData}, so
 * this group exposes two tools.
 *
 * <p>Tools owned by this group:
 * <ul>
 * <li>{@code i2c_read_byte}</li>
 * <li>{@code i2c_write_byte}</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class I2CTools
{

    private final I2CService     i2cService;
    private final McpToolSupport support;

    /**
     * Returns the {@link McpServerFeatures.SyncToolSpecification}s for all i2c tools. Called once at server startup
     * by {@link McpServerConfig}.
     */
    public List<McpServerFeatures.SyncToolSpecification> specs()
    {
        return List.of(
                       i2cReadByteSpec(),
                       i2cWriteByteSpec());
    }

    // ---- handlers ----

    private McpServerFeatures.SyncToolSpecification i2cReadByteSpec()
    {
        return new McpServerFeatures.SyncToolSpecification(
                                                           i2cReadByteTool(),
                                                           (exchange, args) -> support.handle("i2c_read_byte", () ->
                                                           {
                                                               int bus = McpArgs.requiredInt(args, "bus");
                                                               Integer address = McpArgs.optInt(args, "address");
                                                               int localaddress = McpArgs.requiredInt(args, "localaddress");
                                                               int offset = McpArgs.requiredInt(args, "offset");
                                                               return i2cService.provision(bus)
                                                                                .flatMap(control -> control.connectTo(address != null ? address : 0))
                                                                                .flatMap(connector -> connector.read(localaddress, offset, 1))
                                                                                .flatMap(ByteArray::getFirstByte)
                                                                                .orElse(Byte.valueOf((byte) 0));
                                                           }));
    }

    private McpServerFeatures.SyncToolSpecification i2cWriteByteSpec()
    {
        return new McpServerFeatures.SyncToolSpecification(
                                                           i2cWriteByteTool(),
                                                           (exchange, args) -> support.handle("i2c_write_byte", () ->
                                                           {
                                                               int bus = McpArgs.requiredInt(args, "bus");
                                                               Integer address = McpArgs.optInt(args, "address");
                                                               int localaddress = McpArgs.requiredInt(args, "localaddress");
                                                               int offset = McpArgs.requiredInt(args, "offset");
                                                               byte value = (byte) McpArgs.requiredInt(args, "value");
                                                               i2cService.provision(bus)
                                                                         .flatMap(control -> control.connectTo(address != null ? address : 0))
                                                                         .ifPresent(connector -> connector.write(localaddress, value));
                                                               return Map.of("bus", bus, "localaddress", localaddress, "offset", offset, "value", value);
                                                           }));
    }

    // ---- tool schemas ----

    private static McpSchema.Tool i2cReadByteTool()
    {
        return McpSchema.Tool.builder()
                             .name("i2c_read_byte")
                             .description("Reads a single byte from the given I2C bus/address/local-address/offset. "
                                          + "Optional address (default: 0). Returns 0 if the bus/address/read is unavailable.")
                             .inputSchema(new McpSchema.JsonSchema(
                                                                   "object",
                                                                   Map.of(
                                                                          "bus", Map.of("type", "integer"),
                                                                          "address", Map.of("type", "integer", "description", "Optional device address (default: 0)"),
                                                                          "localaddress", Map.of("type", "integer"),
                                                                          "offset", Map.of("type", "integer")),
                                                                   List.of("bus", "localaddress", "offset"),
                                                                   null, null, null))
                             .build();
    }

    private static McpSchema.Tool i2cWriteByteTool()
    {
        return McpSchema.Tool.builder()
                             .name("i2c_write_byte")
                             .description("Writes a single byte to the given I2C bus/address/local-address/offset. "
                                          + "Optional address (default: 0). Silently no-ops if the bus/address is unavailable.")
                             .inputSchema(new McpSchema.JsonSchema(
                                                                   "object",
                                                                   Map.of(
                                                                          "bus", Map.of("type", "integer"),
                                                                          "address", Map.of("type", "integer", "description", "Optional device address (default: 0)"),
                                                                          "localaddress", Map.of("type", "integer"),
                                                                          "offset", Map.of("type", "integer"),
                                                                          "value", Map.of("type", "integer", "description", "Byte value to write (-128 to 127)")),
                                                                   List.of("bus", "localaddress", "offset", "value"),
                                                                   null, null, null))
                             .build();
    }
}
