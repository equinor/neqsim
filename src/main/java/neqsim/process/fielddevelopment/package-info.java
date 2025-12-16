/**
 * Field Development Engine for NeqSim.
 *
 * <p>
 * This package provides a concept-level API for rapid field development screening. It shifts NeqSim
 * from detailed equipment simulation to physics-consistent concept evaluation across:
 * <ul>
 * <li>Production capacity</li>
 * <li>Flow assurance envelopes</li>
 * <li>Safety requirements</li>
 * <li>Power and emissions</li>
 * <li>Economics (screening level)</li>
 * </ul>
 *
 * <h2>Key Packages</h2>
 * <ul>
 * <li>{@link neqsim.process.fielddevelopment.concept} - Concept definition</li>
 * <li>{@link neqsim.process.fielddevelopment.facility} - Modular facility blocks</li>
 * <li>{@link neqsim.process.fielddevelopment.screening} - Screening tools</li>
 * <li>{@link neqsim.process.fielddevelopment.evaluation} - Evaluation orchestration</li>
 * </ul>
 *
 * <h2>Quick Start</h2>
 * 
 * <pre>
 * // 1. Define the concept
 * FieldConcept concept = FieldConcept.builder("Marginal Gas Tieback")
 *     .reservoir(ReservoirInput.richGas().gor(1200).co2Percent(3.5).build())
 *     .wells(WellsInput.builder().producerCount(4).thp(120).ratePerWell(1.5e6, "Sm3/d").build())
 *     .infrastructure(InfrastructureInput.subseaTieback().tiebackLength(35).build()).build();
 *
 * // 2. Evaluate
 * ConceptEvaluator evaluator = new ConceptEvaluator();
 * ConceptKPIs kpis = evaluator.evaluate(concept);
 *
 * // 3. Review results
 * System.out.println(kpis.getSummary());
 * if (kpis.hasBlockingIssues()) {
 *   System.out.println("WARNINGS: " + kpis.getWarnings());
 * }
 * </pre>
 *
 * <h2>Batch Processing</h2>
 * 
 * <pre>
 * BatchConceptRunner runner = new BatchConceptRunner();
 * runner.addConcept(concept1);
 * runner.addConcept(concept2);
 * runner.addConcept(concept3);
 *
 * BatchResults results = runner.runAll();
 * System.out.println(results.getComparisonSummary());
 * ConceptKPIs best = results.getBestConcept();
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
package neqsim.process.fielddevelopment;
