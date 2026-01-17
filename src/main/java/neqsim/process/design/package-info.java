/**
 * Process design framework for automated equipment sizing and optimization.
 *
 * <p>
 * This package provides classes for:
 * </p>
 * <ul>
 * <li>{@link neqsim.process.design.AutoSizeable} - Interface for auto-sizing equipment</li>
 * <li>{@link neqsim.process.design.DesignSpecification} - Builder for equipment specifications</li>
 * <li>{@link neqsim.process.design.ProcessTemplate} - Interface for process templates</li>
 * <li>{@link neqsim.process.design.ProcessBasis} - Design basis for process templates</li>
 * <li>{@link neqsim.process.design.DesignOptimizer} - Integrated design-optimization workflow</li>
 * <li>{@link neqsim.process.design.EquipmentConstraintRegistry} - Default constraint registry</li>
 * </ul>
 *
 * <p>
 * Example workflow:
 * </p>
 * 
 * <pre>
 * // 1. Configure equipment with specifications
 * DesignSpecification.forSeparator("HP-Separator").setKFactor(0.08).setDiameter(3.0, "m")
 *     .applyTo(separator);
 *
 * // 2. Run integrated design-optimization
 * DesignResult result = DesignOptimizer.forProcess(process).autoSizeEquipment(1.2)
 *     .applyDefaultConstraints().setObjective(ObjectiveType.MAXIMIZE_PRODUCTION).optimize();
 *
 * // 3. Check results
 * System.out.println(result.getSummary());
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
package neqsim.process.design;
