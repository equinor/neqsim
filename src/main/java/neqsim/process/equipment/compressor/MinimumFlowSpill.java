package neqsim.process.equipment.compressor;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.capacity.CapacityConstrainedEquipment;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.validation.ValidationResult;

/**
 * Algebraic steady-state compressor circulation at a prescribed minimum suction flow.
 *
 * <p>
 * The internal compressor receives max(net feed, minimum flow). The excess discharge is returned internally with the
 * suction composition and cooled to the net-feed temperature. Only the net forward discharge is an external outlet;
 * internal spill is not a new feed or an external loss. The compressor power is calculated at the gross flow, and the
 * ideal return cooler duty is reported separately. No Recycle iteration is required.
 * </p>
 *
 * <p>
 * This is an imposed minimum-flow screening model, not a dynamic anti-surge controller. It assumes the return has the
 * same composition as the feed and no condensation or separation changes that composition. Use an explicit
 * valve/cooler/recycle model when those effects matter. Disable the compressor's internal AntiSurge flow modification.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class MinimumFlowSpill extends ProcessEquipmentBaseClass implements CapacityConstrainedEquipment {
  private static final long serialVersionUID = 1000L;
  private final StreamInterface inletStream;
  private final Stream suctionStream;
  private final Stream outletStream;
  private final Stream spillStream;
  private final Compressor compressor;
  private double minimumInletFlow = 0.0;
  private String minimumInletFlowUnit = "kg/sec";
  private double spillCoolingDuty = 0.0;

  /**
   * Creates a compressor assembly with one net inlet and one forward outlet.
   *
   * @param name assembly name
   * @param inletStream net feed stream
   * @throws IllegalArgumentException if the inlet has no thermodynamic system
   */
  public MinimumFlowSpill(String name, StreamInterface inletStream) {
    super(name);
    if (inletStream == null || inletStream.getThermoSystem() == null) {
      throw new IllegalArgumentException("Connect a net feed with a thermodynamic system");
    }
    this.inletStream = inletStream;
    suctionStream = new Stream(name + " gross suction", inletStream.getThermoSystem().clone());
    outletStream = new Stream(name + " forward discharge", inletStream.getThermoSystem().clone());
    spillStream = new Stream(name + " internal spill", inletStream.getThermoSystem().clone());
    compressor = new Compressor(name + " compressor", suctionStream);
  }

  /**
   * Sets the imposed minimum gross suction flow.
   *
   * @param value non-negative flow
   * @param unit NeqSim flow unit, evaluated at suction conditions for actual volume
   * @throws IllegalArgumentException if the flow is invalid or the unit is unsupported
   */
  public void setMinimumInletFlow(double value, String unit) {
    if (!Double.isFinite(value) || value < 0.0 || unit == null) {
      throw new IllegalArgumentException("Minimum inlet flow must be finite, non-negative and have a flow unit");
    }
    // Validate units before altering the specification.
    inletStream.getThermoSystem().getFlowRate(unit);
    minimumInletFlow = value;
    minimumInletFlowUnit = unit;
  }

  /**
   * Returns internal compressor for pressure, efficiency and chart configuration.
   *
   * @return internal compressor for pressure, efficiency and chart configuration
   */
  public Compressor getCompressor() {
    return compressor;
  }

  /**
   * Returns net feed stream.
   *
   * @return net feed stream
   */
  public StreamInterface getInletStream() {
    return inletStream;
  }

  /**
   * Returns gross suction stream used by the compressor.
   *
   * @return gross suction stream used by the compressor
   */
  public StreamInterface getSuctionStream() {
    return suctionStream;
  }

  /**
   * Returns net forward stream at compressor discharge conditions.
   *
   * @return net forward stream at compressor discharge conditions
   */
  public StreamInterface getOutletStream() {
    return outletStream;
  }

  /**
   * Returns the internal spill at discharge conditions for inspection.
   *
   * @return internal stream, excluded from external material balance outlets
   */
  public StreamInterface getSpillStream() {
    return spillStream;
  }

  /**
   * Gets the net feed flow on the suction basis.
   *
   * @param unit requested flow unit
   * @return net flow
   */
  public double getNetFlow(String unit) {
    return inletStream.getFlowRate(unit);
  }

  /**
   * Gets spill flow on the suction basis, including actual-volume units.
   *
   * @param unit requested flow unit
   * @return internal circulation flow
   */
  public double getSpillFlow(String unit) {
    double grossMass = suctionStream.getFlowRate("kg/sec");
    if (!(grossMass > 0.0)) {
      return 0.0;
    }
    return suctionStream.getFlowRate(unit) * spillStream.getFlowRate("kg/sec") / grossMass;
  }

  /**
   * Gets the ideal heat rejection needed to restore spill to the net-feed suction state.
   *
   * @param unit W, kW or MW
   * @return positive heat rejected, negative heat required
   * @throws IllegalArgumentException if the duty unit is unsupported
   */
  public double getSpillCoolingDuty(String unit) {
    if ("W".equals(unit)) {
      return spillCoolingDuty;
    }
    if ("kW".equals(unit)) {
      return spillCoolingDuty / 1000.0;
    }
    if ("MW".equals(unit)) {
      return spillCoolingDuty / 1e6;
    }
    throw new IllegalArgumentException("Use W, kW or MW for the spill cooling duty");
  }

  /**
   * Gets compressor power for the gross circulating flow.
   *
   * @param unit W, kW or MW
   * @return compressor power
   */
  public double getPower(String unit) {
    return suctionStream.getFlowRate("kg/sec") > 0.0 ? compressor.getPower(unit) : 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface getThermoSystem() {
    return outletStream.getThermoSystem();
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (compressor.getInletStream() != suctionStream) {
      throw new IllegalStateException("Configure the internal compressor without replacing its gross suction stream");
    }
    if (compressor.getAntiSurge().isActive()) {
      throw new IllegalStateException("Disable internal AntiSurge flow modification when using MinimumFlowSpill");
    }
    double netMass = inletStream.getFlowRate("kg/sec");
    if (!Double.isFinite(netMass) || netMass < 0.0) {
      throw new IllegalArgumentException("Net feed flow must be finite and non-negative");
    }
    SystemInterface suction = inletStream.getThermoSystem().clone();
    // Empty fluids retain z but cannot be scaled from their zero component inventories.
    if (netMass == 0.0 && minimumInletFlow > 0.0) {
      double[] composition = suction.getMolarComposition();
      suction.setEmptyFluid();
      for (int i = 0; i < composition.length; i++) {
        suction.addComponent(i, composition[i]);
      }
    }
    if (inletStream.getFlowRate(minimumInletFlowUnit) < minimumInletFlow) {
      suction.setTotalFlowRate(minimumInletFlow, minimumInletFlowUnit);
    }
    suctionStream.setThermoSystem(suction);
    suctionStream.run(id);
    if (suctionStream.getFlowRate("kg/sec") > 0.0) {
      suctionStream.getThermoSystem().init(2);
    }
    compressor.run(id);
    double grossMass = suctionStream.getFlowRate("kg/sec");
    double spillMass = Math.max(grossMass - netMass, 0.0);
    SystemInterface discharge = compressor.getOutletStream().getThermoSystem();
    if (Math.abs(discharge.getFlowRate("kg/sec") - grossMass) > 1e-8 * Math.max(1.0, grossMass)) {
      throw new IllegalStateException("Internal compressor changed the imposed gross flow; use an explicit recycle");
    }
    spillCoolingDuty = grossMass > 0.0 ? getPower("W") * spillMass / grossMass : 0.0;
    SystemInterface forward = discharge.clone();
    // Preserve the flashed phase count and beta values. setTotalFlowRate invokes
    // init(0), which resets a single-phase discharge to two phases before init(2).
    forward.setTotalNumberOfMoles(grossMass > 0.0 ? discharge.getTotalNumberOfMoles() * netMass / grossMass : 0.0);
    if (netMass > 0.0) {
      forward.init(2);
    }
    outletStream.setThermoSystem(forward);
    SystemInterface spill = discharge.clone();
    spill.setTotalNumberOfMoles(grossMass > 0.0 ? discharge.getTotalNumberOfMoles() * spillMass / grossMass : 0.0);
    if (spillMass > 0.0) {
      spill.init(2);
    }
    spillStream.setThermoSystem(spill);
    outletStream.setCalculationIdentifier(id);
    spillStream.setCalculationIdentifier(id);
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public List<StreamInterface> getInletStreams() {
    return Collections.singletonList(inletStream);
  }

  /** {@inheritDoc} */
  @Override
  public List<StreamInterface> getOutletStreams() {
    return Collections.singletonList(outletStream);
  }

  /** {@inheritDoc} */
  @Override
  public double getMassBalance(String unit) {
    return outletStream.getFlowRate(unit) - inletStream.getFlowRate(unit);
  }

  /** {@inheritDoc} */
  @Override
  public ValidationResult validateSetup() {
    ValidationResult result = new ValidationResult(getName());
    if (compressor.getAntiSurge().isActive()) {
      result.addError("antisurge", "Two flow controllers would modify the gross flow",
          "Disable compressor.getAntiSurge().setActive(false) before using imposed minimum flow");
    }
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isCapacityAnalysisEnabled() {
    return compressor.isCapacityAnalysisEnabled();
  }

  /** {@inheritDoc} */
  @Override
  public void setCapacityAnalysisEnabled(boolean enabled) {
    compressor.setCapacityAnalysisEnabled(enabled);
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, CapacityConstraint> getCapacityConstraints() {
    return compressor.getCapacityConstraints();
  }

  /** {@inheritDoc} */
  @Override
  public CapacityConstraint getBottleneckConstraint() {
    return compressor.getBottleneckConstraint();
  }

  /** {@inheritDoc} */
  @Override
  public boolean isCapacityExceeded() {
    return compressor.isCapacityExceeded();
  }

  /** {@inheritDoc} */
  @Override
  public boolean isHardLimitExceeded() {
    return compressor.isHardLimitExceeded();
  }

  /** {@inheritDoc} */
  @Override
  public double getMaxUtilization() {
    return compressor.getMaxUtilization();
  }

  /** {@inheritDoc} */
  @Override
  public void addCapacityConstraint(CapacityConstraint constraint) {
    compressor.addCapacityConstraint(constraint);
  }

  /** {@inheritDoc} */
  @Override
  public boolean removeCapacityConstraint(String name) {
    return compressor.removeCapacityConstraint(name);
  }

  /** {@inheritDoc} */
  @Override
  public void clearCapacityConstraints() {
    compressor.clearCapacityConstraints();
  }
}
