package neqsim.process.processmodel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import neqsim.process.ProcessElementInterface;
import neqsim.process.dynamics.AdaptiveTransientOptions;
import neqsim.process.dynamics.AdaptiveTransientException;
import neqsim.process.dynamics.AdaptiveTransientResult;
import neqsim.process.dynamics.EventScheduler;
import neqsim.process.dynamics.TransientErrorStateBinding;
import neqsim.process.dynamics.TransientErrorStateVariable;
import neqsim.process.dynamics.TransientErrorStateVector;
import neqsim.process.dynamics.TransientStateParticipant;
import neqsim.process.dynamics.TransientStepIdentifier;
import neqsim.process.dynamics.TransientStepTransaction;

/**
 * Shared opt-in transactional step-doubling coordinator for process systems and models.
 *
 * <p>
 * Callers exclusively own the model, participants and pure numerical providers throughout a call. This coordinator
 * estimates local error; it does not replace a pressure-flow or DAE solve.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
final class AdaptiveTransientStepper {
  /** Safety multiplier for the explicitly selected integration order. */
  private static final double SAFETY = 0.9;

  /** Prevents construction of a static coordinator. */
  private AdaptiveTransientStepper() {
  }

  /**
   * Advances one standalone system.
   *
   * @param system exclusively owned process
   * @param requested requested upper timestep bound in seconds
   * @param parent parent physical interval identifier
   * @param options explicit numerical policy
   * @return accepted step evidence
   */
  static AdaptiveTransientResult advance(ProcessSystem system, double requested, UUID parent,
      AdaptiveTransientOptions options) {
    Map<String, ProcessSystem> areas = new LinkedHashMap<String, ProcessSystem>();
    areas.put("", system);
    return advance(new Target(null, areas), requested, parent, options);
  }

  /**
   * Advances one coordinated model.
   *
   * @param model exclusively owned multi-area model
   * @param requested requested upper timestep bound in seconds
   * @param parent parent physical interval identifier
   * @param options explicit numerical policy
   * @return accepted step evidence
   */
  static AdaptiveTransientResult advance(ProcessModel model, double requested, UUID parent,
      AdaptiveTransientOptions options) {
    if (model.getClass() != ProcessModel.class) {
      throw new IllegalStateException("Adaptive execution requires an audited concrete ProcessModel");
    }
    Map<String, ProcessSystem> areas = new LinkedHashMap<String, ProcessSystem>();
    for (String name : model.getProcessSystemNames()) {
      areas.put(name, model.get(name));
    }
    return advance(new Target(model, areas), requested, parent, options);
  }

  /**
   * Compares restored full and half-step trials and commits only accepted half-step state.
   *
   * @param target frozen model ownership and numerical coverage
   * @param requested requested upper bound in seconds
   * @param parent accepted interval identity
   * @param options explicit policy
   * @return immutable quantitative acceptance evidence
   */
  private static AdaptiveTransientResult advance(Target target, double requested, UUID parent,
      AdaptiveTransientOptions options) {
    ProcessSystem.validateTransientTimestep(requested);
    if (parent == null || options == null) {
      throw new IllegalArgumentException("Adaptive execution requires a parent identifier and options");
    }
    if (requested < options.getMinimumStepSeconds()) {
      throw new IllegalArgumentException("Requested timestep is below the configured minimum");
    }
    target.initialize(options, parent);
    TransientErrorStateVector initial = target.capture();
    double dt = Math.min(requested, options.getMaximumStepSeconds());
    dt = Math.min(dt, target.futureBoundary(target.startTime));
    double lastError = Double.NaN;
    String limiting = null;
    double lastAttempted = dt;
    for (int attempt = 0; attempt < options.getMaximumAttempts(); attempt++) {
      validateAdvance(target.startTime, dt, options.getMinimumStepSeconds(), attempt, lastError, limiting);
      lastAttempted = dt;
      UUID fullId = TransientStepIdentifier.deterministicAdaptivePhysicalStep(parent, attempt, 0);
      UUID firstId = TransientStepIdentifier.deterministicAdaptivePhysicalStep(parent, attempt, 1);
      UUID secondId = TransientStepIdentifier.deterministicAdaptivePhysicalStep(parent, attempt, 2);
      try {
        TransientErrorStateVector full;
        try (TransientStepTransaction transaction = target.begin()) {
          target.step(dt, fullId);
          full = target.capture();
          initial.compareTo(full);
          transaction.prepareCommit();
        }
        try (TransientStepTransaction transaction = target.begin()) {
          target.step(dt / 2.0, firstId);
          initial.compareTo(target.capture());
          double remaining = (target.startTime + dt) - target.areas.values().iterator().next().getTime();
          if (!(remaining > 0.0)) {
            throw new IllegalStateException("Adaptive second half step cannot advance to the common endpoint");
          }
          target.step(remaining, secondId);
          TransientErrorStateVector halves = target.capture();
          initial.compareTo(halves);
          TransientErrorStateVector.ErrorEstimate estimate = full.compareTo(halves);
          lastError = estimate.getNormalizedError();
          limiting = estimate.getLimitingVariable();
          transaction.prepareCommit();
          if (lastError <= 1.0) {
            double factor = lastError == 0.0 ? 2.0
                : SAFETY * Math.pow(lastError, -1.0 / (options.getMethodOrder() + 1.0));
            double next = Math.max(options.getMinimumStepSeconds(),
                Math.min(options.getMaximumStepSeconds(), dt * Math.max(0.2, Math.min(2.0, factor))));
            AdaptiveTransientResult result = new AdaptiveTransientResult(parent, dt, next, lastError, limiting,
                attempt + 1, attempt, firstId, secondId);
            target.acceptProvenance(parent, next);
            transaction.commit();
            return result;
          }
        }
        // Both trial transactions have restored their original state before choosing a retry.
        double factor = SAFETY * Math.pow(lastError, -1.0 / (options.getMethodOrder() + 1.0));
        double reduced = Math.max(options.getMinimumStepSeconds(), dt * Math.max(0.2, Math.min(0.9, factor)));
        if (!(reduced < dt)) {
          throw new AdaptiveTransientException(AdaptiveTransientException.Reason.MINIMUM_STEP,
              "Adaptive minimum timestep exhausted; error=" + lastError + ", limiting variable=" + limiting,
              attempt + 1, dt, lastError, limiting);
        }
        dt = reduced;
      } catch (EventBoundary boundary) {
        // A scoped start action or equipment operation introduced an earlier event.
        if (boundary.getSuppressed().length != 0) {
          throw new IllegalStateException("Adaptive event retry could not restore trial state", boundary);
        }
        if (!(boundary.interval < dt)) {
          throw new IllegalStateException("New event does not provide a smaller representable interval", boundary);
        }
        dt = boundary.interval;
      }
    }
    throw new AdaptiveTransientException(
        AdaptiveTransientException.Reason.ATTEMPT_LIMIT, "Adaptive attempt limit exhausted; attempts="
            + options.getMaximumAttempts() + ", last error=" + lastError + ", limiting variable=" + limiting,
        options.getMaximumAttempts(), lastAttempted, lastError, limiting);
  }

  /**
   * Rejects steps that cannot advance both half steps in floating-point time.
   *
   * @param start current absolute time in seconds
   * @param dt proposed interval in seconds
   * @param minimum explicit lower timestep limit in seconds
   * @param attempts number of completed rejected attempts
   * @param error last available normalized error
   * @param limiting last available limiting variable
   */
  private static void validateAdvance(double start, double dt, double minimum, int attempts, double error,
      String limiting) {
    double half = dt / 2.0;
    if (!Double.isFinite(dt) || dt < minimum || !(half > 0.0) || !Double.isFinite(start + dt) || !(start + half > start)
        || !(start + dt > start + half)) {
      String message = "Adaptive interval cannot advance within configured/representable bounds: " + dt;
      if (Double.isFinite(dt) && dt > 0.0) {
        throw new AdaptiveTransientException(dt < minimum ? AdaptiveTransientException.Reason.MINIMUM_STEP
            : AdaptiveTransientException.Reason.UNREPRESENTABLE_INTERVAL, message, attempts, dt, error, limiting);
      }
      throw new IllegalStateException(message);
    }
  }

  /**
   * Internal signal to restore a trial before localizing a newly inserted event.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class EventBoundary extends RuntimeException {
    private static final long serialVersionUID = 1000L;
    /** New event distance from the original trial start, in seconds. */
    private final double interval;

    /**
     * Creates an event-localization retry signal.
     *
     * @param interval reduced distance from the original start
     */
    private EventBoundary(double interval) {
      super("A new event requires an interval of " + interval + " s");
      this.interval = interval;
    }
  }

  /**
   * Frozen area, participant and provider identities for all trials of one accepted interval.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class Target {
    /** Optional coordinated model, absent for a standalone system. */
    private final ProcessModel model;
    /** Frozen deterministic area membership. */
    private final Map<String, ProcessSystem> areas;
    /** Frozen participant identities and provider bindings in area order. */
    private final List<BoundState> states = new ArrayList<BoundState>();
    /** Frozen per-area element registration. */
    private final Map<ProcessSystem, List<ProcessElementInterface>> elements = new IdentityHashMap<ProcessSystem, List<ProcessElementInterface>>();
    /** Frozen scheduler membership, including shared scheduler aliases. */
    private final Map<ProcessSystem, EventScheduler> schedulers = new IdentityHashMap<ProcessSystem, EventScheduler>();
    /** Original common clock in seconds. */
    private double startTime;

    /**
     * Captures the area references without mutating the process.
     *
     * @param model optional multi-area owner
     * @param areas stable area references
     */
    private Target(ProcessModel model, Map<String, ProcessSystem> areas) {
      this.model = model;
      this.areas = areas;
    }

    /**
     * Validates clock, transaction and explicit numerical provider coverage before trials.
     *
     * @param options explicit bindings
     * @param parent accepted parent identity, which must be new
     */
    private void initialize(AdaptiveTransientOptions options, UUID parent) {
      if (areas.isEmpty()) {
        throw new IllegalStateException("Adaptive execution requires a nonempty process model");
      }
      if (model == null) {
        areas.values().iterator().next().getTransientTransactionCoverage().assertComplete();
      } else {
        model.getTransientTransactionCoverage().assertComplete();
      }
      Map<TransientStateParticipant<?>, TransientErrorStateBinding> bindings = new IdentityHashMap<TransientStateParticipant<?>, TransientErrorStateBinding>();
      for (TransientErrorStateBinding binding : options.getBindings()) {
        if (bindings.put(binding.getParticipant(), binding) != null) {
          throw new IllegalArgumentException("Duplicate numerical provider for one participant");
        }
      }
      Set<ProcessElementInterface> seen = Collections
          .newSetFromMap(new IdentityHashMap<ProcessElementInterface, Boolean>());
      startTime = areas.values().iterator().next().getTime();
      if (!Double.isFinite(startTime)) {
        throw new IllegalStateException("Adaptive process time must be finite");
      }
      for (Map.Entry<String, ProcessSystem> area : areas.entrySet()) {
        ProcessSystem process = area.getValue();
        if (process.getClass() != ProcessSystem.class) {
          throw new IllegalStateException("Adaptive execution requires audited concrete ProcessSystem areas");
        }
        if (Double.compare(process.getTime(), startTime) != 0) {
          throw new IllegalStateException("Adaptive process-area clocks must be exactly aligned");
        }
        if (parent.equals(process.getCalculationIdentifier())) {
          throw new IllegalArgumentException("Parent physical interval identifier was already accepted");
        }
        List<ProcessElementInterface> members = process.getUniqueTransientElements();
        elements.put(process, members);
        schedulers.put(process, process.getEventScheduler());
        for (ProcessElementInterface element : members) {
          if (!seen.add(element)) {
            throw new IllegalStateException("Adaptive areas share a mutable participant: " + element.getName());
          }
          TransientStateParticipant<?> participant = (TransientStateParticipant<?>) element;
          TransientErrorStateBinding binding = bindings.remove(participant);
          if (binding == null) {
            throw new IllegalArgumentException(
                "Missing numerical provider for " + participant.getTransientStateIdentity());
          }
          states.add(new BoundState(area.getKey(), binding));
        }
      }
      if (!bindings.isEmpty()) {
        throw new IllegalArgumentException("Numerical provider is not bound to a participant in this process");
      }
    }

    /**
     * Captures explicit numerical variables and checks frozen ownership/schema inputs.
     *
     * @return area-qualified deterministic numerical state
     */
    private TransientErrorStateVector capture() {
      validateStructure();
      List<TransientErrorStateVariable> values = new ArrayList<TransientErrorStateVariable>();
      for (BoundState state : states) {
        String identity = state.binding.getParticipant().getTransientStateIdentity();
        if (!state.owner.equals(identity)) {
          throw new IllegalArgumentException("Numerical owner identity changed: " + state.owner);
        }
        List<TransientErrorStateVariable> variables = state.binding.getProvider().getTransientErrorState();
        if (variables == null || variables.isEmpty()) {
          throw new IllegalArgumentException("Empty numerical provider for " + identity);
        }
        for (TransientErrorStateVariable variable : variables) {
          if (variable == null || !state.owner.equals(variable.getOwnerIdentity())) {
            throw new IllegalArgumentException("Numerical variable does not belong to bound owner " + state.owner);
          }
          String qualifiedOwner = state.area.length() + ":" + state.area + ":" + identity;
          values.add(new TransientErrorStateVariable(qualifiedOwner, variable.getVariableIdentity(), variable.getUnit(),
              variable.getValue(), variable.getAbsoluteTolerance(), variable.getRelativeTolerance()));
        }
      }
      return new TransientErrorStateVector(values);
    }

    /**
     * Checks frozen area, element and scheduler identity without changing model state.
     */
    private void validateStructure() {
      if (model != null && !model.getProcessSystemNames().equals(new ArrayList<String>(areas.keySet()))) {
        throw new IllegalStateException("Adaptive model area membership changed");
      }
      for (Map.Entry<String, ProcessSystem> area : areas.entrySet()) {
        ProcessSystem process = area.getValue();
        if ((model != null && model.get(area.getKey()) != process)
            || !process.getUniqueTransientElements().equals(elements.get(process))
            || process.getEventScheduler() != schedulers.get(process)) {
          throw new IllegalStateException("Adaptive process element or scheduler membership changed");
        }
      }
    }

    /**
     * Opens an existing identity-preserving transaction over the whole target.
     *
     * @return single-use transaction
     */
    private TransientStepTransaction begin() {
      validateStructure();
      return model == null ? areas.values().iterator().next().beginTransientStepTransaction()
          : model.beginTransientStepTransaction();
    }

    /**
     * Runs shared due events before any area integrates, then checks new event boundaries.
     *
     * @param dt one full or half physical timestep in seconds
     * @param id distinct physical child identity
     */
    private void step(double dt, UUID id) {
      validateStructure();
      double now = areas.values().iterator().next().getTime();
      Set<EventScheduler> seen = Collections.newSetFromMap(new IdentityHashMap<EventScheduler, Boolean>());
      for (ProcessSystem area : areas.values()) {
        EventScheduler scheduler = area.getEventScheduler();
        if (scheduler != null && seen.add(scheduler)) {
          scheduler.fireDueEvents(now);
        }
      }
      validateStructure();
      double boundary = futureBoundary(now);
      if (boundary < dt) {
        throw new EventBoundary((now - startTime) + boundary);
      }
      for (ProcessSystem area : areas.values()) {
        area.runTransientAdaptiveTrial(dt, id);
      }
      validateStructure();
      double end = areas.values().iterator().next().getTime();
      if (Double.compare(end, now + dt) != 0) {
        throw new IllegalStateException("Adaptive trial did not advance the requested interval");
      }
      for (ProcessSystem area : areas.values()) {
        if (!Double.isFinite(area.getTime()) || Double.compare(area.getTime(), end) != 0) {
          throw new IllegalStateException("Adaptive trial did not retain aligned finite area clocks");
        }
        EventScheduler scheduler = area.getEventScheduler();
        if (scheduler != null) {
          for (EventScheduler.ScheduledEvent event : scheduler.getPendingEvents()) {
            if (event.getTime() < end) {
              throw new EventBoundary(event.getTime() - startTime);
            }
          }
        }
      }
    }

    /**
     * Finds the earliest strictly future event among all area schedulers.
     *
     * @param now current interval start in seconds
     * @return event distance or positive infinity when none is future
     */
    private double futureBoundary(double now) {
      double distance = Double.POSITIVE_INFINITY;
      for (ProcessSystem area : areas.values()) {
        EventScheduler scheduler = area.getEventScheduler();
        if (scheduler != null) {
          for (EventScheduler.ScheduledEvent event : scheduler.getPendingEvents()) {
            if (event.getTime() > now) {
              distance = Math.min(distance, event.getTime() - now);
            }
          }
        }
      }
      return distance;
    }

    /**
     * Retains parent provenance while participants retain their accepted physical child identities.
     *
     * @param parent caller's accepted interval identity
     * @param next suggested following timestep in seconds
     */
    private void acceptProvenance(UUID parent, double next) {
      for (ProcessSystem area : areas.values()) {
        area.setCalculationIdentifier(parent);
        area.setTimeStep(next);
      }
    }
  }

  /**
   * Identity-bound numerical provider with stable area/local owner names.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class BoundState {
    /** Stable model area name. */
    private final String area;
    /** Stable local transaction identity. */
    private final String owner;
    /** Caller-supplied pure numerical provider. */
    private final TransientErrorStateBinding binding;

    /**
     * Freezes the binding identity for one accepted interval.
     *
     * @param area stable area name
     * @param binding participant and pure provider
     */
    private BoundState(String area, TransientErrorStateBinding binding) {
      this.area = area;
      this.binding = binding;
      this.owner = binding.getParticipant().getTransientStateIdentity();
    }
  }
}
