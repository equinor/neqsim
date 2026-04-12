package neqsim.process.equipment.reactor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.characterization.BiomassCharacterization;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Pyrolysis reactor for thermal decomposition of biomass in the absence of oxygen.
 *
 * <p>
 * Models slow, fast, and flash pyrolysis by combining empirical product-distribution correlations
 * with constrained Gibbs equilibrium for the vapour phase. The reactor accepts a
 * {@link BiomassCharacterization} feedstock and produces three product streams:
 * </p>
 * <ul>
 * <li><b>Pyrolysis gas</b> — non-condensable gases (CO, CO2, H2, CH4)</li>
 * <li><b>Bio-oil</b> — condensable vapours modelled as a surrogate liquid stream</li>
 * <li><b>Biochar</b> — solid residue (fixed carbon + ash)</li>
 * </ul>
 *
 * <h2>Pyrolysis Modes</h2>
 *
 * <table>
 * <caption>Pyrolysis mode characteristics</caption>
 * <tr>
 * <th>Mode</th>
 * <th>Temperature</th>
 * <th>Heating Rate</th>
 * <th>Residence Time</th>
 * <th>Main Product</th>
 * </tr>
 * <tr>
 * <td>SLOW</td>
 * <td>300-500 C</td>
 * <td>low</td>
 * <td>minutes-hours</td>
 * <td>Biochar</td>
 * </tr>
 * <tr>
 * <td>FAST</td>
 * <td>400-600 C</td>
 * <td>high</td>
 * <td>seconds</td>
 * <td>Bio-oil</td>
 * </tr>
 * <tr>
 * <td>FLASH</td>
 * <td>450-650 C</td>
 * <td>very high</td>
 * <td>milliseconds</td>
 * <td>Bio-oil/Gas</td>
 * </tr>
 * </table>
 *
 * <p>
 * Product distribution is set by empirical yield fractions that the user can override. Default
 * yields are estimated from published correlations based on pyrolysis mode and biomass volatile
 * matter content.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class PyrolysisReactor extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;
  /** Logger instance. */
  private static final Logger logger = LogManager.getLogger(PyrolysisReactor.class);

  /**
   * Pyrolysis mode enumeration.
   */
  public enum PyrolysisMode {
    /** Slow pyrolysis — low heating rate, char-dominant. */
    SLOW,
    /** Fast pyrolysis — high heating rate, bio-oil dominant. */
    FAST,
    /** Flash pyrolysis — very high heating rate, bio-oil/gas dominant. */
    FLASH
  }

  // ── Configuration ──
  /** The biomass feedstock characterization. */
  private BiomassCharacterization biomass;
  /** Biomass feed rate in kg/hr (dry basis). */
  private double biomassFeedRateKgPerHr = 0.0;
  /** Pyrolysis mode. */
  private PyrolysisMode pyrolysisMode = PyrolysisMode.FAST;
  /** Pyrolysis temperature in Kelvin. */
  private double pyrolysisTemperature = 273.15 + 500.0;
  /** Reactor pressure in bara. */
  private double reactorPressure = 1.01325;
  /** Heating rate in K/s (informational, affects default product yields). */
  private double heatingRate = 100.0;
  /** Vapour residence time in seconds. */
  private double vapourResidenceTime = 2.0;

  // ── Product yield fractions (mass basis, dry biomass) ──
  /** Char yield fraction (0-1). Overrides automatic estimate if set. */
  private double charYield = Double.NaN;
  /** Bio-oil yield fraction (0-1). Overrides automatic estimate if set. */
  private double bioOilYield = Double.NaN;
  /** Gas yield fraction (0-1). Overrides automatic estimate if set. */
  private double gasYield = Double.NaN;
  /** Whether user-supplied yields should be used. */
  private boolean userYieldsSet = false;

  // ── Internal reactor ──
  /** Internal Gibbs reactor for vapour-phase equilibrium. */
  private transient GibbsReactor gibbsReactor;

  // ── Output streams ──
  /** Product gas outlet stream (non-condensable gases). */
  private StreamInterface gasOutStream;
  /** Bio-oil outlet stream (condensable vapours). */
  private StreamInterface bioOilOutStream;
  /** Biochar outlet stream (solid residue). */
  private StreamInterface biocharOutStream;

  // ── Results ──
  /** Actual char yield used in last simulation (0-1). */
  private double actualCharYield = Double.NaN;
  /** Actual bio-oil yield used in last simulation (0-1). */
  private double actualBioOilYield = Double.NaN;
  /** Actual gas yield used in last simulation (0-1). */
  private double actualGasYield = Double.NaN;
  /** Bio-oil HHV in MJ/kg. */
  private double bioOilHHV = Double.NaN;
  /** Biochar HHV in MJ/kg. */
  private double biocharHHV = Double.NaN;
  /** Gas LHV in MJ/Nm3. */
  private double gasLHVMjPerNm3 = Double.NaN;
  /** Energy yield as fraction of feed energy in products. */
  private double energyYield = Double.NaN;
  /** Whether the reactor has been run. */
  private boolean hasRun = false;

  // ── Atomic masses ──
  private static final double MW_C = 12.011;
  private static final double MW_H = 1.008;
  private static final double MW_O = 15.999;
  private static final double MW_N = 14.007;
  private static final double MW_S = 32.065;

  /**
   * Creates a pyrolysis reactor with the given name.
   *
   * @param name equipment name
   */
  public PyrolysisReactor(String name) {
    super(name);
  }

  /**
   * Sets the biomass feedstock and feed rate.
   *
   * @param biomass biomass characterization
   * @param feedRateKgPerHr dry biomass feed rate in kg/hr
   */
  public void setBiomass(BiomassCharacterization biomass, double feedRateKgPerHr) {
    this.biomass = biomass;
    this.biomassFeedRateKgPerHr = feedRateKgPerHr;
  }

  /**
   * Sets the pyrolysis mode.
   *
   * @param mode the pyrolysis mode
   */
  public void setPyrolysisMode(PyrolysisMode mode) {
    this.pyrolysisMode = mode;
  }

  /**
   * Gets the pyrolysis mode.
   *
   * @return pyrolysis mode
   */
  public PyrolysisMode getPyrolysisMode() {
    return pyrolysisMode;
  }

  /**
   * Sets the pyrolysis temperature in Kelvin.
   *
   * @param temperatureK temperature in Kelvin
   */
  public void setPyrolysisTemperature(double temperatureK) {
    this.pyrolysisTemperature = temperatureK;
  }

  /**
   * Sets the pyrolysis temperature with unit specification.
   *
   * @param temperature temperature value
   * @param unit unit string ("K", "C", "F")
   */
  public void setPyrolysisTemperature(double temperature, String unit) {
    if ("C".equalsIgnoreCase(unit)) {
      this.pyrolysisTemperature = temperature + 273.15;
    } else if ("F".equalsIgnoreCase(unit)) {
      this.pyrolysisTemperature = (temperature - 32.0) * 5.0 / 9.0 + 273.15;
    } else {
      this.pyrolysisTemperature = temperature;
    }
  }

  /**
   * Gets the pyrolysis temperature in Kelvin.
   *
   * @return pyrolysis temperature in K
   */
  public double getPyrolysisTemperature() {
    return pyrolysisTemperature;
  }

  /**
   * Sets the reactor pressure in bara.
   *
   * @param pressureBara reactor pressure
   */
  public void setReactorPressure(double pressureBara) {
    this.reactorPressure = pressureBara;
  }

  /**
   * Gets the reactor pressure in bara.
   *
   * @return reactor pressure
   */
  public double getReactorPressure() {
    return reactorPressure;
  }

  /**
   * Sets the heating rate in K/s.
   *
   * @param heatingRateKPerS heating rate
   */
  public void setHeatingRate(double heatingRateKPerS) {
    this.heatingRate = heatingRateKPerS;
  }

  /**
   * Gets the heating rate in K/s.
   *
   * @return heating rate
   */
  public double getHeatingRate() {
    return heatingRate;
  }

  /**
   * Sets the vapour residence time in seconds.
   *
   * @param seconds vapour residence time
   */
  public void setVapourResidenceTime(double seconds) {
    this.vapourResidenceTime = seconds;
  }

  /**
   * Gets the vapour residence time in seconds.
   *
   * @return vapour residence time in seconds
   */
  public double getVapourResidenceTime() {
    return vapourResidenceTime;
  }

  /**
   * Sets product yield fractions manually. All three must sum to approximately 1.0.
   *
   * @param charYieldFrac char yield fraction (0-1)
   * @param bioOilYieldFrac bio-oil yield fraction (0-1)
   * @param gasYieldFrac gas yield fraction (0-1)
   */
  public void setProductYields(double charYieldFrac, double bioOilYieldFrac, double gasYieldFrac) {
    this.charYield = charYieldFrac;
    this.bioOilYield = bioOilYieldFrac;
    this.gasYield = gasYieldFrac;
    this.userYieldsSet = true;
  }

  /**
   * Returns the gas outlet stream.
   *
   * @return gas outlet stream, or null if not yet run
   */
  public StreamInterface getGasOutStream() {
    return gasOutStream;
  }

  /**
   * Returns the bio-oil outlet stream.
   *
   * @return bio-oil outlet stream, or null if not yet run
   */
  public StreamInterface getBioOilOutStream() {
    return bioOilOutStream;
  }

  /**
   * Returns the biochar outlet stream.
   *
   * @return biochar outlet stream, or null if not yet run
   */
  public StreamInterface getBiocharOutStream() {
    return biocharOutStream;
  }

  /**
   * Returns the actual char yield from the last run.
   *
   * @return char yield fraction (0-1)
   */
  public double getActualCharYield() {
    return actualCharYield;
  }

  /**
   * Returns the actual bio-oil yield from the last run.
   *
   * @return bio-oil yield fraction (0-1)
   */
  public double getActualBioOilYield() {
    return actualBioOilYield;
  }

  /**
   * Returns the actual gas yield from the last run.
   *
   * @return gas yield fraction (0-1)
   */
  public double getActualGasYield() {
    return actualGasYield;
  }

  /**
   * Returns the bio-oil HHV in MJ/kg.
   *
   * @return bio-oil HHV
   */
  public double getBioOilHHV() {
    return bioOilHHV;
  }

  /**
   * Returns the biochar HHV in MJ/kg.
   *
   * @return biochar HHV
   */
  public double getBiocharHHV() {
    return biocharHHV;
  }

  /**
   * Returns the gas LHV in MJ/Nm3.
   *
   * @return gas LHV
   */
  public double getGasLHVMjPerNm3() {
    return gasLHVMjPerNm3;
  }

  /**
   * Returns the energy yield as the fraction of feed energy recovered in all products.
   *
   * @return energy yield (0-1)
   */
  public double getEnergyYield() {
    return energyYield;
  }

  /** {@inheritDoc} */
  @Override
  public List<StreamInterface> getOutletStreams() {
    List<StreamInterface> outlets = new ArrayList<StreamInterface>();
    if (gasOutStream != null) {
      outlets.add(gasOutStream);
    }
    if (bioOilOutStream != null) {
      outlets.add(bioOilOutStream);
    }
    if (biocharOutStream != null) {
      outlets.add(biocharOutStream);
    }
    return Collections.unmodifiableList(outlets);
  }

  /** {@inheritDoc} */
  @Override
  public List<StreamInterface> getInletStreams() {
    return Collections.unmodifiableList(new ArrayList<StreamInterface>());
  }

  /**
   * Runs the pyrolysis reactor simulation.
   *
   * @param id UUID for this run
   */
  @Override
  public void run(UUID id) {
    if (biomass == null) {
      throw new IllegalStateException("Biomass characterization must be set before running");
    }
    if (biomassFeedRateKgPerHr <= 0.0) {
      throw new IllegalStateException("Biomass feed rate must be positive");
    }
    if (!biomass.isCalculated()) {
      biomass.calculate();
    }

    // ── Step 1: Determine product yields ──
    estimateProductYields();

    double dryFeedKg = biomassFeedRateKgPerHr;
    double charMassKg = dryFeedKg * actualCharYield;
    double bioOilMassKg = dryFeedKg * actualBioOilYield;
    double gasMassKg = dryFeedKg * actualGasYield;

    // ── Step 2: Elemental analysis of biomass ──
    double dafFraction = (100.0 - biomass.getAsh()) / 100.0;
    double cWt = biomass.getCarbonWt() / 100.0;
    double hWt = biomass.getHydrogenWt() / 100.0;
    double oWt = biomass.getOxygenWt() / 100.0;
    double nWt = biomass.getNitrogenWt() / 100.0;
    double sWt = biomass.getSulfurWt() / 100.0;
    double ashFrac = biomass.getAsh() / 100.0;

    // ── Step 3: Build gas-phase fluid for Gibbs equilibrium ──
    // The gas fraction elemental composition is estimated by subtracting char and bio-oil
    // from total biomass. Char is modelled as high-carbon residue, bio-oil as oxygenated
    // hydrocarbons.

    // Char assumed composition: 80% C, 3% H, 10% O, rest ash
    double charCFrac = 0.80;
    double charHFrac = 0.03;
    double charOFrac = 0.10;

    // Bio-oil assumed composition: 55% C, 7% H, 37% O, 1% N
    double oilCFrac = 0.55;
    double oilHFrac = 0.07;
    double oilOFrac = 0.37;
    double oilNFrac = 0.01;

    // Carbon balance
    double totalCKg = dryFeedKg * dafFraction * cWt;
    double charCKg = charMassKg * charCFrac;
    double oilCKg = bioOilMassKg * oilCFrac;
    double gasCKg = Math.max(0.0, totalCKg - charCKg - oilCKg);

    // Hydrogen balance
    double totalHKg = dryFeedKg * dafFraction * hWt;
    double charHKg = charMassKg * charHFrac;
    double oilHKg = bioOilMassKg * oilHFrac;
    double gasHKg = Math.max(0.0, totalHKg - charHKg - oilHKg);

    // Oxygen balance
    double totalOKg = dryFeedKg * dafFraction * oWt;
    double charOKg = charMassKg * charOFrac;
    double oilOKg = bioOilMassKg * oilOFrac;
    double gasOKg = Math.max(0.0, totalOKg - charOKg - oilOKg);

    // Nitrogen balance
    double totalNKg = dryFeedKg * dafFraction * nWt;
    double oilNKg = bioOilMassKg * oilNFrac;
    double gasNKg = Math.max(0.0, totalNKg - oilNKg);

    // Sulfur — all goes to gas phase
    double gasSKg = dryFeedKg * dafFraction * sWt;

    // Mole flows for gas phase
    double gasCMol = gasCKg / MW_C;
    double gasHMol = gasHKg / MW_H;
    double gasOMol = gasOKg / MW_O;
    double gasNMol = gasNKg / MW_N;
    double gasSMol = gasSKg / MW_S;

    // Moisture from biomass enters gas phase
    double moistureFraction = biomass.getMoisture() / 100.0;
    double totalWetFeed = biomassFeedRateKgPerHr / (1.0 - moistureFraction);
    double moistureKg = totalWetFeed * moistureFraction;
    double moistureMol = moistureKg / 18.015;

    // ── Step 4: Build pyrolysis gas fluid ──
    SystemInterface gasFluid = new SystemSrkEos(pyrolysisTemperature, reactorPressure);

    // Carbon as CH4 proxy + excess as CO
    double ch4Mol = Math.min(gasCMol, gasHMol / 4.0);
    double excessH2Mol = Math.max(0.0, (gasHMol - ch4Mol * 4.0) / 2.0);
    double excessCMol = Math.max(0.0, gasCMol - ch4Mol);

    if (ch4Mol > 0) {
      gasFluid.addComponent("methane", ch4Mol, "mole/hr");
    }
    if (excessH2Mol > 0) {
      gasFluid.addComponent("hydrogen", excessH2Mol, "mole/hr");
    }
    if (excessCMol > 0) {
      gasFluid.addComponent("CO", excessCMol, "mole/hr");
    }

    double totalO2Mol = gasOMol / 2.0;
    if (totalO2Mol > 0) {
      gasFluid.addComponent("oxygen", totalO2Mol, "mole/hr");
    }

    double totalWaterMol = moistureMol;
    if (totalWaterMol > 0) {
      gasFluid.addComponent("water", totalWaterMol, "mole/hr");
    }

    if (gasNMol > 0) {
      gasFluid.addComponent("nitrogen", gasNMol / 2.0, "mole/hr");
    }

    if (gasSMol > 0) {
      gasFluid.addComponent("H2S", gasSMol, "mole/hr");
    }

    // Ensure key species present
    ensureComponent(gasFluid, "CO2");
    ensureComponent(gasFluid, "CO");
    ensureComponent(gasFluid, "hydrogen");
    ensureComponent(gasFluid, "methane");
    ensureComponent(gasFluid, "water");

    gasFluid.setMixingRule("classic");
    gasFluid.init(0);
    gasFluid.init(3);

    // ── Step 5: Gibbs equilibrium for gas phase ──
    Stream reactorFeed = new Stream(getName() + " gas feed", gasFluid);
    reactorFeed.run(id);

    gibbsReactor = new GibbsReactor(getName() + " Gibbs", reactorFeed);
    gibbsReactor.setUseAllDatabaseSpecies(false);
    gibbsReactor.setDampingComposition(1e-4);
    gibbsReactor.setMaxIterations(5000);
    gibbsReactor.setConvergenceTolerance(1e-6);
    gibbsReactor.setEnergyMode(GibbsReactor.EnergyMode.ISOTHERMAL);

    if (gasNMol > 0) {
      gibbsReactor.setComponentAsInert("nitrogen");
    }

    gibbsReactor.run(id);

    gasOutStream = gibbsReactor.getOutletStream();
    gasOutStream.setName(getName() + " gas");

    // ── Step 6: Bio-oil stream (surrogate composition) ──
    SystemInterface oilFluid = new SystemSrkEos(273.15 + 25.0, reactorPressure);
    // Bio-oil is modelled as a surrogate: water + acetic acid proxied by CO2+CH4 mix
    // The overall mass flow is what matters for energy balance
    double oilWaterFrac = 0.25; // Typical bio-oil water content
    double oilWaterMol = (bioOilMassKg * oilWaterFrac) / 18.015;
    double oilOrganicKg = bioOilMassKg * (1.0 - oilWaterFrac);
    // Model organic fraction as n-pentane surrogate (similar C/H/O for energy content)
    double oilOrganicMol = Math.max(1e-10, oilOrganicKg / 72.15);

    oilFluid.addComponent("water", Math.max(1e-10, oilWaterMol), "mole/hr");
    oilFluid.addComponent("n-pentane", oilOrganicMol, "mole/hr");
    oilFluid.setMixingRule("classic");
    oilFluid.init(0);
    oilFluid.init(3);
    bioOilOutStream = new Stream(getName() + " bio-oil", oilFluid);
    bioOilOutStream.run(id);

    // ── Step 7: Biochar stream ──
    SystemInterface charFluid = new SystemSrkEos(273.15 + 25.0, reactorPressure);
    double charMol = Math.max(1e-10, charMassKg * charCFrac / MW_C);
    charFluid.addComponent("methane", charMol, "mole/hr");
    charFluid.setMixingRule("classic");
    charFluid.init(0);
    charFluid.init(3);
    biocharOutStream = new Stream(getName() + " biochar", charFluid);
    biocharOutStream.run(id);

    // ── Step 8: Calculate performance metrics ──
    calculatePerformanceMetrics();

    hasRun = true;
  }

  /**
   * Estimates product yields based on pyrolysis mode and biomass properties when user values are
   * not set.
   *
   * <p>
   * Default correlations are derived from published literature averages:
   * </p>
   * <ul>
   * <li>Slow: char ~ 35%, oil ~ 30%, gas ~ 35%</li>
   * <li>Fast: char ~ 15-20%, oil ~ 50-65%, gas ~ 15-25%</li>
   * <li>Flash: char ~ 10-15%, oil ~ 55-70%, gas ~ 15-25%</li>
   * </ul>
   *
   * <p>
   * Temperature adjustments: higher temperature shifts yield from char toward gas.
   * </p>
   */
  private void estimateProductYields() {
    if (userYieldsSet && !Double.isNaN(charYield) && !Double.isNaN(bioOilYield)
        && !Double.isNaN(gasYield)) {
      actualCharYield = charYield;
      actualBioOilYield = bioOilYield;
      actualGasYield = gasYield;
      return;
    }

    double tempC = pyrolysisTemperature - 273.15;
    double vm = biomass.getVolatileMatter(); // volatile matter %

    switch (pyrolysisMode) {
      case SLOW:
        actualCharYield = 0.35;
        actualBioOilYield = 0.30;
        actualGasYield = 0.35;
        break;
      case FAST:
        actualCharYield = 0.18;
        actualBioOilYield = 0.60;
        actualGasYield = 0.22;
        break;
      case FLASH:
        actualCharYield = 0.12;
        actualBioOilYield = 0.65;
        actualGasYield = 0.23;
        break;
      default:
        actualCharYield = 0.20;
        actualBioOilYield = 0.55;
        actualGasYield = 0.25;
        break;
    }

    // Temperature correction: higher T shifts char to gas
    if (tempC > 500.0) {
      double shift = Math.min(0.10, (tempC - 500.0) / 1000.0);
      actualCharYield = Math.max(0.05, actualCharYield - shift);
      actualGasYield = actualGasYield + shift;
    } else if (tempC < 400.0) {
      double shift = Math.min(0.10, (400.0 - tempC) / 1000.0);
      actualCharYield = actualCharYield + shift;
      actualBioOilYield = Math.max(0.10, actualBioOilYield - shift);
    }

    // Volatile matter correction: high VM favours oil + gas over char
    if (vm > 80.0) {
      double shift = (vm - 80.0) / 200.0;
      actualCharYield = Math.max(0.05, actualCharYield - shift);
      actualBioOilYield = actualBioOilYield + shift * 0.6;
      actualGasYield = actualGasYield + shift * 0.4;
    }

    // Normalise to sum to 1.0
    double total = actualCharYield + actualBioOilYield + actualGasYield;
    if (total > 0.0) {
      actualCharYield /= total;
      actualBioOilYield /= total;
      actualGasYield /= total;
    }
  }

  /**
   * Calculates performance metrics after simulation.
   */
  private void calculatePerformanceMetrics() {
    if (gasOutStream == null) {
      return;
    }
    SystemInterface syngas = gasOutStream.getThermoSystem();
    if (syngas == null) {
      return;
    }

    // Gas LHV
    double coFrac = getMoleFraction(syngas, "CO");
    double h2Frac = getMoleFraction(syngas, "hydrogen");
    double ch4Frac = getMoleFraction(syngas, "methane");
    gasLHVMjPerNm3 = coFrac * 12.63 + h2Frac * 10.78 + ch4Frac * 35.88;

    // Bio-oil HHV estimate (typical 16-19 MJ/kg for fast pyrolysis bio-oil)
    bioOilHHV = 17.0;
    // Biochar HHV estimate (typical 25-33 MJ/kg)
    biocharHHV = 30.0;

    // Energy yield: energy in products / energy in feed
    double dryFeedKg = biomassFeedRateKgPerHr;
    double feedEnergyMJ = biomass.getHHV() * dryFeedKg;

    double charEnergyMJ = biocharHHV * dryFeedKg * actualCharYield;
    double oilEnergyMJ = bioOilHHV * dryFeedKg * actualBioOilYield;

    double gasTotalMol = syngas.getFlowRate("mole/hr");
    double gasNm3Hr = gasTotalMol * 22.414 / 1000.0;
    double gasEnergyMJ = gasLHVMjPerNm3 * gasNm3Hr;

    double totalProductEnergy = charEnergyMJ + oilEnergyMJ + gasEnergyMJ;
    energyYield = feedEnergyMJ > 0 ? totalProductEnergy / feedEnergyMJ : 0.0;
  }

  /**
   * Returns the mole fraction of a component in a system, or 0 if not present.
   *
   * @param system the thermo system
   * @param componentName the component name
   * @return mole fraction
   */
  private double getMoleFraction(SystemInterface system, String componentName) {
    try {
      if (system.hasComponent(componentName)) {
        return system.getPhase(0).getComponent(componentName).getz();
      }
    } catch (Exception e) {
      logger.debug("Could not get mole fraction for {}: {}", componentName, e.getMessage());
    }
    return 0.0;
  }

  /**
   * Ensures a component exists in the fluid at a trace amount.
   *
   * @param fluid the system interface
   * @param componentName the component name
   */
  private void ensureComponent(SystemInterface fluid, String componentName) {
    if (!fluid.hasComponent(componentName)) {
      fluid.addComponent(componentName, 1e-20, "mole/hr");
    }
  }

  /**
   * Returns a map of key results from the pyrolysis simulation.
   *
   * @return map of result name to value
   */
  public Map<String, Object> getResults() {
    Map<String, Object> results = new LinkedHashMap<String, Object>();
    results.put("pyrolysisMode", pyrolysisMode.name());
    results.put("pyrolysisTemperature_C", pyrolysisTemperature - 273.15);
    results.put("reactorPressure_bara", reactorPressure);
    results.put("charYield", actualCharYield);
    results.put("bioOilYield", actualBioOilYield);
    results.put("gasYield", actualGasYield);
    results.put("bioOilHHV_MJperKg", bioOilHHV);
    results.put("biocharHHV_MJperKg", biocharHHV);
    results.put("gasLHV_MJperNm3", gasLHVMjPerNm3);
    results.put("energyYield", energyYield);

    if (gasOutStream != null) {
      SystemInterface gas = gasOutStream.getThermoSystem();
      if (gas != null) {
        Map<String, Double> composition = new LinkedHashMap<String, Double>();
        for (int i = 0; i < gas.getPhase(0).getNumberOfComponents(); i++) {
          String cname = gas.getPhase(0).getComponent(i).getComponentName();
          double z = gas.getPhase(0).getComponent(i).getz();
          if (z > 1e-10) {
            composition.put(cname, z);
          }
        }
        results.put("gasComposition_molFrac", composition);
      }
    }
    return results;
  }

  /**
   * Returns a JSON string with the pyrolysis results.
   *
   * @return JSON results string
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(getResults());
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    if (!hasRun) {
      return "PyrolysisReactor '" + getName() + "' (not yet run)";
    }
    StringBuilder sb = new StringBuilder();
    sb.append("PyrolysisReactor '").append(getName()).append("'\n");
    sb.append(
        String.format("  Mode: %s, T = %.0f C%n", pyrolysisMode, pyrolysisTemperature - 273.15));
    sb.append(String.format("  Yields: char = %.1f%%, oil = %.1f%%, gas = %.1f%%%n",
        actualCharYield * 100, actualBioOilYield * 100, actualGasYield * 100));
    sb.append(String.format("  Gas LHV = %.2f MJ/Nm3%n", gasLHVMjPerNm3));
    sb.append(String.format("  Energy yield = %.3f%n", energyYield));
    return sb.toString();
  }
}
