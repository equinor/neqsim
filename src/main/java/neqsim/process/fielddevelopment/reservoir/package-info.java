/**
 * Enhanced reservoir modeling package for field development.
 *
 * <p>
 * This package provides classes for injection strategy calculations and reservoir material balance.
 * </p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 * <li>{@link neqsim.process.fielddevelopment.reservoir.InjectionStrategy} - Calculates injection
 * rates for voidage replacement</li>
 * </ul>
 *
 * <h2>Injection Strategies</h2>
 * <ul>
 * <li>WATER_INJECTION - Water flooding for pressure support</li>
 * <li>GAS_INJECTION - Gas injection (miscible or immiscible)</li>
 * <li>WAG - Water alternating gas</li>
 * <li>PRESSURE_MAINTENANCE - Maintain target reservoir pressure</li>
 * </ul>
 *
 * @see neqsim.process.equipment.reservoir.SimpleReservoir
 * @see neqsim.process.fielddevelopment.workflow
 */
package neqsim.process.fielddevelopment.reservoir;
