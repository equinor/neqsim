package neqsim.process.equipment.powergeneration.gasturbine;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Catalogue-driven gas turbine unit used for dispatch, right-sizing and lifecycle CO2 / fuel
 * studies.
 *
 * <p>
 * Unlike {@link neqsim.process.equipment.powergeneration.GasTurbine} (which builds a full
 * Brayton-cycle air/combustion/expansion thermo model), this class is an engineering-level energy
 * accounting wrapper: it takes a fuel gas inlet stream and a list of {@link Compressor} power
 * consumers (or arbitrary {@link PowerDemandConsumer}s), computes load fraction against the
 * supplied {@link GasTurbineSpec}, looks up effective heat rate from a
 * {@link GasTurbinePerformanceMap}, applies optional {@link GasTurbineDegradation} penalties, sizes
 * the fuel gas mass flow, and reports CO2 / NOx / methane-slip emissions via
 * {@link GasTurbineEmissions}.
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

  private boolean explicitDemandSet = false;
  private double explicitDemandW = 0.0;

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
  }

  /**
   * Construct a unit with an inlet fuel stream and a catalog spec.
   *
   * @param name unit name
   * @param fuelStream fuel gas inlet stream
   * @param spec catalog spec (use {@link GasTurbineCatalog#get(String)})
   */
  public GasTurbineUnit(String name, StreamInterface fuelStream, GasTurbineSpec spec) {
    super(name, fuelStream);
    setSpec(spec);
  }

  /**
   * Set the catalog spec. Re-initialises the performance map and degradation model from the spec
   * defaults if they have not been overridden.
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
   * Convenience setter for ambient temperature only. Leaves ambient pressure at its current value
   * (defaults to ISO sea-level, 1.01325 bara).
   *
   * @param temperatureK ambient temperature [K]
   */
  public void setAmbientTemperatureK(double temperatureK) {
    this.ambientTemperatureK = temperatureK;
  }

  /**
   * Convenience setter for ambient pressure only. Leaves ambient temperature unchanged (defaults to
   * ISO 15 °C = 288.15 K).
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
      return;
    }
    if (performanceMap == null) {
      performanceMap = GasTurbinePerformanceMap.fromSpec(spec);
    }
    if (degradation == null) {
      degradation = new GasTurbineDegradation();
    }

    // 1) Available power at site conditions, with degradation derate
    double sitePower = performanceMap.getAvailablePower(spec.getRatedPowerW(), ambientTemperatureK,
        ambientPressureBara);
    this.availablePowerW = sitePower * degradation.getPowerDerateFactor();

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
    this.belowMinLoad =
        this.loadFraction > 0.0 && this.loadFraction < performanceMap.getMinLoadFraction();

    if (this.demandedPowerW <= 0.0) {
      // no demand — turbine off
      this.effectiveHeatRateKJPerKWh = 0.0;
      this.fuelMassFlowKgPerS = 0.0;
      this.fuelMolarFlowMolPerS = 0.0;
      this.exhaustMassFlowKgPerS = 0.0;
      this.exhaustTemperatureK = ambientTemperatureK;
      this.co2KgPerS = 0.0;
      this.noxKgPerS = 0.0;
      this.methaneSlipKgPerS = 0.0;
      copyInletToOutlet();
      return;
    }

    // 4) Heat rate with part-load + ambient + degradation
    double loadForMap =
        Math.max(performanceMap.getMinLoadFraction() * 0.5, Math.min(1.10, this.loadFraction));
    double baseHeatRate =
        performanceMap.getHeatRate(spec.getHeatRateKJPerKWh(), loadForMap, ambientTemperatureK);
    this.effectiveHeatRateKJPerKWh = baseHeatRate * (1.0 + degradation.getTotalHeatRatePenalty());

    // 5) Fuel mass flow from heat-rate definition (LHV basis)
    // heatRate [kJ/kWh] = fuel energy / shaft energy
    // fuel energy rate [kW] = heatRate * shaftPower [kW] / 3600
    double shaftPowerKW = Math.min(this.demandedPowerW, this.availablePowerW) / 1.0e3;
    double fuelEnergyKW = this.effectiveHeatRateKJPerKWh * shaftPowerKW / 3600.0;
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
    double exhaustScale =
        performanceMap.getAvailablePower(1.0, ambientTemperatureK, ambientPressureBara);
    this.exhaustMassFlowKgPerS =
        performanceMap.getExhaustFlow(spec.getExhaustFlowKgPerS() * exhaustScale, loadForMap);
    this.exhaustTemperatureK =
        performanceMap.getExhaustTemperature(spec.getExhaustTemperatureK(), loadForMap);

    // 7) Emissions
    this.co2KgPerS = emissions.computeCO2KgPerS(fuel, this.fuelMolarFlowMolPerS);
    this.noxKgPerS =
        emissions.computeNOxKgPerS(spec.getNoxPpmDLE(), this.exhaustMassFlowKgPerS, 28.7);
    this.methaneSlipKgPerS = emissions.computeMethaneSlipKgPerS(fuel, this.fuelMolarFlowMolPerS);

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
      neqsim.standards.gasquality.Standard_ISO6976 iso6976 =
          new neqsim.standards.gasquality.Standard_ISO6976(fluid.clone(), 0, 15.55, "mass");
      iso6976.setReferenceState("real");
      iso6976.calculate();
      // getValue returns kJ/kg on a mass basis; convert to J/kg.
      return iso6976.getValue("InferiorCalorificValue") * 1.0e3;
    } catch (Exception ex) {
      logger.warn("Could not compute fuel LCV for {}: {}", getName(), ex.getMessage());
      return 0.0;
    }
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
   * Get the list of attached power consumers.
   *
   * @return defensive copy of the consumer list
   */
  public List<PowerDemandConsumer> getPowerConsumers() {
    return new ArrayList<PowerDemandConsumer>(consumers);
  }
}
