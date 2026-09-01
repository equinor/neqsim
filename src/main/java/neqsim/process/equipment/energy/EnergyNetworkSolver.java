package neqsim.process.equipment.energy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.stream.EnergyBus;
import neqsim.process.equipment.stream.EnergyNetworkReport;
import neqsim.util.validation.ValidationResult;

/**
 * Process equipment that solves one or more {@link EnergyBus} objects between energy producers and consumers.
 *
 * <p>
 * Adding this unit to a {@code ProcessSystem} makes allocation an explicit calculation step. The process graph orders
 * calculated producers before this solver and specification or balance consumers after it.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class EnergyNetworkSolver extends ProcessEquipmentBaseClass {
  private static final long serialVersionUID = 1000L;

  private final List<EnergyBus> energyBuses = new ArrayList<EnergyBus>();
  private final List<EnergyNetworkReport> reports = new ArrayList<EnergyNetworkReport>();

  /**
   * Creates an empty network solver.
   *
   * @param name equipment name
   */
  public EnergyNetworkSolver(String name) {
    super(name);
  }

  /**
   * Creates a network solver for one bus.
   *
   * @param name equipment name
   * @param energyBus energy bus to solve
   */
  public EnergyNetworkSolver(String name, EnergyBus energyBus) {
    this(name);
    addEnergyBus(energyBus);
  }

  /**
   * Adds an energy bus.
   *
   * @param energyBus energy bus to solve
   */
  public void addEnergyBus(EnergyBus energyBus) {
    if (energyBus == null) {
      throw new IllegalArgumentException("Energy bus cannot be null");
    }
    for (EnergyBus existing : energyBuses) {
      if (existing == energyBus) {
        return;
      }
    }
    energyBuses.add(energyBus);
  }

  /**
   * Gets configured energy buses.
   *
   * @return immutable energy-bus list
   */
  public List<EnergyBus> getEnergyBuses() {
    return Collections.unmodifiableList(energyBuses);
  }

  /**
   * Gets report snapshots created by the most recent solver run.
   *
   * <p>
   * Reports are retained by this solver rather than re-read from mutable bus state, so solving one of the buses outside
   * this unit does not change the meaning of "most recent run".
   * </p>
   *
   * @return immutable defensive copy of the report list
   */
  public List<EnergyNetworkReport> getReports() {
    return Collections.unmodifiableList(new ArrayList<EnergyNetworkReport>(reports));
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    reports.clear();
    for (EnergyBus energyBus : energyBuses) {
      reports.add(energyBus.solveBalance());
    }
    setCalculationIdentifier(id);
  }

  /**
   * Re-solves the algebraic energy-bus allocation for a physical timestep.
   *
   * <p>
   * Repeated nonlinear/refinement evaluations with the same non-null calculation identifier still recalculate the bus
   * balance, but advance this solver's local clock only once for that physical timestep. A null identifier preserves
   * the legacy behavior and advances the local clock on every successful evaluation.
   * </p>
   *
   * @param dt physical timestep in seconds
   * @param id physical-step calculation identifier, or null for legacy uncoalesced timing
   */
  @Override
  public void runTransient(double dt, UUID id) {
    boolean alreadyEvaluatedForStep = id != null && id.equals(getCalculationIdentifier());
    for (EnergyBus energyBus : energyBuses) {
      energyBus.clearRealizedBalancePowers();
    }
    run(id);
    if (!alreadyEvaluatedForStep) {
      increaseTime(dt);
    }
  }

  /** {@inheritDoc} */
  @Override
  public ValidationResult validateSetup() {
    ValidationResult result = new ValidationResult(getName());
    if (getName() == null || getName().trim().isEmpty()) {
      result.addError("equipment", "Energy network solver has no name", "Set a name in the constructor");
    }
    if (energyBuses.isEmpty()) {
      result.addError("energy", "No energy buses are configured", "Call addEnergyBus(bus)");
    }
    for (EnergyBus energyBus : energyBuses) {
      if (energyBus.getRegisteredPorts().isEmpty()) {
        result.addWarning("energy", "Energy bus " + energyBus.getName() + " has no connected ports",
            "Connect producer and consumer energy ports to the bus");
      }
    }
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(getReports());
  }
}
