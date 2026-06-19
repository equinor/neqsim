package neqsim.process.equipment.expander;

import java.util.UUID;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.expander.ExpanderMechanicalDesign;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * Expander class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class Expander extends Compressor implements ExpanderInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Mechanical design for the expander. */
  private ExpanderMechanicalDesign expanderMechanicalDesign;

  /**
   * Rated recovered shaft power of the expander in kW. A value of {@code 0} (the default) means no recovered-power
   * capacity limit is defined, so no {@code recoveredPower} constraint is created.
   */
  private double ratedRecoveredPower = 0.0;

  /**
   * Constructor for Expander.
   *
   * @param name name of unit operation
   */
  public Expander(String name) {
    super(name);
    initExpanderMechanicalDesign();
  }

  /**
   * <p>
   * Constructor for Expander.
   * </p>
   *
   * @param name        a {@link java.lang.String} object
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public Expander(String name, StreamInterface inletStream) {
    super(name, inletStream);
    initExpanderMechanicalDesign();
  }

  /**
   * Get the expander-specific mechanical design.
   *
   * @return expander mechanical design
   */
  public ExpanderMechanicalDesign getExpanderMechanicalDesign() {
    return expanderMechanicalDesign;
  }

  /**
   * Initialize the expander mechanical design.
   */
  private void initExpanderMechanicalDesign() {
    expanderMechanicalDesign = new ExpanderMechanicalDesign(this);
  }

  /**
   * Gets the rated recovered shaft power of the expander.
   *
   * @return rated recovered power in kW; {@code 0} means no recovered-power limit is defined
   */
  public double getRatedRecoveredPower() {
    return ratedRecoveredPower;
  }

  /**
   * Sets the rated recovered shaft power of the expander and rebuilds the capacity constraints so the
   * {@code recoveredPower} utilization reflects the new rating.
   *
   * <p>
   * The recovered power of an expander is mechanical work extracted from the gas, so {@link #getPower()} returns a
   * negative value. The {@code recoveredPower} constraint compares the magnitude of the extracted power against this
   * rating, giving a meaningful 0&ndash;100+% utilization for the turbo-expander. When the rating is {@code 0} no
   * recovered-power constraint is created.
   * </p>
   *
   * @param ratedRecoveredPowerKW rated recovered power in kW; must be &ge; 0
   */
  public void setRatedRecoveredPower(double ratedRecoveredPowerKW) {
    this.ratedRecoveredPower = ratedRecoveredPowerKW;
    reinitializeCapacityConstraints();
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Builds the inherited compressor capacity constraints, then adapts them for expander operation. The consumed-power
   * constraints ({@code power} and {@code ratedPower}) read {@link #getPower()}, which is negative for an expander and
   * therefore always reports zero utilization, so they are removed. When a rated recovered power has been set, a
   * {@code recoveredPower} constraint is added that tracks the magnitude of the extracted shaft power against the
   * rating.
   * </p>
   */
  @Override
  protected void initializeCapacityConstraints() {
    super.initializeCapacityConstraints();
    // Consumed-power constraints are meaningless for an expander (power < 0 -> 0% utilization).
    removeCapacityConstraint("power");
    removeCapacityConstraint("ratedPower");
    if (ratedRecoveredPower > 0.0) {
      final double ratedKW = ratedRecoveredPower;
      addCapacityConstraint(new CapacityConstraint("recoveredPower", "kW", CapacityConstraint.ConstraintType.HARD)
	  .setDesignValue(ratedKW).setMaxValue(ratedKW * 1.1).setWarningThreshold(0.9)
	  .setDescription("Recovered shaft power vs rated recovered power").setDataSource("equipment")
	  .setValueSupplier(() -> {
	    if (getThermoSystem() == null) {
	      return 0.0;
	    }
	    double recoveredKW = Math.abs(this.getPower("kW"));
	    if (Double.isNaN(recoveredKW)) {
	      return 0.0;
	    }
	    return recoveredKW;
	  }));
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Validates that the expander simulation produced physically reasonable results. Unlike a compressor, an expander
   * extracts work from the gas, so it is valid for the shaft power to be negative, the polytropic head to be negative,
   * and the gas to cool across the machine. This override checks only for non-NaN key values and that the outlet gas is
   * not hotter than the inlet (within a small numerical tolerance). Using the compressor validity rules here would
   * incorrectly flag every expander as invalid and trigger a spurious 150% utilization fallback.
   * </p>
   */
  @Override
  public boolean isSimulationValid() {
    if (thermoSystem == null) {
      return false;
    }
    double power = getPower();
    double inletTemp = getInletTemperature();
    double outletTemp = getOutletTemperature();
    if (Double.isNaN(power) || Double.isNaN(inletTemp) || Double.isNaN(outletTemp)) {
      return false;
    }
    // Expansion cools the gas; allow small tolerance for numerical precision.
    if (outletTemp > inletTemp + 1.0) {
      return false;
    }
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    // System.out.println("expander running..");
    thermoSystem = inStream.getThermoSystem().clone();
    ThermodynamicOperations thermoOps = new ThermodynamicOperations(getThermoSystem());
    thermoOps = new ThermodynamicOperations(thermoSystem);
    thermoSystem.init(3);
    // double presinn = getThermoSystem().getPressure();
    double hinn = getThermoSystem().getEnthalpy();
    // double densInn = getThermoSystem().getDensity();
    double entropy = getThermoSystem().getEntropy();
    inletEnthalpy = hinn;
    double hout = hinn;
    if (usePolytropicCalc) {
      int numbersteps = 40;
      double dp = (pressure - getThermoSystem().getPressure()) / (1.0 * numbersteps);

      for (int i = 0; i < numbersteps; i++) {
	entropy = getThermoSystem().getEntropy();
	double hinn_loc = getThermoSystem().getEnthalpy();
	getThermoSystem().setPressure(getThermoSystem().getPressure() + dp);
	thermoOps.PSflash(entropy);
	hout = hinn_loc + (getThermoSystem().getEnthalpy() - hinn_loc) * polytropicEfficiency;
	thermoOps.PHflash(hout, 0);
      }

      dH = hout - hinn;
      /*
       * HYSYS method double oldPolyt = 10.5; int iter = 0; do {
       *
       * iter++; double n = Math.log(thermoSystem.getPressure() / presinn) / Math.log(thermoSystem.getDensity() /
       * densInn); double k = Math.log(thermoSystem.getPressure() / presinn) / Math.log(densOutIdeal / densInn); double
       * factor = ((Math.pow(thermoSystem.getPressure() / presinn, (n - 1.0) / n) - 1.0) * (n / (n - 1.0)) * (k - 1) /
       * k) / (Math.pow(thermoSystem.getPressure() / presinn, (k - 1.0) / k) - 1.0); oldPolyt = polytropicEfficiency;
       * polytropicEfficiency = factor * isentropicEfficiency; dH = thermoSystem.getEnthalpy() - hinn; hout = hinn + dH
       * / polytropicEfficiency; thermoOps.PHflash(hout, 0); System.out.println(" factor " + factor + " n " + n + " k "
       * + k + " polytropic effici " + polytropicEfficiency + " iter " + iter); } while (Math.abs((oldPolyt -
       * polytropicEfficiency) / oldPolyt) > 1e-5 && iter < 500); // polytropicEfficiency = isentropicEfficiency * ();
       */
    } else {
      getThermoSystem().setPressure(pressure);

      // System.out.println("entropy inn.." + entropy);
      thermoOps.PSflash(entropy);
      // double densOutIdeal = getThermoSystem().getDensity();
      if (!powerSet) {
	dH = (getThermoSystem().getEnthalpy() - hinn) * isentropicEfficiency;
      }
      hout = hinn + dH;
      isentropicEfficiency = dH / (getThermoSystem().getEnthalpy() - hinn);
      // System.out.println("isentropicEfficiency.. " + isentropicEfficiency);
      dH = hout - hinn;
      thermoOps.PHflash(hout, 0);
    }
    if (isSetEnergyStream()) {
      energyStream.setDuty(-dH);
    }
    // thermoSystem.display();
    outStream.setThermoSystem(getThermoSystem());
    setCalculationIdentifier(id);
  }
}
