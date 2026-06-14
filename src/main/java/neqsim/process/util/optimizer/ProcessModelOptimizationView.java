package neqsim.process.util.optimizer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.capacity.CapacityConstrainedEquipment;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Read-through adapter that presents a multi-area {@link ProcessModel} as a single
 * {@link ProcessSystem} so that the existing {@link ProductionOptimizer} engine can optimize a
 * whole plant without any change to its proven single-{@code ProcessSystem} search and evaluation
 * logic.
 *
 * <p>
 * The adapter overrides only the three operations the optimizer engine performs on a
 * {@code ProcessSystem}:
 * </p>
 * <ul>
 * <li>{@link #run(UUID)} &mdash; delegates to {@link ProcessModel#runUntilConverged(int, double)}
 * so cross-area recycles and shared streams converge between iterations (instead of a single
 * {@code ProcessSystem.run()} pass).</li>
 * <li>{@link #getUnitOperations()} &mdash; returns the units of every area concatenated in area
 * insertion order, so the optimizer's capacity / bottleneck scan covers the entire plant.</li>
 * <li>{@link #getConstrainedEquipment()} &mdash; aggregates capacity-constrained equipment across
 * all areas.</li>
 * </ul>
 *
 * <p>
 * Because the inherited {@link ProcessSystem#getUnit(String)} is implemented in terms of
 * {@link #getUnitOperations()}, manipulated-variable setters and objective / constraint functions
 * written against the {@code ProcessSystem} API (for example {@code proc.getUnit("Compressor")})
 * resolve units across all areas with no API change. Area-qualified addresses of the form
 * {@code "Area::Unit"} are also supported.
 * </p>
 *
 * <p>
 * This class is purely additive: it does not modify {@link ProcessModel} or {@link ProcessSystem}.
 * The adapter is a read-through view &mdash; it never adds units to its own internal list and never
 * calls {@code super.run()}.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @see ProductionOptimizer
 * @see ProcessModel
 */
public class ProcessModelOptimizationView extends ProcessSystem {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(ProcessModelOptimizationView.class);

  /** Separator used in area-qualified unit addresses ({@code "Area::Unit"}). */
  private static final String AREA_SEPARATOR = "::";

  /** The multi-area plant this view delegates to. */
  private final ProcessModel model;

  /** Maximum cross-area convergence iterations passed to {@code runUntilConverged}. */
  private final int maxIterations;

  /** Convergence tolerance passed to {@code runUntilConverged}. */
  private final double tolerance;

  /**
   * Creates a view over the given model using robust default convergence settings (50 iterations,
   * tolerance 5e-3, which is robust for plants with near-zero-flow anti-surge recycles).
   *
   * @param model the multi-area plant to optimize (must not be null)
   */
  public ProcessModelOptimizationView(ProcessModel model) {
    this(model, 50, 5.0e-3);
  }

  /**
   * Creates a view over the given model with explicit convergence settings.
   *
   * @param model the multi-area plant to optimize (must not be null)
   * @param maxIterations maximum cross-area convergence iterations (must be greater than zero)
   * @param tolerance convergence tolerance for {@code runUntilConverged} (must be greater than
   *        zero)
   */
  public ProcessModelOptimizationView(ProcessModel model, int maxIterations, double tolerance) {
    super("ProcessModelOptimizationView");
    if (model == null) {
      throw new NullPointerException("ProcessModel is required");
    }
    if (maxIterations <= 0) {
      throw new IllegalArgumentException("maxIterations must be greater than zero");
    }
    if (tolerance <= 0.0) {
      throw new IllegalArgumentException("tolerance must be greater than zero");
    }
    this.model = model;
    this.maxIterations = maxIterations;
    this.tolerance = tolerance;
  }

  /**
   * Returns the multi-area plant backing this view.
   *
   * @return the wrapped {@link ProcessModel}
   */
  public ProcessModel getModel() {
    return model;
  }

  /**
   * Runs the whole plant to cross-area convergence instead of performing a single
   * {@code ProcessSystem.run()} pass.
   *
   * @param id calculation identifier (unused; convergence is managed by the model)
   */
  @Override
  public synchronized void run(UUID id) {
    boolean converged = model.runUntilConverged(maxIterations, tolerance);
    if (!converged) {
      logger.warn(
          "ProcessModel did not reach cross-area convergence within {} iterations (tolerance {})."
              + " Optimizer will evaluate the unconverged state.",
          maxIterations, tolerance);
    }
  }

  /**
   * Returns the unit operations of every area, concatenated in area insertion order.
   *
   * @return an aggregated list of all unit operations across the plant
   */
  @Override
  public List<ProcessEquipmentInterface> getUnitOperations() {
    List<ProcessEquipmentInterface> all = new ArrayList<ProcessEquipmentInterface>();
    for (ProcessSystem area : model.getAllProcesses()) {
      all.addAll(area.getUnitOperations());
    }
    return all;
  }

  /**
   * Returns capacity-constrained equipment aggregated across every area.
   *
   * @return an aggregated list of capacity-constrained equipment across the plant
   */
  @Override
  public List<CapacityConstrainedEquipment> getConstrainedEquipment() {
    List<CapacityConstrainedEquipment> all = new ArrayList<CapacityConstrainedEquipment>();
    for (ProcessSystem area : model.getAllProcesses()) {
      all.addAll(area.getConstrainedEquipment());
    }
    return all;
  }

  /**
   * Resolves a unit by name across all areas. Area-qualified addresses of the form
   * {@code "Area::Unit"} are resolved against the named area first; plain names fall back to a
   * plant-wide search via the inherited {@link ProcessSystem#getUnit(String)} (which iterates
   * {@link #getUnitOperations()}).
   *
   * @param name the unit name, optionally area-qualified as {@code "Area::Unit"}
   * @return the matching unit, or {@code null} if no unit matches
   */
  @Override
  public ProcessEquipmentInterface getUnit(String name) {
    if (name != null && name.contains(AREA_SEPARATOR)) {
      int idx = name.indexOf(AREA_SEPARATOR);
      String areaName = name.substring(0, idx);
      String unitName = name.substring(idx + AREA_SEPARATOR.length());
      ProcessSystem area = model.get(areaName);
      return area != null ? area.getUnit(unitName) : null;
    }

    for (ProcessSystem area : model.getAllProcesses()) {
      ProcessEquipmentInterface unit = area.getUnit(name);
      if (unit != null) {
        return unit;
      }
    }
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public ProcessEquipmentInterface getBottleneck() {
    return model.getBottleneck();
  }

  /** {@inheritDoc} */
  @Override
  public double getBottleneckUtilization() {
    return model.getBottleneckUtilization();
  }

  /** {@inheritDoc} */
  @Override
  public neqsim.process.equipment.capacity.BottleneckResult findBottleneck() {
    return model.findBottleneck();
  }

  /** {@inheritDoc} */
  @Override
  public boolean isAnyEquipmentOverloaded() {
    return model.isAnyEquipmentOverloaded();
  }

  /** {@inheritDoc} */
  @Override
  public boolean isAnyHardLimitExceeded() {
    return model.isAnyHardLimitExceeded();
  }

  /** {@inheritDoc} */
  @Override
  public String getUtilizationSnapshotJson() {
    return model.getUtilizationSnapshotJson();
  }
}
