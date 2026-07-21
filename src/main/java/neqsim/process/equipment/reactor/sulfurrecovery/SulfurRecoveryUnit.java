package neqsim.process.equipment.reactor.sulfurrecovery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Integrated, converged Claus sulfur-recovery process model.
 *
 * <p>The flowsheet contains combustion-air control, a reaction furnace, a reactive waste-heat
 * boiler, a thermal sulfur condenser, configurable reheater/converter/condenser trains, optional
 * tail-gas hydrogenation and H2S recycle, and an optional thermal incinerator. Sulfur-product
 * streams and all major equipment objects remain accessible after a run for rating and reporting.
 * Plant-specific kinetic factors can therefore be calibrated without changing the flowsheet
 * material balances.</p>
 */
public class SulfurRecoveryUnit extends TwoPortEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Supported Claus process configurations. */
  public enum Configuration {
    /** Conventional straight-through air Claus process. */
    STRAIGHT_THROUGH,
    /** Acid-gas split-flow process for lean or difficult feeds. */
    SPLIT_FLOW,
    /** Straight-through process using oxygen-enriched oxidant. */
    OXYGEN_ENRICHED,
    /** Final cyclic converter operates below the sulfur dew point. */
    SUB_DEW_POINT
  }

  private Configuration configuration = Configuration.STRAIGHT_THROUGH;
  private int numberOfCatalyticStages = 2;
  private boolean tailGasTreatmentEnabled;
  private boolean incineratorEnabled = true;
  private double oxidantOxygenMoleFraction = 0.21;
  private double splitFlowFurnaceFraction = 0.35;
  private double airControlTolerance = 0.01;
  private int maximumAirControlIterations = 14;
  private double recycleTolerance = 1.0e-6;
  private int maximumRecycleIterations = 20;
  private double recycleRelaxationFactor = 0.5;
  private double[] converterInletTemperaturesK = {513.15, 493.15, 473.15};
  private ClausCatalyticConverter.CatalystType[] catalystTypes = {
      ClausCatalyticConverter.CatalystType.ALUMINA,
      ClausCatalyticConverter.CatalystType.ALUMINA,
      ClausCatalyticConverter.CatalystType.TITANIA};

  private final AirDemandController airDemandController = new AirDemandController();
  private ClausReactionFurnace reactionFurnace;
  private ReactiveWasteHeatBoiler wasteHeatBoiler;
  private List<ClausCatalyticConverter> catalyticConverters = Collections.emptyList();
  private List<SulfurCondenser> sulfurCondensers = Collections.emptyList();
  private List<StreamInterface> sulfurProductStreams = Collections.emptyList();
  private TailGasTreatmentUnit tailGasTreatmentUnit;
  private ThermalIncinerator thermalIncinerator;
  private StreamInterface clausTailGasStream;
  private StreamInterface acidGasRecycleStream;
  private StreamInterface combinedSulfurProductStream;
  private SulfurRecoveryPerformance performance;
  private double lastOxygenDemandMoles;
  private int lastAirControlIterations;
  private int lastRecycleIterations;
  private boolean airControlConverged;
  private boolean recycleConverged;

  /** Create an unconnected sulfur-recovery unit. */
  public SulfurRecoveryUnit(String name) {
    super(name);
  }

  /** Create a sulfur-recovery unit connected to an acid-gas feed. */
  public SulfurRecoveryUnit(String name, StreamInterface acidGasFeed) {
    super(name, acidGasFeed);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (inStream == null) {
      throw new IllegalStateException("SulfurRecoveryUnit requires an acid-gas inlet stream");
    }
    if (inStream.getThermoSystem() == null) {
      throw new IllegalStateException("SulfurRecoveryUnit inlet stream requires a fluid system");
    }
    SystemInterface freshFeed = SulfurProcessUtil.prepareSystem(inStream.getThermoSystem());
    airControlConverged = false;
    recycleConverged = !tailGasTreatmentEnabled;
    double freshFeedSulfurAtoms = SulfurProcessUtil.sulfurAtomMoles(freshFeed);
    if (freshFeedSulfurAtoms <= 1.0e-20) {
      throw new IllegalArgumentException("SulfurRecoveryUnit feed contains no supported sulfur");
    }

    SystemInterface recycle = SulfurProcessUtil.scaledClone(freshFeed, 0.0);
    SystemInterface recycleUsedInFinalPass = recycle;
    ClausSectionResult section = null;
    StreamInterface treatedTail = null;
    TailGasTreatmentUnit finalTailGasUnit = null;
    int recycleIterations = tailGasTreatmentEnabled ? maximumRecycleIterations : 1;
    for (int iteration = 1; iteration <= recycleIterations; iteration++) {
      lastRecycleIterations = iteration;
      recycleUsedInFinalPass = recycle;
      SystemInterface combinedFeed = SulfurProcessUtil.prepareSystem(freshFeed);
      if (tailGasTreatmentEnabled) {
        SulfurProcessUtil.addSystem(combinedFeed, recycle);
      }
      section = solveAirDemand(combinedFeed, id);
      treatedTail = section.tailGas;

      if (!tailGasTreatmentEnabled) {
        break;
      }

      finalTailGasUnit = new TailGasTreatmentUnit(getName() + " tail gas treatment",
          section.tailGas);
      finalTailGasUnit.run(id);
      treatedTail = finalTailGasUnit.getOutletStream();
      SystemInterface calculatedRecycle = SulfurProcessUtil.prepareSystem(
          finalTailGasUnit.getAcidGasRecycleStream().getThermoSystem());
      double previousH2S = SulfurProcessUtil.moles(recycle, "H2S");
      double calculatedH2S = SulfurProcessUtil.moles(calculatedRecycle, "H2S");
      double relativeChange = Math.abs(calculatedH2S - previousH2S)
          / Math.max(calculatedH2S, 1.0e-20);
      if (relativeChange <= recycleTolerance && iteration > 1) {
        recycle = calculatedRecycle;
        recycleConverged = true;
        break;
      }
      recycle = relaxRecycle(recycle, calculatedRecycle);
    }

    if (section == null || treatedTail == null) {
      throw new IllegalStateException("Sulfur-recovery flowsheet did not produce an outlet");
    }
    reactionFurnace = section.furnace;
    wasteHeatBoiler = section.wasteHeatBoiler;
    catalyticConverters = Collections.unmodifiableList(section.converters);
    sulfurCondensers = Collections.unmodifiableList(section.condensers);
    sulfurProductStreams = Collections.unmodifiableList(section.sulfurProducts);
    clausTailGasStream = section.tailGas;
    tailGasTreatmentUnit = finalTailGasUnit;
    acidGasRecycleStream = tailGasTreatmentEnabled && finalTailGasUnit != null
        ? finalTailGasUnit.getAcidGasRecycleStream() : null;
    combinedSulfurProductStream = combineSulfurProducts(section.sulfurProducts, freshFeed, id);

    StreamInterface finalOutlet = treatedTail;
    thermalIncinerator = null;
    if (incineratorEnabled) {
      thermalIncinerator = new ThermalIncinerator(getName() + " incinerator", treatedTail);
      thermalIncinerator.setOxidantOxygenMoleFraction(oxidantOxygenMoleFraction);
      thermalIncinerator.run(id);
      finalOutlet = thermalIncinerator.getOutletStream();
    }
    SulfurProcessUtil.updateOutlet(outStream,
        SulfurProcessUtil.prepareSystem(finalOutlet.getThermoSystem()), id);
    calculatePerformance(freshFeed, finalOutlet, section, recycleUsedInFinalPass);
    setCalculationIdentifier(id);
  }

  /** Solve combustion-air demand by bisection on the final Claus H2S/SO2 ratio. */
  private ClausSectionResult solveAirDemand(SystemInterface feed, UUID id) {
    airControlConverged = false;
    double baseDemand = airDemandController.calculateOxygenDemand(feed);
    double lowerDemand = 0.70 * baseDemand;
    double upperDemand = 1.30 * baseDemand;
    ClausSectionResult result = null;
    for (int iteration = 1; iteration <= maximumAirControlIterations; iteration++) {
      lastAirControlIterations = iteration;
      double oxygenDemand = iteration == 1 ? baseDemand : 0.5 * (lowerDemand + upperDemand);
      result = runClausSection(feed, oxygenDemand, id);
      double ratio = SulfurProcessUtil.h2sToSo2Ratio(result.tailGas.getThermoSystem());
      lastOxygenDemandMoles = oxygenDemand;
      if (Double.isFinite(ratio)
          && Math.abs(ratio - airDemandController.getTargetH2SToSO2Ratio())
              <= airControlTolerance) {
        airControlConverged = true;
        break;
      }
      if (!Double.isFinite(ratio)
          || ratio > airDemandController.getTargetH2SToSO2Ratio()) {
        lowerDemand = oxygenDemand;
      } else {
        upperDemand = oxygenDemand;
      }
    }
    return result;
  }

  /** Execute one forward pass through the thermal and catalytic Claus sections. */
  private ClausSectionResult runClausSection(SystemInterface feed, double oxygenDemand, UUID id) {
    ClausSectionResult result = new ClausSectionResult();
    double furnaceFraction = configuration == Configuration.SPLIT_FLOW
        ? SulfurProcessUtil.clamp(splitFlowFurnaceFraction, 0.05, 1.0) : 1.0;
    SystemInterface furnaceFeed = SulfurProcessUtil.scaledClone(feed, furnaceFraction);
    SulfurProcessUtil.addMoles(furnaceFeed, "oxygen", oxygenDemand);
    double oxygenFraction = SulfurProcessUtil.clamp(oxidantOxygenMoleFraction, 0.01, 0.99);
    SulfurProcessUtil.addMoles(furnaceFeed, "nitrogen",
        oxygenDemand * (1.0 - oxygenFraction) / oxygenFraction);
    SulfurProcessUtil.flash(furnaceFeed, getName() + " furnace feed");
    Stream furnaceFeedStream = new Stream(getName() + " furnace feed", furnaceFeed);
    furnaceFeedStream.run(id);

    result.furnace = new ClausReactionFurnace(getName() + " reaction furnace",
        furnaceFeedStream);
    result.furnace.run(id);
    result.wasteHeatBoiler = new ReactiveWasteHeatBoiler(getName() + " waste heat boiler",
        result.furnace.getOutletStream());
    result.wasteHeatBoiler.run(id);
    StreamInterface current = result.wasteHeatBoiler.getOutletStream();

    if (configuration == Configuration.SPLIT_FLOW && furnaceFraction < 1.0) {
      SystemInterface mixed = SulfurProcessUtil.prepareSystem(current.getThermoSystem());
      SulfurProcessUtil.addSystem(mixed, SulfurProcessUtil.scaledClone(feed, 1.0 - furnaceFraction));
      SulfurProcessUtil.flash(mixed, getName() + " split-flow mixer");
      Stream mixedStream = new Stream(getName() + " split-flow mixed gas", mixed);
      mixedStream.run(id);
      current = mixedStream;
    }

    SulfurCondenser thermalCondenser = new SulfurCondenser(
        getName() + " thermal condenser", current);
    thermalCondenser.run(id);
    result.condensers.add(thermalCondenser);
    result.sulfurProducts.add(thermalCondenser.getLiquidSulfurStream());
    current = thermalCondenser.getOutletStream();

    for (int stage = 0; stage < numberOfCatalyticStages; stage++) {
      double reheatTemperature = converterInletTemperaturesK[
          Math.min(stage, converterInletTemperaturesK.length - 1)];
      if (configuration == Configuration.SUB_DEW_POINT
          && stage == numberOfCatalyticStages - 1) {
        reheatTemperature = Math.min(reheatTemperature, 438.15);
      }
      StreamInterface reheated = SulfurProcessUtil.createConditionedStream(
          getName() + " converter " + (stage + 1) + " feed", current,
          reheatTemperature, id);
      ClausCatalyticConverter converter;
      if (configuration == Configuration.SUB_DEW_POINT
          && stage == numberOfCatalyticStages - 1) {
        converter = new SubDewPointSulfurReactor(
            getName() + " sub-dew-point converter " + (stage + 1), reheated);
      } else {
        converter = new ClausCatalyticConverter(
            getName() + " converter " + (stage + 1), reheated);
      }
      converter.setCatalystType(catalystTypes[Math.min(stage, catalystTypes.length - 1)]);
      converter.run(id);
      result.converters.add(converter);
      current = converter.getOutletStream();
      if (converter instanceof SubDewPointSulfurReactor) {
        result.sulfurProducts.add(
            ((SubDewPointSulfurReactor) converter).getSulfurProductStream());
      }

      SulfurCondenser condenser = new SulfurCondenser(
          getName() + " catalytic condenser " + (stage + 1), current);
      condenser.run(id);
      result.condensers.add(condenser);
      result.sulfurProducts.add(condenser.getLiquidSulfurStream());
      current = condenser.getOutletStream();
    }
    result.tailGas = current;
    return result;
  }

  /** Relax the recycle composition to stabilize the fixed-point solution. */
  private SystemInterface relaxRecycle(SystemInterface previous, SystemInterface calculated) {
    SystemInterface relaxed = SulfurProcessUtil.prepareSystem(previous);
    for (int i = 0; i < relaxed.getNumberOfComponents(); i++) {
      String component = relaxed.getComponent(i).getComponentName();
      double updated = (1.0 - recycleRelaxationFactor)
          * SulfurProcessUtil.moles(previous, component)
          + recycleRelaxationFactor * SulfurProcessUtil.moles(calculated, component);
      SulfurProcessUtil.setMoles(relaxed, component, updated);
    }
    relaxed.setTemperature(calculated.getTemperature());
    relaxed.setPressure(calculated.getPressure());
    SulfurProcessUtil.flash(relaxed, getName() + " recycle relaxation");
    return relaxed;
  }

  /** Combine the individual sulfur products into a single reporting stream. */
  private StreamInterface combineSulfurProducts(List<StreamInterface> products,
      SystemInterface reference, UUID id) {
    double totalS8Moles = 0.0;
    for (StreamInterface product : products) {
      if (product != null) {
        totalS8Moles += SulfurProcessUtil.moles(product.getThermoSystem(), "S8");
      }
    }
    return SulfurProcessUtil.createSingleComponentStream(getName() + " sulfur product",
        reference, "S8", totalS8Moles, 423.15, reference.getPressure(), id);
  }

  /** Calculate overall sulfur closure and process KPIs. */
  private void calculatePerformance(SystemInterface freshFeed, StreamInterface finalOutlet,
      ClausSectionResult section, SystemInterface recycle) {
    double feedSulfurAtoms = SulfurProcessUtil.sulfurAtomMoles(freshFeed);
    double productSulfurAtoms = 8.0 * SulfurProcessUtil.moles(
        combinedSulfurProductStream.getThermoSystem(), "S8");
    double outletSulfurAtoms = SulfurProcessUtil.sulfurAtomMoles(
        finalOutlet.getThermoSystem());
    double recycleResidualAtoms = tailGasTreatmentEnabled && acidGasRecycleStream != null
        ? SulfurProcessUtil.sulfurAtomMoles(acidGasRecycleStream.getThermoSystem())
            - SulfurProcessUtil.sulfurAtomMoles(recycle)
        : 0.0;
    double feedSulfurKgPerHour = feedSulfurAtoms
        * SulfurProcessUtil.SULFUR_ATOMIC_MOLAR_MASS_KG_PER_MOL * 3600.0;
    double productSulfurKgPerHour = productSulfurAtoms
        * SulfurProcessUtil.SULFUR_ATOMIC_MOLAR_MASS_KG_PER_MOL * 3600.0;
    double clausRecovery = 100.0 * productSulfurAtoms / Math.max(feedSulfurAtoms, 1.0e-30);
    double overallRecovery = 100.0
        * (1.0 - outletSulfurAtoms / Math.max(feedSulfurAtoms, 1.0e-30));
    double balanceError = (productSulfurAtoms + outletSulfurAtoms + recycleResidualAtoms
        - feedSulfurAtoms) / Math.max(feedSulfurAtoms, 1.0e-30);
    double stackSO2 = thermalIncinerator == null ? 0.0
        : thermalIncinerator.getSo2Emission("kg/hr");
    performance = new SulfurRecoveryPerformance(feedSulfurKgPerHour,
        productSulfurKgPerHour, clausRecovery, overallRecovery, balanceError,
        SulfurProcessUtil.h2sToSo2Ratio(section.tailGas.getThermoSystem()),
        lastOxygenDemandMoles, section.furnace.getFurnaceTemperature(), stackSO2,
        lastAirControlIterations, lastRecycleIterations, airControlConverged,
        recycleConverged);
  }

  /** Set process configuration and apply its conventional oxidant default. */
  public void setConfiguration(Configuration configuration) {
    if (configuration == null) {
      throw new IllegalArgumentException("configuration cannot be null");
    }
    this.configuration = configuration;
    if (configuration == Configuration.OXYGEN_ENRICHED && oxidantOxygenMoleFraction <= 0.21) {
      oxidantOxygenMoleFraction = 0.35;
    }
  }

  /** Return selected process configuration. */
  public Configuration getConfiguration() {
    return configuration;
  }

  /** Set number of catalytic conversion stages, from one to three. */
  public void setNumberOfCatalyticStages(int stages) {
    if (stages < 1 || stages > 3) {
      throw new IllegalArgumentException("Catalytic stages must be in the range 1-3");
    }
    numberOfCatalyticStages = stages;
  }

  /** Enable or disable tail-gas treatment and acid-gas recycle. */
  public void setTailGasTreatmentEnabled(boolean enabled) {
    tailGasTreatmentEnabled = enabled;
  }

  /** Enable or disable final thermal incineration. */
  public void setIncineratorEnabled(boolean enabled) {
    incineratorEnabled = enabled;
  }

  /** Set oxygen mole fraction of the oxidant, including enriched oxygen service. */
  public void setOxidantOxygenMoleFraction(double fraction) {
    oxidantOxygenMoleFraction = SulfurProcessUtil.clamp(fraction, 0.01, 0.99);
  }

  /** Set fraction of acid gas routed through the split-flow furnace. */
  public void setSplitFlowFurnaceFraction(double fraction) {
    splitFlowFurnaceFraction = SulfurProcessUtil.clamp(fraction, 0.05, 1.0);
  }

  /** Set target final Claus H2S/SO2 molar ratio. */
  public void setTargetH2SToSO2Ratio(double ratio) {
    airDemandController.setTargetH2SToSO2Ratio(ratio);
  }

  /** Set air-controller ratio tolerance. */
  public void setAirControlTolerance(double tolerance) {
    airControlTolerance = Math.max(tolerance, 1.0e-8);
  }

  /** Set maximum air-demand iterations. */
  public void setMaximumAirControlIterations(int iterations) {
    maximumAirControlIterations = Math.max(iterations, 1);
  }

  /** Set tail-gas recycle convergence options. */
  public void setRecycleConvergence(double tolerance, int maximumIterations,
      double relaxationFactor) {
    recycleTolerance = Math.max(tolerance, 1.0e-12);
    maximumRecycleIterations = Math.max(maximumIterations, 1);
    recycleRelaxationFactor = SulfurProcessUtil.clamp(relaxationFactor, 0.01, 1.0);
  }

  /** Set converter inlet temperature for a zero-based stage index. */
  public void setConverterInletTemperature(int stage, double value, String unit) {
    if (stage < 0 || stage >= converterInletTemperaturesK.length) {
      throw new IllegalArgumentException("Converter stage index must be in the range 0-2");
    }
    converterInletTemperaturesK[stage] = "C".equalsIgnoreCase(unit) ? value + 273.15 : value;
  }

  /** Set catalyst formulation for a zero-based stage index. */
  public void setCatalystType(int stage, ClausCatalyticConverter.CatalystType catalystType) {
    if (stage < 0 || stage >= catalystTypes.length) {
      throw new IllegalArgumentException("Catalyst stage index must be in the range 0-2");
    }
    if (catalystType == null) {
      throw new IllegalArgumentException("catalystType cannot be null");
    }
    catalystTypes[stage] = catalystType;
  }

  /** Return latest reaction-furnace calculation. */
  public ClausReactionFurnace getReactionFurnace() {
    return reactionFurnace;
  }

  /** Return latest waste-heat-boiler calculation. */
  public ReactiveWasteHeatBoiler getWasteHeatBoiler() {
    return wasteHeatBoiler;
  }

  /** Return latest catalytic converters in process order. */
  public List<ClausCatalyticConverter> getCatalyticConverters() {
    return catalyticConverters;
  }

  /** Return latest sulfur condensers in process order. */
  public List<SulfurCondenser> getSulfurCondensers() {
    return sulfurCondensers;
  }

  /** Return all latest individual sulfur-product streams. */
  public List<StreamInterface> getSulfurProductStreams() {
    return sulfurProductStreams;
  }

  /** Return the combined elemental-sulfur product stream. */
  public StreamInterface getCombinedSulfurProductStream() {
    return combinedSulfurProductStream;
  }

  /** Return tail gas leaving the final Claus condenser before treatment. */
  public StreamInterface getClausTailGasStream() {
    return clausTailGasStream;
  }

  /** Return the converged tail-gas acid-gas recycle stream, or null when disabled. */
  public StreamInterface getAcidGasRecycleStream() {
    return acidGasRecycleStream;
  }

  /** Return tail-gas treatment equipment, or null when disabled. */
  public TailGasTreatmentUnit getTailGasTreatmentUnit() {
    return tailGasTreatmentUnit;
  }

  /** Return incinerator equipment, or null when disabled. */
  public ThermalIncinerator getThermalIncinerator() {
    return thermalIncinerator;
  }

  /** Return immutable KPIs from the latest converged run. */
  public SulfurRecoveryPerformance getPerformance() {
    return performance;
  }

  /** Mutable internal result holder for one forward Claus-section pass. */
  private static final class ClausSectionResult {
    private ClausReactionFurnace furnace;
    private ReactiveWasteHeatBoiler wasteHeatBoiler;
    private final List<ClausCatalyticConverter> converters =
        new ArrayList<ClausCatalyticConverter>();
    private final List<SulfurCondenser> condensers = new ArrayList<SulfurCondenser>();
    private final List<StreamInterface> sulfurProducts = new ArrayList<StreamInterface>();
    private StreamInterface tailGas;
  }
}
