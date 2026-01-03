/**
 * DEXPI integration layer for importing and exporting P&ID data.
 *
 * <p>
 * This package provides utilities for working with DEXPI (Data Exchange in the Process Industry)
 * XML files, enabling round-trip import and export of process flow diagrams.
 * </p>
 *
 * <h2>Key Components</h2>
 * <ul>
 * <li>{@link neqsim.process.processmodel.dexpi.DexpiXmlReader} - Import DEXPI P&ID XML →
 * ProcessSystem</li>
 * <li>{@link neqsim.process.processmodel.dexpi.DexpiXmlWriter} - Export ProcessSystem → DEXPI
 * XML</li>
 * <li>{@link neqsim.process.processmodel.dexpi.DexpiProcessUnit} - Lightweight placeholder for
 * imported equipment</li>
 * <li>{@link neqsim.process.processmodel.dexpi.DexpiStream} - Runnable stream with DEXPI
 * metadata</li>
 * <li>{@link neqsim.process.processmodel.dexpi.DexpiMetadata} - Shared constants for DEXPI
 * exchanges</li>
 * <li>{@link neqsim.process.processmodel.dexpi.DexpiRoundTripProfile} - Validation for round-trip
 * fidelity</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>
 * // Import from DEXPI XML
 * ProcessSystem process = DexpiXmlReader.read(new File("plant.xml"), templateStream);
 *
 * // Access imported equipment
 * DexpiProcessUnit pump = (DexpiProcessUnit) process.getUnit("P-101");
 * EquipmentEnum type = pump.getMappedEquipment();
 *
 * // Export back to DEXPI XML
 * DexpiXmlWriter.write(process, new File("export.xml"));
 * </pre>
 *
 * @author NeqSim
 * @version 1.0
 * @see <a href="https://dexpi.org/">DEXPI Standard</a>
 */
package neqsim.process.processmodel.dexpi;
