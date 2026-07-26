package org.omnaest.pi.adapter.mcp;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Centralises the try/catch error mapping and JSON serialisation for every MCP tool handler.
 *
 * <p>The {@link #handle(String, ThrowingSupplier)} method:
 * <ol>
 * <li>Invokes {@code body.get()} inside a try block.</li>
 * <li>Serialises the returned object to JSON and wraps it in a success {@link McpSchema.CallToolResult}.</li>
 * <li>On any exception, logs {@code log.error("<toolName> failed", e)} and returns an error result.</li>
 * </ol>
 *
 * <p>Ported near-verbatim from {@code ClaudeMemoryServer}'s {@code McpToolSupport} (package rename only).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpToolSupport
{

    private final ObjectMapper objectMapper;

    /**
     * Executes {@code body}, serialises the result to JSON, and returns a success {@link McpSchema.CallToolResult}.
     * Maps any thrown exception to an error result with a {@code log.error} entry.
     *
     * @param toolName the tool name used in the log message (e.g. {@code "gpio_digital_input_read"})
     * @param body     the supplier producing the result to serialise; may throw any Exception
     */
    public McpSchema.CallToolResult handle(String toolName, ThrowingSupplier<?> body)
    {
        try
        {
            Object result = body.get();
            return successResult(this.objectMapper.writeValueAsString(result));
        }
        catch (Exception e)
        {
            log.error("{} failed", toolName, e);
            return errorResult(e.getMessage());
        }
    }

    /**
     * A {@link java.util.function.Supplier} variant that may throw a checked exception.
     */
    @FunctionalInterface
    public interface ThrowingSupplier<T>
    {
        T get() throws Exception;
    }

    // ---- private helpers ----

    private static McpSchema.CallToolResult successResult(String json)
    {
        return McpSchema.CallToolResult.builder()
                                       .content(List.of(new McpSchema.TextContent(json)))
                                       .isError(false)
                                       .build();
    }

    private static McpSchema.CallToolResult errorResult(String message)
    {
        return McpSchema.CallToolResult.builder()
                                       .content(List.of(new McpSchema.TextContent(message != null ? message : "Unknown error")))
                                       .isError(true)
                                       .build();
    }
}
