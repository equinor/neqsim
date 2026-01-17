/**
 * Process templates for common separation and compression configurations.
 *
 * <p>
 * This package contains pre-built templates for standard process configurations that can be
 * instantiated with a {@link neqsim.process.design.ProcessBasis} to create complete process
 * systems.
 * </p>
 *
 * <h2>Available Templates</h2>
 * <ul>
 * <li>{@link neqsim.process.design.template.ThreeStageSeparationTemplate} - Standard HP/MP/LP
 * separation train</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * 
 * <pre>{@code
 * // Create process basis
 * ProcessBasis basis = ProcessBasis.builder().feedFluid(myOilGasFluid)
 *     .feedFlowRate(5000.0, "Sm3/hr").stagePressures(80.0, 20.0, 2.0).build();
 *
 * // Create process from template
 * ProcessTemplate template = new ThreeStageSeparationTemplate();
 * if (template.isApplicable(basis.getFeedFluid())) {
 *   ProcessSystem process = template.create(basis);
 *   process.run();
 * }
 * }</pre>
 *
 * @see neqsim.process.design.ProcessTemplate
 * @see neqsim.process.design.ProcessBasis
 * @see neqsim.process.design.DesignOptimizer
 */
package neqsim.process.design.template;
