/**
 * Optimization utilities for batch studies and parameter screening.
 *
 * <p>
 * This package provides tools for systematic exploration of design spaces:
 * </p>
 *
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
 * </ul>
 *
 * <h2>Usage Example:</h2>
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
 * @see neqsim.process.util.optimization.BatchStudy
 * @author ESOL
 * @version 1.0
 */
package neqsim.process.util.optimization;
