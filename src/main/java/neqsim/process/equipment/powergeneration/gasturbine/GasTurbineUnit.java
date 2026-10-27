package neqsim.process.equipment.powergeneration.gasturbine;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.driver.GasTurbineDriver;
import neqsim.process.equipment.stream.EnergyPortDirection;
import neqsim.process.equipment.stream.EnergyPortMode;
import neqsim.process.equipment.stream.EnergyType;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Catalogue-driven gas turbine unit used for dispatch, right-sizing and lifecycle CO2 / fuel studies.
 *
 * <p>
 * Unlike {@link neqsim.process.equipment.powergeneration.GasTurbine} (which builds a full Brayton-cycle
 * air/combustion/expansion thermo model), this class is an engineering-level energy accounting wrapper: it takes a fuel
 * gas inlet stream and a list of {@link Compressor} power consumers (or arbitrary {@link PowerDemandConsumer}s),
 * computes load fraction against the supplied {@link GasTurbineSpec}, looks up effective heat rate from a
 * {@link GasTurbinePerformanceMap}, applies optional {@link GasTurbineDegradation} penalties, sizes the fuel gas mass
 * flow, and reports CO2 / NOx / methane-slip emissions via {@link GasTurbineEmissions}.
 * </p>
 *
 * <p>
 * Typical use in a {@link neqsim.process.processmodel.ProcessSystem}:
 * </p>
 *
 * <pre>{@code
 * GasTurbineUnit gt = new GasTurbineUnit("GT-A", fuelGasStream, GasTurbineCatalog.get("LM2500"));
 * gt.addPowerConsumer(exportCompressor);
 * gt.addPowerConsumer(injectionCompressor);
 * process.add(gt);
 * process.run();
 * double fuelFlowKgPerHr = gt.getFuelMassFlowKgPerHr();
 * double co2KgPerHr = gt.getCO2EmissionKgPerHr();
 * }</pre>
 *
 * @author neqsim
 * @version $Id: $Id
 */
public class GasTurbineUnit extends TwoPortEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1L;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(GasTurbineUnit.class);

  private GasTurbineSpec spec;
  private GasTurbinePerformanceMap performanceMap;
  private GasTurbineDegradation degradation;
  private GasTurbineEmissions emissions = new GasTurbineEmissions();

  private double ambientTemperatureK = GasTurbinePerformanceMap.T_ISO_K;
  private double ambientPressureBara = GasTurbinePerformanceMap.P_ISO_BARA;

  private final List<PowerDemandConsumer> consumers = new ArrayList<PowerDemandConsumer>();

  /** Compressors attached via {@link #addPowerConsumer(Compressor)}, kept for power feedback. */
  private final List<Compressor> drivenCompressors = new ArrayList<Compressor>();

  /** Opt-in: feed an available-power limit back to the driven compressors on each run. */
  private boolean enforcePowerLimit = false;

  private boolean explicitDemandSet = false;
  private double explicitDemandW = 0.0;

  /** Exhaust (power-turbine inlet or stack) temperature limit [K]; 0 = no limit. */
  private double maxExhaustTemperatureK = 0.0;
  /** Fraction of site power lost per kelvin that the full-load exhaust temperature exceeds the limit. */
  private double exhaustLimitPowerLossPerKelvin = 0.01;
  /** Mean exhaust gas specific heat used for waste-heat duty [kJ/kg/K]. */
  private double exhaustCpKJPerKgK = 1.10;
  private boolean exhaustTemperatureLimited = false;
  private double fuelEnergyRateW = 0.0;
  private double shaftPowerW = 0.0;

  // computed each run
  private double availablePowerW = 0.0;
  private double demandedPowerW = 0.0;
  private double loadFraction = 0.0;
  private double effectiveHeatRateKJPerKWh = 0.0;
  private double fuelMassFlowKgPerS = 0.0;
  private double fuelMolarFlowMolPerS = 0.0;
  private double exhaustMassFlowKgPerS = 0.0;
  private double exhaustTemperatureK = 0.0;
  private double co2KgPerS = 0.0;
  private double noxKgPerS = 0.0;
  private double methaneSlipKgPerS = 0.0;
  private boolean overloaded = false;
  private boolean belowMinLoad = false;

  /**
   * Construct a unit with a name only — fuel stream and spec must be set separately before running.
   *
   * @param name unit name
   */
  public GasTurbineUnit(String name) {
    super(name);
    registerEnergyPort("shaftPower", EnergyType.SHAFT_WORK, EnergyPortDirection.OUTPUT, EnergyPortMode.CALCULATED);
  }

  /**
   * Construct a unit with an inlet fuel stream and a catalog spec.
   *
   * @param name unit name
   * @param fuelStream fuel gas inlet stream
   * @param spec catalog spec (use {@link GasTurbineCatalog#get(String)})
   */
  public GasTurbineUnit(String name, StreamInterface fuelStream, GasTurbineSpec spec) {
    this(name);
    setInletStream(fuelStream);
    setSpec(spec);
  }

  /**
   * Set the catalog spec. Re-initialises the performance map and degradation model from the spec defaults if they have
   * not been overridden.
   *
   * @param spec catalog spec
   */
  public final void setSpec(GasTurbineSpec spec) {
    this.spec = spec;
    if (performanceMap == null && spec != null) {
      this.performanceMap = GasTurbinePerformanceMap.fromSpec(spec);
    }
    if (degradation == null) {
      this.degradation = new GasTurbineDegradation();
    }
  }

  /**
   * Set the performance map (override the type-default map from
   * {@link GasTurbinePerformanceMap#fromSpec(GasTurbineSpec)}).
   *
   * @param performanceMap performance map
   */
  public void setPerformanceMap(GasTurbinePerformanceMap performanceMap) {
    this.performanceMap = performanceMap;
  }

  /**
   * Set the degradation model.
   *
   * @param degradation degradation model
   */
  public void setDegradation(GasTurbineDegradation degradation) {
    this.degradation = degradation;
  }

  /**
   * Set the emissions model (override the default no-slip model).
   *
   * @param emissions emissions model
   */
  public void setEmissions(GasTurbineEmissions emissions) {
    this.emissions = emissions;
  }

  /**
   * Set site ambient conditions for ISO power / heat-rate correction.
   *
   * @param temperatureK ambient temperature [K]
   * @param pressureBara ambient pressure [bara]
   */
  public void setAmbient(double temperatureK, double pressureBara) {
    this.ambientTemperatureK = temperatureK;
    this.ambientPressureBara = pressureBara;
  }

  /**
   * Convenience setter for ambient temperature only. Leaves ambient pressure at its current value (defaults to ISO
   * sea-level, 1.01325 bara).
   *
   * @param temperatureK ambient temperature [K]
   */
  public void setAmbientTemperatureK(double temperatureK) {
    this.ambientTemperatureK = temperatureK;
  }

  /**
   * Convenience setter for ambient pressure only. Leaves ambient temperature unchanged (defaults to ISO 15 °C = 288.15
   * K).
   *
   * @param pressureBara ambient pressure [bara]
   */
  public void setAmbientPressureBara(double pressureBara) {
    this.ambientPressureBara = pressureBara;
  }

  /**
   * Add a compressor as a power consumer.
   *
   * @param compressor compressor whose getPower() will be summed into demand
   */
  public void addPowerConsumer(final Compressor compressor) {
    if (compressor == null) {
      return;
    }
    drivenCompressors.add(compressor);
    consumers.add(new PowerDemandConsumer() {
      @Override
      public double getDemandedPowerW() {
        return compressor.getPower();
      }

      @Override
      public String getConsumerName() {
        return compressor.getName();
      }
    });
  }

  /**
   * Add a generic power consumer.
   *
   * @param consumer consumer implementation
   */
  public void addPowerConsumer(PowerDemandConsumer consumer) {
    if (consumer != null) {
      consumers.add(consumer);
    }
  }

  /** Remove all attached consumers. */
  public void clearPowerConsumers() {
    consumers.clear();
    drivenCompressors.clear();
  }

  /**
   * Set an explicit demanded power, overriding any attached consumers.
   *
   * @param powerW demanded power [W]
   */
  public void setDemandedPower(double powerW) {
    this.explicitDemandSet = true;
    this.explicitDemandW = powerW;
  }

  /** Clear the explicit demand override and revert to summing consumers. */
  public void clearDemandedPowerOverride() {
    this.explicitDemandSet = false;
    this.explicitDemandW = 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (spec == null) {
      logger.warn("GasTurbineUnit {} has no spec — cannot run", getName());
      getEnergyPort("shaftPower").setDuty(0.0);
      return;
    }
    if (performanceMap == null) {
      performanceMap = GasTurbinePerformanceMap.fromSpec(spec);
    }
    if (degradation == null) {
      degradation = new GasTurbineDegradation();
    }

    // 1) Available power at site conditions, with degradation derate and exhaust-temperature limit
    this.availablePowerW = getAvailablePowerAtAmbient(ambientTemperatureK);
    this.exhaustTemperatureLimited = isExhaustLimitActive(ambientTemperatureK);

    // 2) Demanded power
    if (explicitDemandSet) {
      this.demandedPowerW = explicitDemandW;
    } else {
      double sum = 0.0;
      for (PowerDemandConsumer c : consumers) {
        double p = c.getDemandedPowerW();
        if (p > 0.0) {
          sum += p;
        }
      }
      this.demandedPowerW = sum;
    }

    // 3) Load fraction
    if (this.availablePowerW <= 0.0) {
      this.loadFraction = 0.0;
    } else {
      this.loadFraction = this.demandedPowerW / this.availablePowerW;
    }
    this.overloaded = this.loadFraction > 1.0;
    this.belowMinLoad = this.loadFraction > 0.0 && this.loadFraction < performanceMap.getMinLoadFraction();

    if (enforcePowerLimit) {
      applyAvailablePowerLimitToCompressors();
      if (this.overloaded) {
        logger.warn("GasTurbineUnit {} overloaded: demanded {} W exceeds available {} W (shortfall {} W)", getName(),
            this.demandedPowerW, this.availablePowerW, getPowerShortfallW());
      }
    }

    if (this.demandedPowerW <= 0.0) {
      // no demand — turbine off
      this.effectiveHeatRateKJPerKWh = 0.0;
      this.fuelMassFlowKgPerS = 0.0;
      this.fuelMolarFlowMolPerS = 0.0;
      this.exhaustMassFlowKgPerS = 0.0;
      this.exhaustTemperatureK = ambientTemperatureK;
      this.fuelEnergyRateW = 0.0;
      this.shaftPowerW = 0.0;
      this.co2KgPerS = 0.0;
      this.noxKgPerS = 0.0;
      this.methaneSlipKgPerS = 0.0;
      getEnergyPort("shaftPower").setDuty(0.0);
      copyInletToOutlet();
      return;
    }

    // 4) Heat rate with part-load + ambient + degradation
    double loadForMap = Math.max(performanceMap.getMinLoadFraction() * 0.5, Math.min(1.10, this.loadFraction));
    double baseHeatRate = performanceMap.getHeatRate(spec.getHeatRateKJPerKWh(), loadForMap, ambientTemperatureK);
    this.effectiveHeatRateKJPerKWh = baseHeatRate * (1.0 + degradation.getTotalHeatRatePenalty());

    // 5) Fuel mass flow from heat-rate definition (LHV basis)
    // heatRate [kJ/kWh] = fuel energy / shaft energy
    // fuel energy rate [kW] = heatRate * shaftPower [kW] / 3600
    double shaftPowerKW = Math.min(this.demandedPowerW, this.availablePowerW) / 1.0e3;
    double fuelEnergyKW = this.effectiveHeatRateKJPerKWh * shaftPowerKW / 3600.0;
    this.fuelEnergyRateW = fuelEnergyKW * 1.0e3;
    this.shaftPowerW = shaftPowerKW * 1.0e3;
    // fuel mass flow = fuelEnergyKW * 1000 [W] / LHV [J/kg]
    double lhvJPerKg = getFuelLHVJPerKg();
    if (lhvJPerKg <= 0.0) {
      logger.warn("GasTurbineUnit {} has zero fuel LHV — falling back to 50 MJ/kg", getName());
      lhvJPerKg = 50.0e6;
    }
    this.fuelMassFlowKgPerS = fuelEnergyKW * 1.0e3 / lhvJPerKg;

    // Molar flow from mean molar mass
    SystemInterface fuel = getInletStream() == null ? null : getInletStream().getFluid();
    double meanMW = (fuel != null) ? fuel.getMolarMass() * 1.0e3 : 17.0;
    this.fuelMolarFlowMolPerS = this.fuelMassFlowKgPerS / (meanMW * 1.0e-3);

    // 6) Exhaust at part-load
    double exhaustScale = performanceMap.getAvailablePower(1.0, ambientTemperatureK, ambientPressureBara);
    this.exhaustMassFlowKgPerS = performanceMap.getExhaustFlow(spec.getExhaustFlowKgPerS() * exhaustScale, loadForMap);
    this.exhaustTemperatureK = performanceMap.getExhaustTemperature(spec.getExhaustTemperatureK(), loadForMap,
        ambientTemperatureK);

    // 7) Emissions
    this.co2KgPerS = emissions.computeCO2KgPerS(fuel, this.fuelMolarFlowMolPerS);
    this.noxKgPerS = emissions.computeNOxKgPerS(spec.getNoxPpmDLE(), this.exhaustMassFlowKgPerS, 28.7);
    this.methaneSlipKgPerS = emissions.computeMethaneSlipKgPerS(fuel, this.fuelMolarFlowMolPerS);

    getEnergyPort("shaftPower").setDuty(Math.min(this.demandedPowerW, this.availablePowerW));
    copyInletToOutlet();
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    run(UUID.randomUUID());
  }

  private void copyInletToOutlet() {
    if (getInletStream() == null) {
      return;
    }
    if (outStream == null) {
      outStream = getInletStream().clone();
      outStream.setName(getName() + " fuel out");
    }
    // Outlet represents the fuel actually consumed (mass flow rebased)
    SystemInterface outSys = outStream.getFluid();
    if (outSys != null && this.fuelMassFlowKgPerS >= 0.0) {
      outSys.setTotalFlowRate(this.fuelMassFlowKgPerS, "kg/sec");
    }
  }

  private double getFuelLHVJPerKg() {
    if (getInletStream() == null) {
      return 0.0;
    }
    try {
      // Stream.LCV() returns the inferior calorific value on a VOLUME basis (J/Sm3),
      // not J/mol. To get J/kg we compute ISO 6976 directly on a mass basis.
      SystemInterface fluid = getInletStream().getFluid();
      if (fluid == null) {
        return 0.0;
      }
      neqsim.standards.gasquality.Standard_ISO6976 iso6976 = new neqsim.standards.gasquality.Standard_ISO6976(
          fluid.clone(), 0, 15.55, "mass");
      iso6976.setReferenceState("real");
      iso6976.calculate();
      // getValue returns kJ/kg on a mass basis; convert to J/kg.
      return iso6976.getValue("InferiorCalorificValue") * 1.0e3;
    } catch (Exception ex) {
      logger.warn("Could not compute fuel LCV for {}: {}", getName(), ex.getMessage());
      return 0.0;
    }
  }

  // ── Site rating, exhaust limit and waste-heat recovery ───────────────

  /**
   * Maximum continuous shaft power available at a given ambient temperature (site rating): the performance-map power at
   * the unit's ambient pressure, times the degradation derate, reduced when a full-load exhaust-temperature limit is
   * exceeded. Does not run the unit and does not change the stored ambient temperature.
   *
   * @param ambientTemperatureK ambient air temperature [K]
   * @return available shaft power [W], 0 when no spec is set
   */
  public double getAvailablePowerAtAmbient(double ambientTemperatureK) {
    if (spec == null) {
      return 0.0;
    }
    GasTurbinePerformanceMap map = performanceMap != null ? performanceMap : GasTurbinePerformanceMap.fromSpec(spec);
    double derate = degradation != null ? degradation.getPowerDerateFactor() : 1.0;
    double power = map.getAvailablePower(spec.getRatedPowerW(), ambientTemperatureK, ambientPressureBara) * derate;
    if (maxExhaustTemperatureK > 0.0) {
      double excess = map.getFullLoadExhaustTemperature(spec.getExhaustTemperatureK(), ambientTemperatureK)
          - maxExhaustTemperatureK;
      if (excess > 0.0) {
        power *= Math.max(0.3, 1.0 - exhaustLimitPowerLossPerKelvin * excess);
      }
    }
    return power;
  }

  /**
   * Site rating at an ambient temperature in degrees Celsius.
   *
   * @param ambientTemperatureC ambient air temperature [deg C]
   * @return available shaft power [W]
   */
  public double getAvailablePowerAtAmbientC(double ambientTemperatureC) {
    return getAvailablePowerAtAmbient(ambientTemperatureC + 273.15);
  }

  /**
   * Site-rating curve: available shaft power for each of the ambient temperatures.
   *
   * @param ambientTemperaturesC ambient temperatures [deg C]
   * @return available shaft power [W] per ambient temperature
   */
  public double[] getSiteRatingCurveW(double[] ambientTemperaturesC) {
    double[] out = new double[ambientTemperaturesC.length];
    for (int i = 0; i < out.length; i++) {
      out[i] = getAvailablePowerAtAmbientC(ambientTemperaturesC[i]);
    }
    return out;
  }

  /**
   * Whether the full-load exhaust temperature at the ambient temperature exceeds the configured limit (so the power
   * rating is exhaust-temperature limited rather than only ambient derated).
   *
   * @param ambientTemperatureK ambient air temperature [K]
   * @return true when a limit is set and exceeded at full load
   */
  private boolean isExhaustLimitActive(double ambientTemperatureK) {
    return maxExhaustTemperatureK > 0.0 && performanceMap != null && spec != null && performanceMap
        .getFullLoadExhaustTemperature(spec.getExhaustTemperatureK(), ambientTemperatureK) > maxExhaustTemperatureK;
  }

  /**
   * Set an exhaust-temperature limit for the power rating. When the full-load exhaust temperature at the ambient
   * temperature is above the limit the available power is cut by {@code exhaustLimitPowerLossPerKelvin} (default 1 %
   * per kelvin) per kelvin of excess, floored at 30 % of the site power.
   *
   * @param limitK exhaust temperature limit [K]; 0 or negative removes the limit
   */
  public void setMaxExhaustTemperatureK(double limitK) {
    this.maxExhaustTemperatureK = Math.max(0.0, limitK);
  }

  /**
   * Get the exhaust temperature limit.
   *
   * @return limit [K], 0 when none
   */
  public double getMaxExhaustTemperatureK() {
    return maxExhaustTemperatureK;
  }

  /**
   * Set the power sensitivity used when the exhaust-temperature limit is exceeded.
   *
   * @param fractionPerKelvin fraction of site power lost per kelvin of excess full-load exhaust temperature
   */
  public void setExhaustLimitPowerLossPerKelvin(double fractionPerKelvin) {
    this.exhaustLimitPowerLossPerKelvin = Math.max(0.0, fractionPerKelvin);
  }

  /**
   * Whether the last run was exhaust-temperature limited.
   *
   * @return true if the exhaust-temperature limit reduced the available power
   */
  public boolean isExhaustTemperatureLimited() {
    return exhaustTemperatureLimited;
  }

  /**
   * Get the stored ambient air temperature.
   *
   * @return ambient temperature [K]
   */
  public double getAmbientTemperatureK() {
    return ambientTemperatureK;
  }

  /**
   * Set the ambient air temperature in degrees Celsius.
   *
   * @param temperatureC ambient temperature [deg C]
   */
  public void setAmbientTemperatureC(double temperatureC) {
    this.ambientTemperatureK = temperatureC + 273.15;
  }

  /**
   * Set the mean exhaust-gas specific heat used for waste-heat duties.
   *
   * @param cpKJPerKgK specific heat [kJ/kg/K], typically 1.05 to 1.15 for GT exhaust
   */
  public void setExhaustSpecificHeatKJPerKgK(double cpKJPerKgK) {
    if (cpKJPerKgK > 0.0) {
      this.exhaustCpKJPerKgK = cpKJPerKgK;
    }
  }

  /**
   * Heat in the exhaust gas above a stack (gas outlet) temperature at the last run condition.
   *
   * @param stackTemperatureK minimum gas outlet temperature [K], e.g. heating-medium return plus pinch
   * @return exhaust heat above the stack temperature [W]; 0 when the unit is off or the exhaust is colder
   */
  public double getExhaustHeatAboveW(double stackTemperatureK) {
    double dT = exhaustTemperatureK - stackTemperatureK;
    if (dT <= 0.0 || exhaustMassFlowKgPerS <= 0.0) {
      return 0.0;
    }
    return exhaustMassFlowKgPerS * exhaustCpKJPerKgK * 1.0e3 * dT;
  }

  /**
   * Waste-heat recovery unit (WHRU) duty from the exhaust of the last run: the exhaust heat above the stack temperature
   * times a recovery effectiveness.
   *
   * @param stackTemperatureK minimum gas outlet temperature [K]
   * @param effectiveness fraction of the available exhaust heat transferred (0 to 1)
   * @return recovered heat duty [W]
   */
  public double getWasteHeatRecoveryDutyW(double stackTemperatureK, double effectiveness) {
    return getExhaustHeatAboveW(stackTemperatureK) * Math.max(0.0, Math.min(1.0, effectiveness));
  }

  /**
   * Fuel energy input rate (LHV basis) of the last run.
   *
   * @return fuel energy rate [W]
   */
  public double getFuelEnergyRateW() {
    return fuelEnergyRateW;
  }

  /**
   * Shaft power actually delivered in the last run, limited by the available power.
   *
   * @return delivered shaft power [W]
   */
  public double getShaftPowerW() {
    return shaftPowerW;
  }

  // ── Getters ───────────────────────────────────────────────────────────

  /**
   * Get the catalog spec.
   *
   * @return spec
   */
  public GasTurbineSpec getSpec() {
    return spec;
  }

  /**
   * Get the performance map.
   *
   * @return performance map
   */
  public GasTurbinePerformanceMap getPerformanceMap() {
    return performanceMap;
  }

  /**
   * Get the degradation model.
   *
   * @return degradation model
   */
  public GasTurbineDegradation getDegradation() {
    return degradation;
  }

  /**
   * Get the emissions model.
   *
   * @return emissions model
   */
  public GasTurbineEmissions getEmissions() {
    return emissions;
  }

  /**
   * Get the demanded shaft power.
   *
   * @return demanded power [W]
   */
  public double getDemandedPowerW() {
    return demandedPowerW;
  }

  /**
   * Get the available shaft power after site / degradation correction.
   *
   * @return available power [W]
   */
  public double getAvailablePowerW() {
    return availablePowerW;
  }

  /**
   * Get the current load fraction.
   *
   * @return load fraction (1.0 = at site rated)
   */
  public double getLoadFraction() {
    return loadFraction;
  }

  /**
   * Get the effective heat rate (LHV basis) at the current operating point.
   *
   * @return heat rate [kJ/kWh]
   */
  public double getEffectiveHeatRateKJPerKWh() {
    return effectiveHeatRateKJPerKWh;
  }

  /**
   * Get the current thermal efficiency.
   *
   * @return thermal efficiency (0–1)
   */
  public double getThermalEfficiency() {
    if (effectiveHeatRateKJPerKWh <= 0.0) {
      return 0.0;
    }
    return 3600.0 / effectiveHeatRateKJPerKWh;
  }

  /**
   * Get the fuel mass flow.
   *
   * @return fuel mass flow [kg/s]
   */
  public double getFuelMassFlowKgPerS() {
    return fuelMassFlowKgPerS;
  }

  /**
   * Get the fuel mass flow in kg/hr.
   *
   * @return fuel mass flow [kg/hr]
   */
  public double getFuelMassFlowKgPerHr() {
    return fuelMassFlowKgPerS * 3600.0;
  }

  /**
   * Get the CO2 emission rate.
   *
   * @return CO2 emission [kg/s]
   */
  public double getCO2EmissionKgPerS() {
    return co2KgPerS;
  }

  /**
   * Get the CO2 emission rate.
   *
   * @return CO2 emission [kg/hr]
   */
  public double getCO2EmissionKgPerHr() {
    return co2KgPerS * 3600.0;
  }

  /**
   * Get the CO2 intensity per MWh of shaft work.
   *
   * @return CO2 intensity [kg CO2 / MWh]
   */
  public double getCO2IntensityKgPerMWh() {
    if (demandedPowerW <= 0.0) {
      return 0.0;
    }
    return (co2KgPerS * 3600.0) / (demandedPowerW / 1.0e6);
  }

  /**
   * Get the NOx emission rate.
   *
   * @return NOx [kg/s]
   */
  public double getNOxEmissionKgPerS() {
    return noxKgPerS;
  }

  /**
   * Get the methane slip rate.
   *
   * @return methane slip [kg/s]
   */
  public double getMethaneSlipKgPerS() {
    return methaneSlipKgPerS;
  }

  /**
   * Get the exhaust mass flow.
   *
   * @return exhaust [kg/s]
   */
  public double getExhaustMassFlowKgPerS() {
    return exhaustMassFlowKgPerS;
  }

  /**
   * Get the exhaust temperature.
   *
   * @return exhaust T [K]
   */
  public double getExhaustTemperatureK() {
    return exhaustTemperatureK;
  }

  /**
   * Indicate whether the demand exceeded the available power on the last run.
   *
   * @return true if overloaded
   */
  public boolean isOverloaded() {
    return overloaded;
  }

  /**
   * Indicate whether the load fell below the minimum stable load fraction.
   *
   * @return true if below minimum stable load
   */
  public boolean isBelowMinLoad() {
    return belowMinLoad;
  }

  /**
   * Enable or disable feeding an available-power limit back to the driven compressors.
   *
   * <p>
   * When enabled (opt-in; default {@code false} preserves the historical one-way snapshot behaviour), each
   * {@link #run()} distributes {@link #getAvailablePowerW()} across the compressors attached via
   * {@link #addPowerConsumer(Compressor)} and installs the share on each compressor as a {@link GasTurbineDriver}
   * performance curve. The per-compressor caps sum to the turbine available power, so the capacity / bottleneck
   * framework enforces &Sigma; compressor power &le; turbine available power.
   * </p>
   *
   * @param enforcePowerLimit true to feed the available-power limit back to driven compressors
   */
  public void setEnforcePowerLimit(boolean enforcePowerLimit) {
    this.enforcePowerLimit = enforcePowerLimit;
  }

  /**
   * Indicate whether available-power feedback to the driven compressors is enabled.
   *
   * @return true if the available-power limit is fed back to driven compressors on each run
   */
  public boolean isEnforcePowerLimit() {
    return enforcePowerLimit;
  }

  /**
   * Get the power shortfall on the last run (how much demand exceeded available power).
   *
   * @return shortfall in Watts ({@code demanded - available}), clamped to be non-negative
   */
  public double getPowerShortfallW() {
    double shortfall = demandedPowerW - availablePowerW;
    return shortfall > 0.0 ? shortfall : 0.0;
  }

  /**
   * Get the available-power allocation across the driven compressors.
   *
   * <p>
   * Each driven compressor receives a share of {@link #getAvailablePowerW()} proportional to its current power demand
   * (equal split when no demand is registered). The shares sum to the turbine available power. This is the distribution
   * applied to the compressors when {@link #isEnforcePowerLimit()} is true.
   * </p>
   *
   * @return ordered map of compressor name to allocated available power [W]
   */
  public java.util.Map<String, Double> getPowerAllocationW() {
    java.util.Map<String, Double> allocation = new java.util.LinkedHashMap<String, Double>();
    if (drivenCompressors.isEmpty()) {
      return allocation;
    }
    double totalDemand = 0.0;
    for (Compressor c : drivenCompressors) {
      double p = c.getPower();
      if (p > 0.0) {
        totalDemand += p;
      }
    }
    int count = drivenCompressors.size();
    for (Compressor c : drivenCompressors) {
      double shareW;
      if (totalDemand > 0.0) {
        double p = Math.max(0.0, c.getPower());
        shareW = availablePowerW * (p / totalDemand);
      } else {
        shareW = availablePowerW / count;
      }
      allocation.put(c.getName(), shareW);
    }
    return allocation;
  }

  /**
   * Distribute the turbine available shaft power across the driven compressors and feed each one an available-power
   * limit via {@link Compressor#setDriverCurve(neqsim.process.equipment.compressor.driver.DriverCurve)}.
   *
   * <p>
   * The share is installed as a {@link GasTurbineDriver} performance curve evaluated at ISO ambient conditions so that
   * each compressor's {@link Compressor#getCapacityMax()} reports exactly its allocation. The per-compressor caps sum
   * to {@link #getAvailablePowerW()}, enforcing &Sigma; compressor power &le; turbine available power in the capacity /
   * bottleneck framework.
   * </p>
   */
  private void applyAvailablePowerLimitToCompressors() {
    if (drivenCompressors.isEmpty() || availablePowerW <= 0.0) {
      return;
    }
    java.util.Map<String, Double> allocation = getPowerAllocationW();
    for (Compressor c : drivenCompressors) {
      Double shareW = allocation.get(c.getName());
      if (shareW == null || shareW <= 0.0) {
        continue;
      }
      GasTurbineDriver curve = new GasTurbineDriver(shareW / 1000.0, 0.35);
      curve.setAmbientTemperature(15.0); // ISO so the allocation maps 1:1 to available power
      c.setDriverCurve(curve);
    }
  }

  /**
   * Get the list of attached power consumers.
   *
   * @return defensive copy of the consumer list
   */
  public List<PowerDemandConsumer> getPowerConsumers() {
    return new ArrayList<PowerDemandConsumer>(consumers);
  }
}
