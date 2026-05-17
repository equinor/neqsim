package neqsim.process.equipment.network;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Detects independent loops in a pipeline network using graph theory algorithms.
 *
 * <p>
 * Uses Depth-First Search (DFS) to build a spanning tree of the network graph. Non-tree edges
 * (chords) each define exactly one independent loop. The number of independent loops equals: E - V
 * + 1 (for a connected graph) where E is the number of edges (pipelines) and V is the number of
 * vertices (nodes).
 * </p>
 *
 * <h2>Algorithm</h2>
 * <ol>
 * <li>Build a graph representation from node/edge data</li>
 * <li>Run DFS to create a spanning tree</li>
 * <li>Identify chord edges (edges not in the spanning tree)</li>
 * <li>For each chord, trace the fundamental cycle (loop)</li>
 * </ol>
 *
 * <h2>References</h2>
 * <ul>
 * <li>Cormen, T.H. et al. "Introduction to Algorithms" - Graph algorithms</li>
 * <li>Cross, H. (1936). "Analysis of Flow in Networks of Conduits or Conductors."</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 * @see NetworkLoop
 * @see LoopedPipeNetwork
 */
public class LoopDetector implements Serializable {

  private static final long serialVersionUID = 1000L;

  /**
   * Represents an edge in the network graph.
   */
  private static class Edge implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Source node name. */
    final String from;
    /** Target node name. */
    final String to;
    /** Pipeline name. */
    final String pipeName;

    /**
     * Create an edge.
     *
     * @param from source node
     * @param to target node
     * @param pipeName pipe name
     */
    Edge(String from, String to, String pipeName) {
      this.from = from;
      this.to = to;
      this.pipeName = pipeName;
    }
  }

  /** Adjacency list representation of the network graph. */
  private final Map<String, List<Edge>> adjacencyList;

  /** All edges in the graph (forward direction only). */
  private final List<Edge> allEdges;

  /** Set of node names. */
  private final Set<String> nodes;

  /** Parent pointers from DFS spanning tree. */
  private final Map<String, String> parent;

  /** Edge used to reach each node in spanning tree. */
  private final Map<String, Edge> parentEdge;

  /** Depth of each node in the spanning tree. */
  private final Map<String, Integer> depth;

  /** Set of pipe names that are in the spanning tree. */
  private final Set<String> spanningTreePipes;

  /** Detected independent loops. */
  private List<NetworkLoop> independentLoops;

  /** Counter for generating loop IDs. */
  private int loopIdCounter = 0;

  /**
   * Create a new loop detector.
   */
  public LoopDetector() {
    adjacencyList = new HashMap<>();
    allEdges = new ArrayList<>();
    nodes = new HashSet<>();
    parent = new HashMap<>();
    parentEdge = new HashMap<>();
    depth = new HashMap<>();
    spanningTreePipes = new HashSet<>();
    independentLoops = new ArrayList<>();
  }

  /**
   * Add an edge (pipe) to the graph.
   *
   * @param fromNode source node name
   * @param toNode target node name
   * @param pipeName pipe name
   */
  public void addEdge(String fromNode, String toNode, String pipeName) {
    // Add nodes if not present
    if (!adjacencyList.containsKey(fromNode)) {
      adjacencyList.put(fromNode, new ArrayList<>());
      nodes.add(fromNode);
    }
    if (!adjacencyList.containsKey(toNode)) {
      adjacencyList.put(toNode, new ArrayList<>());
      nodes.add(toNode);
    }

    // Create a single edge object (forward direction)
    Edge forwardEdge = new Edge(fromNode, toNode, pipeName);

    // Add to forward adjacency list
    adjacencyList.get(fromNode).add(forwardEdge);

    // Create backward edge that shares the same inSpanningTree flag reference
    // by using a wrapper approach - actually, let's use a different approach
    // We'll store both edges but link them together
    Edge backwardEdge = new Edge(toNode, fromNode, pipeName);
    adjacencyList.get(toNode).add(backwardEdge);

    // Store the forward edge in allEdges
    allEdges.add(forwardEdge);
  }

  /**
   * Clear all data and prepare for new analysis.
   */
  public void clear() {
    adjacencyList.clear();
    allEdges.clear();
    nodes.clear();
    parent.clear();
    parentEdge.clear();
    depth.clear();
    spanningTreePipes.clear();
    independentLoops.clear();
    loopIdCounter = 0;
  }

  /**
   * Detect all independent loops in the network.
   *
   * @return list of independent loops
   */
  public List<NetworkLoop> findLoops() {
    if (nodes.isEmpty()) {
      return Collections.emptyList();
    }

    // Clear previous results
    parent.clear();
    parentEdge.clear();
    depth.clear();
    spanningTreePipes.clear();
    independentLoops.clear();
    loopIdCounter = 0;

    // Run DFS to build spanning tree
    buildSpanningTree();

    // Find chord edges (non-tree edges) and create loops
    findChordLoops();

    return Collections.unmodifiableList(independentLoops);
  }

  /**
   * Build a spanning tree using DFS.
   */
  private void buildSpanningTree() {
    Set<String> visited = new HashSet<>();
    Set<String> discovered = new HashSet<>(); // Nodes pushed to stack but not yet processed

    // Start from first node
    String startNode = nodes.iterator().next();

    // DFS using stack (iterative to handle large graphs)
    Deque<String> stack = new ArrayDeque<>();
    stack.push(startNode);
    parent.put(startNode, null);
    depth.put(startNode, Integer.valueOf(0));
    discovered.add(startNode);

    while (!stack.isEmpty()) {
      String current = stack.pop();

      if (visited.contains(current)) {
        continue;
      }
      visited.add(current);

      List<Edge> edges = adjacencyList.get(current);
      if (edges == null) {
        continue;
      }

      for (Edge edge : edges) {
        // Only add to spanning tree if target is not yet discovered
        if (!discovered.contains(edge.to)) {
          parent.put(edge.to, current);
          parentEdge.put(edge.to, edge);
          depth.put(edge.to, Integer.valueOf(depth.get(current).intValue() + 1));
          stack.push(edge.to);
          discovered.add(edge.to);
          // Mark pipe as in spanning tree using the Set
          spanningTreePipes.add(edge.pipeName);
        }
      }
    }

    // Handle disconnected components
    for (String node : nodes) {
      if (!discovered.contains(node)) {
        stack.push(node);
        parent.put(node, null);
        depth.put(node, Integer.valueOf(0));
        discovered.add(node);

        while (!stack.isEmpty()) {
          String current = stack.pop();

          if (visited.contains(current)) {
            continue;
          }
          visited.add(current);

          List<Edge> edges = adjacencyList.get(current);
          if (edges == null) {
            continue;
          }

          for (Edge edge : edges) {
            if (!discovered.contains(edge.to)) {
              parent.put(edge.to, current);
              parentEdge.put(edge.to, edge);
              depth.put(edge.to, Integer.valueOf(depth.get(current).intValue() + 1));
              stack.push(edge.to);
              discovered.add(edge.to);
              spanningTreePipes.add(edge.pipeName);
            }
          }
        }
      }
    }
  }

  /**
   * Find chord edges and construct the fundamental cycle for each.
   */
  private void findChordLoops() {
    Set<String> processedPipes = new HashSet<>();

    for (Edge edge : allEdges) {
      // Skip tree edges (using Set) and already processed pipes
      if (spanningTreePipes.contains(edge.pipeName) || processedPipes.contains(edge.pipeName)) {
        continue;
      }

      processedPipes.add(edge.pipeName);

      // This chord creates a fundamental cycle
      NetworkLoop loop = traceFundamentalCycle(edge);
      if (loop != null && loop.size() >= 2) {
        independentLoops.add(loop);
      }
    }
  }

  /**
   * Trace the fundamental cycle created by a chord edge.
   *
   * <p>
   * The fundamental cycle consists of the chord edge plus the unique path in the spanning tree
   * between the chord's endpoints.
   * </p>
   *
   * @param chord the chord edge
   * @return the network loop, or null if no valid loop
   */
  private NetworkLoop traceFundamentalCycle(Edge chord) {
    String node1 = chord.from;
    String node2 = chord.to;

    // Find paths from both nodes to their common ancestor
    List<String> path1 = pathToRoot(node1);
    List<String> path2 = pathToRoot(node2);

    // Find lowest common ancestor (LCA)
    Set<String> path1Set = new HashSet<>(path1);
    String lca = null;
    int lcaIndex2 = -1;

    for (int i = 0; i < path2.size(); i++) {
      if (path1Set.contains(path2.get(i))) {
        lca = path2.get(i);
        lcaIndex2 = i;
        break;
      }
    }

    if (lca == null) {
      // Nodes are in different components - no loop
      return null;
    }

    // Find LCA index in path1
    int lcaIndex1 = path1.indexOf(lca);

    // Create loop
    loopIdCounter++;
    NetworkLoop loop = new NetworkLoop(String.valueOf(loopIdCounter));

    // Add edges from node1 to LCA (going up the tree)
    for (int i = 0; i < lcaIndex1; i++) {
      String from = path1.get(i);
      String to = path1.get(i + 1);
      Edge treeEdge = findEdgeBetween(from, to);
      if (treeEdge != null) {
        // Direction: we're going from node1 toward LCA (up the tree)
        int direction = treeEdge.from.equals(from) ? 1 : -1;
        loop.addMember(treeEdge.pipeName, direction);
      }
    }

    // Add edges from LCA to node2 (going down the tree)
    for (int i = lcaIndex2; i > 0; i--) {
      String from = path2.get(i);
      String to = path2.get(i - 1);
      Edge treeEdge = findEdgeBetween(from, to);
      if (treeEdge != null) {
        // Direction: we're going from LCA toward node2 (down the tree)
        int direction = treeEdge.from.equals(from) ? 1 : -1;
        loop.addMember(treeEdge.pipeName, direction);
      }
    }

    // Add the chord edge (from node2 back to node1)
    // This closes the loop
    loop.addMember(chord.pipeName, -1);

    return loop;
  }

  /**
   * Find the path from a node to the root of its spanning tree.
   *
   * @param node starting node
   * @return list of nodes from node to root (inclusive)
   */
  private List<String> pathToRoot(String node) {
    List<String> path = new ArrayList<>();
    String current = node;

    while (current != null) {
      path.add(current);
      current = parent.get(current);
    }

    return path;
  }

  /**
   * Find an edge between two adjacent nodes.
   *
   * @param from source node
   * @param to target node
   * @return the edge, or null if not found
   */
  private Edge findEdgeBetween(String from, String to) {
    List<Edge> edges = adjacencyList.get(from);
    if (edges != null) {
      for (Edge edge : edges) {
        if (edge.to.equals(to)) {
          return edge;
        }
      }
    }
    return null;
  }

  /**
   * Get the number of independent loops.
   *
   * @return number of independent loops
   */
  public int getLoopCount() {
    return independentLoops.size();
  }

  /**
   * Get the detected independent loops.
   *
   * @return unmodifiable list of loops
   */
  public List<NetworkLoop> getLoops() {
    return Collections.unmodifiableList(independentLoops);
  }

  /**
   * Check if the network contains any loops.
   *
   * @return true if the network has loops (is not a tree)
   */
  public boolean hasLoops() {
    return !independentLoops.isEmpty();
  }

  /**
   * Get the number of nodes in the graph.
   *
   * @return number of nodes
   */
  public int getNodeCount() {
    return nodes.size();
  }

  /**
   * Get the number of edges in the graph.
   *
   * @return number of edges
   */
  public int getEdgeCount() {
    return allEdges.size();
  }
}
