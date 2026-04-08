/**
 * IEC 81346 reference designation support for NeqSim process equipment.
 *
 * <p>
 * This package implements the IEC 81346 standard for structuring principles and reference
 * designations in industrial systems. The standard defines how to identify objects in a plant using
 * three orthogonal aspects:
 * </p>
 * <ul>
 * <li><strong>Function aspect</strong> ({@code =}): What the system does</li>
 * <li><strong>Product aspect</strong> ({@code -}): What the physical equipment is</li>
 * <li><strong>Location aspect</strong> ({@code +}): Where the equipment is installed</li>
 * </ul>
 *
 * <p>
 * Key classes:
 * </p>
 * <ul>
 * <li>{@link neqsim.process.equipment.iec81346.IEC81346LetterCode} — Enum mapping IEC 81346-2
 * letter codes to NeqSim equipment types</li>
 * <li>{@link neqsim.process.equipment.iec81346.ReferenceDesignation} — Data class holding the three
 * aspects and the equipment letter code</li>
 * <li>{@link neqsim.process.equipment.iec81346.ReferenceDesignationGenerator} — Walks a
 * {@link neqsim.process.processmodel.ProcessSystem} or
 * {@link neqsim.process.processmodel.ProcessModel} and auto-assigns reference designations</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
package neqsim.process.equipment.iec81346;
