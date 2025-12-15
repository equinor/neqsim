/**
 * Graph-based process representation for NeqSim process flowsheets.
 *
 * <p>
 * This package provides an explicit DAG (Directed Acyclic Graph) representation of process
 * flowsheets, addressing the limitation that {@code ProcessSystem} is a list where topology is
 * implicit via stream connections.
 * </p>
 *
 * <h2>Why This Matters</h2>
 *
 * <p>
 * Without explicit graph representation:
 * </p>
 * <ul>
 * <li>Execution order equals insertion order (fragile)</li>
 * <li>Rearranging units or adding recycles late causes wrong results</li>
 * <li>Parallel execution risks silent convergence failures</li>
 * </ul>
 *
 * <p>
 * With a graph:
 * </p>
 * <ul>
 * <li>Execution order is derived from topology, not assumed</li>
 * <li>Recycles and feedback loops are explicit objects</li>
 * <li>Partitioning enables safe parallel execution</li>
 * <li>AI agents can reason about flowsheet structure</li>
 * </ul>
 *
 * <h2>Key Classes</h2>
 *
 * <ul>
 * <li>{@link neqsim.process.processmodel.graph.ProcessGraph} - Main graph structure with analysis
 * methods</li>
 * <li>{@link neqsim.process.processmodel.graph.ProcessNode} - Node representing equipment</li>
 * <li>{@link neqsim.process.processmodel.graph.ProcessEdge} - Edge representing stream
 * connections</li>
 * <li>{@link neqsim.process.processmodel.graph.ProcessGraphBuilder} - Builds graph from
 * ProcessSystem</li>
 * <li>{@link neqsim.process.processmodel.graph.ProcessModelGraph} - Hierarchical graph for combined
 * ProcessSystems in a ProcessModule</li>
 * <li>{@link neqsim.process.processmodel.graph.ProcessModelGraphBuilder} - Builds hierarchical
 * graphs from ProcessModule</li>
 * </ul>
 *
 * <h2>Usage Example - Single ProcessSystem</h2>
 *
 * <pre>
 * // Build graph from existing ProcessSystem
 * ProcessSystem system = new ProcessSystem();
 * // ... add units ...
 * ProcessGraph graph = ProcessGraphBuilder.buildGraph(system);
 *
 * // Get derived calculation order (topology-based, not insertion-based)
 * List&lt;ProcessEquipmentInterface&gt; calcOrder = graph.getCalculationOrder();
 *
 * // Detect cycles (recycle loops)
 * ProcessGraph.CycleAnalysisResult cycles = graph.analyzeCycles();
 * if (cycles.hasCycles()) {
 *   System.out.println("Found " + cycles.getCycleCount() + " recycle loops");
 * }
 *
 * // Partition for parallel execution
 * ProcessGraph.ParallelPartition partition = graph.partitionForParallelExecution();
 * for (List&lt;ProcessNode&gt; level : partition.getLevels()) {
 *   // Units in each level can be executed in parallel
 *   level.parallelStream().forEach(node -&gt; node.getEquipment().run());
 * }
 *
 * // Get GNN-compatible tensors for AI analysis
 * double[][] nodeFeatures = graph.getNodeFeatureMatrix();
 * int[][] edgeIndex = graph.getEdgeIndexTensor();
 * double[][] edgeFeatures = graph.getEdgeFeatureMatrix();
 * </pre>
 *
 * <h2>Usage Example - Combined ProcessSystems (ProcessModule)</h2>
 *
 * <pre>
 * // Create a module combining multiple process systems
 * ProcessModule plant = new ProcessModule("Gas Processing Plant");
 * plant.add(separationSystem);
 * plant.add(compressionSystem);
 * plant.add(exportSystem);
 *
 * // Build hierarchical graph
 * ProcessModelGraph modelGraph = plant.buildModelGraph();
 *
 * // Analyze the combined structure
 * System.out.println("Sub-systems: " + modelGraph.getSubSystemCount());
 * System.out.println("Total equipment: " + modelGraph.getTotalNodeCount());
 * System.out.println("Inter-system connections: " + modelGraph.getInterSystemConnectionCount());
 *
 * // Get overall calculation order across all systems
 * List&lt;ProcessEquipmentInterface&gt; order = modelGraph.getCalculationOrder();
 *
 * // Analyze inter-system connections
 * for (ProcessModelGraph.InterSystemConnection conn : modelGraph.getInterSystemConnections()) {
 *   System.out.println(conn); // e.g., "Separation[outlet] -&gt; Compression[feed]"
 * }
 *
 * // Get summary
 * System.out.println(modelGraph.getSummary());
 * </pre>
 *
 * <h2>Graph Neural Network Compatibility</h2>
 *
 * <p>
 * The graph representation is designed for compatibility with Graph Neural Networks:
 * </p>
 * <ul>
 * <li>Node features: one-hot equipment type, in/out degree, operating conditions</li>
 * <li>Edge features: edge type, stream properties, back-edge indicator</li>
 * <li>COO format edge index tensor for sparse representation</li>
 * <li>Adjacency matrix and list representations available</li>
 * </ul>
 *
 * @see neqsim.process.processmodel.ProcessSystem
 * @see neqsim.process.processmodel.ProcessModule
 */
package neqsim.process.processmodel.graph;
