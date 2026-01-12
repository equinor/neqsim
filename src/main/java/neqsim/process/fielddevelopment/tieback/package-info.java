/**
 * Tie-back analysis package for field development screening.
 *
 * <p>
 * This package provides tools for evaluating subsea tie-back options to existing host facilities.
 * Tie-backs are a common development concept for marginal fields where building standalone
 * infrastructure is not economic.
 * </p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 * <li>{@link neqsim.process.fielddevelopment.tieback.HostFacility} - Represents an existing host
 * platform or FPSO with spare capacity</li>
 * <li>{@link neqsim.process.fielddevelopment.tieback.TiebackOption} - A specific tie-back
 * configuration with associated costs and constraints</li>
 * <li>{@link neqsim.process.fielddevelopment.tieback.TiebackAnalyzer} - Evaluates and ranks
 * tie-back options</li>
 * <li>{@link neqsim.process.fielddevelopment.tieback.TiebackReport} - Comprehensive screening
 * report</li>
 * </ul>
 *
 * <h2>Screening Workflow</h2>
 * <ol>
 * <li>Define the discovery using {@link neqsim.process.fielddevelopment.concept.FieldConcept}</li>
 * <li>Create a list of potential {@link HostFacility} objects with spare capacity</li>
 * <li>Use {@link TiebackAnalyzer} to evaluate each option</li>
 * <li>Review the {@link TiebackReport} to compare options by NPV, distance, and flow assurance</li>
 * </ol>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>{@code
 * // Define discovery
 * FieldConcept discovery = FieldConcept.gasTieback("Marginal Gas", 25.0, 2, 1.5);
 * 
 * // Define potential hosts
 * List<HostFacility> hosts = Arrays.asList(
 *     HostFacility.builder("Platform A").location(61.5, 2.3).waterDepth(110).spareGasCapacity(3.0)
 *         .build(),
 *     HostFacility.builder("FPSO B").location(61.8, 2.1).waterDepth(350).spareGasCapacity(5.0)
 *         .build());
 * 
 * // Analyze options
 * TiebackAnalyzer analyzer = new TiebackAnalyzer();
 * TiebackReport report = analyzer.analyze(discovery, hosts);
 * 
 * // Review results
 * System.out.println(report.getSummary());
 * TiebackOption best = report.getBestOption();
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 */
package neqsim.process.fielddevelopment.tieback;
