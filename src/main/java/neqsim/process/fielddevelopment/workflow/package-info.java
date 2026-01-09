/**
 * Field development workflow orchestration.
 *
 * <p>
 * This package provides unified workflow orchestration for field development studies, integrating
 * PVT, reservoir, well, and process simulations with economics.
 * </p>
 *
 * <h2>Main Classes</h2>
 * <ul>
 * <li>{@link neqsim.process.fielddevelopment.workflow.FieldDevelopmentWorkflow} - Main
 * orchestrator</li>
 * <li>{@link neqsim.process.fielddevelopment.workflow.WorkflowResult} - Result container</li>
 * </ul>
 *
 * <h2>Fidelity Levels</h2>
 * <ul>
 * <li><b>SCREENING</b> - Analog-based, ±50% accuracy</li>
 * <li><b>CONCEPTUAL</b> - EOS fluid, IPR/VLP, ±30% accuracy</li>
 * <li><b>DETAILED</b> - Tuned EOS, Monte Carlo, ±20% accuracy</li>
 * </ul>
 *
 * @see neqsim.process.fielddevelopment.workflow.FieldDevelopmentWorkflow
 */
package neqsim.process.fielddevelopment.workflow;
