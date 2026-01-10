/**
 * Enhanced reservoir modeling package for field development.
 *
 * <p>
 * This package provides classes for injection strategy calculations and reservoir material balance.
 * It supports the "Reservoir Material Balance with Injection" topic from NTNU's TPG4230 course.
 * </p>
 *
 * <h2>TPG4230 Topic: Reservoir Material Balance with Injection</h2>
 * <p>
 * The injection strategy class addresses:
 * </p>
 * <ul>
 * <li>Voidage replacement ratio (VRR) calculations</li>
 * <li>Formation volume factor estimation (Bo, Bg, Bw)</li>
 * <li>Pressure maintenance through water/gas injection</li>
 * <li>WAG (water-alternating-gas) scheduling</li>
 * </ul>
 *
 * <h2>Key Classes</h2>
 * <ul>
 * <li>{@link neqsim.process.fielddevelopment.reservoir.InjectionStrategy} - Calculates injection
 * rates for voidage replacement</li>
 * </ul>
 *
 * <h2>Injection Strategies</h2>
 * <ul>
 * <li>NATURAL_DEPLETION - No injection, primary recovery only</li>
 * <li>WATER_INJECTION - Water flooding for pressure support</li>
 * <li>GAS_INJECTION - Gas injection (miscible or immiscible)</li>
 * <li>WAG - Water alternating gas for improved sweep</li>
 * <li>PRESSURE_MAINTENANCE - Maintain target reservoir pressure</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>{@code
 * // Calculate water injection for full voidage replacement
 * InjectionStrategy strategy = InjectionStrategy.waterInjection(1.0); // VRR = 1.0
 * InjectionResult result = strategy.calculateInjection(reservoir, oilRate, gasRate, waterRate);
 * System.out.println("Water injection: " + result.waterInjectionRate + " Sm3/d");
 * System.out.println("Achieved VRR: " + result.achievedVRR);
 * }</pre>
 *
 * @see neqsim.process.equipment.reservoir.SimpleReservoir
 * @see neqsim.process.fielddevelopment.workflow.FieldDevelopmentWorkflow
 */
package neqsim.process.fielddevelopment.reservoir;
