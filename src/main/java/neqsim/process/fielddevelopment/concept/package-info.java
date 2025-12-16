/**
 * Field concept definition classes for rapid field development screening.
 *
 * <p>
 * This package provides the input data structures for defining field development concepts at a high
 * level, enabling rapid iteration during early project phases. All classes use the builder pattern
 * for fluent, readable construction.
 * </p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 * <li>{@link neqsim.process.fielddevelopment.concept.FieldConcept} - Main concept container
 * combining reservoir, wells, and infrastructure into a complete development scenario</li>
 * <li>{@link neqsim.process.fielddevelopment.concept.ReservoirInput} - Reservoir fluid properties
 * including composition, temperature, pressure, and contaminants (CO2, H2S)</li>
 * <li>{@link neqsim.process.fielddevelopment.concept.WellsInput} - Well count, deliverability, and
 * operating conditions</li>
 * <li>{@link neqsim.process.fielddevelopment.concept.InfrastructureInput} - Processing location
 * (platform/FPSO/subsea), export route, and power source</li>
 * </ul>
 *
 * <h2>Enumerations</h2>
 * <ul>
 * <li>{@code FluidType} - Fluid classification (LEAN_GAS, RICH_GAS, GAS_CONDENSATE, etc.)</li>
 * <li>{@code ProcessingLocation} - Where processing occurs (PLATFORM, FPSO, SUBSEA, ONSHORE)</li>
 * <li>{@code ExportType} - How products are exported (PIPELINE_GAS, LNG, SHUTTLE_TANKER, etc.)</li>
 * <li>{@code PowerSource} - Power generation method (GAS_TURBINE, POWER_FROM_SHORE, HYBRID)</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * 
 * <pre>
 * FieldConcept concept = FieldConcept.builder().name("North Sea Gas Development")
 *     .reservoir(ReservoirInput.builder().fluidType(FluidType.RICH_GAS).reservoirTempC(85.0)
 *         .reservoirPressureBara(350.0).co2Percent(3.5).build())
 *     .wells(WellsInput.builder().producerCount(4).ratePerWellSm3d(500000.0).build())
 *     .infrastructure(InfrastructureInput.builder().processingLocation(ProcessingLocation.PLATFORM)
 *         .exportType(ExportType.PIPELINE_GAS).waterDepthM(120.0).build())
 *     .build();
 * </pre>
 *
 * @since 3.0
 * @see neqsim.process.fielddevelopment.evaluation.ConceptEvaluator
 */
package neqsim.process.fielddevelopment.concept;
