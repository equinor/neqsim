/**
 * Process optimization engine and constraint evaluation utilities.
 *
 * <p>
 * This package provides a comprehensive framework for process optimization including:
 * </p>
 *
 * <h2>Core Classes</h2>
 * <ul>
 * <li>{@link neqsim.process.util.optimizer.ProcessOptimizationEngine} - Unified API for process
 * optimization with multiple search algorithms (binary, golden section, gradient descent)</li>
 * <li>{@link neqsim.process.util.optimizer.ProcessConstraintEvaluator} - Composite constraint
 * evaluation with caching and sensitivity analysis</li>
 * <li>{@link neqsim.process.util.optimizer.OptimizationResultBase} - Unified result base class with
 * status tracking, timing, and constraint violation details</li>
 * <li>{@link neqsim.process.util.optimizer.EclipseVFPExporter} - Export VFP tables for Eclipse
 * reservoir simulation</li>
 * </ul>
 *
 * <h2>Key Features</h2>
 * <ul>
 * <li><b>Multiple Search Algorithms:</b> Binary search, golden section, and gradient descent</li>
 * <li><b>Constraint Caching:</b> TTL-based caching for repeated constraint evaluations</li>
 * <li><b>Sensitivity Analysis:</b> Calculate flow sensitivities and shadow prices</li>
 * <li><b>Bottleneck Detection:</b> Identify limiting equipment and constraints</li>
 * <li><b>FlowRateOptimizer Integration:</b> Seamless integration with production optimizer</li>
 * <li><b>Eclipse Export:</b> Generate VFPPROD, VFPINJ, and VFPEXP tables</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * 
 * <pre>
 * // Create process system
 * ProcessSystem process = new ProcessSystem();
 * // ... add equipment ...
 * process.run();
 *
 * // Create optimization engine
 * ProcessOptimizationEngine engine = new ProcessOptimizationEngine(process);
 * engine.setSearchAlgorithm(ProcessOptimizationEngine.SearchAlgorithm.GRADIENT_DESCENT);
 *
 * // Find maximum throughput
 * ProcessOptimizationEngine.OptimizationResult result =
 *     engine.findMaximumThroughput(50.0, 10.0, 1000.0, 100000.0);
 *
 * System.out.println("Optimal flow: " + result.getOptimalValue() + " kg/hr");
 * System.out.println("Bottleneck: " + result.getBottleneck());
 *
 * // Analyze sensitivity
 * ProcessOptimizationEngine.SensitivityResult sensitivity =
 *     engine.analyzeSensitivity(5000.0, 50.0, 10.0);
 *
 * if (sensitivity.isAtCapacity()) {
 *   System.out.println("Near capacity - bottleneck: " + sensitivity.getBottleneckEquipment());
 * }
 *
 * // Calculate shadow prices
 * Map&lt;String, Double&gt; shadowPrices = engine.calculateShadowPrices(5000.0, 50.0, 10.0);
 * </pre>
 *
 * <h2>Constraint Evaluation with Caching</h2>
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
 * @see neqsim.process.util.optimizer.ProcessConstraintEvaluator
 * @see neqsim.process.util.optimizer.OptimizationResultBase
 * @see neqsim.process.util.optimizer.EclipseVFPExporter
 * @see neqsim.process.util.optimization.FlowRateOptimizer
 * @see neqsim.process.util.optimization.ProductionOptimizer
 * @author NeqSim Development Team
 * @version 1.6
 */
package neqsim.process.util.optimizer;
