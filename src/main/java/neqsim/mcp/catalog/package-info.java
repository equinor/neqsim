/**
 * Catalog classes for NeqSim MCP (Model Context Protocol) integration.
 *
 * <p>
 * Provides example JSON inputs and JSON schemas for all MCP tools. These are designed to be served
 * as MCP Resources (read-only, URI-addressable content) that language models can read to learn the
 * input/output format of each tool.
 * </p>
 *
 * <h2>MCP Resource URIs:</h2>
 * <ul>
 * <li>{@code neqsim://example/{category}/{name}} — example JSON inputs</li>
 * <li>{@code neqsim://schema/{toolName}/input} — JSON Schema for tool inputs</li>
 * <li>{@code neqsim://schema/{toolName}/output} — JSON Schema for tool outputs</li>
 * <li>{@code neqsim://catalog/examples} — listing of all examples</li>
 * <li>{@code neqsim://catalog/schemas} — listing of all schemas</li>
 * </ul>
 */
package neqsim.mcp.catalog;
