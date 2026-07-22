package neqsim.process.engineering.impact;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import neqsim.process.engineering.impact.ImpactAnalysisRule.Direction;
import neqsim.process.engineering.model.EngineeringEdge;
import neqsim.process.engineering.model.EngineeringGraph;
import neqsim.process.engineering.model.EngineeringNode;
import neqsim.process.processmodel.lifecycle.event.ModelChangeEvent;
import neqsim.process.processmodel.lifecycle.event.ModelChangeSubject;
import neqsim.process.processmodel.lifecycle.event.ModelChangeSubject.SubjectType;

/** Configurable multi-relationship change propagation across the canonical engineering graph. */
public final class GeneralizedImpactAnalyzer {
  private final List<ImpactAnalysisRule> rules;

  public GeneralizedImpactAnalyzer() {
    this(defaultRules());
  }

  public GeneralizedImpactAnalyzer(List<ImpactAnalysisRule> rules) {
    if (rules == null || rules.isEmpty()) {
      throw new IllegalArgumentException("rules must not be empty");
    }
    this.rules = new ArrayList<ImpactAnalysisRule>(rules);
    if (this.rules.contains(null)) {
      throw new IllegalArgumentException("rules must not contain null");
    }
  }

  /** Analyzes direct event subjects, relationship changes and supplied downstream impact hints. */
  public ImpactAnalysisResult analyze(EngineeringGraph graph, ModelChangeEvent event) {
    if (graph == null || event == null) {
      throw new IllegalArgumentException("graph and event must not be null");
    }
    Map<String, State> states = new LinkedHashMap<String, State>();
    List<String> unresolved = new ArrayList<String>();
    Deque<String> queue = new ArrayDeque<String>();
    for (ModelChangeSubject subject : event.getSubjects()) {
      if (subject.getSubjectType() == SubjectType.ENGINEERING_NODE) {
        if (graph.getNode(subject.getSubjectId()) == null) {
          unresolved.add(subject.getSubjectId());
        } else {
          seed(graph, states, queue, subject.getSubjectId(), true, 0, ImpactAction.REVIEW_CHANGE, null);
        }
      } else {
        EngineeringEdge relationship = graph.getEdges().get(subject.getSubjectId());
        if (relationship == null) {
          unresolved.add(subject.getSubjectId());
        } else {
          seed(graph, states, queue, relationship.getSourceId(), false, 0, ImpactAction.REVALIDATE,
              relationship.getId());
          seed(graph, states, queue, relationship.getTargetId(), false, 0, ImpactAction.REVALIDATE,
              relationship.getId());
        }
      }
    }
    for (String nodeId : event.getImpactHintNodeIds()) {
      if (graph.getNode(nodeId) == null) {
        if (!unresolved.contains(nodeId)) {
          unresolved.add(nodeId);
        }
      } else if (!states.containsKey(nodeId)) {
        seed(graph, states, queue, nodeId, false, 1, ImpactAction.REVALIDATE, null);
      }
    }
    return propagate(graph, event.getEventId(), states, queue, unresolved);
  }

  /** Analyzes an ad-hoc set of changed graph node ids without first constructing an event. */
  public ImpactAnalysisResult analyze(EngineeringGraph graph, Collection<String> changedNodeIds) {
    if (graph == null || changedNodeIds == null || changedNodeIds.isEmpty()) {
      throw new IllegalArgumentException("graph and changedNodeIds must not be empty");
    }
    Map<String, State> states = new LinkedHashMap<String, State>();
    List<String> unresolved = new ArrayList<String>();
    Deque<String> queue = new ArrayDeque<String>();
    for (String nodeId : changedNodeIds) {
      if (nodeId == null || graph.getNode(nodeId) == null) {
        unresolved.add(String.valueOf(nodeId));
      } else {
        seed(graph, states, queue, nodeId, true, 0, ImpactAction.REVIEW_CHANGE, null);
      }
    }
    return propagate(graph, "AD_HOC", states, queue, unresolved);
  }

  public List<ImpactAnalysisRule> getRules() {
    return Collections.unmodifiableList(rules);
  }

  public static List<ImpactAnalysisRule> defaultRules() {
    List<ImpactAnalysisRule> result = new ArrayList<ImpactAnalysisRule>();
    result.add(rule(EngineeringEdge.Kind.DEPENDS_ON, Direction.TARGET_TO_SOURCE, ImpactAction.REVALIDATE));
    result.add(rule(EngineeringEdge.Kind.GENERATED_FROM, Direction.TARGET_TO_SOURCE, ImpactAction.REGENERATE));
    result.add(rule(EngineeringEdge.Kind.REFERENCES, Direction.TARGET_TO_SOURCE, ImpactAction.REVALIDATE));
    result.add(rule(EngineeringEdge.Kind.APPROVES, Direction.TARGET_TO_SOURCE, ImpactAction.REAPPROVE));
    result.add(rule(EngineeringEdge.Kind.GOVERNS, Direction.SOURCE_TO_TARGET, ImpactAction.REVALIDATE));
    result.add(rule(EngineeringEdge.Kind.REPRESENTS_SAME_AS, Direction.BIDIRECTIONAL, ImpactAction.REVALIDATE));
    result.add(rule(EngineeringEdge.Kind.AUTHORED_IN, Direction.TARGET_TO_SOURCE, ImpactAction.REVALIDATE));
    result.add(rule(EngineeringEdge.Kind.SYNCHRONIZED_FROM, Direction.TARGET_TO_SOURCE, ImpactAction.REVALIDATE));
    result.add(rule(EngineeringEdge.Kind.CALCULATED_BY, Direction.TARGET_TO_SOURCE, ImpactAction.RECALCULATE));
    result.add(rule(EngineeringEdge.Kind.VALIDATED_AGAINST, Direction.TARGET_TO_SOURCE, ImpactAction.REVALIDATE));
    result.add(rule(EngineeringEdge.Kind.CONSUMED_BY_MODEL, Direction.SOURCE_TO_TARGET, ImpactAction.REVALIDATE));
    result.add(rule(EngineeringEdge.Kind.INVALIDATES, Direction.SOURCE_TO_TARGET, ImpactAction.REVALIDATE));
    result.add(rule(EngineeringEdge.Kind.REQUIRES_REAPPROVAL, Direction.SOURCE_TO_TARGET, ImpactAction.REAPPROVE));
    return Collections.unmodifiableList(result);
  }

  private ImpactAnalysisResult propagate(EngineeringGraph graph, String eventId, Map<String, State> states,
      Deque<String> queue, List<String> unresolved) {
    Map<String, List<Transition>> adjacency = adjacency(graph);
    Set<String> cycleEdges = new LinkedHashSet<String>();
    while (!queue.isEmpty()) {
      String currentId = queue.removeFirst();
      State current = states.get(currentId);
      List<Transition> transitions = adjacency.get(currentId);
      if (transitions == null) {
        continue;
      }
      for (Transition transition : transitions) {
        State existing = states.get(transition.nextNodeId);
        if (existing != null && current.path.contains(transition.nextNodeId)) {
          cycleEdges.add(transition.edgeId);
          existing.reasonEdgeIds.add(transition.edgeId);
          continue;
        }
        if (existing == null) {
          List<String> path = new ArrayList<String>(current.path);
          path.add(transition.nextNodeId);
          existing = new State(false, current.depth + 1, path);
          states.put(transition.nextNodeId, existing);
          queue.addLast(transition.nextNodeId);
        } else if (current.depth + 1 < existing.depth) {
          existing.depth = current.depth + 1;
          existing.path = new ArrayList<String>(current.path);
          existing.path.add(transition.nextNodeId);
        }
        existing.actions.add(transition.action);
        addNodeAction(graph.getNode(transition.nextNodeId), existing.actions);
        existing.reasonEdgeIds.add(transition.edgeId);
      }
    }

    List<ImpactedEngineeringObject> impacts = impacts(graph, states);
    RecalculationPlan recalculation = recalculationPlan(graph, states.keySet());
    List<String> reapprovals = new ArrayList<String>();
    for (ImpactedEngineeringObject impact : impacts) {
      if (impact.getRequiredActions().contains(ImpactAction.REAPPROVE)) {
        reapprovals.add(impact.getNodeId());
      }
    }
    return new ImpactAnalysisResult(eventId, graph.getProjectId(), graph.getRevision(), impacts, unresolved,
        new ArrayList<String>(cycleEdges), recalculation.order, recalculation.cycleNodeIds, reapprovals);
  }

  private Map<String, List<Transition>> adjacency(EngineeringGraph graph) {
    Map<EngineeringEdge.Kind, List<ImpactAnalysisRule>> rulesByKind = new LinkedHashMap<EngineeringEdge.Kind, List<ImpactAnalysisRule>>();
    for (ImpactAnalysisRule rule : rules) {
      List<ImpactAnalysisRule> values = rulesByKind.get(rule.getRelationship());
      if (values == null) {
        values = new ArrayList<ImpactAnalysisRule>();
        rulesByKind.put(rule.getRelationship(), values);
      }
      values.add(rule);
    }
    Map<String, List<Transition>> result = new LinkedHashMap<String, List<Transition>>();
    for (EngineeringEdge edge : graph.getEdges().values()) {
      List<ImpactAnalysisRule> matching = rulesByKind.get(edge.getKind());
      if (matching == null) {
        continue;
      }
      for (ImpactAnalysisRule rule : matching) {
        if (rule.getDirection() == Direction.SOURCE_TO_TARGET || rule.getDirection() == Direction.BIDIRECTIONAL) {
          addTransition(result, edge.getSourceId(), edge.getTargetId(), edge.getId(), rule.getAction());
        }
        if (rule.getDirection() == Direction.TARGET_TO_SOURCE || rule.getDirection() == Direction.BIDIRECTIONAL) {
          addTransition(result, edge.getTargetId(), edge.getSourceId(), edge.getId(), rule.getAction());
        }
      }
    }
    return result;
  }

  private static void addTransition(Map<String, List<Transition>> adjacency, String from, String to, String edgeId,
      ImpactAction action) {
    List<Transition> values = adjacency.get(from);
    if (values == null) {
      values = new ArrayList<Transition>();
      adjacency.put(from, values);
    }
    values.add(new Transition(to, edgeId, action));
  }

  private static void seed(EngineeringGraph graph, Map<String, State> states, Deque<String> queue, String nodeId,
      boolean direct, int depth, ImpactAction action, String reasonEdgeId) {
    State state = states.get(nodeId);
    if (state == null) {
      state = new State(direct, depth, Collections.singletonList(nodeId));
      states.put(nodeId, state);
      queue.addLast(nodeId);
    } else {
      state.direct = state.direct || direct;
      state.depth = Math.min(state.depth, depth);
    }
    state.actions.add(action);
    addNodeAction(graph.getNode(nodeId), state.actions);
    if (reasonEdgeId != null) {
      state.reasonEdgeIds.add(reasonEdgeId);
    }
  }

  private static void addNodeAction(EngineeringNode node, Set<ImpactAction> actions) {
    if (node.getKind() == EngineeringNode.Kind.CALCULATION) {
      actions.add(ImpactAction.RECALCULATE);
    } else if (node.getKind() == EngineeringNode.Kind.APPROVAL) {
      actions.add(ImpactAction.REAPPROVE);
    } else if (node.getKind() == EngineeringNode.Kind.DOCUMENT) {
      actions.add(ImpactAction.REVALIDATE);
    }
  }

  private static List<ImpactedEngineeringObject> impacts(EngineeringGraph graph, Map<String, State> states) {
    List<String> nodeIds = new ArrayList<String>(states.keySet());
    Collections.sort(nodeIds);
    List<ImpactedEngineeringObject> result = new ArrayList<ImpactedEngineeringObject>();
    for (String nodeId : nodeIds) {
      State state = states.get(nodeId);
      result.add(new ImpactedEngineeringObject(nodeId, graph.getNode(nodeId).getKind(), state.direct, state.depth,
          state.actions, state.path, state.reasonEdgeIds));
    }
    return result;
  }

  private static RecalculationPlan recalculationPlan(EngineeringGraph graph, Set<String> impactedNodeIds) {
    Set<String> calculations = new LinkedHashSet<String>();
    for (String nodeId : impactedNodeIds) {
      if (graph.getNode(nodeId).getKind() == EngineeringNode.Kind.CALCULATION) {
        calculations.add(nodeId);
      }
    }
    Map<String, Integer> indegree = new LinkedHashMap<String, Integer>();
    Map<String, List<String>> dependents = new LinkedHashMap<String, List<String>>();
    for (String calculation : calculations) {
      indegree.put(calculation, Integer.valueOf(0));
      dependents.put(calculation, new ArrayList<String>());
    }
    for (EngineeringEdge edge : graph.getEdges().values()) {
      if (edge.getKind() == EngineeringEdge.Kind.DEPENDS_ON && calculations.contains(edge.getSourceId())
          && calculations.contains(edge.getTargetId())) {
        dependents.get(edge.getTargetId()).add(edge.getSourceId());
        indegree.put(edge.getSourceId(), Integer.valueOf(indegree.get(edge.getSourceId()).intValue() + 1));
      }
    }
    PriorityQueue<String> ready = new PriorityQueue<String>();
    for (Map.Entry<String, Integer> entry : indegree.entrySet()) {
      if (entry.getValue().intValue() == 0) {
        ready.add(entry.getKey());
      }
    }
    List<String> order = new ArrayList<String>();
    while (!ready.isEmpty()) {
      String current = ready.remove();
      order.add(current);
      List<String> nextValues = dependents.get(current);
      Collections.sort(nextValues);
      for (String next : nextValues) {
        int value = indegree.get(next).intValue() - 1;
        indegree.put(next, Integer.valueOf(value));
        if (value == 0) {
          ready.add(next);
        }
      }
    }
    List<String> cycles = new ArrayList<String>();
    for (String calculation : calculations) {
      if (!order.contains(calculation)) {
        cycles.add(calculation);
      }
    }
    return new RecalculationPlan(order, cycles);
  }

  private static ImpactAnalysisRule rule(EngineeringEdge.Kind kind, Direction direction, ImpactAction action) {
    return new ImpactAnalysisRule(kind, direction, action);
  }

  private static final class Transition {
    private final String nextNodeId;
    private final String edgeId;
    private final ImpactAction action;

    private Transition(String nextNodeId, String edgeId, ImpactAction action) {
      this.nextNodeId = nextNodeId;
      this.edgeId = edgeId;
      this.action = action;
    }
  }

  private static final class State {
    private boolean direct;
    private int depth;
    private List<String> path;
    private final Set<ImpactAction> actions = EnumSet.noneOf(ImpactAction.class);
    private final Set<String> reasonEdgeIds = new LinkedHashSet<String>();

    private State(boolean direct, int depth, List<String> path) {
      this.direct = direct;
      this.depth = depth;
      this.path = new ArrayList<String>(path);
    }
  }

  private static final class RecalculationPlan {
    private final List<String> order;
    private final List<String> cycleNodeIds;

    private RecalculationPlan(List<String> order, List<String> cycleNodeIds) {
      this.order = order;
      this.cycleNodeIds = cycleNodeIds;
    }
  }
}
