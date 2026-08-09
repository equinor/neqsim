/**
 * Deterministic Process Flow Diagram (PFD) topology and Graphviz export for NeqSim.
 *
 * <p>
 * This package provides a neutral topology adapter and a layout layer for generating simulator-style process flow
 * diagrams from NeqSim process simulations. The current exporters are not qualified engineering documents or claims of
 * ISO 10628 conformance.
 * </p>
 *
 * <h2>Key Features</h2>
 * <ul>
 * <li><b>Simulator-style layout</b> - Left-to-right flow with vertical phase stratification</li>
 * <li><b>Gravity-based positioning</b> - Gas at top, oil in middle, water at bottom</li>
 * <li><b>Phase-aware styling</b> - Streams colored by vapor/liquid fraction</li>
 * <li><b>Equipment semantics</b> - Separator outlets correctly positioned</li>
 * <li><b>DEXPI integration</b> - Import P&amp;ID data and generate diagrams</li>
 * <li><b>Canonical topology</b> - Stable plant, area, equipment, port, and connection identities</li>
 * <li><b>Multiple detail levels</b> - MINIMAL, STANDARD, DETAILED, DEBUG</li>
 * <li><b>Deterministic output</b> - Same model always produces same diagram</li>
 * </ul>
 *
 * <h2>Quick Start</h2>
 *
 * <pre>
 * // Create a process diagram from a ProcessSystem
 * ProcessDiagramExporter exporter = new ProcessDiagramExporter(processSystem);
 *
 * // Configure (optional)
 * exporter.setTitle("Gas Processing Plant").setDetailLevel(DiagramDetailLevel.STANDARD).setShowStreamValues(true);
 *
 * // Export to DOT format (text)
 * String dot = exporter.toDOT();
 *
 * // Export to SVG (requires Graphviz installed)
 * exporter.exportSVG(Path.of("diagram.svg"));
 *
 * // Export directly from ProcessSystem
 * String dot = processSystem.toDOT();
 * </pre>
 *
 * <h2>DEXPI Integration</h2>
 *
 * <pre>
 * // Import DEXPI P&amp;ID and create diagram
 * ProcessDiagramExporter exporter = DexpiDiagramBridge.importAndCreateExporter(Paths.get("plant.xml"));
 * exporter.exportDOT(Paths.get("diagram.dot"));
 *
 * // Full round-trip: DEXPI to simulate to diagram to DEXPI
 * DexpiDiagramBridge.roundTrip(Paths.get("input.xml"), Paths.get("diagram.dot"), Paths.get("output.xml"));
 * </pre>
 *
 * <h2>Architecture</h2>
 * <ul>
 * <li>{@link neqsim.process.processmodel.diagram.ProcessDiagramGraphAdapter} - Neutral adapter to the shared
 * engineering graph model</li>
 * <li>{@link neqsim.process.processmodel.diagram.ProcessDiagramExporter} - Main exporter class</li>
 * <li>{@link neqsim.process.processmodel.diagram.PFDLayoutPolicy} - Layout intelligence layer</li>
 * <li>{@link neqsim.process.processmodel.diagram.EquipmentRole} - Equipment role classification</li>
 * <li>{@link neqsim.process.processmodel.diagram.DiagramDetailLevel} - Detail level enumeration</li>
 * <li>{@link neqsim.process.processmodel.diagram.EquipmentVisualStyle} - Visual styling</li>
 * <li>{@link neqsim.process.processmodel.diagram.DexpiDiagramBridge} - DEXPI integration bridge</li>
 * </ul>
 *
 * <h2>Layout Philosophy</h2>
 * <p>
 * The current Graphviz layout follows these simulator-style conventions:
 * </p>
 * <ol>
 * <li><b>Left-to-right flow</b> - Feed streams enter left, products exit right</li>
 * <li><b>Vertical phase zones</b> - Gas top, oil middle, water bottom</li>
 * <li><b>Equipment semantics</b> - Separators have positioned outlets by phase</li>
 * <li><b>Stable layout</b> - Same model produces same diagram every time</li>
 * </ol>
 *
 * @author NeqSim
 * @version 1.0
 * @see neqsim.process.processmodel.diagram.ProcessDiagramExporter
 * @see neqsim.process.processmodel.diagram.DexpiDiagramBridge
 */
package neqsim.process.processmodel.diagram;
