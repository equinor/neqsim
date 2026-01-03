/**
 * Professional Process Flow Diagram (PFD) generation for NeqSim.
 *
 * <p>
 * This package provides a layout intelligence layer for generating oil &amp; gas industry-standard
 * process flow diagrams from NeqSim process simulations.
 * </p>
 *
 * <h2>Key Features</h2>
 * <ul>
 * <li><b>Gravity-based layout</b> - Gas equipment positioned at top, liquid at bottom</li>
 * <li><b>Phase-aware styling</b> - Streams colored by vapor/liquid fraction</li>
 * <li><b>Equipment semantics</b> - Separator outlets correctly positioned (gas top, liquid
 * bottom)</li>
 * <li><b>Professional appearance</b> - Industry-standard shapes and colors</li>
 * <li><b>Multiple detail levels</b> - CONCEPTUAL, ENGINEERING, DEBUG</li>
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
 * exporter.setTitle("Gas Processing Plant").setDetailLevel(DiagramDetailLevel.ENGINEERING)
 *     .setVerticalLayout(true);
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
 * <h2>Architecture</h2>
 * <ul>
 * <li>{@link neqsim.process.processmodel.diagram.ProcessDiagramExporter} - Main exporter class</li>
 * <li>{@link neqsim.process.processmodel.diagram.PFDLayoutPolicy} - Layout intelligence layer</li>
 * <li>{@link neqsim.process.processmodel.diagram.EquipmentRole} - Equipment role
 * classification</li>
 * <li>{@link neqsim.process.processmodel.diagram.DiagramDetailLevel} - Detail level
 * enumeration</li>
 * <li>{@link neqsim.process.processmodel.diagram.EquipmentVisualStyle} - Visual styling</li>
 * </ul>
 *
 * <h2>Layout Philosophy</h2>
 * <p>
 * Professional PFDs are not drawn - they are computed using engineering layout rules:
 * </p>
 * <ol>
 * <li><b>Gravity logic</b> - Gas flows upward, liquids flow downward</li>
 * <li><b>Functional zoning</b> - Inlet/separation center, gas processing upper, liquid lower</li>
 * <li><b>Equipment semantics</b> - Separators have top (gas) and bottom (liquid) outlets</li>
 * <li><b>Stable layout</b> - Same model produces same diagram every time</li>
 * </ol>
 *
 * @author NeqSim
 * @version 1.0
 * @see neqsim.process.processmodel.diagram.ProcessDiagramExporter
 */
package neqsim.process.processmodel.diagram;
