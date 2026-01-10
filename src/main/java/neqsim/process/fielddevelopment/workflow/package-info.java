/**
 * Field development workflow orchestration.
 *
 * <p>
 * This package provides unified workflow orchestration for field development studies, integrating
 * PVT, reservoir, well, and process simulations with economics. The framework supports the complete
 * field lifecycle from discovery through operations.
 * </p>
 *
 * <h2>TPG4230 Course Topic Coverage</h2>
 * <p>
 * This package is designed to support education aligned with NTNU's TPG4230 course "Underground
 * reservoirs fluid production and injection":
 * </p>
 * <ul>
 * <li><b>Field Lifecycle Management</b> - Discovery → Feasibility → Concept → FEED →
 * Operations</li>
 * <li><b>PVT Characterization</b> - EOS selection and tuning</li>
 * <li><b>Reservoir Material Balance</b> - Tank models with injection</li>
 * <li><b>Well Performance (IPR/VLP)</b> - Nodal analysis</li>
 * <li><b>Production Network Optimization</b> - Multi-well gathering</li>
 * <li><b>Economic Evaluation</b> - NPV, IRR, country-specific taxes</li>
 * <li><b>Flow Assurance Screening</b> - Hydrates, wax, corrosion</li>
 * </ul>
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
 * <li><b>SCREENING</b> - Analog-based correlations, ±50% accuracy, portfolio screening</li>
 * <li><b>CONCEPTUAL</b> - EOS fluid, IPR/VLP models, ±30% accuracy, concept selection</li>
 * <li><b>DETAILED</b> - Tuned EOS, full process, Monte Carlo, ±20% accuracy, FEED basis</li>
 * </ul>
 *
 * @see neqsim.process.fielddevelopment.workflow.FieldDevelopmentWorkflow
 * @see neqsim.process.fielddevelopment.network.NetworkSolver
 * @see neqsim.process.fielddevelopment.reservoir.InjectionStrategy
 */
package neqsim.process.fielddevelopment.workflow;
