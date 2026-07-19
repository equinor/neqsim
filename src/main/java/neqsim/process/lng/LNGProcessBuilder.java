package neqsim.process.lng;

import java.util.ArrayList;
import java.util.List;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.LNGHeatExchanger;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Builder for closed-loop LNG liquefaction process templates.
 *
 * <p>
 * Four routes are supported: single mixed refrigerant (SMR), propane-precooled
 * mixed refrigerant (C3MR), dual mixed refrigerant (DMR), and a nitrogen-expander
 * reverse-Brayton cycle. Each route uses energy-coupled {@link LNGHeatExchanger}
 * units and explicit {@link Recycle} tear streams. This avoids the common screening
 * error of imposing natural-gas precooling with disconnected coolers whose duty is
 * not included in refrigeration power.
 * </p>
 *
 * <p>
 * The templates are intended for thermodynamic screening, teaching, optimization,
 * and regression testing. C3MR uses a single equivalent propane evaporation level;
 * detailed baseload design should replace it with three or four propane pressure
 * levels, phase separators, pumps, and project-specific compressor maps. Likewise,
 * vendor guarantees require calibrated exchanger geometry and refrigerant inventory.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class LNGProcessBuilder {
  /** Process name. */
  private String name = "LNG liquefaction";

  /** Selected process cycle. */
  private LNGProcessCycle cycle = LNGProcessCycle.SMR;

  /** Optional caller-supplied pretreated feed fluid. */
  private SystemInterface feedFluid;

  /** Optional upstream process system to extend with the LNG train. */
  private ProcessSystem upstreamProcessSystem;

  /** Live feed stream produced by the optional upstream process system. */
  private StreamInterface upstreamFeedStream;

  /** Natural-gas feed rate in kg/h. */
  private double feedFlowKgPerHour = 100000.0;

  /** Feed temperature in Celsius. */
  private double feedTemperatureC = 25.0;

  /** Feed pressure in bara. */
  private double feedPressureBara = 60.0;

  /** LNG product flash pressure in bara. */
  private double productPressureBara = 1.20;

  /** Natural-gas target temperature before product letdown in Celsius. */
  private double targetLiquefactionTemperatureC = -155.0;

  /** Minimum exchanger approach used by the parent MSHE solver in Celsius. */
  private double minimumApproachC = 3.0;

  /** Number of initial axial zones in each cryogenic exchanger. */
  private int numberOfZones = 12;

  /** Whether exchanger zones are refined around phase transitions. */
  private boolean adaptiveRefinement = true;

  /** Whether process-wide K-value warm starts are enabled. */
  private boolean useFlashWarmStart = true;

  /** Compressor isentropic efficiency. */
  private double compressorEfficiency = 0.78;

  /** Expander isentropic efficiency. */
  private double expanderEfficiency = 0.82;

  /**
   * Sets the process name.
   *
   * @param name process name
   * @return this builder
   */
  public LNGProcessBuilder setName(String name) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("name cannot be null or empty");
    }
    this.name = name;
    return this;
  }

  /**
   * Sets the liquefaction cycle.
   *
   * @param cycle process cycle
   * @return this builder
   */
  public LNGProcessBuilder setCycle(LNGProcessCycle cycle) {
    if (cycle == null) {
      throw new IllegalArgumentException("cycle cannot be null");
    }
    this.cycle = cycle;
    return this;
  }

  /**
   * Sets a custom pretreated natural-gas feed fluid.
   *
   * <p>
   * The builder clones the supplied system and applies configured feed temperature,
   * pressure, and flow. Water, carbon dioxide, mercury, and heavy hydrocarbon removal
   * must be represented upstream when those contaminants are present.
   * </p>
   *
   * @param feedFluid pretreated feed thermodynamic system
   * @return this builder
   */
  public LNGProcessBuilder setFeedFluid(SystemInterface feedFluid) {
    if (feedFluid == null) {
      throw new IllegalArgumentException("feedFluid cannot be null");
    }
    this.feedFluid = feedFluid.clone();
    this.upstreamFeedStream = null;
    this.upstreamProcessSystem = null;
    return this;
  }

  /**
   * Connects an existing NeqSim stream directly to the LNG process.
   *
   * <p>
   * The stream is retained by reference rather than cloned, so its composition, flow,
   * temperature, and pressure remain connected to the surrounding simulation. When the
   * producing equipment belongs to another {@link ProcessSystem}, run that upstream system
   * before this model. Use {@link #setUpstreamProcess(ProcessSystem, StreamInterface)} when the
   * upstream equipment and LNG train should execute and participate in capacity analysis as one
   * process system.
   * </p>
   *
   * @param feedStream live pretreated natural-gas stream
   * @return this builder
   */
  public LNGProcessBuilder setFeedStream(StreamInterface feedStream) {
    if (feedStream == null) {
      throw new IllegalArgumentException("feedStream cannot be null");
    }
    this.upstreamFeedStream = feedStream;
    this.feedFluid = null;
    return this;
  }

  /**
   * Integrates the LNG train with an existing upstream NeqSim process.
   *
   * <p>
   * The supplied process system is extended in place with the selected LNG route, and the
   * supplied stream becomes the live LNG feed. This allows inlet pipelines, pretreatment,
   * fractionation, controls, measurement devices, and other NeqSim functionality to be
   * evaluated in the same flowsheet and capacity analysis. The feed stream should be an
   * outlet of equipment already registered in {@code upstreamProcessSystem}.
   * </p>
   *
   * <p>
   * When this integration is used, the feed flow, temperature, and pressure are governed by
   * the upstream process rather than the corresponding builder defaults.
   * </p>
   *
   * @param upstreamProcessSystem existing process system to extend
   * @param upstreamFeedStream live pretreated natural-gas feed stream
   * @return this builder
   */
  public LNGProcessBuilder setUpstreamProcess(ProcessSystem upstreamProcessSystem,
      StreamInterface upstreamFeedStream) {
    if (upstreamProcessSystem == null) {
      throw new IllegalArgumentException("upstreamProcessSystem cannot be null");
    }
    if (upstreamFeedStream == null) {
      throw new IllegalArgumentException("upstreamFeedStream cannot be null");
    }
    this.upstreamProcessSystem = upstreamProcessSystem;
    return setFeedStream(upstreamFeedStream);
  }

  /**
   * Sets natural-gas feed flow.
   *
   * @param feedFlowKgPerHour mass flow in kg/h
   * @return this builder
   */
  public LNGProcessBuilder setFeedFlowRate(double feedFlowKgPerHour) {
    validatePositive(feedFlowKgPerHour, "feedFlowKgPerHour");
    this.feedFlowKgPerHour = feedFlowKgPerHour;
    return this;
  }

  /**
   * Sets feed temperature.
   *
   * @param feedTemperatureC temperature in Celsius
   * @return this builder
   */
  public LNGProcessBuilder setFeedTemperature(double feedTemperatureC) {
    if (!Double.isFinite(feedTemperatureC) || feedTemperatureC <= -200.0
        || feedTemperatureC >= 100.0) {
      throw new IllegalArgumentException("feedTemperatureC must be between -200 and 100 C");
    }
    this.feedTemperatureC = feedTemperatureC;
    return this;
  }

  /**
   * Sets feed pressure.
   *
   * @param feedPressureBara pressure in bara
   * @return this builder
   */
  public LNGProcessBuilder setFeedPressure(double feedPressureBara) {
    validatePositive(feedPressureBara, "feedPressureBara");
    this.feedPressureBara = feedPressureBara;
    return this;
  }

  /**
   * Sets LNG product flash pressure.
   *
   * @param productPressureBara pressure in bara
   * @return this builder
   */
  public LNGProcessBuilder setProductPressure(double productPressureBara) {
    validatePositive(productPressureBara, "productPressureBara");
    this.productPressureBara = productPressureBara;
    return this;
  }

  /**
   * Sets natural-gas temperature before product letdown.
   *
   * @param targetTemperatureC temperature in Celsius
   * @return this builder
   */
  public LNGProcessBuilder setTargetLiquefactionTemperature(double targetTemperatureC) {
    if (!Double.isFinite(targetTemperatureC) || targetTemperatureC <= -190.0
        || targetTemperatureC >= -100.0) {
      throw new IllegalArgumentException(
          "targetTemperatureC must be between -190 and -100 C");
    }
    this.targetLiquefactionTemperatureC = targetTemperatureC;
    return this;
  }

  /**
   * Sets the minimum exchanger approach.
   *
   * @param minimumApproachC approach in Celsius
   * @return this builder
   */
  public LNGProcessBuilder setMinimumApproach(double minimumApproachC) {
    validatePositive(minimumApproachC, "minimumApproachC");
    this.minimumApproachC = minimumApproachC;
    return this;
  }

  /**
   * Sets cryogenic exchanger zone count.
   *
   * @param numberOfZones positive zone count
   * @return this builder
   */
  public LNGProcessBuilder setNumberOfZones(int numberOfZones) {
    if (numberOfZones < 2) {
      throw new IllegalArgumentException("numberOfZones must be at least 2");
    }
    this.numberOfZones = numberOfZones;
    return this;
  }

  /**
   * Enables or disables adaptive exchanger refinement.
   *
   * @param adaptiveRefinement true to refine phase-transition zones
   * @return this builder
   */
  public LNGProcessBuilder setAdaptiveRefinement(boolean adaptiveRefinement) {
    this.adaptiveRefinement = adaptiveRefinement;
    return this;
  }

  /**
   * Enables or disables process-wide flash warm starts.
   *
   * @param useFlashWarmStart true to reuse converged K values
   * @return this builder
   */
  public LNGProcessBuilder setUseFlashWarmStart(boolean useFlashWarmStart) {
    this.useFlashWarmStart = useFlashWarmStart;
    return this;
  }

  /**
   * Sets compressor efficiency.
   *
   * @param compressorEfficiency isentropic efficiency in (0,1]
   * @return this builder
   */
  public LNGProcessBuilder setCompressorEfficiency(double compressorEfficiency) {
    validateEfficiency(compressorEfficiency, "compressorEfficiency");
    this.compressorEfficiency = compressorEfficiency;
    return this;
  }

  /**
   * Sets expander efficiency.
   *
   * @param expanderEfficiency isentropic efficiency in (0,1]
   * @return this builder
   */
  public LNGProcessBuilder setExpanderEfficiency(double expanderEfficiency) {
    validateEfficiency(expanderEfficiency, "expanderEfficiency");
    this.expanderEfficiency = expanderEfficiency;
    return this;
  }

  /**
   * Builds the selected closed-loop process.
   *
   * @return runnable LNG process model
   */
  public LNGProcessModel build() {
    switch (cycle) {
      case SMR:
        return buildSMR();
      case C3MR:
        return buildC3MR();
      case DMR:
        return buildDMR();
      case NITROGEN_EXPANDER:
        return buildNitrogenExpander();
      default:
        throw new IllegalStateException("Unsupported LNG process cycle " + cycle);
    }
  }

  /**
   * Builds a single mixed-refrigerant process.
   *
   * @return SMR process model
   */
  private LNGProcessModel buildSMR() {
    BuildContext context = newContext();
    Stream mrSuction = createMixedRefrigerant(name + " MR suction",
        new String[] {"nitrogen", "methane", "ethane", "propane"},
        new double[] {0.05, 0.42, 0.33, 0.20}, 20.0, 3.0,
        context.feedFlowKgPerHour * 2.0);
    context.process.add(mrSuction);

    CompressionTrain mrTrain = addTwoStageCompression(context, name + " MR",
        mrSuction, 30.0, compressorEfficiency);

    LNGHeatExchanger mche = createExchanger(name + " main cryogenic exchanger");
    mche.addInStreamMSHE(context.feed, "hot", targetLiquefactionTemperatureC);
    mche.addInStreamMSHE(mrTrain.outlet, "hot", -150.0);

    ThrottlingValve mrValve =
        new ThrottlingValve(name + " MR JT valve", mche.getOutStream(1));
    mrValve.setOutletPressure(3.0, "bara");
    mche.addInStreamMSHE(mrValve.getOutletStream(), "cold", null);
    context.exchangers.add(mche);

    context.process.add(mche);
    context.process.add(mrValve);
    addRecycle(context, name + " MR recycle", mche.getOutStream(2), mrSuction);

    ProductSection product = addProductSection(context, mche.getOutStream(0));
    return context.toModel(cycle, product);
  }

  /**
   * Builds a propane-precooled mixed-refrigerant process.
   *
   * @return C3MR process model
   */
  private LNGProcessModel buildC3MR() {
    BuildContext context = newContext();

    Stream propaneSuction = createPureRefrigerant(name + " propane suction",
        "propane", -35.0, 1.5, context.feedFlowKgPerHour * 1.25);
    context.process.add(propaneSuction);
    CompressionTrain propaneTrain = addTwoStageCompression(context,
        name + " propane", propaneSuction, 15.0, 0.80);

    ThrottlingValve propaneValve = new ThrottlingValve(name + " propane JT valve",
        propaneTrain.outlet);
    propaneValve.setOutletPressure(1.5, "bara");
    context.process.add(propaneValve);

    Stream mrSuction = createMixedRefrigerant(name + " MR suction",
        new String[] {"nitrogen", "methane", "ethane", "propane"},
        new double[] {0.04, 0.43, 0.36, 0.17}, 20.0, 4.0,
        context.feedFlowKgPerHour * 1.75);
    context.process.add(mrSuction);
    CompressionTrain mrTrain = addTwoStageCompression(context, name + " MR",
        mrSuction, 45.0, compressorEfficiency);

    LNGHeatExchanger precooler = createExchanger(name + " propane precooler");
    precooler.addInStreamMSHE(context.feed, "hot", -32.0);
    precooler.addInStreamMSHE(mrTrain.outlet, "hot", -32.0);
    precooler.addInStreamMSHE(propaneValve.getOutletStream(), "cold", null);
    context.exchangers.add(precooler);
    context.process.add(precooler);
    addRecycle(context, name + " propane recycle", precooler.getOutStream(2),
        propaneSuction);

    LNGHeatExchanger mche = createExchanger(name + " main cryogenic exchanger");
    mche.addInStreamMSHE(precooler.getOutStream(0), "hot",
        targetLiquefactionTemperatureC);
    mche.addInStreamMSHE(precooler.getOutStream(1), "hot", -150.0);

    ThrottlingValve mrValve =
        new ThrottlingValve(name + " MR JT valve", mche.getOutStream(1));
    mrValve.setOutletPressure(4.0, "bara");
    mche.addInStreamMSHE(mrValve.getOutletStream(), "cold", null);
    context.exchangers.add(mche);
    context.process.add(mche);
    context.process.add(mrValve);
    addRecycle(context, name + " MR recycle", mche.getOutStream(2), mrSuction);

    ProductSection product = addProductSection(context, mche.getOutStream(0));
    return context.toModel(cycle, product);
  }

  /**
   * Builds a dual mixed-refrigerant process.
   *
   * @return DMR process model
   */
  private LNGProcessModel buildDMR() {
    BuildContext context = newContext();

    Stream warmMrSuction = createMixedRefrigerant(name + " warm MR suction",
        new String[] {"methane", "ethane", "propane", "n-butane"},
        new double[] {0.12, 0.33, 0.42, 0.13}, 20.0, 3.5,
        context.feedFlowKgPerHour * 1.45);
    context.process.add(warmMrSuction);
    CompressionTrain warmTrain = addTwoStageCompression(context, name + " warm MR",
        warmMrSuction, 18.0, compressorEfficiency);

    ThrottlingValve warmValve = new ThrottlingValve(name + " warm MR JT valve",
        warmTrain.outlet);
    warmValve.setOutletPressure(3.5, "bara");
    context.process.add(warmValve);

    Stream coldMrSuction = createMixedRefrigerant(name + " cold MR suction",
        new String[] {"nitrogen", "methane", "ethane", "propane"},
        new double[] {0.08, 0.48, 0.31, 0.13}, 20.0, 3.0,
        context.feedFlowKgPerHour * 1.55);
    context.process.add(coldMrSuction);
    CompressionTrain coldTrain = addTwoStageCompression(context, name + " cold MR",
        coldMrSuction, 38.0, compressorEfficiency);

    LNGHeatExchanger precooler = createExchanger(name + " warm MR precooler");
    precooler.addInStreamMSHE(context.feed, "hot", -45.0);
    precooler.addInStreamMSHE(coldTrain.outlet, "hot", -45.0);
    precooler.addInStreamMSHE(warmValve.getOutletStream(), "cold", null);
    context.exchangers.add(precooler);
    context.process.add(precooler);
    addRecycle(context, name + " warm MR recycle", precooler.getOutStream(2),
        warmMrSuction);

    LNGHeatExchanger mche = createExchanger(name + " main cryogenic exchanger");
    mche.addInStreamMSHE(precooler.getOutStream(0), "hot",
        targetLiquefactionTemperatureC);
    mche.addInStreamMSHE(precooler.getOutStream(1), "hot", -150.0);

    ThrottlingValve coldValve =
        new ThrottlingValve(name + " cold MR JT valve", mche.getOutStream(1));
    coldValve.setOutletPressure(3.0, "bara");
    mche.addInStreamMSHE(coldValve.getOutletStream(), "cold", null);
    context.exchangers.add(mche);
    context.process.add(mche);
    context.process.add(coldValve);
    addRecycle(context, name + " cold MR recycle", mche.getOutStream(2),
        coldMrSuction);

    ProductSection product = addProductSection(context, mche.getOutStream(0));
    return context.toModel(cycle, product);
  }

  /**
   * Builds a closed reverse-Brayton nitrogen-expander process.
   *
   * @return nitrogen-expander process model
   */
  private LNGProcessModel buildNitrogenExpander() {
    BuildContext context = newContext();

    Stream nitrogenSuction = createPureRefrigerant(name + " nitrogen suction",
        "nitrogen", 20.0, 8.0, context.feedFlowKgPerHour * 5.0);
    context.process.add(nitrogenSuction);
    CompressionTrain nitrogenTrain = addTwoStageCompression(context,
        name + " nitrogen", nitrogenSuction, 55.0, 0.82);

    LNGHeatExchanger mche = createExchanger(name + " nitrogen main exchanger");
    mche.addInStreamMSHE(context.feed, "hot", targetLiquefactionTemperatureC);
    mche.addInStreamMSHE(nitrogenTrain.outlet, "hot", -105.0);

    Expander expander =
        new Expander(name + " nitrogen expander", mche.getOutStream(1));
    expander.setOutletPressure(8.0, "bara");
    expander.setIsentropicEfficiency(expanderEfficiency);
    context.expanders.add(expander);
    mche.addInStreamMSHE(expander.getOutletStream(), "cold", null);
    context.exchangers.add(mche);

    context.process.add(mche);
    context.process.add(expander);
    addRecycle(context, name + " nitrogen recycle", mche.getOutStream(2),
        nitrogenSuction);

    ProductSection product = addProductSection(context, mche.getOutStream(0));
    return context.toModel(cycle, product);
  }

  /**
   * Creates a new process build context.
   *
   * @return initialized context
   */
  private BuildContext newContext() {
    ProcessSystem process =
        upstreamProcessSystem == null ? new ProcessSystem(name) : upstreamProcessSystem;
    process.setUseFlashWarmStart(useFlashWarmStart);
    process.setUseOptimizedExecution(true);
    StreamInterface feed = upstreamFeedStream;
    if (feed == null) {
      feed = createFeed();
      process.add(feed);
    }
    double designFeedFlowKgPerHour = feed.getFlowRate("kg/hr");
    validatePositive(designFeedFlowKgPerHour, "live feed flow in kg/hr");
    return new BuildContext(process, feed, designFeedFlowKgPerHour);
  }

  /**
   * Creates the natural-gas feed.
   *
   * @return feed stream
   */
  private Stream createFeed() {
    SystemInterface fluid;
    if (feedFluid == null) {
      fluid = new SystemSrkEos(feedTemperatureC + 273.15, feedPressureBara);
      fluid.addComponent("nitrogen", 0.005);
      fluid.addComponent("methane", 0.890);
      fluid.addComponent("ethane", 0.065);
      fluid.addComponent("propane", 0.025);
      fluid.addComponent("i-butane", 0.007);
      fluid.addComponent("n-butane", 0.008);
      fluid.setMixingRule("classic");
    } else {
      fluid = feedFluid.clone();
    }
    fluid.setTemperature(feedTemperatureC, "C");
    fluid.setPressure(feedPressureBara, "bara");
    Stream feed = new Stream(name + " pretreated natural gas", fluid);
    feed.setFlowRate(feedFlowKgPerHour, "kg/hr");
    feed.setTemperature(feedTemperatureC, "C");
    feed.setPressure(feedPressureBara, "bara");
    return feed;
  }

  /**
   * Creates a mixed-refrigerant suction stream.
   *
   * @param streamName stream name
   * @param components component names
   * @param fractions relative mole fractions
   * @param temperatureC temperature in Celsius
   * @param pressureBara pressure in bara
   * @param flowKgPerHour mass flow in kg/h
   * @return refrigerant stream
   */
  private Stream createMixedRefrigerant(String streamName, String[] components,
      double[] fractions, double temperatureC, double pressureBara,
      double flowKgPerHour) {
    if (components.length != fractions.length) {
      throw new IllegalArgumentException("components and fractions must have equal length");
    }
    SystemInterface fluid = new SystemSrkEos(temperatureC + 273.15, pressureBara);
    for (int i = 0; i < components.length; i++) {
      fluid.addComponent(components[i], fractions[i]);
    }
    fluid.setMixingRule("classic");
    Stream stream = new Stream(streamName, fluid);
    stream.setFlowRate(flowKgPerHour, "kg/hr");
    stream.setTemperature(temperatureC, "C");
    stream.setPressure(pressureBara, "bara");
    return stream;
  }

  /**
   * Creates a pure-refrigerant suction stream.
   *
   * @param streamName stream name
   * @param component component name
   * @param temperatureC temperature in Celsius
   * @param pressureBara pressure in bara
   * @param flowKgPerHour mass flow in kg/h
   * @return refrigerant stream
   */
  private Stream createPureRefrigerant(String streamName, String component,
      double temperatureC, double pressureBara, double flowKgPerHour) {
    return createMixedRefrigerant(streamName, new String[] {component},
        new double[] {1.0}, temperatureC, pressureBara, flowKgPerHour);
  }

  /**
   * Adds a two-stage compressor train with intercooling.
   *
   * @param context build context
   * @param unitPrefix equipment-name prefix
   * @param suction suction stream
   * @param dischargePressureBara final discharge pressure in bara
   * @param efficiency isentropic efficiency
   * @return train outlet and compressor references
   */
  private CompressionTrain addTwoStageCompression(BuildContext context,
      String unitPrefix, StreamInterface suction, double dischargePressureBara,
      double efficiency) {
    double suctionPressure = suction.getPressure("bara");
    double intermediatePressure =
        Math.sqrt(suctionPressure * dischargePressureBara);

    Separator suctionScrubber =
        new Separator(unitPrefix + " compressor suction scrubber", suction);
    context.process.add(suctionScrubber);

    Compressor first = new Compressor(unitPrefix + " compressor stage 1",
        suctionScrubber.getGasOutStream());
    first.setOutletPressure(intermediatePressure, "bara");
    first.setIsentropicEfficiency(efficiency);
    context.compressors.add(first);
    context.process.add(first);

    Cooler intercooler =
        new Cooler(unitPrefix + " intercooler", first.getOutletStream());
    intercooler.setOutTemperature(303.15);
    context.process.add(intercooler);

    Compressor second = new Compressor(unitPrefix + " compressor stage 2",
        intercooler.getOutletStream());
    second.setOutletPressure(dischargePressureBara, "bara");
    second.setIsentropicEfficiency(efficiency);
    context.compressors.add(second);
    context.process.add(second);

    Cooler aftercooler =
        new Cooler(unitPrefix + " aftercooler", second.getOutletStream());
    aftercooler.setOutTemperature(303.15);
    context.process.add(aftercooler);

    return new CompressionTrain(aftercooler.getOutletStream());
  }

  /**
   * Creates and configures a cryogenic exchanger.
   *
   * @param exchangerName name
   * @return configured exchanger
   */
  private LNGHeatExchanger createExchanger(String exchangerName) {
    LNGHeatExchanger exchanger = new LNGHeatExchanger(exchangerName);
    exchanger.setNumberOfZones(numberOfZones);
    exchanger.setAdaptiveRefinement(adaptiveRefinement);
    exchanger.setMaxAdaptiveZones(Math.max(numberOfZones + 1, numberOfZones * 3));
    exchanger.setTemperatureApproach(minimumApproachC);
    exchanger.setFlowMaldistributionFactor(0.97);
    return exchanger;
  }

  /**
   * Adds a recycle tear stream.
   *
   * @param context build context
   * @param recycleName name
   * @param returnStream calculated loop-return stream
   * @param suctionStream tear stream updated by the recycle
   */
  private void addRecycle(BuildContext context, String recycleName,
      StreamInterface returnStream, StreamInterface suctionStream) {
    Recycle recycle = new Recycle(recycleName);
    recycle.addStream(returnStream);
    recycle.setOutletStream(suctionStream);
    recycle.setTolerance(1.0e-5);
    recycle.setMaxIterations(100);
    context.process.add(recycle);
  }

  /**
   * Adds product letdown and flash separation.
   *
   * @param context build context
   * @param coldNaturalGas high-pressure liquefied natural gas
   * @return liquid LNG and product-flash gas streams
   */
  private ProductSection addProductSection(BuildContext context,
      StreamInterface coldNaturalGas) {
    ThrottlingValve productValve =
        new ThrottlingValve(name + " LNG product valve", coldNaturalGas);
    productValve.setOutletPressure(productPressureBara, "bara");
    context.process.add(productValve);

    Separator productFlash = new Separator(name + " LNG product flash",
        productValve.getOutletStream());
    context.process.add(productFlash);
    return new ProductSection(productFlash.getLiquidOutStream(),
        productFlash.getGasOutStream());
  }

  /**
   * Validates a positive finite number.
   *
   * @param value value
   * @param parameter parameter name
   */
  private void validatePositive(double value, String parameter) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(parameter + " must be finite and greater than zero");
    }
  }

  /**
   * Validates an efficiency.
   *
   * @param value efficiency
   * @param parameter parameter name
   */
  private void validateEfficiency(double value, String parameter) {
    if (!Double.isFinite(value) || value <= 0.0 || value > 1.0) {
      throw new IllegalArgumentException(parameter + " must be finite and in (0,1]");
    }
  }

  /**
   * Compressor-train build result.
   *
   * @author NeqSim contributors
   * @version 1.0
   */
  private static final class CompressionTrain {
    private final StreamInterface outlet;

    /**
     * Creates a compression-train result.
     *
     * @param outlet aftercooler outlet
     */
    private CompressionTrain(StreamInterface outlet) {
      this.outlet = outlet;
    }
  }

  /**
   * Product-section streams exposed by the process model.
   *
   * @author NeqSim contributors
   * @version 1.0
   */
  private static final class ProductSection {
    private final StreamInterface lng;
    private final StreamInterface flashGas;

    /**
     * Creates a product-section result.
     *
     * @param lng liquid LNG product
     * @param flashGas product-flash gas
     */
    private ProductSection(StreamInterface lng, StreamInterface flashGas) {
      this.lng = lng;
      this.flashGas = flashGas;
    }
  }

  /**
   * Mutable state used while assembling a route.
   *
   * @author NeqSim contributors
   * @version 1.0
   */
  private final class BuildContext {
    private final ProcessSystem process;
    private final StreamInterface feed;
    private final double feedFlowKgPerHour;
    private final List<Compressor> compressors = new ArrayList<Compressor>();
    private final List<Expander> expanders = new ArrayList<Expander>();
    private final List<LNGHeatExchanger> exchangers =
        new ArrayList<LNGHeatExchanger>();

    /**
     * Creates a build context.
     *
     * @param process process system
     * @param feed natural-gas feed
     * @param feedFlowKgPerHour feed flow used to initialize refrigerant circulation
     */
    private BuildContext(ProcessSystem process, StreamInterface feed,
        double feedFlowKgPerHour) {
      this.process = process;
      this.feed = feed;
      this.feedFlowKgPerHour = feedFlowKgPerHour;
    }

    /**
     * Creates the final process model.
     *
     * @param builtCycle cycle type
     * @param product product-section streams
     * @return LNG process model
     */
    private LNGProcessModel toModel(LNGProcessCycle builtCycle,
        ProductSection product) {
      return new LNGProcessModel(name, builtCycle, process, feed, product.lng,
          product.flashGas, compressors, expanders, exchangers);
    }
  }
}
