/**
 * Process synthesis and candidate-ranking utilities for process research workflows.
 *
 * <p>
 * The package generates candidate {@link neqsim.process.processmodel.ProcessSystem} definitions
 * from feed, product, reaction, and unit-operation specifications, then evaluates and ranks the
 * candidates with NeqSim's existing process simulation and optimization stack. It combines
 * P-graph-style path enumeration, feasibility pruning, rigorous NeqSim simulation, heat-integration
 * metrics, multi-objective scoring, robustness hooks, dominance marking, and external
 * superstructure export for GDP/MINLP tools.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
package neqsim.process.research;
