/**
 * Process topology analysis and functional location tagging.
 *
 * <p>
 * This package provides tools for understanding the structure and dependencies in a process system:
 * </p>
 *
 * <ul>
 * <li>{@link neqsim.process.util.topology.ProcessTopologyAnalyzer} - Extracts graph structure</li>
 * <li>{@link neqsim.process.util.topology.FunctionalLocation} - STID tagging (ISO 14224)</li>
 * <li>{@link neqsim.process.util.topology.DependencyAnalyzer} - Failure impact analysis</li>
 * </ul>
 *
 * <h2>Key Features</h2>
 * <ul>
 * <li>Extract functional sequence (upstream/downstream relationships)</li>
 * <li>Identify parallel equipment (redundancy)</li>
 * <li>Tag equipment with STID functional locations</li>
 * <li>Analyze "if X fails, what becomes critical?"</li>
 * <li>Cross-installation dependency tracking</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>{@code
 * // Build topology
 * ProcessTopologyAnalyzer topology = new ProcessTopologyAnalyzer(process);
 * topology.buildTopology();
 *
 * // Add STID tags
 * topology.setFunctionalLocation("Export Compressor", "1775-KA-23011A");
 * topology.setFunctionalLocation("Backup Compressor", "1775-KA-23011B");
 *
 * // Analyze dependencies
 * DependencyAnalyzer deps = new DependencyAnalyzer(process, topology);
 * DependencyResult result = deps.analyzeFailure("Export Compressor");
 *
 * // What to monitor?
 * Map<String, String> toWatch = deps.getEquipmentToMonitor("Export Compressor");
 * // "Backup Compressor" -> "KRITISK - Overtar last ved feil"
 *
 * // Export as graph
 * String dotGraph = topology.toDotGraph();
 * }</pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
package neqsim.process.util.topology;
