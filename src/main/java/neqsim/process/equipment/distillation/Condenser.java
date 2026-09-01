package neqsim.process.equipment.distillation;

import java.util.UUID;
import neqsim.process.equipment.stream.EnergyPortDirection;
import neqsim.process.equipment.stream.EnergyPortMode;
import neqsim.process.equipment.stream.EnergyType;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Condenser class.
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class Condenser extends SimpleTray {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private double refluxRatio = 0.1;
  boolean refluxIsSet = false;
  double duty = 0.0;
  boolean totalCondenser = false;
  Splitter mixedStreamSplitter = null;
  private boolean separation_with_liquid_reflux = false;
  private double reflux_value;
  private String reflux_unit;
  /** Relative shortfall of the latest fixed liquid reflux split. */
  private double lastFixedLiquidRefluxResidual = Double.NaN;
  /** Available condensate before the latest fixed liquid reflux split, in the configured unit. */
  private double lastAvailableLiquidReflux = Double.NaN;
  /** Actual returned reflux after the latest fixed liquid reflux split, in the configured unit. */
  private double lastFixedLiquidReflux = Double.NaN;
  /** Relative tolerance used to accept a fixed liquid reflux specification. */
  private static final double FIXED_LIQUID_REFLUX_RELATIVE_TOLERANCE = 1.0e-9;

  /**
   * Constructor for the Condenser class.
   *
   * @param name a {@link java.lang.String} object
   */
  public Condenser(String name) {
    super(name);
    registerEnergyPort("heatDuty", EnergyType.HEAT, EnergyPortDirection.OUTPUT, EnergyPortMode.CALCULATED);
  }

  /**
   * Checks if the separation process involves liquid reflux.
   *
   * @return {@code true} if the separation process involves liquid reflux, {@code false} otherwise.
   */
  public boolean isSeparation_with_liquid_reflux() {
    return separation_with_liquid_reflux;
  }

  /**
   * Get the configured fixed liquid reflux value.
   *
   * @return fixed liquid reflux in {@link #getFixedLiquidRefluxUnit()}
   */
  public double getFixedLiquidRefluxValue() {
    return reflux_value;
  }

  /**
   * Get the configured fixed liquid reflux unit.
   *
   * @return configured flow-rate unit, or {@code null} before the mode has been configured
   */
  public String getFixedLiquidRefluxUnit() {
    return reflux_unit;
  }

  /**
   * Get the available condensate before the latest fixed liquid reflux split.
   *
   * @return available condensate in {@link #getFixedLiquidRefluxUnit()}, or {@link Double#NaN} when no fixed split has
   * been run
   */
  public double getLastAvailableFixedLiquidReflux() {
    return lastAvailableLiquidReflux;
  }

  /**
   * Get the actual reflux returned by the latest fixed liquid reflux split.
   *
   * @return actual reflux in {@link #getFixedLiquidRefluxUnit()}, or {@link Double#NaN} when no fixed split has been
   * run
   */
  public double getLastFixedLiquidReflux() {
    return lastFixedLiquidReflux;
  }

  /**
   * Get the normalized shortfall of the latest fixed liquid reflux split.
   *
   * <p>
   * The residual is zero when the requested reflux is delivered and approaches one as the delivered reflux approaches
   * zero. An inactive or not-yet-run fixed split reports {@link Double#NaN}.
   * </p>
   *
   * @return dimensionless non-negative reflux shortfall
   */
  public double getFixedLiquidRefluxSpecificationResidual() {
    return lastFixedLiquidRefluxResidual;
  }

  /**
   * Check whether the latest fixed liquid reflux split satisfied its requested flow.
   *
   * @return {@code true} for an inactive mode or an active split within the fixed reflux tolerance
   */
  public boolean isFixedLiquidRefluxSpecificationSatisfied() {
    return !separation_with_liquid_reflux || (Double.isFinite(lastFixedLiquidRefluxResidual)
        && lastFixedLiquidRefluxResidual <= FIXED_LIQUID_REFLUX_RELATIVE_TOLERANCE);
  }

  /**
   * Sets the separation with liquid reflux parameters.
   *
   * @param separation_with_liquid_reflux a boolean indicating if separation with liquid reflux is set
   * @param value the value of the reflux
   * @param unit the unit of the reflux value
   * @throws IllegalArgumentException if an active reflux value is negative or non-finite, or its unit is blank
   */
  public void setSeparation_with_liquid_reflux(boolean separation_with_liquid_reflux, double value, String unit) {
    if (separation_with_liquid_reflux && (!Double.isFinite(value) || value < 0.0)) {
      throw new IllegalArgumentException("Fixed liquid reflux must be finite and non-negative");
    }
    if (separation_with_liquid_reflux && (unit == null || unit.trim().isEmpty())) {
      throw new IllegalArgumentException("Fixed liquid reflux requires a flow-rate unit");
    }
    if (separation_with_liquid_reflux) {
      refluxIsSet = true;
    } else if (this.separation_with_liquid_reflux) {
      refluxIsSet = false;
    }
    this.separation_with_liquid_reflux = separation_with_liquid_reflux;
    this.reflux_value = value;
    this.reflux_unit = separation_with_liquid_reflux ? unit.trim() : unit;
    lastAvailableLiquidReflux = Double.NaN;
    lastFixedLiquidReflux = Double.NaN;
    lastFixedLiquidRefluxResidual = Double.NaN;
  }

  /**
   * Setter for the field <code>totalCondenser</code>.
   *
   * @param isTotalCondenser a boolean
   */
  public void setTotalCondenser(boolean isTotalCondenser) {
    this.totalCondenser = isTotalCondenser;
  }

  /**
   * Checks whether this condenser is configured as a total condenser.
   *
   * @return {@code true} when the condenser is configured as total, otherwise {@code false}
   */
  public boolean isTotalCondenser() {
    return totalCondenser;
  }

  /**
   * Getter for the field <code>refluxRatio</code>.
   *
   * @return the refluxRatio
   */
  public double getRefluxRatio() {
    return refluxRatio;
  }

  /**
   * Checks whether a reflux equation is active.
   *
   * @return {@code true} when ratio-controlled or fixed liquid reflux is configured
   */
  public boolean isRefluxSet() {
    return refluxIsSet;
  }

  /**
   * Setter for the field <code>refluxRatio</code>.
   *
   * @param refluxRatio finite non-negative liquid-to-distillate reflux ratio
   * @throws IllegalArgumentException if the ratio is negative or non-finite
   */
  public void setRefluxRatio(double refluxRatio) {
    if (!Double.isFinite(refluxRatio) || refluxRatio < 0.0) {
      throw new IllegalArgumentException("Condenser reflux ratio must be finite and >= 0");
    }
    this.refluxRatio = refluxRatio;
    refluxIsSet = true;
  }

  /**
   * Clear ratio-controlled reflux while leaving fixed liquid-reflux mode unchanged.
   */
  public void clearRefluxRatio() {
    if (!separation_with_liquid_reflux) {
      refluxIsSet = false;
    }
  }

  /**
   * Getter for the field <code>duty</code>.
   *
   * @return a double
   */
  public double getDuty() {
    // return calcMixStreamEnthalpy();
    return duty;
  }

  /**
   * getDuty.
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getDuty(String unit) {
    neqsim.util.unit.PowerUnit powerUnit = new neqsim.util.unit.PowerUnit(duty, "W");
    return powerUnit.getValue(unit);
  }

  /** {@inheritDoc} */
  @Override
  public StreamInterface getGasOutStream() {
    if (totalCondenser && mixedStreamSplitter != null) {
      return new Stream("", mixedStreamSplitter.getSplitStream(1));
    } else {
      return super.getGasOutStream();
    }
  }

  /**
   * getProductOutStream.
   *
   * @return a {@link neqsim.process.equipment.stream.Stream} object
   */
  public StreamInterface getProductOutStream() {
    return getGasOutStream();
  }

  /** {@inheritDoc} */
  @Override
  public StreamInterface getLiquidOutStream() {
    if ((totalCondenser || separation_with_liquid_reflux) && mixedStreamSplitter != null) {
      return mixedStreamSplitter.getSplitStream(0);
    } else {
      return super.getLiquidOutStream();
    }
  }

  /**
   * Get the liquid product stream from the condenser.
   *
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getLiquidProductStream() {
    if (separation_with_liquid_reflux && mixedStreamSplitter != null) {
      return mixedStreamSplitter.getSplitStream(1);
    } else {
      return null;
    }
  }

  /**
   * Discard the separate liquid product after the owning column replaces rejected tray products with a full-feed
   * fallback.
   *
   * <p>
   * The fallback already exposes the complete feed inventory through the column's gas and bottom product streams.
   * Retaining this rejected condenser product beside those streams would double-count material. Fixed-reflux
   * availability and delivery diagnostics are invalidated because they describe the rejected tray state, not the
   * fallback products.
   *
   * @param id calculation identifier assigned to the cleared product stream
   */
  void discardLiquidProductAfterColumnFallback(UUID id) {
    StreamInterface liquidProduct = getLiquidProductStream();
    if (liquidProduct != null) {
      liquidProduct.setFlowRate(0.0, "kg/hr");
      liquidProduct.setCalculationIdentifier(id);
    }
    lastAvailableLiquidReflux = Double.NaN;
    lastFixedLiquidReflux = Double.NaN;
    lastFixedLiquidRefluxResidual = Double.NaN;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (refluxIsSet && !separation_with_liquid_reflux && (!Double.isFinite(refluxRatio) || refluxRatio < 0.0)) {
      throw new IllegalStateException("Condenser " + getName() + " has invalid reflux ratio " + refluxRatio);
    }
    if (totalCondenser && (!refluxIsSet || separation_with_liquid_reflux)) {
      throw new IllegalStateException(
          "Total condenser " + getName() + " requires an explicit reflux ratio before it can run");
    }
    lastAvailableLiquidReflux = Double.NaN;
    lastFixedLiquidReflux = Double.NaN;
    lastFixedLiquidRefluxResidual = Double.NaN;
    // System.out.println("guess temperature " + getTemperature());
    if (refluxIsSet && totalCondenser) {
      prepareMixedStreamForRefluxFlash();
      ThermodynamicOperations testOps = new ThermodynamicOperations(mixedStream.getThermoSystem());
      try {
        testOps.bubblePointTemperatureFlash();
      } catch (Exception e) {
        throw new IllegalStateException(
            "Total condenser " + getName() + " could not calculate its bubble-point temperature", e);
      }
      mixedStream.getThermoSystem().init(3);
      // mixedStream.getThermoSystem().prettyPrint();

      mixedStreamSplitter = new Splitter("splitter", mixedStream, 2);
      double refluxFraction = refluxRatio <= 0.0 ? 0.0 : refluxRatio / (1.0 + refluxRatio);
      mixedStreamSplitter.setSplitFactors(new double[] { refluxFraction, 1.0 - refluxFraction });
      mixedStreamSplitter.run();
    } else if (!refluxIsSet) {
      UUID oldID = getCalculationIdentifier();
      super.run(id);
      setCalculationIdentifier(oldID);
    } else if (separation_with_liquid_reflux) {
      if (!Double.isFinite(reflux_value) || reflux_value < 0.0 || reflux_unit == null || reflux_unit.trim().isEmpty()) {
        throw new IllegalStateException(
            "Condenser " + getName() + " has invalid fixed liquid reflux value " + reflux_value + " " + reflux_unit);
      }
      super.run(id);
      StreamInterface liquidstream = super.getLiquidOutStream().clone();
      liquidstream.setName("temp liq stream");
      liquidstream.run();
      lastAvailableLiquidReflux = liquidstream.getFlowRate(this.reflux_unit);
      mixedStreamSplitter = new Splitter("splitter", liquidstream, 2);
      mixedStreamSplitter.setFlowRates(new double[] { this.reflux_value, Splitter.REMAINDER }, this.reflux_unit);
      mixedStreamSplitter.run();
      lastFixedLiquidReflux = mixedStreamSplitter.getSplitStream(0).getFlowRate(this.reflux_unit);
      lastFixedLiquidRefluxResidual = reflux_value == 0.0 ? 0.0
          : Math.max(0.0, reflux_value - lastFixedLiquidReflux) / reflux_value;
    } else {
      prepareMixedStreamForRefluxFlash();
      ThermodynamicOperations testOps = new ThermodynamicOperations(mixedStream.getThermoSystem());
      testOps.PVrefluxFlash(refluxRatio, 0);
    }
    // System.out.println("enthalpy: " +
    // mixedStream.getThermoSystem().getEnthalpy());
    // System.out.println("enthalpy: " + enthalpy);
    // System.out.println("temperature: " +
    // mixedStream.getThermoSystem().getTemperature());
    duty = mixedStream.getFluid().getEnthalpy() - calcMixStreamEnthalpy0();
    getEnergyPort("heatDuty").setDuty(duty);
    // System.out.println("beta " + mixedStream.getThermoSystem().getBeta())

    setCalculationIdentifier(id);
  }

  /**
   * Prepare the mixed stream before a condenser reflux flash.
   *
   * @throws IllegalStateException if no inlet streams are connected
   */
  private void prepareMixedStreamForRefluxFlash() {
    if (streams.isEmpty()) {
      throw new IllegalStateException("Condenser has no inlet streams");
    }
    SystemInterface thermoSystem = streams.get(0).getThermoSystem().clone();
    mixedStream.setThermoSystem(thermoSystem);
    mixedStream.getThermoSystem().setNumberOfPhases(2);
    mixedStream.getThermoSystem().init(0);
    mixStream();
  }
}
