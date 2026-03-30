/**
 * Typed model classes for NeqSim MCP (Model Context Protocol) integration.
 *
 * <p>
 * This package provides Java POJOs for request and response models used by the MCP runner layer.
 * These complement the string-based {@code run(String json)} interface with typed alternatives for
 * direct Java consumers, and enable auto-generation of JSON schemas for MCP tool declarations.
 * </p>
 *
 * <h2>Core types:</h2>
 * <ul>
 * <li>{@link neqsim.mcp.model.ValueWithUnit} — numeric value with unit string</li>
 * <li>{@link neqsim.mcp.model.DiagnosticIssue} — validation/simulation issue</li>
 * <li>{@link neqsim.mcp.model.ApiEnvelope} — standard response envelope</li>
 * <li>{@link neqsim.mcp.model.FlashRequest} — typed flash calculation input</li>
 * <li>{@link neqsim.mcp.model.FlashResult} — typed flash calculation output</li>
 * </ul>
 */
package neqsim.mcp.model;
