/**
 * Facility configuration and building blocks for field development.
 *
 * <p>
 * This package provides a fluent API for assembling facility configurations from pre-validated
 * process blocks. It enables rapid, concept-level facility design without requiring detailed
 * equipment parameters upfront.
 * </p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 * <li>{@link neqsim.process.fielddevelopment.facility.FacilityBuilder} - Fluent builder for
 * constructing facility configurations from process blocks</li>
 * <li>{@link neqsim.process.fielddevelopment.facility.BlockType} - Enumeration of available process
 * block types (separation, compression, dehydration, CO2 removal, etc.)</li>
 * <li>{@link neqsim.process.fielddevelopment.facility.BlockConfig} - Configuration for individual
 * process blocks with type-specific parameters</li>
 * <li>{@link neqsim.process.fielddevelopment.facility.FacilityConfig} - Immutable facility
 * configuration containing all blocks</li>
 * </ul>
 *
 * <h2>Available Block Types</h2>
 * <table border="1">
 * <tr>
 * <th>Block Type</th>
 * <th>Description</th>
 * </tr>
 * <tr>
 * <td>INLET_SEPARATION</td>
 * <td>Inlet slug catcher/separator</td>
 * </tr>
 * <tr>
 * <td>THREE_PHASE_SEPARATOR</td>
 * <td>Oil-water-gas separation</td>
 * </tr>
 * <tr>
 * <td>COMPRESSION</td>
 * <td>Gas compression (multi-stage)</td>
 * </tr>
 * <tr>
 * <td>TEG_DEHYDRATION</td>
 * <td>Glycol-based gas dehydration</td>
 * </tr>
 * <tr>
 * <td>CO2_REMOVAL_AMINE</td>
 * <td>Amine-based CO2 removal</td>
 * </tr>
 * <tr>
 * <td>CO2_REMOVAL_MEMBRANE</td>
 * <td>Membrane-based CO2 removal</td>
 * </tr>
 * </table>
 *
 * <h2>Usage Example</h2>
 * 
 * <pre>
 * // Build a gas processing facility
 * FacilityConfig facility =
 *     FacilityBuilder.builder().addBlock(BlockConfig.of(BlockType.INLET_SEPARATION))
 *         .addBlock(BlockConfig.of(BlockType.THREE_PHASE_SEPARATOR))
 *         .addBlock(
 *             BlockConfig.of(BlockType.CO2_REMOVAL_AMINE).withParameter("capacity_mmscfd", 200.0))
 *         .addBlock(BlockConfig.of(BlockType.TEG_DEHYDRATION))
 *         .addBlock(BlockConfig.of(BlockType.COMPRESSION).withParameter("stages", 3)).build();
 * 
 * // Use with concept evaluation
 * ConceptEvaluator evaluator = new ConceptEvaluator();
 * ConceptKPIs kpis = evaluator.evaluate(concept, facility);
 * </pre>
 *
 * @since 3.0
 * @see neqsim.process.fielddevelopment.concept
 * @see neqsim.process.fielddevelopment.screening.EconomicsEstimator
 */
package neqsim.process.fielddevelopment.facility;
