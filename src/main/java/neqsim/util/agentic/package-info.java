/**
 * Agentic engineering infrastructure for NeqSim.
 *
 * <p>
 * Provides session tracking, feedback collection, and result validation for AI agent workflows that
 * solve engineering tasks using NeqSim's thermodynamic and process simulation API.
 * </p>
 *
 * <h2>Key Classes:</h2>
 * <ul>
 * <li>{@link neqsim.util.agentic.AgentSession} — Tracks a single agent workflow session from scope
 * through reporting, recording phases, tool invocations, and simulation runs</li>
 * <li>{@link neqsim.util.agentic.AgentFeedbackCollector} — Aggregates metrics across sessions for
 * continuous improvement: success rates, failure patterns, API gaps</li>
 * <li>{@link neqsim.util.agentic.TaskResultValidator} — Validates task results.json files against
 * the expected schema for the task-solving workflow</li>
 * </ul>
 *
 * <h2>Architecture:</h2>
 *
 * <pre>
 * Agent Session Lifecycle:
 *
 *   SCOPE --&gt; RESEARCH --&gt; ANALYSIS --&gt; VALIDATION --&gt; REPORTING
 *     |          |            |             |              |
 *     +----------+------------+-------------+--------------+
 *                         |
 *                   AgentSession (tracks phases, tools, simulations)
 *                         |
 *                   AgentFeedbackCollector (aggregates across sessions)
 *                         |
 *                   TaskResultValidator (validates output schema)
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 */
package neqsim.util.agentic;
