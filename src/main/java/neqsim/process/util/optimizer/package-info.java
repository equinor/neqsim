/**
 * Process optimization engine and constraint evaluation utilities.
 *
 * <p>
 * This package provides a comprehensive framework for process optimization including:
 * </p>
 *
 * <p>
 * <strong>Core Classes:</strong>
 * </p>
 * <ul>
 * <li>{@link neqsim.process.util.optimizer.ProcessOptimizationEngine} - Unified API for process
 * optimization with multiple search algorithms (binary, golden section, gradient descent,
 * BFGS)</li>
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
 * Armijo-Wolfe line search, and BFGS quasi-Newton method</li>
 * <li><strong>Constraint Caching:</strong> TTL-based caching for repeated constraint
 * evaluations</li>
 * <li><strong>Sensitivity Analysis:</strong> Calculate flow sensitivities and shadow prices</li>
 * <li><strong>Bottleneck Detection:</strong> Identify limiting equipment and constraints</li>
 * <li><strong>FlowRateOptimizer Integration:</strong> Seamless integration with production
 * optimizer</li>
 * <li><strong>Adjuster Integration:</strong> Coordinate optimization with existing Adjuster
 * units</li>
 * <li><strong>Eclipse Export:</strong> Generate VFPPROD, VFPINJ, and VFPEXP tables</li>
 * <li><strong>Fluent API:</strong> Builder pattern for convenient configuration</li>
 * </ul>
 *
 * <p>
 * <strong>Quick Start - Using ProcessSystem Methods:</strong>
 * </p>
 * 
 * <pre>
 * // Simplest usage - direct methods on ProcessSystem
 * ProcessSystem process = new ProcessSystem();
 * // ... add equipment ...
 * process.run();
 *
 * // Find maximum throughput (simple)
 * double maxFlow = process.findMaxThroughput(50.0, 10.0);
 *
 * // Or use fluent API for more control
 * double optimizedFlow = process.optimize().withPressures(50.0, 10.0)
 *     .withFlowBounds(1000.0, 100000.0).usingAlgorithm(SearchAlgorithm.BFGS).findMaxThroughput();
 * </pre>
 *
 * <p>
 * <strong>Advanced Usage - Direct ProcessOptimizationEngine:</strong>
 * </p>
 * 
 * <pre>
 * // Create optimization engine directly
 * ProcessOptimizationEngine engine = process.createOptimizer();
 * engine.setSearchAlgorithm(ProcessOptimizationEngine.SearchAlgorithm.BFGS);
 *
 * // Find maximum throughput with detailed results
 * ProcessOptimizationEngine.OptimizationResult result =
 *     engine.findMaximumThroughput(50.0, 10.0, 1000.0, 100000.0);
 *
 * System.out.println("Optimal flow: " + result.getOptimalValue() + " kg/hr");
 * System.out.println("Bottleneck: " + result.getBottleneck());
 *
 * // Analyze sensitivity
 * ProcessOptimizationEngine.SensitivityResult sensitivity = engine.analyzeSensitivity(5000.0);
 *
 * if (sensitivity.isAtCapacity()) {
 *   System.out.println("Near capacity - bottleneck: " + sensitivity.getBottleneckEquipment());
 * }
 * </pre>
 *
 * <p>
 * <strong>Adjuster Integration:</strong>
 * </p>
 * 
 * <pre>
 * // Optimize with Adjusters temporarily disabled
 * OptimizationResult result = engine.optimizeWithAdjustersDisabled(50.0, 10.0, 1000.0, 100000.0);
 *
 * // Or optimize while respecting Adjuster targets
 * OptimizationResult result = engine.optimizeWithAdjusterTargets(50.0, 10.0, 1000.0, 100000.0);
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
 * @see neqsim.process.util.optimizer.ProcessConstraintEvaluator
 * @see neqsim.process.util.optimizer.OptimizationResultBase
 * @see neqsim.process.util.optimizer.EclipseVFPExporter
 * @see neqsim.process.util.optimizer.FlowRateOptimizer
 * @see neqsim.process.util.optimizer.ProductionOptimizer
 * @author NeqSim Development Team
 * @version 1.7
 */
package neqsim.process.util.optimizer;
