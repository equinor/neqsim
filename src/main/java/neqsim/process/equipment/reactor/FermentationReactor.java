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
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Fermentation reactor with Monod and Contois kinetics for bio-chemical conversion.
 *
 * <p>
 * Extends {@link Fermenter} with mechanistic kinetic models (Monod, Contois) for microbial growth,
 * substrate consumption, and product formation. Supports substrate inhibition, product inhibition,
 * and fed-batch operation modes. Suitable for modelling ethanol fermentation, organic acid
 * production, and other biofuel/bioproduct pathways.
 * </p>
 *
 * <h2>Kinetic Models</h2>
 *
 * <table>
 * <caption>Supported kinetic model types</caption>
 * <tr>
 * <th>Model</th>
 * <th>Equation</th>
 * <th>Typical Use</th>
 * </tr>
 * <tr>
 * <td>MONOD</td>
 * <td>mu = muMax * S / (Ks + S)</td>
 * <td>Dilute substrates, wastewater</td>
 * </tr>
 * <tr>
 * <td>CONTOIS</td>
 * <td>mu = muMax * S / (Ksx * X + S)</td>
 * <td>High-solids, solid-state fermentation</td>
 * </tr>
 * <tr>
 * <td>SUBSTRATE_INHIBITED</td>
 * <td>mu = muMax * S / (Ks + S + S^2/Ki)</td>
 * <td>High-concentration substrates</td>
 * </tr>
 * <tr>
 * <td>PRODUCT_INHIBITED</td>
 * <td>mu = muMax * S / (Ks + S) * (1 - P/Pmax)^n</td>
 * <td>Ethanol fermentation</td>
 * </tr>
 * </table>
 *
 * <h2>Operation Modes</h2>
 * <ul>
 * <li><b>CONTINUOUS</b> - steady-state CSTR with constant dilution rate</li>
 * <li><b>BATCH</b> - closed system, time-integrated kinetics</li>
 * <li><b>FED_BATCH</b> - batch with periodic substrate feeding</li>
 * </ul>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * FermentationReactor reactor = new FermentationReactor("EtOH-R1");
 * reactor.setKineticModel(FermentationReactor.KineticModel.PRODUCT_INHIBITED);
 * reactor.setOperationMode(FermentationReactor.OperationMode.CONTINUOUS);
 * reactor.setMaxSpecificGrowthRate(0.40); // 1/hr
 * reactor.setMonodConstant(2.0); // g/L
 * reactor.setYieldBiomass(0.05); // g cells / g substrate
 * reactor.setYieldProduct(0.46); // g ethanol / g glucose (stoichiometric ~0.51)
 * reactor.setProductInhibitionConcentration(90.0); // g/L
 * reactor.setSubstrateConcentration(180.0); // g/L glucose
 * reactor.setBiomassConcentration(2.0); // g/L inoculum
 * reactor.setVesselVolume(200.0); // m3
 * reactor.setResidenceTime(48.0, "hr");
 * reactor.setReactorTemperature(32.0, "C");
 * reactor.run();
 *
 * double substrateConversion = reactor.getSubstrateConversion();
 * double productConcentration = reactor.getProductConcentrationGPerL();
 * double productivity = reactor.getProductivity();
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class FermentationReactor extends Fermenter {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1003L;
  /** Logger instance. */
  private static final Logger logger = LogManager.getLogger(FermentationReactor.class);

  /**
   * Kinetic model type enumeration.
   */
  public enum KineticModel {
    /** Classic Monod kinetics: mu = muMax * S / (Ks + S). */
    MONOD,
    /** Contois kinetics: mu = muMax * S / (Ksx * X + S). */
    CONTOIS,
    /** Substrate-inhibited Monod: mu = muMax * S / (Ks + S + S^2/Ki). */
    SUBSTRATE_INHIBITED,
    /** Product-inhibited Monod: mu = muMax * S / (Ks + S) * (1 - P/Pmax)^n. */
    PRODUCT_INHIBITED
  }

  /**
   * Operation mode enumeration.
   */
  public enum OperationMode {
    /** Continuous steady-state CSTR. */
    CONTINUOUS,
    /** Batch fermentation. */
    BATCH,
    /** Fed-batch with periodic substrate addition. */
    FED_BATCH
  }

  // ── Kinetic parameters ──
  /** Selected kinetic model. */
  private KineticModel kineticModel = KineticModel.MONOD;
  /** Operation mode. */
  private OperationMode operationMode = OperationMode.CONTINUOUS;
  /** Maximum specific growth rate in 1/hr. */
  private double muMax = 0.30;
  /** Monod half-saturation constant (Ks) in g/L. */
  private double monodConstant = 1.0;
  /** Contois constant (Ksx) in g substrate / g biomass, used with CONTOIS model. */
  private double contoisConstant = 5.0;
  /** Substrate inhibition constant (Ki) in g/L, used with SUBSTRATE_INHIBITED model. */
  private double substrateInhibitionConstant = 150.0;
  /** Product inhibition concentration (Pmax) in g/L, used with PRODUCT_INHIBITED model. */
  private double productInhibitionConcentration = 100.0;
  /** Product inhibition exponent, used with PRODUCT_INHIBITED model. */
  private double productInhibitionExponent = 1.0;

  // ── Yield coefficients ──
  /** Biomass yield (g cells / g substrate consumed). */
  private double yieldBiomass = 0.10;
  /** Product yield (g product / g substrate consumed). */
  private double yieldProduct = 0.45;
  /** Maintenance coefficient (g substrate / (g cells * hr)). */
  private double maintenanceCoeff = 0.01;

  // ── Operating conditions ──
  /** Initial / feed substrate concentration in g/L. */
  private double substrateConcentration = 100.0;
  /** Initial / feed biomass concentration in g/L. */
  private double biomassConcentration = 1.0;
  /** Initial product concentration in g/L. */
  private double initialProductConcentration = 0.0;

  // ── Fed-batch configuration ──
  /** Feeding rate in L/hr for fed-batch mode. */
  private double feedingRate = 0.0;
  /** Feed substrate concentration in g/L for fed-batch mode. */
  private double feedSubstrateConcentration = 300.0;
  /** Batch time in hours for BATCH and FED_BATCH modes. */
  private double batchTime = 48.0;

  // ── Product stream configuration ──
  /** Name of the main fermentation product for NeqSim component mapping. */
  private String productComponentName = "ethanol";
  /** Name of the substrate component for NeqSim component mapping. */
  private String substrateComponentName = "n-hexane";
  /** Name of the gas product component. */
  private String gasProductName = "CO2";
  /** Gas product yield per substrate consumed (mol gas / mol substrate). */
  private double gasYieldMolPerMol = 2.0;

  // ── Output streams ──
  /** Liquid product outlet stream. */
  private StreamInterface liquidOutStream;
  /** Gas product outlet stream (CO2, etc.). */
  private StreamInterface gasOutStream;

  // ── Results ──
  /** Final biomass concentration in g/L. */
  private double finalBiomassConc = 0.0;
  /** Final substrate concentration in g/L. */
  private double finalSubstrateConc = 0.0;
  /** Final product concentration in g/L. */
  private double finalProductConc = 0.0;
  /** Substrate conversion (0-1). */
  private double substrateConversion = 0.0;
  /** Specific growth rate achieved in 1/hr. */
  private double specificGrowthRate = 0.0;
  /** Volumetric productivity in g/(L*hr). */
  private double productivity = 0.0;
  /** Total substrate consumed in kg/hr. */
  private double substrateConsumedKgPerHr = 0.0;
  /** Total product formed in kg/hr. */
  private double productFormedKgPerHr = 0.0;
  /** Whether the reactor has been run. */
  private boolean hasRun = false;

  /**
   * Creates a fermentation reactor with the given name.
   *
   * @param name equipment name
   */
  public FermentationReactor(String name) {
    super(name);
    setAerobic(false);
    setIsothermal(true);
  }

  /**
   * Creates a fermentation reactor with inlet stream.
   *
   * @param name equipment name
   * @param inletStream feed stream
   */
  public FermentationReactor(String name, StreamInterface inletStream) {
    super(name, inletStream);
    setAerobic(false);
    setIsothermal(true);
  }

  // ── Kinetic model configuration ──

  /**
   * Sets the kinetic model.
   *
   * @param model kinetic model type
   */
  public void setKineticModel(KineticModel model) {
    this.kineticModel = model;
  }

  /**
   * Gets the kinetic model.
   *
   * @return kinetic model type
   */
  public KineticModel getKineticModel() {
    return kineticModel;
  }

  /**
   * Sets the operation mode.
   *
   * @param mode operation mode
   */
  public void setOperationMode(OperationMode mode) {
    this.operationMode = mode;
  }

  /**
   * Gets the operation mode.
   *
   * @return operation mode
   */
  public OperationMode getOperationMode() {
    return operationMode;
  }

  /**
   * Sets the maximum specific growth rate.
   *
   * @param muMax maximum specific growth rate in 1/hr
   */
  public void setMaxSpecificGrowthRate(double muMax) {
    this.muMax = muMax;
  }

  /**
   * Gets the maximum specific growth rate.
   *
   * @return maximum specific growth rate in 1/hr
   */
  public double getMaxSpecificGrowthRate() {
    return muMax;
  }

  /**
   * Sets the Monod half-saturation constant.
   *
   * @param ks half-saturation constant in g/L
   */
  public void setMonodConstant(double ks) {
    this.monodConstant = ks;
  }

  /**
   * Gets the Monod constant.
   *
   * @return half-saturation constant in g/L
   */
  public double getMonodConstant() {
    return monodConstant;
  }

  /**
   * Sets the Contois constant.
   *
   * @param ksx Contois constant in g/g
   */
  public void setContoisConstant(double ksx) {
    this.contoisConstant = ksx;
  }

  /**
   * Gets the Contois constant.
   *
   * @return Contois constant in g/g
   */
  public double getContoisConstant() {
    return contoisConstant;
  }

  /**
   * Sets the substrate inhibition constant.
   *
   * @param ki substrate inhibition constant in g/L
   */
  public void setSubstrateInhibitionConstant(double ki) {
    this.substrateInhibitionConstant = ki;
  }

  /**
   * Gets the substrate inhibition constant.
   *
   * @return substrate inhibition constant in g/L
   */
  public double getSubstrateInhibitionConstant() {
    return substrateInhibitionConstant;
  }

  /**
   * Sets the product inhibition concentration threshold.
   *
   * @param pmax product concentration threshold in g/L
   */
  public void setProductInhibitionConcentration(double pmax) {
    this.productInhibitionConcentration = pmax;
  }

  /**
   * Gets the product inhibition concentration.
   *
   * @return product inhibition concentration in g/L
   */
  public double getProductInhibitionConcentration() {
    return productInhibitionConcentration;
  }

  /**
   * Sets the product inhibition exponent.
   *
   * @param n product inhibition exponent
   */
  public void setProductInhibitionExponent(double n) {
    this.productInhibitionExponent = n;
  }

  /**
   * Gets the product inhibition exponent.
   *
   * @return product inhibition exponent
   */
  public double getProductInhibitionExponent() {
    return productInhibitionExponent;
  }

  // ── Yield configuration ──

  /**
   * Sets the biomass yield coefficient.
   *
   * @param yxs biomass yield in g cells / g substrate
   */
  public void setYieldBiomass(double yxs) {
    this.yieldBiomass = yxs;
  }

  /**
   * Gets the biomass yield coefficient.
   *
   * @return biomass yield in g cells / g substrate
   */
  public double getYieldBiomass() {
    return yieldBiomass;
  }

  /**
   * Sets the product yield coefficient.
   *
   * @param yps product yield in g product / g substrate
   */
  public void setYieldProduct(double yps) {
    this.yieldProduct = yps;
  }

  /**
   * Gets the product yield coefficient.
   *
   * @return product yield in g product / g substrate
   */
  public double getYieldProduct() {
    return yieldProduct;
  }

  /**
   * Sets the maintenance coefficient.
   *
   * @param ms maintenance coefficient in g substrate / (g cells * hr)
   */
  public void setMaintenanceCoefficient(double ms) {
    this.maintenanceCoeff = ms;
  }

  /**
   * Gets the maintenance coefficient.
   *
   * @return maintenance coefficient
   */
  public double getMaintenanceCoefficient() {
    return maintenanceCoeff;
  }

  // ── Operating conditions ──

  /**
   * Sets the feed substrate concentration.
   *
   * @param concGPerL substrate concentration in g/L
   */
  public void setSubstrateConcentration(double concGPerL) {
    this.substrateConcentration = concGPerL;
  }

  /**
   * Gets the substrate concentration.
   *
   * @return substrate concentration in g/L
   */
  public double getSubstrateConcentration() {
    return substrateConcentration;
  }

  /**
   * Sets the biomass inoculum concentration.
   *
   * @param concGPerL biomass concentration in g/L
   */
  public void setBiomassConcentration(double concGPerL) {
    this.biomassConcentration = concGPerL;
  }

  /**
   * Gets the biomass concentration.
   *
   * @return biomass concentration in g/L
   */
  public double getBiomassConcentration() {
    return biomassConcentration;
  }

  /**
   * Sets the initial product concentration.
   *
   * @param concGPerL initial product concentration in g/L
   */
  public void setInitialProductConcentration(double concGPerL) {
    this.initialProductConcentration = concGPerL;
  }

  // ── Fed-batch configuration ──

  /**
   * Sets the feeding rate for fed-batch mode.
   *
   * @param rateL_hr feeding rate in L/hr
   */
  public void setFeedingRate(double rateL_hr) {
    this.feedingRate = rateL_hr;
  }

  /**
   * Sets the feed substrate concentration for fed-batch mode.
   *
   * @param concGPerL feed substrate concentration in g/L
   */
  public void setFeedSubstrateConcentration(double concGPerL) {
    this.feedSubstrateConcentration = concGPerL;
  }

  /**
   * Sets the batch time for batch or fed-batch modes.
   *
   * @param hours batch time in hours
   */
  public void setBatchTime(double hours) {
    this.batchTime = hours;
  }

  /**
   * Gets the batch time.
   *
   * @return batch time in hours
   */
  public double getBatchTime() {
    return batchTime;
  }

  // ── Component mapping ──

  /**
   * Sets the NeqSim component name for the fermentation product.
   *
   * @param componentName NeqSim component name (e.g. "ethanol", "methanol")
   */
  public void setProductComponentName(String componentName) {
    this.productComponentName = componentName;
  }

  /**
   * Gets the product component name.
   *
   * @return product component name
   */
  public String getProductComponentName() {
    return productComponentName;
  }

  /**
   * Sets the NeqSim component name for the substrate surrogate.
   *
   * @param componentName NeqSim component name (e.g. "n-hexane" as glucose surrogate)
   */
  public void setSubstrateComponentName(String componentName) {
    this.substrateComponentName = componentName;
  }

  /**
   * Gets the substrate component name.
   *
   * @return substrate component name
   */
  public String getSubstrateComponentName() {
    return substrateComponentName;
  }

  /**
   * Sets the gas product component name.
   *
   * @param componentName gas product name (e.g. "CO2")
   */
  public void setGasProductName(String componentName) {
    this.gasProductName = componentName;
  }

  /**
   * Sets the gas yield per substrate consumed.
   *
   * @param yieldMolPerMol moles of gas per mole of substrate
   */
  public void setGasYieldMolPerMol(double yieldMolPerMol) {
    this.gasYieldMolPerMol = yieldMolPerMol;
  }

  // ── Results access ──

  /**
   * Returns the liquid product outlet stream.
   *
   * @return liquid outlet stream, or null if not yet run
   */
  public StreamInterface getLiquidOutStream() {
    return liquidOutStream;
  }

  /**
   * Returns the gas product outlet stream.
   *
   * @return gas outlet stream, or null if not yet run
   */
  public StreamInterface getGasOutStream() {
    return gasOutStream;
  }

  /**
   * Returns the final biomass concentration.
   *
   * @return biomass concentration in g/L
   */
  public double getFinalBiomassConcentration() {
    return finalBiomassConc;
  }

  /**
   * Returns the final substrate concentration.
   *
   * @return substrate concentration in g/L
   */
  public double getFinalSubstrateConcentration() {
    return finalSubstrateConc;
  }

  /**
   * Returns the final product concentration.
   *
   * @return product concentration in g/L
   */
  public double getProductConcentrationGPerL() {
    return finalProductConc;
  }

  /**
   * Returns the substrate conversion.
   *
   * @return substrate conversion fraction (0-1)
   */
  public double getSubstrateConversion() {
    return substrateConversion;
  }

  /**
   * Returns the specific growth rate achieved.
   *
   * @return specific growth rate in 1/hr
   */
  public double getSpecificGrowthRate() {
    return specificGrowthRate;
  }

  /**
   * Returns the volumetric productivity.
   *
   * @return productivity in g/(L*hr)
   */
  public double getProductivity() {
    return productivity;
  }

  /**
   * Returns the total substrate consumed.
   *
   * @return substrate consumed in kg/hr
   */
  public double getSubstrateConsumedKgPerHr() {
    return substrateConsumedKgPerHr;
  }

  /**
   * Returns the total product formed.
   *
   * @return product formed in kg/hr
   */
  public double getProductFormedKgPerHr() {
    return productFormedKgPerHr;
  }

  /** {@inheritDoc} */
  @Override
  public List<StreamInterface> getOutletStreams() {
    List<StreamInterface> outlets = new ArrayList<StreamInterface>();
    if (liquidOutStream != null) {
      outlets.add(liquidOutStream);
    }
    if (gasOutStream != null) {
      outlets.add(gasOutStream);
    }
    return Collections.unmodifiableList(outlets);
  }

  /**
   * Computes the specific growth rate using the selected kinetic model.
   *
   * @param s substrate concentration in g/L
   * @param x biomass concentration in g/L
   * @param p product concentration in g/L
   * @return specific growth rate in 1/hr
   */
  public double computeGrowthRate(double s, double x, double p) {
    double mu;
    switch (kineticModel) {
      case CONTOIS:
        mu = muMax * s / (contoisConstant * x + s);
        break;
      case SUBSTRATE_INHIBITED:
        mu = muMax * s / (monodConstant + s + s * s / substrateInhibitionConstant);
        break;
      case PRODUCT_INHIBITED:
        double monodTerm = muMax * s / (monodConstant + s);
        double inhibFactor = Math.pow(Math.max(0.0, 1.0 - p / productInhibitionConcentration),
            productInhibitionExponent);
        mu = monodTerm * inhibFactor;
        break;
      case MONOD:
      default:
        mu = muMax * s / (monodConstant + s);
        break;
    }
    return mu;
  }

  /**
   * Runs the fermentation reactor simulation.
   *
   * @param id UUID for this run
   */
  @Override
  public void run(UUID id) {
    double reactorVol = getVesselVolume(); // m3
    double volL = reactorVol * 1000.0; // liters

    if (operationMode == OperationMode.CONTINUOUS) {
      runContinuous(id, volL);
    } else {
      runBatch(id, volL);
    }

    hasRun = true;
    setCalculationIdentifier(id);
  }

  /**
   * Runs continuous CSTR simulation solving steady-state mass balances.
   *
   * @param id UUID for this run
   * @param volL reactor volume in liters
   */
  private void runContinuous(UUID id, double volL) {
    double tau = getResidenceTime(); // hours
    double dilutionRate = 1.0 / tau; // 1/hr

    // Iterative solver: find steady-state S, X, P
    double s = substrateConcentration;
    double x = biomassConcentration;
    double p = initialProductConcentration;

    for (int iter = 0; iter < 200; iter++) {
      double mu = computeGrowthRate(s, x, p);

      // CSTR steady-state: D*X = mu*X => mu = D (at steady state)
      // Substrate balance: D*(S0 - S) = mu*X/Yxs + ms*X
      // Product balance: D*(P0 - P) = -yieldProduct * (mu*X/Yxs + ms*X) (product mass balance)

      // Solve for S from mu = D condition
      double sNew = solveSteadyStateSubstrate(dilutionRate);

      if (sNew < 0 || sNew > substrateConcentration) {
        // Washout condition: D > muMax, no conversion
        finalSubstrateConc = substrateConcentration;
        finalBiomassConc = 0.0;
        finalProductConc = 0.0;
        substrateConversion = 0.0;
        specificGrowthRate = 0.0;
        break;
      }

      double substrateConsumedPerL = substrateConcentration - sNew;
      double xNew = yieldBiomass * substrateConsumedPerL / (1.0 + maintenanceCoeff * tau);
      double pNew = initialProductConcentration + yieldProduct * substrateConsumedPerL;

      if (Math.abs(sNew - s) < 1e-8) {
        s = sNew;
        x = xNew;
        p = pNew;
        break;
      }
      s = sNew;
      x = xNew;
      p = pNew;
    }

    finalSubstrateConc = Math.max(0.0, s);
    finalBiomassConc = Math.max(0.0, x);
    finalProductConc = Math.max(0.0, p);
    substrateConversion =
        substrateConcentration > 0 ? 1.0 - finalSubstrateConc / substrateConcentration : 0.0;
    specificGrowthRate = computeGrowthRate(finalSubstrateConc, finalBiomassConc, finalProductConc);
    productivity = tau > 0 ? finalProductConc / tau : 0.0;

    // Mass flows
    double volumetricFlowLPerHr = volL / Math.max(1e-10, tau);
    substrateConsumedKgPerHr =
        (substrateConcentration - finalSubstrateConc) * volumetricFlowLPerHr / 1000.0;
    productFormedKgPerHr = finalProductConc * volumetricFlowLPerHr / 1000.0;

    buildOutputStreams(id, volumetricFlowLPerHr);
  }

  /**
   * Runs batch or fed-batch simulation using time integration.
   *
   * @param id UUID for this run
   * @param volL reactor volume in liters
   */
  private void runBatch(UUID id, double volL) {
    double dt = 0.05; // time step in hours
    int steps = (int) Math.ceil(batchTime / dt);

    double s = substrateConcentration;
    double x = biomassConcentration;
    double p = initialProductConcentration;
    double v = volL;

    for (int i = 0; i < steps; i++) {
      double mu = computeGrowthRate(s, x, p);

      // Growth
      double dxdt = mu * x;
      // Substrate consumption
      double dsdt = -(mu / Math.max(1e-10, yieldBiomass)) * x - maintenanceCoeff * x;
      // Product formation
      double dpdt = yieldProduct * (mu / Math.max(1e-10, yieldBiomass)) * x;

      // Fed-batch feeding
      if (operationMode == OperationMode.FED_BATCH && feedingRate > 0) {
        double feedFrac = feedingRate / Math.max(1e-10, v);
        dsdt += feedFrac * (feedSubstrateConcentration - s);
        dxdt -= feedFrac * x;
        dpdt -= feedFrac * p;
        v += feedingRate * dt;
      }

      s = Math.max(0.0, s + dsdt * dt);
      x = Math.max(0.0, x + dxdt * dt);
      p = Math.max(0.0, p + dpdt * dt);

      if (s <= 0.0) {
        break;
      }
    }

    finalSubstrateConc = s;
    finalBiomassConc = x;
    finalProductConc = p;
    substrateConversion =
        substrateConcentration > 0 ? 1.0 - finalSubstrateConc / substrateConcentration : 0.0;
    specificGrowthRate = computeGrowthRate(finalSubstrateConc, finalBiomassConc, finalProductConc);
    productivity = batchTime > 0 ? finalProductConc / batchTime : 0.0;

    substrateConsumedKgPerHr =
        (substrateConcentration - finalSubstrateConc) * volL / (1000.0 * batchTime);
    productFormedKgPerHr = finalProductConc * volL / (1000.0 * batchTime);

    double volumetricFlowLPerHr = volL / Math.max(1e-10, batchTime);
    buildOutputStreams(id, volumetricFlowLPerHr);
  }

  /**
   * Solves for steady-state substrate concentration in CSTR from kinetic model.
   *
   * @param dilutionRate dilution rate in 1/hr (= 1/tau)
   * @return steady-state substrate concentration in g/L
   */
  private double solveSteadyStateSubstrate(double dilutionRate) {
    // At steady state mu = D, solve for S
    switch (kineticModel) {
      case CONTOIS:
        // D = muMax * S / (Ksx * X + S), but X depends on S - use iterative
        return solveIterative(dilutionRate);
      case SUBSTRATE_INHIBITED:
        // D = muMax * S / (Ks + S + S^2/Ki) - quadratic
        return solveSubstrateInhibited(dilutionRate);
      case PRODUCT_INHIBITED:
        return solveIterative(dilutionRate);
      case MONOD:
      default:
        // D = muMax * S / (Ks + S) => S = Ks * D / (muMax - D)
        if (dilutionRate >= muMax) {
          return substrateConcentration; // washout
        }
        return monodConstant * dilutionRate / (muMax - dilutionRate);
    }
  }

  /**
   * Solves substrate-inhibited kinetics for steady-state S.
   *
   * @param d dilution rate
   * @return steady-state substrate concentration
   */
  private double solveSubstrateInhibited(double d) {
    // d = muMax * S / (Ks + S + S^2/Ki)
    // d*(Ks + S + S^2/Ki) = muMax * S
    // d*Ks + d*S + d*S^2/Ki = muMax * S
    // d*S^2/Ki + (d - muMax)*S + d*Ks = 0
    double a = d / substrateInhibitionConstant;
    double b = d - muMax;
    double c = d * monodConstant;
    double disc = b * b - 4.0 * a * c;
    if (disc < 0 || a == 0) {
      return substrateConcentration; // washout
    }
    double s1 = (-b - Math.sqrt(disc)) / (2.0 * a);
    double s2 = (-b + Math.sqrt(disc)) / (2.0 * a);
    // Return the smaller positive root
    if (s1 > 0 && s1 < substrateConcentration) {
      return s1;
    }
    if (s2 > 0 && s2 < substrateConcentration) {
      return s2;
    }
    return substrateConcentration;
  }

  /**
   * Iterative solver for steady-state substrate in complex kinetic models.
   *
   * @param d dilution rate
   * @return steady-state substrate concentration
   */
  private double solveIterative(double d) {
    double s = substrateConcentration * 0.5;
    double x = biomassConcentration;
    double p = initialProductConcentration;

    for (int iter = 0; iter < 500; iter++) {
      double subConsumed = substrateConcentration - s;
      x = yieldBiomass * subConsumed / (1.0 + maintenanceCoeff / Math.max(1e-10, d));
      p = initialProductConcentration + yieldProduct * subConsumed;

      double mu = computeGrowthRate(s, Math.max(0.001, x), p);
      double error = mu - d;

      if (Math.abs(error) < 1e-10) {
        break;
      }

      // Bisection: if mu > D, S is too high; if mu < D, S is too low
      // Simple Newton-like step
      double sNew = s - error * s / Math.max(1e-10, mu) * 0.5;
      sNew = Math.max(0.0, Math.min(substrateConcentration, sNew));

      if (Math.abs(sNew - s) < 1e-10) {
        break;
      }
      s = sNew;
    }
    return s;
  }

  /**
   * Creates the liquid and gas output streams from fermentation results.
   *
   * @param id UUID for this run
   * @param volumetricFlowLPerHr volumetric flow in L/hr
   */
  private void buildOutputStreams(UUID id, double volumetricFlowLPerHr) {
    double reactorTemp =
        Double.isNaN(getReactorTemperature()) ? 273.15 + 30.0 : getReactorTemperature();
    double reactorPres = Double.isNaN(getReactorPressure()) ? 1.01325 : getReactorPressure();

    // ── Liquid product stream ──
    double waterKgPerHr = volumetricFlowLPerHr / 1000.0 * 997.0; // ~water density
    double waterMolPerHr = waterKgPerHr * 1000.0 / 18.015;
    double productMolPerHr = productFormedKgPerHr * 1000.0 / 46.07; // ethanol MW
    double substrateMolPerHr = finalSubstrateConc * volumetricFlowLPerHr / 1000.0 * 1000.0 / 86.18; // n-hexane
                                                                                                    // MW

    SystemInterface liquidFluid = new SystemSrkEos(reactorTemp, reactorPres);
    liquidFluid.addComponent("water", Math.max(1e-10, waterMolPerHr), "mole/hr");
    liquidFluid.addComponent(productComponentName, Math.max(1e-10, productMolPerHr), "mole/hr");
    if (finalSubstrateConc > 0.01) {
      liquidFluid.addComponent(substrateComponentName, Math.max(1e-10, substrateMolPerHr),
          "mole/hr");
    }
    liquidFluid.setMixingRule("classic");
    liquidFluid.init(0);
    liquidFluid.init(3);

    liquidOutStream = new Stream(getName() + " liquid", liquidFluid);
    liquidOutStream.run(id);

    // ── Gas product stream (CO2) ──
    double gasProductKgPerHr = substrateConsumedKgPerHr * gasYieldMolPerMol * 44.01 / 180.16;
    double gasMolPerHr = gasProductKgPerHr * 1000.0 / 44.01;

    SystemInterface gasFluid = new SystemSrkEos(reactorTemp, reactorPres);
    gasFluid.addComponent(gasProductName, Math.max(1e-10, gasMolPerHr), "mole/hr");
    gasFluid.addComponent("water", Math.max(1e-10, gasMolPerHr * 0.02), "mole/hr");
    gasFluid.setMixingRule("classic");
    gasFluid.init(0);
    gasFluid.init(3);

    gasOutStream = new Stream(getName() + " gas", gasFluid);
    gasOutStream.run(id);
  }

  /**
   * Returns a results map for JSON output.
   *
   * @return map of result names to values
   */
  public Map<String, Object> getResults() {
    Map<String, Object> results = new LinkedHashMap<String, Object>();
    results.put("equipmentName", getName());
    results.put("kineticModel", kineticModel.name());
    results.put("operationMode", operationMode.name());
    results.put("muMax_1_per_hr", muMax);
    results.put("monodConstant_g_per_L", monodConstant);
    results.put("yieldBiomass_g_g", yieldBiomass);
    results.put("yieldProduct_g_g", yieldProduct);
    results.put("feedSubstrateConc_g_per_L", substrateConcentration);
    results.put("feedBiomassConc_g_per_L", biomassConcentration);
    results.put("vesselVolume_m3", getVesselVolume());
    results.put("residenceTime_hr", getResidenceTime());
    results.put("finalSubstrateConc_g_per_L", finalSubstrateConc);
    results.put("finalBiomassConc_g_per_L", finalBiomassConc);
    results.put("finalProductConc_g_per_L", finalProductConc);
    results.put("substrateConversion", substrateConversion);
    results.put("specificGrowthRate_1_per_hr", specificGrowthRate);
    results.put("productivity_g_per_L_hr", productivity);
    results.put("substrateConsumed_kg_per_hr", substrateConsumedKgPerHr);
    results.put("productFormed_kg_per_hr", productFormedKgPerHr);
    results.put("productComponentName", productComponentName);

    if (kineticModel == KineticModel.SUBSTRATE_INHIBITED) {
      results.put("substrateInhibitionConstant_g_per_L", substrateInhibitionConstant);
    }
    if (kineticModel == KineticModel.PRODUCT_INHIBITED) {
      results.put("productInhibitionConc_g_per_L", productInhibitionConcentration);
      results.put("productInhibitionExponent", productInhibitionExponent);
    }
    if (kineticModel == KineticModel.CONTOIS) {
      results.put("contoisConstant_g_g", contoisConstant);
    }
    return results;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(getResults());
  }
}
