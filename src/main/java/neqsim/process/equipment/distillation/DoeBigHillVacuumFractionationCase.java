package neqsim.process.equipment.distillation;

import java.util.Objects;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.characterization.DoeBigHillSweetAssay;
import neqsim.thermo.characterization.OilAssayCharacterisation;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Reusable low-pressure column handoff for DOE Big Hill Sweet heavy material.
 *
 * <p>
 * The normalized screening factory uses {@link DoeBigHillSweetAssay#createVacuumScreeningFeed(SystemInterface)}. The
 * atmospheric-bottoms factory instead clones one solved DOE Big Hill stream so that the same assay identity and
 * calculated composition cross the unit boundary. Every pressure, temperature, tray, and reflux value remains an
 * explicit caller input because the DOE assay does not report a matching refinery vacuum-column operating case.
 * </p>
 *
 * <p>
 * Both factories configure an unsolved MESH-residual column. They do not establish convergence, product yields, cut
 * quality, pressure correction, or plant agreement. The atmospheric-bottoms overload is a modeled stream handoff, not
 * evidence that the public assay reports a measured atmospheric-bottoms or vacuum-plant composition.
 * </p>
 */
public final class DoeBigHillVacuumFractionationCase {
  /** Standard atmosphere in bar absolute; the configured column must remain below this pressure. */
  public static final double STANDARD_ATMOSPHERE_BARA = 1.01325;

  private static final int MINIMUM_SIMPLE_TRAY_COUNT = 2;
  private static final int MAXIMUM_SIMPLE_TRAY_COUNT = 100;
  private static final double MASS_BALANCE_TOLERANCE = 5.0e-2;
  private static final double ENTHALPY_BALANCE_TOLERANCE = 5.0e-2;
  private static final double TEMPERATURE_TOLERANCE_KELVIN = 0.20;
  private static final double RELAXATION_FACTOR = 0.30;
  private static final int MAXIMUM_ITERATION_COUNT = 600;

  private final Stream feedStream;
  private final DistillationColumn column;
  private final OperatingInputs operatingInputs;

  private DoeBigHillVacuumFractionationCase(Stream feedStream, DistillationColumn column,
      OperatingInputs operatingInputs) {
    this.feedStream = feedStream;
    this.column = column;
    this.operatingInputs = operatingInputs;
  }

  /**
   * Create an unsolved low-pressure fractionation case from the qualified Big Hill screening feed.
   *
   * @param name non-blank case name
   * @param feedMassFlowKgPerHour positive feed mass flow in kg/h
   * @param operatingInputs explicit source-unreported column operating inputs
   * @return configured feed and unsolved column
   * @throws NullPointerException if {@code operatingInputs} is {@code null}
   * @throws IllegalArgumentException if the name, flow, or operating inputs are invalid
   */
  public static DoeBigHillVacuumFractionationCase create(String name, double feedMassFlowKgPerHour,
      OperatingInputs operatingInputs) {
    validateNameAndInputs(name, operatingInputs);
    requirePositiveFlow(feedMassFlowKgPerHour);

    SystemInterface feedFluid = new SystemSrkEos(operatingInputs.getFeedTemperatureKelvin(),
        operatingInputs.getFeedPressureBara());
    OilAssayCharacterisation assay = DoeBigHillSweetAssay.createVacuumScreeningFeed(feedFluid);
    assay.apply();
    feedFluid.setMixingRule("classic");

    Stream feed = createFeedStream(name, feedFluid, feedMassFlowKgPerHour, operatingInputs);
    return configureCase(name, feed, operatingInputs);
  }

  /**
   * Create an unsolved low-pressure case from modeled DOE Big Hill atmospheric bottoms.
   *
   * <p>
   * The source stream is cloned before its explicit vacuum-feed temperature and pressure are applied. All components
   * are retained; the normalized three-cut screening feed is not substituted. The source must contain the three named
   * DOE heavy pseudo-components, which prevents an unrelated crude or Sarir case from being relabeled as the Big Hill
   * handoff.
   * </p>
   *
   * @param name non-blank case name
   * @param atmosphericBottoms modeled Big Hill atmospheric liquid-bottoms stream
   * @param operatingInputs explicit source-unreported vacuum-column operating inputs
   * @return configured cloned feed and unsolved low-pressure column
   * @throws NullPointerException if the source stream or inputs are {@code null}
   * @throws IllegalArgumentException if the name, flow, or Big Hill identity is invalid
   */
  public static DoeBigHillVacuumFractionationCase createFromAtmosphericBottoms(String name,
      StreamInterface atmosphericBottoms, OperatingInputs operatingInputs) {
    validateNameAndInputs(name, operatingInputs);
    Objects.requireNonNull(atmosphericBottoms, "atmosphericBottoms");

    double feedMassFlowKgPerHour = atmosphericBottoms.getFlowRate("kg/hr");
    requirePositiveFlow(feedMassFlowKgPerHour);
    SystemInterface feedFluid = atmosphericBottoms.getThermoSystem().clone();
    requireDoeHeavyComponent(feedFluid, "DOE_BH_650_850_PC");
    requireDoeHeavyComponent(feedFluid, "DOE_BH_850_1050_PC");
    requireDoeHeavyComponent(feedFluid, "DOE_BH_1050_PLUS_PC");

    Stream feed = createFeedStream(name, feedFluid, feedMassFlowKgPerHour, operatingInputs);
    return configureCase(name, feed, operatingInputs);
  }

  private static Stream createFeedStream(String name, SystemInterface feedFluid, double feedMassFlowKgPerHour,
      OperatingInputs operatingInputs) {
    Stream feed = new Stream(name + " feed", feedFluid);
    feed.setFlowRate(feedMassFlowKgPerHour, "kg/hr");
    feed.setTemperature(operatingInputs.getFeedTemperatureKelvin(), "K");
    feed.setPressure(operatingInputs.getFeedPressureBara(), "bara");
    feed.run();
    return feed;
  }

  private static DoeBigHillVacuumFractionationCase configureCase(String name, Stream feed,
      OperatingInputs operatingInputs) {
    DistillationColumn configuredColumn = new DistillationColumn(name + " column", operatingInputs.getSimpleTrayCount(),
        true, true);
    configuredColumn.addFeedStream(feed, operatingInputs.getFeedTrayIndex());
    configuredColumn.setTopPressure(operatingInputs.getTopPressureBara());
    configuredColumn.setBottomPressure(operatingInputs.getBottomPressureBara());
    configuredColumn.setCondenserMode(DistillationColumn.CondenserMode.PARTIAL);
    configuredColumn.getReboiler().setOutletTemperature(operatingInputs.getReboilerTemperatureKelvin());
    configuredColumn.setCondenserRefluxRatio(operatingInputs.getCondenserRefluxRatio());

    configuredColumn.setSolverType(DistillationColumn.SolverType.MESH_RESIDUAL);
    configuredColumn.setRelaxationFactor(RELAXATION_FACTOR);
    configuredColumn.setMaxNumberOfIterations(MAXIMUM_ITERATION_COUNT, true);
    configuredColumn.setTemperatureTolerance(TEMPERATURE_TOLERANCE_KELVIN);
    configuredColumn.setMassBalanceTolerance(MASS_BALANCE_TOLERANCE);
    configuredColumn.setEnthalpyBalanceTolerance(ENTHALPY_BALANCE_TOLERANCE);
    configuredColumn.setEnforceEnergyBalanceTolerance(true);

    return new DoeBigHillVacuumFractionationCase(feed, configuredColumn, operatingInputs);
  }

  private static void validateNameAndInputs(String name, OperatingInputs operatingInputs) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("Case name must be non-blank");
    }
    Objects.requireNonNull(operatingInputs, "operatingInputs");
  }

  private static void requirePositiveFlow(double feedMassFlowKgPerHour) {
    if (!Double.isFinite(feedMassFlowKgPerHour) || !(feedMassFlowKgPerHour > 0.0)) {
      throw new IllegalArgumentException("Feed mass flow must be finite and positive");
    }
  }

  private static void requireDoeHeavyComponent(SystemInterface system, String componentName) {
    if (system.getComponent(componentName) == null || !(system.getComponent(componentName).getNumberOfmoles() > 0.0)) {
      throw new IllegalArgumentException("Atmospheric bottoms must retain positive " + componentName);
    }
  }

  /** @return configured mutable feed stream */
  public Stream getFeedStream() {
    return feedStream;
  }

  /** @return configured mutable, unsolved low-pressure column */
  public DistillationColumn getColumn() {
    return column;
  }

  /** @return immutable engineering inputs used to configure the case */
  public OperatingInputs getOperatingInputs() {
    return operatingInputs;
  }

  /**
   * Explicit engineering inputs for the source-unreported low-pressure column.
   */
  public static final class OperatingInputs {
    private final int simpleTrayCount;
    private final int feedTrayIndex;
    private final double feedTemperatureKelvin;
    private final double feedPressureBara;
    private final double topPressureBara;
    private final double bottomPressureBara;
    private final double reboilerTemperatureKelvin;
    private final double condenserRefluxRatio;

    /**
     * Create validated low-pressure column controls.
     *
     * @param simpleTrayCount number of simple trays, excluding condenser and reboiler
     * @param feedTrayIndex bottom-up NeqSim simple-tray index for the feed
     * @param feedTemperatureKelvin feed temperature in kelvin
     * @param feedPressureBara feed pressure in bar absolute
     * @param topPressureBara column-top pressure in bar absolute
     * @param bottomPressureBara column-bottom pressure in bar absolute
     * @param reboilerTemperatureKelvin reboiler outlet temperature in kelvin
     * @param condenserRefluxRatio condenser reflux ratio, dimensionless and non-negative
     * @throws IllegalArgumentException if a value is outside the qualified topology or physical domain
     */
    public OperatingInputs(int simpleTrayCount, int feedTrayIndex, double feedTemperatureKelvin,
        double feedPressureBara, double topPressureBara, double bottomPressureBara, double reboilerTemperatureKelvin,
        double condenserRefluxRatio) {
      if (simpleTrayCount < MINIMUM_SIMPLE_TRAY_COUNT || simpleTrayCount > MAXIMUM_SIMPLE_TRAY_COUNT) {
        throw new IllegalArgumentException("Simple-tray count must be in [2, 100]");
      }
      if (feedTrayIndex <= 0 || feedTrayIndex > simpleTrayCount) {
        throw new IllegalArgumentException("Feed tray must be an internal simple-tray index");
      }
      requireFinitePositive(feedTemperatureKelvin, "Feed temperature");
      requireFinitePositive(feedPressureBara, "Feed pressure");
      requireFinitePositive(topPressureBara, "Top pressure");
      requireFinitePositive(bottomPressureBara, "Bottom pressure");
      requireFinitePositive(reboilerTemperatureKelvin, "Reboiler temperature");
      requireFiniteNonNegative(condenserRefluxRatio, "Condenser reflux ratio");

      if (!(topPressureBara < bottomPressureBara)) {
        throw new IllegalArgumentException("Top pressure must be lower than bottom pressure");
      }
      if (feedPressureBara < topPressureBara || feedPressureBara > bottomPressureBara) {
        throw new IllegalArgumentException("Feed pressure must be inside the column pressure envelope");
      }
      if (!(bottomPressureBara < STANDARD_ATMOSPHERE_BARA)) {
        throw new IllegalArgumentException("The complete column pressure envelope must be sub-atmospheric");
      }
      if (!(reboilerTemperatureKelvin > feedTemperatureKelvin)) {
        throw new IllegalArgumentException("Reboiler temperature must exceed feed temperature");
      }

      this.simpleTrayCount = simpleTrayCount;
      this.feedTrayIndex = feedTrayIndex;
      this.feedTemperatureKelvin = feedTemperatureKelvin;
      this.feedPressureBara = feedPressureBara;
      this.topPressureBara = topPressureBara;
      this.bottomPressureBara = bottomPressureBara;
      this.reboilerTemperatureKelvin = reboilerTemperatureKelvin;
      this.condenserRefluxRatio = condenserRefluxRatio;
    }

    /** @return number of simple trays, excluding condenser and reboiler */
    public int getSimpleTrayCount() {
      return simpleTrayCount;
    }

    /** @return bottom-up NeqSim simple-tray index for the feed */
    public int getFeedTrayIndex() {
      return feedTrayIndex;
    }

    /** @return feed temperature in kelvin */
    public double getFeedTemperatureKelvin() {
      return feedTemperatureKelvin;
    }

    /** @return feed pressure in bar absolute */
    public double getFeedPressureBara() {
      return feedPressureBara;
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
  }
}

