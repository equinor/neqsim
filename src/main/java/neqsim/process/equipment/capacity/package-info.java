/**
 * Equipment capacity constraint framework for process optimization.
 *
 * <p>
 * This package provides a standardized framework for defining, tracking, and querying capacity
 * constraints on process equipment. It enables automated bottleneck detection and optimization
 * across entire process systems.
 * </p>
 *
 * <h2>Key Components</h2>
 * <ul>
 * <li>{@link neqsim.process.equipment.capacity.CapacityConstraint} - Defines a single capacity
 * limit with design value, max value, current value supplier, and utilization calculations</li>
 * <li>{@link neqsim.process.equipment.capacity.CapacityConstrainedEquipment} - Interface for
 * equipment that has capacity limits (separators, compressors, pumps, etc.)</li>
 * <li>{@link neqsim.process.equipment.capacity.StandardConstraintType} - Predefined constraint
 * types with standard names and units</li>
 * </ul>
 *
 * <h2>Constraint Types</h2>
 * <ul>
 * <li><b>HARD</b> - Cannot be exceeded (e.g., max speed, surge limit). Exceeding causes trip or
 * damage.</li>
 * <li><b>SOFT</b> - Can be temporarily exceeded (e.g., design flow). Reduces efficiency or
 * life.</li>
 * <li><b>DESIGN</b> - Informational only. Used for reporting and optimization guidance.</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * 
 * <pre>
 * // In equipment class (e.g., Compressor)
 * public class Compressor implements CapacityConstrainedEquipment {
 *   private Map&lt;String, CapacityConstraint&gt; constraints = new LinkedHashMap&lt;&gt;();
 *
 *   public void initializeConstraints() {
 *     // Speed constraint
 *     addCapacityConstraint(StandardConstraintType.COMPRESSOR_SPEED.createConstraint()
 *         .setDesignValue(10000.0)
 *         .setMaxValue(11000.0)
 *         .setWarningThreshold(0.9)
 *         .setValueSupplier(() -&gt; this.getSpeed()));
 *
 *     // Power constraint
 *     addCapacityConstraint(StandardConstraintType.COMPRESSOR_POWER.createConstraint()
 *         .setDesignValue(5000.0)
 *         .setMaxValue(5500.0)
 *         .setValueSupplier(() -&gt; this.getPower()));
 *   }
 * }
 *
 * // In ProcessSystem or optimization code
 * ProcessSystem process = ...;
 * BottleneckResult bottleneck = process.findBottleneck();
 * System.out.println("Bottleneck: " + bottleneck.getEquipmentName());
 * System.out.println("Constraint: " + bottleneck.getConstraint().getName());
 * System.out.println("Utilization: " + bottleneck.getUtilizationPercent() + "%");
 * </pre>
 *
 * <h2>Supported Equipment Types</h2>
 * <table border="1">
 * <tr>
 * <th>Equipment</th>
 * <th>Typical Constraints</th>
 * </tr>
 * <tr>
 * <td>Separator</td>
 * <td>Gas load factor, liquid load factor, residence time</td>
 * </tr>
 * <tr>
 * <td>Compressor</td>
 * <td>Speed, power, surge margin, discharge temperature</td>
 * </tr>
 * <tr>
 * <td>Pump</td>
 * <td>NPSH margin, flow rate, power</td>
 * </tr>
 * <tr>
 * <td>Heat Exchanger</td>
 * <td>Duty, approach temperature, pressure drop</td>
 * </tr>
 * <tr>
 * <td>Valve</td>
 * <td>Cv utilization, pressure drop, opening</td>
 * </tr>
 * <tr>
 * <td>Pipe</td>
 * <td>Velocity, erosional velocity ratio, pressure drop</td>
 * </tr>
 * </table>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @see neqsim.process.equipment.capacity.CapacityConstraint
 * @see neqsim.process.equipment.capacity.CapacityConstrainedEquipment
 * @see neqsim.process.equipment.capacity.StandardConstraintType
 */
package neqsim.process.equipment.capacity;
