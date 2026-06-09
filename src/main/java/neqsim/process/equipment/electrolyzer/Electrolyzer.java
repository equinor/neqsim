package neqsim.process.equipment.electrolyzer;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.Fluid;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * Electrolyzer unit converting water to hydrogen and oxygen using electrical energy.
 * </p>
 *
 * @author esol
 */
public class Electrolyzer extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Electrolyzer.class);

  private StreamInterface waterInlet;
  private Stream hydrogenOutStream;
  private Stream oxygenOutStream;

  private double cellVoltage = 1.23; // V
  private static final double FARADAY_CONSTANT = 96485.3329; // C/mol e-

  /** Molar mass of hydrogen (kg/mol). */
  private static final double MW_H2 = 0.002016;

  /** Selected technology. Null preserves the original (1.23 V) behaviour. */
  private ElectrolyzerTechnology technology = null;

  /** Optional polarisation model. When set, drives {@link #cellVoltage} from current density. */
  private ElectrolyzerIVCharacteristic ivCharacteristic = null;

  /** Current density (A/cm2), used when {@link #ivCharacteristic} is non-null. */
  private double currentDensity = 0.0;

  /** Faradaic (current) efficiency, fraction of electrons that split water (0..1). */
  private double faradaicEfficiency = 1.0;

  /** Stack power computed by the most recent {@link #run(java.util.UUID)} (W). */
  private double stackPower = 0.0;

  /**
   * Operating mode of the electrolyzer.
   */
  public enum OperationMode {
    /** Water-feed driven: the inlet water rate sets production; power is an output. */
    WATER_FEED,
    /** Power driven: available electrical power sets production; water demand is an output. */
    POWER
  }

  /**
   * Selected operating mode. Default {@link OperationMode#WATER_FEED} for backward compatibility.
   */
  private OperationMode operationMode = OperationMode.WATER_FEED;

  // --- NIP-2: stack geometry / sizing ---
  /** Active area per cell (cm2). */
  private double activeCellArea = 0.0;
  /** Number of cells in series in the stack. */
  private int numberOfCells = 0;
  /**
   * Explicit total active area (cm2). Overrides {@code activeCellArea * numberOfCells} when set.
   */
  private double stackActiveArea = 0.0;
  /** Rated (nameplate) stack power (W). */
  private double ratedPower = 0.0;
  /** Nominal current density used for sizing and as the inversion upper bound (A/cm2). */
  private double nominalCurrentDensity = 0.0;

  // --- NIP-1: power-driven mode ---
  /** Available electrical (AC, system-level) power for the most recent run (W). */
  private double availablePower = 0.0;
  /** Electrical power curtailed because available power exceeded rated power (W). */
  private double curtailedPower = 0.0;
  /** Total stack current from the most recent run (A). */
  private double stackCurrent = 0.0;

  // --- NIP-3: minimum turndown / standby ---
  /** Minimum load as a fraction of rated power; below this the stack goes to standby (0..1). */
  private double minimumLoadFraction = 0.0;
  /** Standby auxiliary draw as a fraction of rated power while idling (0..1). */
  private double standbyPowerFraction = 0.0;
  /** True if the most recent run left the stack in standby (no production). */
  private boolean standby = false;

  // --- NIP-4: ramp-rate limiting / transient support ---
  /** Maximum ramp rate as a fraction of rated power per second (0 = unlimited). */
  private double maxRampRate = 0.0;
  /** System power actually delivered after the previous transient step (W); negative = unset. */
  private double operatingPower = -1.0;

  // --- NIP-5: balance-of-plant losses ---
  /** Rectifier (AC/DC) efficiency (0..1]. */
  private double rectifierEfficiency = 1.0;
  /** Auxiliary (pump, thermal management) load as a fraction of stack power (>= 0). */
  private double auxiliaryLoadFraction = 0.0;

  // --- NIP-6: thermal model ---
  /** Thermoneutral voltage (V), HHV basis at 25 C (Larminie &amp; Dicks). */
  private static final double V_THERMONEUTRAL = 1.481;
  /** Molar mass of water (kg/mol). */
  private static final double MW_H2O = 0.018015;

  // --- NIP-7: hydrogen delivery pressure ---
  /** Hydrogen delivery pressure (bara); 0 keeps the inlet pressure. */
  private double hydrogenDeliveryPressure = 0.0;
  /** Universal gas constant (J/mol/K). */
  private static final double R_GAS = 8.314462618;

  /**
   * <p>
   * Constructor for Electrolyzer.
   * </p>
   *
   * @param name name of unit
   */
  public Electrolyzer(String name) {
    super(name);
  }

  /**
   * <p>
   * Constructor for Electrolyzer.
   * </p>
   *
   * @param name name of unit
   * @param inletStream water inlet stream
   */
  public Electrolyzer(String name, StreamInterface inletStream) {
    this(name);
    setInletStream(inletStream);
  }

  /**
   * <p>
   * Setter for the field <code>inletStream</code>.
   * </p>
   *
   * @param inletStream water inlet stream
   */
  public void setInletStream(StreamInterface inletStream) {
    this.waterInlet = inletStream;
    SystemInterface h2System =
        new Fluid().create2(new String[] {"hydrogen"}, new double[] {1.0}, "mole/sec");
    hydrogenOutStream = new Stream("hydrogenOutStream", h2System);
    SystemInterface o2System =
        new Fluid().create2(new String[] {"oxygen"}, new double[] {1.0}, "mole/sec");
    oxygenOutStream = new Stream("oxygenOutStream", o2System);

    double pressure = inletStream.getPressure("bara");
    double temperature = inletStream.getTemperature("K");
    hydrogenOutStream.setPressure(pressure, "bara");
    hydrogenOutStream.setTemperature(temperature, "K");
    oxygenOutStream.setPressure(pressure, "bara");
    oxygenOutStream.setTemperature(temperature, "K");
  }

  /**
   * <p>
   * Getter for the field <code>hydrogenOutStream</code>.
   * </p>
   *
   * @return hydrogen product stream
   */
  public StreamInterface getHydrogenOutStream() {
    return hydrogenOutStream;
  }

  /**
   * Gets the cell voltage.
   *
   * @return cell voltage in V
   */
  public double getCellVoltage() {
    return cellVoltage;
  }

  /**
   * Sets the cell voltage.
   *
   * @param cellVoltage cell voltage in V
   */
  public void setCellVoltage(double cellVoltage) {
    this.cellVoltage = cellVoltage;
  }

  /**
   * <p>
   * Getter for the field <code>oxygenOutStream</code>.
   * </p>
   *
   * @return oxygen product stream
   */
  public StreamInterface getOxygenOutStream() {
    return oxygenOutStream;
  }

  /** {@inheritDoc} */
  @Override
  public double getMassBalance(String unit) {
    double inletFlow = waterInlet.getThermoSystem().getFlowRate(unit);
    double outletFlow = hydrogenOutStream.getThermoSystem().getFlowRate(unit)
        + oxygenOutStream.getThermoSystem().getFlowRate(unit);
    return outletFlow - inletFlow;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    double tempK = waterInlet.getTemperature("K");
    double inletPressure = waterInlet.getPressure("bara");
    curtailedPower = 0.0;
    standby = false;

    double hydrogenFlow; // mole/sec (actual H2 produced)

    if (operationMode == OperationMode.POWER) {
      double area = getStackActiveArea();
      if (area <= 0.0) {
        throw new IllegalStateException("Power-driven mode requires stack geometry: call "
            + "sizeStack(...), setStackActiveArea(...), or setActiveCellArea(...) + "
            + "setNumberOfCells(...) before run().");
      }
      double systemBudget = availablePower;
      double ratedSystem =
          ratedPower > 0.0 ? systemPowerFromStack(ratedPower) : Double.POSITIVE_INFINITY;

      // NIP-3: standby below minimum turndown.
      if (ratedPower > 0.0 && systemBudget < minimumLoadFraction * ratedSystem) {
        standby = true;
        currentDensity = 0.0;
        stackCurrent = 0.0;
        cellVoltage =
            ivCharacteristic != null ? ivCharacteristic.getReversibleVoltage(tempK) : cellVoltage;
        stackPower = 0.0;
        hydrogenFlow = 0.0;
        waterInlet.setFlowRate(0.0, "mole/sec");
        waterInlet.run(id);
        setProductStreams(0.0, 0.0, inletPressure, inletPressure, tempK, id);
        energyStream.setDuty(standbyPowerFraction * ratedPower);
        setEnergyStream(true);
        setCalculationIdentifier(id);
        return;
      }

      // NIP-3: curtail any power above rated.
      if (ratedPower > 0.0 && systemBudget > ratedSystem) {
        curtailedPower = systemBudget - ratedSystem;
        systemBudget = ratedSystem;
      }

      // NIP-5: convert system (AC) power budget into stack (DC) power.
      double targetStackPower = stackPowerFromSystem(systemBudget);
      // NIP-1: invert P = j * A * V_cell(j, T) for the current density.
      double j = solveCurrentDensityForStackPower(targetStackPower, tempK);
      currentDensity = j;
      cellVoltage = cellVoltageAt(j, tempK);
      stackCurrent = j * area;
      double h2FromCurrent = stackCurrent / (2.0 * FARADAY_CONSTANT); // mol/sec
      hydrogenFlow = h2FromCurrent * faradaicEfficiency;
      // Water demand follows the splitting current (NIP-6 stoichiometric basis).
      waterInlet.setFlowRate(h2FromCurrent, "mole/sec");
      waterInlet.run(id);
      stackPower = stackCurrent * cellVoltage;
    } else {
      double waterFlow = waterInlet.getFlowRate("mole/sec");
      hydrogenFlow = waterFlow * faradaicEfficiency;
      if (ivCharacteristic != null) {
        cellVoltage = ivCharacteristic.getCellVoltage(currentDensity, tempK);
      }
      stackCurrent = hydrogenFlow * 2.0 * FARADAY_CONSTANT;
      stackPower = stackCurrent * cellVoltage;
    }

    double oxygenFlow = hydrogenFlow / 2.0;
    double deliveryPressure =
        hydrogenDeliveryPressure > 0.0 ? hydrogenDeliveryPressure : inletPressure;
    setProductStreams(hydrogenFlow, oxygenFlow, deliveryPressure, inletPressure, tempK, id);

    energyStream.setDuty(stackPower);
    setEnergyStream(true);
    setCalculationIdentifier(id);
  }

  /**
   * Set the hydrogen and oxygen product stream flow rates, pressures and temperature, and run them.
   *
   * @param hydrogenFlow hydrogen molar flow (mole/sec)
   * @param oxygenFlow oxygen molar flow (mole/sec)
   * @param hydrogenPressure hydrogen delivery pressure (bara)
   * @param oxygenPressure oxygen delivery pressure (bara)
   * @param temperatureK product temperature (K)
   * @param id calculation identifier
   */
  private void setProductStreams(double hydrogenFlow, double oxygenFlow, double hydrogenPressure,
      double oxygenPressure, double temperatureK, UUID id) {
    hydrogenOutStream.setFlowRate(hydrogenFlow, "mole/sec");
    hydrogenOutStream.setPressure(hydrogenPressure, "bara");
    hydrogenOutStream.setTemperature(temperatureK, "K");
    hydrogenOutStream.run(id);

    oxygenOutStream.setFlowRate(oxygenFlow, "mole/sec");
    oxygenOutStream.setPressure(oxygenPressure, "bara");
    oxygenOutStream.setTemperature(temperatureK, "K");
    oxygenOutStream.run(id);
  }

  /** {@inheritDoc} */
  @Override
  public void runTransient(double dt, UUID id) {
    if (getCalculateSteadyState()) {
      run(id);
      increaseTime(dt);
      return;
    }
    if (operationMode == OperationMode.POWER && maxRampRate > 0.0 && ratedPower > 0.0) {
      double target = availablePower;
      if (operatingPower < 0.0) {
        operatingPower = 0.0; // cold start: ramp up from zero
      }
      double maxDelta = maxRampRate * ratedPower * dt;
      double ramped = target;
      if (target > operatingPower) {
        ramped = Math.min(target, operatingPower + maxDelta);
      } else if (target < operatingPower) {
        ramped = Math.max(target, operatingPower - maxDelta);
      }
      double commanded = availablePower;
      availablePower = ramped;
      run(id);
      availablePower = commanded;
      operatingPower = ramped;
    } else {
      run(id);
      if (operationMode == OperationMode.POWER) {
        operatingPower = availablePower;
      }
    }
    increaseTime(dt);
  }

  /**
   * Set the electrolyzer technology. Applies the technology's default cell voltage, current
   * density, and faradaic efficiency unless they have already been set explicitly. Does not
   * override an already-attached I-V characteristic.
   *
   * @param technology technology selector
   */
  public void setTechnology(ElectrolyzerTechnology technology) {
    if (technology == null) {
      throw new IllegalArgumentException("technology must not be null");
    }
    this.technology = technology;
    this.cellVoltage = technology.getDefaultCellVoltage();
    this.currentDensity = technology.getDefaultCurrentDensity();
    this.nominalCurrentDensity = technology.getDefaultCurrentDensity();
    this.faradaicEfficiency = technology.getDefaultFaradaicEfficiency();
  }

  /**
   * Get the electrolyzer technology, or {@code null} if not set.
   *
   * @return technology, or null
   */
  public ElectrolyzerTechnology getTechnology() {
    return technology;
  }

  /**
   * Attach an I-V characteristic. When set, the cell voltage is recomputed during
   * {@link #run(UUID)} from the current density and feed temperature.
   *
   * @param ivCharacteristic polarisation model, or {@code null} to disable
   */
  public void setIVCharacteristic(ElectrolyzerIVCharacteristic ivCharacteristic) {
    this.ivCharacteristic = ivCharacteristic;
  }

  /**
   * Get the attached I-V characteristic.
   *
   * @return polarisation model, or {@code null} if none
   */
  public ElectrolyzerIVCharacteristic getIVCharacteristic() {
    return ivCharacteristic;
  }

  /**
   * Set the operating current density (A/cm2). Used together with an attached I-V characteristic to
   * compute the cell voltage during {@link #run(UUID)}.
   *
   * @param currentDensity current density (A/cm2), must be non-negative
   */
  public void setCurrentDensity(double currentDensity) {
    if (currentDensity < 0.0) {
      throw new IllegalArgumentException("currentDensity must be non-negative");
    }
    this.currentDensity = currentDensity;
  }

  /**
   * Get the operating current density.
   *
   * @return current density (A/cm2)
   */
  public double getCurrentDensity() {
    return currentDensity;
  }

  /**
   * Set the faradaic (current) efficiency.
   *
   * @param faradaicEfficiency fraction of stack current that actually splits water, 0..1
   */
  public void setFaradaicEfficiency(double faradaicEfficiency) {
    if (faradaicEfficiency <= 0.0 || faradaicEfficiency > 1.0) {
      throw new IllegalArgumentException(
          "faradaicEfficiency must be in (0,1], got " + faradaicEfficiency);
    }
    this.faradaicEfficiency = faradaicEfficiency;
  }

  /**
   * Get the faradaic (current) efficiency.
   *
   * @return faradaic efficiency, 0..1
   */
  public double getFaradaicEfficiency() {
    return faradaicEfficiency;
  }

  /**
   * Get the stack electrical power computed by the last {@link #run(UUID)}.
   *
   * @return stack power (W)
   */
  public double getStackPower() {
    return stackPower;
  }

  /**
   * Get the specific energy consumption in kWh per kg of hydrogen produced, based on the most
   * recent {@link #run(UUID)}. Returns {@code 0.0} if no hydrogen has been produced.
   *
   * @return specific energy consumption (kWh / kg H2)
   */
  public double getSpecificEnergyConsumption_kWh_per_kg_H2() {
    if (hydrogenOutStream == null) {
      return 0.0;
    }
    double h2MoleFlow = hydrogenOutStream.getFlowRate("mole/sec");
    if (h2MoleFlow <= 0.0) {
      return 0.0;
    }
    double h2MassFlowKgPerHour = h2MoleFlow * MW_H2 * 3600.0;
    if (h2MassFlowKgPerHour <= 0.0) {
      return 0.0;
    }
    double powerKW = stackPower / 1000.0;
    return powerKW / h2MassFlowKgPerHour;
  }

  // ==========================================================================
  // NIP-1: power-driven operating mode
  // ==========================================================================

  /**
   * Set the operating mode.
   *
   * @param operationMode {@link OperationMode#WATER_FEED} or {@link OperationMode#POWER}
   */
  public void setOperationMode(OperationMode operationMode) {
    if (operationMode == null) {
      throw new IllegalArgumentException("operationMode must not be null");
    }
    this.operationMode = operationMode;
  }

  /**
   * Get the operating mode.
   *
   * @return operating mode
   */
  public OperationMode getOperationMode() {
    return operationMode;
  }

  /**
   * Set the available electrical (AC, system-level) power and switch the unit into power-driven
   * mode. During {@link #run(UUID)} the current density and water demand are computed so the stack
   * consumes this power, subject to balance-of-plant losses, the minimum turndown, and curtailment
   * above rated power. Requires stack geometry (see {@link #sizeStack(double)} or
   * {@link #setStackActiveArea(double)}).
   *
   * @param power available electrical power (W), must be non-negative
   */
  public void setAvailablePower(double power) {
    if (power < 0.0) {
      throw new IllegalArgumentException("available power must be non-negative, got " + power);
    }
    this.availablePower = power;
    this.operationMode = OperationMode.POWER;
  }

  /**
   * Get the available electrical power used in the most recent run.
   *
   * @return available power (W)
   */
  public double getAvailablePower() {
    return availablePower;
  }

  /**
   * Get the electrical power curtailed in the most recent run because the available power exceeded
   * the rated power.
   *
   * @return curtailed power (W)
   */
  public double getCurtailedPower() {
    return curtailedPower;
  }

  /**
   * Get the total stack current from the most recent run.
   *
   * @return stack current (A)
   */
  public double getStackCurrent() {
    return stackCurrent;
  }

  /**
   * Compute the cell voltage at a given current density and temperature, using the attached I-V
   * characteristic when present and the fixed cell voltage otherwise.
   *
   * @param j current density (A/cm2)
   * @param temperatureK temperature (K)
   * @return cell voltage (V)
   */
  private double cellVoltageAt(double j, double temperatureK) {
    if (ivCharacteristic != null) {
      return ivCharacteristic.getCellVoltage(j, temperatureK);
    }
    return cellVoltage;
  }

  /**
   * Solve the stack-power balance {@code P = j * A * V_cell(j, T)} for the current density. With no
   * I-V characteristic the cell voltage is constant and the solution is explicit; otherwise a
   * bounded bisection is used (the balance is monotonically increasing in current density).
   *
   * @param targetStackPower target stack power (W)
   * @param temperatureK temperature (K)
   * @return current density (A/cm2)
   */
  private double solveCurrentDensityForStackPower(double targetStackPower, double temperatureK) {
    double area = getStackActiveArea();
    if (targetStackPower <= 0.0) {
      return 0.0;
    }
    if (ivCharacteristic == null) {
      double j = targetStackPower / (area * cellVoltage);
      double jmax = currentDensityUpperBound();
      return jmax > 0.0 ? Math.min(j, jmax) : j;
    }
    double lo = 1.0e-6;
    double hi = currentDensityUpperBound();
    if (hi <= 0.0) {
      hi = 3.0;
    }
    double powerAtHi = hi * area * ivCharacteristic.getCellVoltage(hi, temperatureK);
    if (targetStackPower >= powerAtHi) {
      return hi;
    }
    for (int i = 0; i < 100; i++) {
      double mid = 0.5 * (lo + hi);
      double power = mid * area * ivCharacteristic.getCellVoltage(mid, temperatureK);
      if (power < targetStackPower) {
        lo = mid;
      } else {
        hi = mid;
      }
      if (hi - lo < 1.0e-7) {
        break;
      }
    }
    return 0.5 * (lo + hi);
  }

  /**
   * Upper bound for current density used in sizing and inversion: the nominal current density when
   * set, otherwise the technology default, otherwise zero (unbounded).
   *
   * @return current-density upper bound (A/cm2), or 0 if unknown
   */
  private double currentDensityUpperBound() {
    if (nominalCurrentDensity > 0.0) {
      return nominalCurrentDensity;
    }
    if (technology != null) {
      return technology.getDefaultCurrentDensity();
    }
    return 0.0;
  }

  // ==========================================================================
  // NIP-2: stack geometry / sizing
  // ==========================================================================

  /**
   * Set the active area per cell.
   *
   * @param activeCellArea active area per cell (cm2), must be positive
   */
  public void setActiveCellArea(double activeCellArea) {
    if (activeCellArea <= 0.0) {
      throw new IllegalArgumentException("activeCellArea must be positive");
    }
    this.activeCellArea = activeCellArea;
  }

  /**
   * Get the active area per cell.
   *
   * @return active area per cell (cm2)
   */
  public double getActiveCellArea() {
    return activeCellArea;
  }

  /**
   * Set the number of cells in the stack.
   *
   * @param numberOfCells number of cells, must be positive
   */
  public void setNumberOfCells(int numberOfCells) {
    if (numberOfCells <= 0) {
      throw new IllegalArgumentException("numberOfCells must be positive");
    }
    this.numberOfCells = numberOfCells;
  }

  /**
   * Get the number of cells in the stack.
   *
   * @return number of cells
   */
  public int getNumberOfCells() {
    return numberOfCells;
  }

  /**
   * Set the total stack active area explicitly. Overrides {@code activeCellArea * numberOfCells}.
   *
   * @param stackActiveArea total active area (cm2), must be positive
   */
  public void setStackActiveArea(double stackActiveArea) {
    if (stackActiveArea <= 0.0) {
      throw new IllegalArgumentException("stackActiveArea must be positive");
    }
    this.stackActiveArea = stackActiveArea;
  }

  /**
   * Get the total stack active area, preferring an explicit value over
   * {@code activeCellArea * numberOfCells}.
   *
   * @return total active area (cm2), or 0 if not configured
   */
  public double getStackActiveArea() {
    if (stackActiveArea > 0.0) {
      return stackActiveArea;
    }
    if (activeCellArea > 0.0 && numberOfCells > 0) {
      return activeCellArea * numberOfCells;
    }
    return 0.0;
  }

  /**
   * Set the rated (nameplate) stack power.
   *
   * @param ratedPower rated power (W), must be positive
   */
  public void setRatedPower(double ratedPower) {
    if (ratedPower <= 0.0) {
      throw new IllegalArgumentException("ratedPower must be positive");
    }
    this.ratedPower = ratedPower;
  }

  /**
   * Get the rated (nameplate) stack power.
   *
   * @return rated power (W)
   */
  public double getRatedPower() {
    return ratedPower;
  }

  /**
   * Set the nominal current density used for sizing and as the inversion upper bound.
   *
   * @param nominalCurrentDensity nominal current density (A/cm2), must be positive
   */
  public void setNominalCurrentDensity(double nominalCurrentDensity) {
    if (nominalCurrentDensity <= 0.0) {
      throw new IllegalArgumentException("nominalCurrentDensity must be positive");
    }
    this.nominalCurrentDensity = nominalCurrentDensity;
  }

  /**
   * Get the nominal current density.
   *
   * @return nominal current density (A/cm2)
   */
  public double getNominalCurrentDensity() {
    return nominalCurrentDensity;
  }

  /**
   * Size the stack active area for a rated power, using the nominal current density (technology
   * default if not set explicitly) and the inlet temperature.
   *
   * @param ratedPowerW rated power (W), must be positive
   */
  public void sizeStack(double ratedPowerW) {
    double temperatureK = waterInlet != null ? waterInlet.getTemperature("K") : 353.15;
    double nominalJ = currentDensityUpperBound();
    if (nominalJ <= 0.0) {
      throw new IllegalStateException(
          "set a technology or nominal current density before calling sizeStack(double)");
    }
    sizeStack(ratedPowerW, nominalJ, temperatureK);
  }

  /**
   * Size the stack active area for a rated power at a nominal current density and temperature so
   * that {@code ratedPower = nominalJ * area * V_cell(nominalJ, T)}.
   *
   * @param ratedPowerW rated power (W), must be positive
   * @param nominalJ nominal current density (A/cm2), must be positive
   * @param temperatureK temperature (K), must be positive
   */
  public void sizeStack(double ratedPowerW, double nominalJ, double temperatureK) {
    if (ratedPowerW <= 0.0) {
      throw new IllegalArgumentException("ratedPowerW must be positive");
    }
    if (nominalJ <= 0.0) {
      throw new IllegalArgumentException("nominalJ must be positive");
    }
    if (temperatureK <= 0.0) {
      throw new IllegalArgumentException("temperatureK must be positive");
    }
    this.ratedPower = ratedPowerW;
    this.nominalCurrentDensity = nominalJ;
    double v = cellVoltageAt(nominalJ, temperatureK);
    this.stackActiveArea = ratedPowerW / (nominalJ * v);
  }

  // ==========================================================================
  // NIP-3: minimum turndown / standby
  // ==========================================================================

  /**
   * Set the minimum load as a fraction of rated power. Below this, power-driven runs leave the
   * stack in standby with no production.
   *
   * @param minimumLoadFraction minimum load fraction (0..1)
   */
  public void setMinimumLoadFraction(double minimumLoadFraction) {
    if (minimumLoadFraction < 0.0 || minimumLoadFraction > 1.0) {
      throw new IllegalArgumentException("minimumLoadFraction must be in [0,1]");
    }
    this.minimumLoadFraction = minimumLoadFraction;
  }

  /**
   * Get the minimum load fraction.
   *
   * @return minimum load fraction (0..1)
   */
  public double getMinimumLoadFraction() {
    return minimumLoadFraction;
  }

  /**
   * Set the standby auxiliary draw as a fraction of rated power while idling.
   *
   * @param standbyPowerFraction standby power fraction (0..1)
   */
  public void setStandbyPowerFraction(double standbyPowerFraction) {
    if (standbyPowerFraction < 0.0 || standbyPowerFraction > 1.0) {
      throw new IllegalArgumentException("standbyPowerFraction must be in [0,1]");
    }
    this.standbyPowerFraction = standbyPowerFraction;
  }

  /**
   * Get the standby power fraction.
   *
   * @return standby power fraction (0..1)
   */
  public double getStandbyPowerFraction() {
    return standbyPowerFraction;
  }

  /**
   * Whether the most recent run left the stack in standby (below minimum turndown).
   *
   * @return true if in standby
   */
  public boolean isStandby() {
    return standby;
  }

  // ==========================================================================
  // NIP-4: ramp-rate limiting
  // ==========================================================================

  /**
   * Set the maximum ramp rate as a fraction of rated power per second. Applied during
   * {@link #runTransient(double, UUID)} in power-driven mode. Zero disables the limit.
   *
   * @param maxRampRate maximum ramp rate (fraction of rated power per second), must be non-negative
   */
  public void setMaxRampRate(double maxRampRate) {
    if (maxRampRate < 0.0) {
      throw new IllegalArgumentException("maxRampRate must be non-negative");
    }
    this.maxRampRate = maxRampRate;
  }

  /**
   * Get the maximum ramp rate.
   *
   * @return maximum ramp rate (fraction of rated power per second)
   */
  public double getMaxRampRate() {
    return maxRampRate;
  }

  /**
   * Get the system power actually delivered after the previous transient step.
   *
   * @return operating power (W); negative if no transient step has run
   */
  public double getOperatingPower() {
    return operatingPower;
  }

  // ==========================================================================
  // NIP-5: balance-of-plant losses (system vs stack efficiency)
  // ==========================================================================

  /**
   * Set the rectifier (AC/DC) efficiency.
   *
   * @param rectifierEfficiency rectifier efficiency in (0,1]
   */
  public void setRectifierEfficiency(double rectifierEfficiency) {
    if (rectifierEfficiency <= 0.0 || rectifierEfficiency > 1.0) {
      throw new IllegalArgumentException("rectifierEfficiency must be in (0,1]");
    }
    this.rectifierEfficiency = rectifierEfficiency;
  }

  /**
   * Get the rectifier efficiency.
   *
   * @return rectifier efficiency (0,1]
   */
  public double getRectifierEfficiency() {
    return rectifierEfficiency;
  }

  /**
   * Set the auxiliary (pump, thermal management) load as a fraction of stack power.
   *
   * @param auxiliaryLoadFraction auxiliary load fraction, must be non-negative
   */
  public void setAuxiliaryLoadFraction(double auxiliaryLoadFraction) {
    if (auxiliaryLoadFraction < 0.0) {
      throw new IllegalArgumentException("auxiliaryLoadFraction must be non-negative");
    }
    this.auxiliaryLoadFraction = auxiliaryLoadFraction;
  }

  /**
   * Get the auxiliary load fraction.
   *
   * @return auxiliary load fraction
   */
  public double getAuxiliaryLoadFraction() {
    return auxiliaryLoadFraction;
  }

  /**
   * Convert a stack (DC) power into the corresponding system (AC) power, adding rectifier losses
   * and auxiliary loads.
   *
   * @param stackPowerW stack power (W)
   * @return system power (W)
   */
  private double systemPowerFromStack(double stackPowerW) {
    return stackPowerW / rectifierEfficiency + auxiliaryLoadFraction * stackPowerW;
  }

  /**
   * Convert a system (AC) power budget into the stack (DC) power it can deliver.
   *
   * @param systemPowerW system power (W)
   * @return stack power (W)
   */
  private double stackPowerFromSystem(double systemPowerW) {
    return systemPowerW / (1.0 / rectifierEfficiency + auxiliaryLoadFraction);
  }

  /**
   * Get the system-level electrical power consumed in the most recent run, including rectifier
   * losses, auxiliary loads, and (in standby) the standby draw.
   *
   * @return system power (W)
   */
  public double getSystemPower() {
    double system = systemPowerFromStack(stackPower);
    if (standby) {
      system += standbyPowerFraction * ratedPower;
    }
    return system;
  }

  /**
   * Get the system-level specific energy consumption in kWh per kg of hydrogen, based on the most
   * recent run. Includes rectifier and auxiliary losses. Returns {@code 0.0} if no hydrogen was
   * produced.
   *
   * @return system specific energy consumption (kWh / kg H2)
   */
  public double getSystemSpecificEnergyConsumption_kWh_per_kg_H2() {
    if (hydrogenOutStream == null) {
      return 0.0;
    }
    double h2MoleFlow = hydrogenOutStream.getFlowRate("mole/sec");
    if (h2MoleFlow <= 0.0) {
      return 0.0;
    }
    double h2MassFlowKgPerHour = h2MoleFlow * MW_H2 * 3600.0;
    if (h2MassFlowKgPerHour <= 0.0) {
      return 0.0;
    }
    return (getSystemPower() / 1000.0) / h2MassFlowKgPerHour;
  }

  // ==========================================================================
  // NIP-6: thermal model + water consumption
  // ==========================================================================

  /**
   * Get the stack waste heat from the most recent run, computed from the overpotential above the
   * thermoneutral voltage: {@code Q = max(0, V_cell - V_tn) * I_stack}.
   *
   * @return waste heat (W)
   */
  public double getWasteHeat() {
    return Math.max(0.0, cellVoltage - V_THERMONEUTRAL) * stackCurrent;
  }

  /**
   * Get the stoichiometric water consumption from the most recent run (1 mol H2O per mol H2).
   *
   * @param unit one of {@code "mole/sec"}, {@code "kg/sec"}, {@code "kg/hr"}
   * @return water consumption in the requested unit
   */
  public double getWaterConsumption(String unit) {
    if (hydrogenOutStream == null) {
      return 0.0;
    }
    double molesH2O = hydrogenOutStream.getFlowRate("mole/sec");
    if ("mole/sec".equals(unit)) {
      return molesH2O;
    }
    double massKgPerSec = molesH2O * MW_H2O;
    if ("kg/sec".equals(unit)) {
      return massKgPerSec;
    }
    if ("kg/hr".equals(unit)) {
      return massKgPerSec * 3600.0;
    }
    throw new IllegalArgumentException(
        "unsupported unit '" + unit + "', use mole/sec, kg/sec or kg/hr");
  }

  // ==========================================================================
  // NIP-7: hydrogen delivery pressure / downstream compression
  // ==========================================================================

  /**
   * Set the hydrogen delivery pressure. When positive, the hydrogen product leaves at this
   * pressure; zero keeps the inlet pressure.
   *
   * @param hydrogenDeliveryPressure delivery pressure (bara), must be non-negative
   */
  public void setHydrogenDeliveryPressure(double hydrogenDeliveryPressure) {
    if (hydrogenDeliveryPressure < 0.0) {
      throw new IllegalArgumentException("hydrogenDeliveryPressure must be non-negative");
    }
    this.hydrogenDeliveryPressure = hydrogenDeliveryPressure;
  }

  /**
   * Get the hydrogen delivery pressure.
   *
   * @return delivery pressure (bara); 0 means the inlet pressure is used
   */
  public double getHydrogenDeliveryPressure() {
    return hydrogenDeliveryPressure;
  }

  /**
   * Estimate the ideal isothermal compression power required to raise the hydrogen product from the
   * stack pressure to the delivery pressure: {@code W = n R T ln(P2 / P1)}. Returns {@code 0.0}
   * when the delivery pressure does not exceed the stack pressure.
   *
   * @return ideal compression power (W)
   */
  public double getHydrogenCompressionPower() {
    if (hydrogenDeliveryPressure <= 0.0 || waterInlet == null) {
      return 0.0;
    }
    double stackPressure = waterInlet.getPressure("bara");
    if (hydrogenDeliveryPressure <= stackPressure) {
      return 0.0;
    }
    double moleFlow = hydrogenOutStream.getFlowRate("mole/sec");
    double temperatureK = hydrogenOutStream.getTemperature("K");
    return moleFlow * R_GAS * temperatureK * Math.log(hydrogenDeliveryPressure / stackPressure);
  }
}
