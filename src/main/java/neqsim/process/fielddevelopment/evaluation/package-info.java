/**
 * Concept evaluation and batch processing for field development.
 *
 * <p>
 * This package contains the main orchestration classes for concept-level evaluation. It coordinates
 * the various screening tools to produce comprehensive KPIs for field development concepts.
 * </p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 * <li>{@link neqsim.process.fielddevelopment.evaluation.ConceptEvaluator} - Main evaluation
 * orchestrator that runs all screeners and aggregates results</li>
 * <li>{@link neqsim.process.fielddevelopment.evaluation.ConceptKPIs} - Immutable container holding
 * all screening results (flow assurance, economics, emissions, safety)</li>
 * <li>{@link neqsim.process.fielddevelopment.evaluation.BatchConceptRunner} - Parallel batch
 * processing for comparing multiple concepts</li>
 * </ul>
 *
 * <h2>Evaluation Workflow</h2>
 * <ol>
 * <li>Create a {@link neqsim.process.fielddevelopment.concept.FieldConcept} with reservoir, wells,
 * and infrastructure</li>
 * <li>Optionally create a {@link neqsim.process.fielddevelopment.facility.FacilityConfig} for
 * detailed equipment definition</li>
 * <li>Use {@link neqsim.process.fielddevelopment.evaluation.ConceptEvaluator} to run all
 * screenings</li>
 * <li>Access results through {@link neqsim.process.fielddevelopment.evaluation.ConceptKPIs}</li>
 * </ol>
 *
 * <h2>Single Concept Evaluation</h2>
 * 
 * <pre>
 * // Create evaluator
 * ConceptEvaluator evaluator = new ConceptEvaluator();
 * 
 * // Evaluate concept
 * ConceptKPIs kpis = evaluator.evaluate(concept);
 * 
 * // Access individual reports
 * FlowAssuranceReport fa = kpis.getFlowAssuranceReport();
 * EconomicsEstimator.EconomicsReport econ = kpis.getEconomicsReport();
 * EmissionsTracker.EmissionsReport emissions = kpis.getEmissionsReport();
 * SafetyScreener.SafetyReport safety = kpis.getSafetyReport();
 * 
 * // Get summary
 * System.out.println(kpis.getSummary());
 * </pre>
 *
 * <h2>Batch Concept Comparison</h2>
 * 
 * <pre>
 * // Create multiple concepts
 * List&lt;FieldConcept&gt; concepts =
 *     Arrays.asList(createPlatformConcept(), createFPSOConcept(), createSubseaConcept());
 * 
 * // Run batch evaluation
 * BatchConceptRunner runner = new BatchConceptRunner();
 * Map&lt;String, ConceptKPIs&gt; results = runner.runAll(concepts);
 * 
 * // Compare results
 * results.forEach((name, kpis) -&gt; {
 *   System.out.printf("%s: CAPEX=%.0f MUSD, CO2=%.1f kg/boe%n", name,
 *       kpis.getEconomicsReport().getTotalCapexMUSD(),
 *       kpis.getEmissionsReport().getCo2IntensityKgPerBoe());
 * });
 * 
 * // Rank by CAPEX
 * List&lt;ConceptKPIs&gt; ranked =
 *     runner.rankBy(results, kpis -&gt; kpis.getEconomicsReport().getTotalCapexMUSD());
 * </pre>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * {@link neqsim.process.fielddevelopment.evaluation.ConceptEvaluator} is stateless and thread-safe.
 * {@link neqsim.process.fielddevelopment.evaluation.BatchConceptRunner} uses an ExecutorService for
 * parallel evaluation but requires single-threaded execution when thermodynamic database operations
 * are involved.
 * </p>
 *
 * @since 3.0
 * @see neqsim.process.fielddevelopment.concept
 * @see neqsim.process.fielddevelopment.screening
 * @see neqsim.process.fielddevelopment.facility
 */
package neqsim.process.fielddevelopment.evaluation;
