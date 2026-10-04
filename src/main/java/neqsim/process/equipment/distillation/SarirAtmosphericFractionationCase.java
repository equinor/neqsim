package neqsim.process.equipment.distillation;

import java.util.Objects;
import java.util.UUID;
import neqsim.process.equipment.heatexchanger.SarirAtmosphericCrudeHeatingCase;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.characterization.OilAssayCharacterisation;
import neqsim.thermo.characterization.SarirAtmosphericAssay;
import neqsim.thermo.characterization.SarirAtmosphericReference;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Reusable, source-bounded atmospheric-fractionation case for the public Sarir refinery reference.
 *
 * <p>
 * The factory derives the published crude-feed rate, temperature, pressure, valve-tray count, and top-counted feed-tray
 * location from {@link SarirAtmosphericReference}. The source does not publish per-cut density or molar-mass profiles,
 * endpoint controls, or side-draw locations and fractions, so callers must provide every one of those values
 * explicitly.
 * </p>
 *
 * <p>
 * Steam, side strippers, pump-arounds, tray efficiencies, and published plant product rates are not configured. The
 * returned objects are an executable engineering case, not a reproduction or calibration of the source plant.
 * </p>
 */
public final class SarirAtmosphericFractionationCase {
  /** Number of simple trays reported for the atmospheric column. */
  public static final int SIMPLE_TRAY_COUNT = 34;

  /** NeqSim bottom-up simple-tray index corresponding to source tray 31 from the top. */
  public static final int FEED_INTERNAL_INDEX = 4;

  private static final double MASS_BALANCE_TOLERANCE = 5.0e-2;
  private static final double ENTHALPY_BALANCE_TOLERANCE = 5.0e-2;
  private static final double TEMPERATURE_TOLERANCE_KELVIN = 0.20;
  private static final double RELAXATION_FACTOR = 0.30;
  private static final int MAXIMUM_ITERATION_COUNT = 600;

  private final Stream feedStream;
  private final DistillationColumn column;
  private final OperatingInputs operatingInputs;

  private SarirAtmosphericFractionationCase(Stream feedStream, DistillationColumn column,
      OperatingInputs operatingInputs) {
    this.feedStream = feedStream;
    this.column = column;
    this.operatingInputs = operatingInputs;
  }

  /**
   * Create an executable Sarir atmospheric-fractionation case.
   *
   * <p>
   * Profile validation is delegated to {@link SarirAtmosphericAssay}, including the gates against the published
   * whole-crude density and average molar mass. No column solve is performed.
   * </p>
   *
   * @param name non-blank case name
   * @param cutSpecificGravity specific gravity for each of the 18 source-derived cuts
   * @param cutMolarMassKgPerMol molar mass for each cut, in kg/mol
   * @param operatingInputs explicit unreported endpoint and side-draw engineering inputs
   * @return configured feed and column case
   * @throws NullPointerException if {@code operatingInputs} is {@code null}
   * @throws IllegalArgumentException if the name, profiles, or operating inputs are invalid
   */
  public static SarirAtmosphericFractionationCase create(String name, double[] cutSpecificGravity,
      double[] cutMolarMassKgPerMol, OperatingInputs operatingInputs) {
    validateNameAndTopology(name, operatingInputs);

    double feedTemperatureKelvin = SarirAtmosphericReference.getColumnFeedTemperatureCelsius() + 273.15;
    double feedPressureBara = SarirAtmosphericReference.getColumnFeedPressureKPa() / 100.0;
    SystemInterface crude = new SystemSrkEos(feedTemperatureKelvin, feedPressureBara);
    OilAssayCharacterisation assay = SarirAtmosphericAssay.create(crude, cutSpecificGravity, cutMolarMassKgPerMol);
    assay.apply();
    crude.setMixingRule("classic");

    Stream feed = new Stream(name + " feed", crude);
    feed.setFlowRate(SarirAtmosphericReference.getColumnCrudeFeedRateKgPerHour(), "kg/hr");
    feed.setTemperature(feedTemperatureKelvin, "K");
    feed.setPressure(feedPressureBara, "bara");
    feed.run();

    return configureColumn(name, feed, operatingInputs);
  }

  /**
   * Create an atmospheric-fractionation case connected to a qualified Sarir heating case.
   *
   * <p>
   * The heating case must have completed a successful run. Its live furnace outlet becomes the column feed; the assay
   * is not reconstructed. The published flow, temperature, and pressure boundary is checked before the column is
   * configured.
   * </p>
   *
   * @param name non-blank case name
   * @param heatingCase successfully solved source-bounded Sarir crude-heating case
   * @param operatingInputs explicit source-unreported column controls
   * @return configured connected feed and column case
   * @throws NullPointerException if the heating case or operating inputs are null
   * @throws IllegalArgumentException if the name or operating inputs are invalid
   * @throws IllegalStateException if the heating case has not solved or its outlet does not match the published
   * boundary
   */
  public static SarirAtmosphericFractionationCase createFromHeatingCase(String name,
      SarirAtmosphericCrudeHeatingCase heatingCase, OperatingInputs operatingInputs) {
    validateNameAndTopology(name, operatingInputs);
    Objects.requireNonNull(heatingCase, "heatingCase");
    validateHeatingBoundary(heatingCase);

    Stream connectedFeed = new Stream(name + " feed", heatingCase.getColumnFeedStream());
    return configureColumn(name, connectedFeed, operatingInputs);
  }

  private static void validateNameAndTopology(String name, OperatingInputs operatingInputs) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("Case name must be non-blank");
    }
    Objects.requireNonNull(operatingInputs, "operatingInputs");

    int sourceTrayCount = SarirAtmosphericReference.getColumnTrayCount();
    int sourceFeedTrayFromTop = SarirAtmosphericReference.getFeedTrayFromTop();
    int feedInternalIndex = sourceTrayCount - sourceFeedTrayFromTop + 1;
    if (sourceTrayCount != SIMPLE_TRAY_COUNT || feedInternalIndex != FEED_INTERNAL_INDEX) {
      throw new IllegalStateException("Sarir source tray mapping no longer matches the qualified case");
    }
  }

  private static SarirAtmosphericFractionationCase configureColumn(String name, Stream feed,
      OperatingInputs operatingInputs) {
    DistillationColumn configuredColumn = new DistillationColumn(name + " column", SIMPLE_TRAY_COUNT, true, true);
    configuredColumn.addFeedStream(feed, FEED_INTERNAL_INDEX);
    configuredColumn.setTopPressure(operatingInputs.getTopPressureBara());
    configuredColumn.setBottomPressure(operatingInputs.getBottomPressureBara());
    configuredColumn.setCondenserMode(DistillationColumn.CondenserMode.PARTIAL);
    configuredColumn.getReboiler().setOutletTemperature(operatingInputs.getReboilerTemperatureKelvin());
    configuredColumn.setCondenserRefluxRatio(operatingInputs.getCondenserRefluxRatio());
    configuredColumn.setLiquidSideDrawFraction(operatingInputs.getKeroseneSideDrawTray(),
        operatingInputs.getKeroseneSideDrawFraction());
    configuredColumn.setLiquidSideDrawFraction(operatingInputs.getDieselSideDrawTray(),
        operatingInputs.getDieselSideDrawFraction());

    configuredColumn.setSolverType(DistillationColumn.SolverType.MESH_RESIDUAL);
    configuredColumn.setRelaxationFactor(RELAXATION_FACTOR);
    configuredColumn.setMaxNumberOfIterations(MAXIMUM_ITERATION_COUNT, true);
    configuredColumn.setTemperatureTolerance(TEMPERATURE_TOLERANCE_KELVIN);
    configuredColumn.setMassBalanceTolerance(MASS_BALANCE_TOLERANCE);
    configuredColumn.setEnthalpyBalanceTolerance(ENTHALPY_BALANCE_TOLERANCE);
    configuredColumn.setEnforceEnergyBalanceTolerance(true);

    return new SarirAtmosphericFractionationCase(feed, configuredColumn, operatingInputs);
  }

  private static void validateHeatingBoundary(SarirAtmosphericCrudeHeatingCase heatingCase) {
    double firedDutyW = heatingCase.getFurnace().getFiredDuty("W");
    if (!Double.isFinite(firedDutyW) || !(firedDutyW > 0.0)) {
      throw new IllegalStateException("Sarir heating case must complete a successful run before connection");
    }
    validateFeedBoundary(heatingCase.getColumnFeedStream());
  }

  private static void validateFeedBoundary(StreamInterface feed) {
    double expectedFlowKgPerHour = SarirAtmosphericReference.getColumnCrudeFeedRateKgPerHour();
    double expectedTemperatureKelvin = SarirAtmosphericReference.getColumnFeedTemperatureCelsius() + 273.15;
    double expectedPressureBara = SarirAtmosphericReference.getColumnFeedPressureKPa() / 100.0;
    requireRelativeClose(feed.getFlowRate("kg/hr"), expectedFlowKgPerHour, 1.0e-10, "Connected feed mass flow");
    requireAbsoluteClose(feed.getTemperature("K"), expectedTemperatureKelvin, 1.0e-7, "Connected feed temperature");
    requireAbsoluteClose(feed.getPressure("bara"), expectedPressureBara, 1.0e-10, "Connected feed pressure");
  }

  private static void requireRelativeClose(double value, double expected, double relativeTolerance, String label) {
    if (!Double.isFinite(value) || Math.abs(value - expected) > relativeTolerance * Math.max(1.0, Math.abs(expected))) {
      throw new IllegalStateException(label + " does not match the published Sarir boundary");
    }
  }

  private static void requireAbsoluteClose(double value, double expected, double absoluteTolerance, String label) {
    if (!Double.isFinite(value) || Math.abs(value - expected) > absoluteTolerance) {
      throw new IllegalStateException(label + " does not match the published Sarir boundary");
    }
  }

  /**
   * Return the configured mutable feed stream.
   *
   * @return feed stream at the published Sarir rate, temperature, and pressure
   */
  public Stream getFeedStream() {
    return feedStream;
  }

  /**
   * Return the configured mutable atmospheric column.
   *
   * @return unsolved 34-simple-tray column
   */
  public DistillationColumn getColumn() {
    return column;
  }

  /**
   * Return the immutable engineering inputs used to configure this case.
   *
   * @return source-unreported operating inputs supplied by the caller
   */
  public OperatingInputs getOperatingInputs() {
    return operatingInputs;
  }

  /**
   * Run the connected feed and atmospheric column under one calculation identifier.
   *
   * @param id calculation identifier
   * @throws NullPointerException if {@code id} is null
   * @throws IllegalStateException if the feed boundary changes before the column solve
   */
  public void run(UUID id) {
    Objects.requireNonNull(id, "id");
    feedStream.run(id);
    validateFeedBoundary(feedStream);
    column.run(id);
  }

  /**
   * Explicit engineering inputs for source-unreported column controls.
   */
  public static final class OperatingInputs {
    private final double topPressureBara;
    private final double bottomPressureBara;
    private final double reboilerTemperatureKelvin;
    private final double condenserRefluxRatio;
    private final int keroseneSideDrawTray;
    private final double keroseneSideDrawFraction;
    private final int dieselSideDrawTray;
    private final double dieselSideDrawFraction;

    /**
     * Create validated atmospheric-column engineering controls.
     *
     * @param topPressureBara column-top pressure, in bar absolute
     * @param bottomPressureBara column-bottom pressure, in bar absolute
     * @param reboilerTemperatureKelvin reboiler outlet temperature, in kelvin
     * @param condenserRefluxRatio condenser reflux ratio, dimensionless and non-negative
     * @param keroseneSideDrawTray bottom-up NeqSim tray index for the lighter liquid side draw
     * @param keroseneSideDrawFraction fraction of tray liquid withdrawn, in [0, 1)
     * @param dieselSideDrawTray bottom-up NeqSim tray index for the heavier liquid side draw
     * @param dieselSideDrawFraction fraction of tray liquid withdrawn, in [0, 1)
     * @throws IllegalArgumentException if a value is non-finite or outside its physical or topological domain
     */
    public OperatingInputs(double topPressureBara, double bottomPressureBara, double reboilerTemperatureKelvin,
        double condenserRefluxRatio, int keroseneSideDrawTray, double keroseneSideDrawFraction, int dieselSideDrawTray,
        double dieselSideDrawFraction) {
      requireFinitePositive(topPressureBara, "Top pressure");
      requireFinitePositive(bottomPressureBara, "Bottom pressure");
      requireFinitePositive(reboilerTemperatureKelvin, "Reboiler temperature");
      requireFiniteNonNegative(condenserRefluxRatio, "Condenser reflux ratio");
      requireFraction(keroseneSideDrawFraction, "Kerosene side-draw fraction");
      requireFraction(dieselSideDrawFraction, "Diesel side-draw fraction");
      requireSideDrawTray(keroseneSideDrawTray, "Kerosene side-draw tray");
      requireSideDrawTray(dieselSideDrawTray, "Diesel side-draw tray");
      if (topPressureBara > bottomPressureBara) {
        throw new IllegalArgumentException("Top pressure must not exceed bottom pressure");
      }
      if (keroseneSideDrawTray <= dieselSideDrawTray) {
        throw new IllegalArgumentException("Kerosene side-draw tray must be above the diesel side-draw tray");
      }

      this.topPressureBara = topPressureBara;
      this.bottomPressureBara = bottomPressureBara;
      this.reboilerTemperatureKelvin = reboilerTemperatureKelvin;
      this.condenserRefluxRatio = condenserRefluxRatio;
      this.keroseneSideDrawTray = keroseneSideDrawTray;
      this.keroseneSideDrawFraction = keroseneSideDrawFraction;
      this.dieselSideDrawTray = dieselSideDrawTray;
      this.dieselSideDrawFraction = dieselSideDrawFraction;
    }

    /** @return column-top pressure in bar absolute */
    public double getTopPressureBara() {
      return topPressureBara;
    }

    /** @return column-bottom pressure in bar absolute */
    public double getBottomPressureBara() {
      return bottomPressureBara;
    }

    /** @return reboiler outlet temperature in kelvin */
    public double getReboilerTemperatureKelvin() {
      return reboilerTemperatureKelvin;
    }

    /** @return condenser reflux ratio */
    public double getCondenserRefluxRatio() {
      return condenserRefluxRatio;
    }

    /** @return bottom-up NeqSim tray index for the kerosene screen draw */
    public int getKeroseneSideDrawTray() {
      return keroseneSideDrawTray;
    }

    /** @return fraction of tray liquid withdrawn for the kerosene screen draw */
    public double getKeroseneSideDrawFraction() {
      return keroseneSideDrawFraction;
    }

    /** @return bottom-up NeqSim tray index for the diesel screen draw */
    public int getDieselSideDrawTray() {
      return dieselSideDrawTray;
    }

    /** @return fraction of tray liquid withdrawn for the diesel screen draw */
    public double getDieselSideDrawFraction() {
      return dieselSideDrawFraction;
    }

    private static void requireFinitePositive(double value, String label) {
      if (!Double.isFinite(value) || !(value > 0.0)) {
        throw new IllegalArgumentException(label + " must be finite and positive");
      }
    }

    private static void requireFiniteNonNegative(double value, String label) {
      if (!Double.isFinite(value) || value < 0.0) {
        throw new IllegalArgumentException(label + " must be finite and non-negative");
      }
    }

    private static void requireFraction(double value, String label) {
      if (!Double.isFinite(value) || value < 0.0 || value >= 1.0) {
        throw new IllegalArgumentException(label + " must be finite and in [0, 1)");
      }
    }

    private static void requireSideDrawTray(int tray, String label) {
      if (tray <= 0 || tray > SIMPLE_TRAY_COUNT || tray == FEED_INTERNAL_INDEX) {
        throw new IllegalArgumentException(label + " must be a simple-tray index distinct from the feed tray");
      }
    }
  }
}
