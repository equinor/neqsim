/**
 * Equipment failure modeling for reliability and risk analysis.
 *
 * <p>
 * This package provides classes for modeling equipment failures, their effects on process
 * operations, and integration with risk analysis frameworks.
 * </p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 * <li>{@link neqsim.process.equipment.failure.EquipmentFailureMode} - Defines failure types and
 * consequences</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * 
 * <pre>
 * {@code
 * // Create a trip failure mode
 * EquipmentFailureMode tripMode = EquipmentFailureMode.trip("compressor");
 * 
 * // Apply to equipment
 * compressor.setFailureMode(tripMode);
 * 
 * // Simulate with ProductionImpactAnalyzer
 * ProductionImpactAnalyzer analyzer = new ProductionImpactAnalyzer(process);
 * ProductionImpactResult result = analyzer.analyzeFailureImpact("Compressor 1");
 * }
 * </pre>
 *
 * @since 3.3.0
 */
package neqsim.process.equipment.failure;
