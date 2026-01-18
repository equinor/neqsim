/**
 * Optimization utilities for batch studies, parameter screening, and production optimization.
 *
 * <p>
 * This package provides tools for systematic exploration of design spaces and production
 * optimization:
 * </p>
 *
 * <h2>Production Optimization</h2>
 * <ul>
 * <li><b>FlowRateOptimizer:</b> Calculate flow rates for given pressure boundaries, generate lift
 * curves for Eclipse reservoir simulation</li>
 * <li><b>ProductionOptimizer:</b> Multi-algorithm production optimization with binary, golden
 * section, Nelder-Mead, and particle swarm search</li>
 * <li><b>PressureBoundaryOptimizer:</b> Simplified wrapper for pressure boundary flow
 * calculations</li>
 * </ul>
 *
 * <h2>Batch Studies</h2>
 * <ul>
 * <li><b>BatchStudy:</b> Parallel execution of parameter variations</li>
 * <li><b>Multi-objective:</b> Compare cases by CAPEX, OPEX, emissions, etc.</li>
 * <li><b>Cloud-ready:</b> Designed for horizontal scaling</li>
 * <li><b>Result Export:</b> CSV output for further analysis</li>
 * </ul>
 *
 * <h2>Typical Use Cases:</h2>
 * <ul>
 * <li>Field development concept screening</li>
 * <li>Design optimization studies</li>
 * <li>Sensitivity analysis</li>
 * <li>Operating envelope mapping</li>
 * <li>Lift curve generation for reservoir simulation</li>
 * <li>Maximum throughput determination</li>
 * </ul>
 *
 * <h2>Flow Rate Optimization Example:</h2>
 * 
 * <pre>
 * // Create FlowRateOptimizer
 * FlowRateOptimizer optimizer = new FlowRateOptimizer(process, "feed", "outlet");
 * optimizer.setFlowUnit("kg/hr");
 * optimizer.setMaxFlow(100000.0);
 *
 * // Find max flow for given pressures
 * double maxFlow = optimizer.findFlowRate(50.0, 10.0, "bara");
 *
 * // Generate lift curve table
 * double[] inletPressures = {30.0, 40.0, 50.0, 60.0};
 * double[] outletPressures = {10.0, 15.0, 20.0};
 * LiftCurveTable table = optimizer.generateLiftCurveTable(inletPressures, outletPressures, "bara");
 * System.out.println(table.toEclipseFormat());
 * </pre>
 *
 * <h2>Batch Study Example:</h2>
 * 
 * <pre>
 * BatchStudy study = BatchStudy.builder(process).vary("compressor.pressure", 30.0, 80.0, 10)
 *     .vary("cooler.temperature", 20.0, 40.0, 5).addObjective("power", Objective.MINIMIZE,
 *         p -&gt; ((Compressor) p.getUnit("compressor")).getPower("MW"))
 *     .parallelism(16).build();
 *
 * BatchStudyResult result = study.run();
 * result.exportToCSV("screening_results.csv");
 * </pre>
 *
 * @see neqsim.process.util.optimization.FlowRateOptimizer
 * @see neqsim.process.util.optimization.ProductionOptimizer
 * @see neqsim.process.util.optimization.BatchStudy
 * @see neqsim.process.util.optimizer.ProcessOptimizationEngine
 * @see neqsim.process.util.optimizer.ProcessConstraintEvaluator
 * @author ESOL
 * @version 1.1
 */
package neqsim.process.util.optimization;

