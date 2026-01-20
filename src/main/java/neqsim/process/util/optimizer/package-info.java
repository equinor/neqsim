/**
 * Process optimization engine and constraint evaluation utilities.
 *
 * <p>
 * This package provides a comprehensive framework for process optimization. For a detailed overview
 * of when to use which optimizer, see the documentation at
 * {@code docs/process/optimization/OPTIMIZATION_OVERVIEW.md}.
 * </p>
 *
 * <p>
 * <strong>Two Main Optimizers - When to Use Which:</strong>
 * </p>
 *
 * <table border="1">
 * <caption>Optimizer Selection Guide</caption>
 * <tr>
 * <th>Scenario</th>
 * <th>Use</th>
 * </tr>
 * <tr>
 * <td>Find max throughput at given pressures</td>
 * <td>{@link neqsim.process.util.optimizer.ProcessOptimizationEngine}</td>
 * </tr>
 * <tr>
 * <td>Find bottleneck equipment</td>
 * <td>{@link neqsim.process.util.optimizer.ProcessOptimizationEngine}</td>
 * </tr>
 * <tr>
 * <td>Generate Eclipse VFP tables</td>
 * <td>{@link neqsim.process.util.optimizer.ProcessOptimizationEngine}</td>
 * </tr>
 * <tr>
 * <td>Custom objective (e.g., minimize cost)</td>
 * <td>{@link neqsim.process.util.optimizer.ProductionOptimizer}</td>
 * </tr>
 * <tr>
 * <td>Multi-variable optimization</td>
 * <td>{@link neqsim.process.util.optimizer.ProductionOptimizer}</td>
 * </tr>
 * <tr>
 * <td>Pareto multi-objective</td>
 * <td>{@link neqsim.process.util.optimizer.ProductionOptimizer}</td>
 * </tr>
 * <tr>
 * <td>Model calibration to data</td>
 * <td>{@link neqsim.process.calibration.BatchParameterEstimator}</td>
 * </tr>
 * </table>
 *
 * <p>
 * <strong>Core Classes:</strong>
 * </p>
 * <ul>
 * <li>{@link neqsim.process.util.optimizer.ProcessOptimizationEngine} - Unified API for throughput
 * optimization with equipment constraint evaluation. Best for "find max flow at given
 * pressures".</li>
 * <li>{@link neqsim.process.util.optimizer.ProductionOptimizer} - General-purpose optimizer
 * supporting custom objectives, multi-variable, and Pareto multi-objective optimization.</li>
 * <li>{@link neqsim.process.util.optimizer.ProcessConstraintEvaluator} - Composite constraint
 * evaluation with caching and sensitivity analysis</li>
 * <li>{@link neqsim.process.util.optimizer.ProcessSimulationEvaluator} - Interface for external
 * optimizers (Python/SciPy integration)</li>
 * <li>{@link neqsim.process.util.optimizer.OptimizationResultBase} - Unified result base class with
 * status tracking, timing, and constraint violation details</li>
 * <li>{@link neqsim.process.util.optimizer.EclipseVFPExporter} - Export VFP tables for Eclipse
 * reservoir simulation</li>
 * </ul>
 *
 * <p>
 * <strong>Key Features:</strong>
 * </p>
 * <ul>
 * <li><strong>Multiple Search Algorithms:</strong> Binary search, golden section, gradient descent,
 * Armijo-Wolfe line search, BFGS, Nelder-Mead, and Particle Swarm</li>
 * <li><strong>Constraint Caching:</strong> TTL-based caching for repeated constraint
 * evaluations</li>
 * <li><strong>Sensitivity Analysis:</strong> Calculate flow sensitivities and shadow prices</li>
 * <li><strong>Bottleneck Detection:</strong> Identify limiting equipment and constraints</li>
 * <li><strong>Multi-Objective Pareto:</strong> Generate Pareto fronts for conflicting
 * objectives</li>
 * <li><strong>Parallel Evaluation:</strong> Evaluate scenarios in parallel</li>
 * <li><strong>FlowRateOptimizer Integration:</strong> Seamless integration with production
 * optimizer</li>
 * <li><strong>Adjuster Integration:</strong> Coordinate optimization with existing Adjuster
 * units</li>
 * <li><strong>Eclipse Export:</strong> Generate VFPPROD, VFPINJ, and VFPEXP tables</li>
 * <li><strong>Fluent API:</strong> Builder pattern for convenient configuration</li>
 * </ul>
 *
 * <p>
 * <strong>Quick Start - ProcessOptimizationEngine (throughput-focused):</strong>
 * </p>
 * 
 * <pre>
 * // Find maximum throughput at given pressures
 * ProcessOptimizationEngine engine = new ProcessOptimizationEngine(process);
 * engine.setSearchAlgorithm(SearchAlgorithm.GOLDEN_SECTION);
 *
 * OptimizationResult result = engine.findMaximumThroughput(50.0, // inlet pressure (bara)
 *     10.0, // outlet pressure (bara)
 *     1000.0, // min flow
 *     100000.0 // max flow
 * );
 *
 * System.out.println("Max flow: " + result.getOptimalValue() + " kg/hr");
 * System.out.println("Bottleneck: " + result.getBottleneck());
 * </pre>
 *
 * <p>
 * <strong>Quick Start - ProductionOptimizer (general-purpose):</strong>
 * </p>
 * 
 * <pre>
 * // Optimize with custom objective
 * ProductionOptimizer optimizer = new ProductionOptimizer();
 * OptimizationConfig config = new OptimizationConfig(50000.0, 200000.0).tolerance(100.0)
 *     .searchMode(SearchMode.GOLDEN_SECTION_SCORE);
 *
 * // Define objective (minimize compressor power)
 * List&lt;OptimizationObjective&gt; objectives = Arrays.asList(new OptimizationObjective("power",
 *     proc -&gt; ((Compressor) proc.getUnit("comp")).getPower("kW"), 1.0, ObjectiveType.MINIMIZE));
 *
 * OptimizationResult result = optimizer.optimize(process, feed, config, objectives, null);
 * </pre>
 *
 * <p>
 * <strong>Pareto Multi-Objective Optimization:</strong>
 * </p>
 * 
 * <pre>
 * // Trade off throughput vs power
 * List&lt;OptimizationObjective&gt; objectives = Arrays.asList(
 *     new OptimizationObjective("throughput", proc -&gt; proc.getUnit("outlet").getFlowRate("kg/hr"),
 *         1.0, ObjectiveType.MAXIMIZE),
 *     new OptimizationObjective("power",
 *         proc -&gt; ((Compressor) proc.getUnit("comp")).getPower("kW"), 1.0,
 *         ObjectiveType.MINIMIZE));
 *
 * OptimizationConfig config = new OptimizationConfig(50000.0, 200000.0).paretoGridSize(20);
 *
 * ParetoResult pareto = optimizer.optimizePareto(process, feed, config, objectives);
 *
 * for (ParetoPoint pt : pareto.getPoints()) {
 *   System.out.println("Flow: " + pt.getObjectives().get("throughput") + ", Power: "
 *       + pt.getObjectives().get("power"));
 * }
 * </pre>
 *
 * <p>
 * <strong>Constraint Evaluation with Caching:</strong>
 * </p>
 * 
 * <pre>
 * ProcessConstraintEvaluator evaluator = new ProcessConstraintEvaluator(process);
 * evaluator.setCacheTTLMillis(30000); // 30 second cache
 *
 * // Evaluate constraints (uses cache if valid)
 * ProcessConstraintEvaluator.ConstraintEvaluationResult result = evaluator.evaluate();
 *
 * System.out.println("Utilization: " + result.getOverallUtilization() * 100 + "%");
 * System.out.println("Bottleneck: " + result.getBottleneckEquipment());
 *
 * // Calculate flow sensitivities
 * Map&lt;String, Double&gt; sensitivities = evaluator.calculateFlowSensitivities(5000.0, "kg/hr");
 *
 * // Estimate max feasible flow
 * double maxFlow = evaluator.estimateMaxFlow(5000.0, "kg/hr");
 * </pre>
 *
 * @see neqsim.process.util.optimizer.ProcessOptimizationEngine
 * @see neqsim.process.util.optimizer.ProductionOptimizer
 * @see neqsim.process.util.optimizer.ProcessConstraintEvaluator
 * @see neqsim.process.util.optimizer.OptimizationResultBase
 * @see neqsim.process.util.optimizer.EclipseVFPExporter
 * @see neqsim.process.util.optimizer.FlowRateOptimizer
 * @author NeqSim Development Team
 * @version 1.8
 */
package neqsim.process.util.optimizer;
