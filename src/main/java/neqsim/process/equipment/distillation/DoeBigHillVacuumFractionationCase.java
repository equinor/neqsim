package neqsim.process.equipment.distillation;

import java.util.Objects;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.characterization.DoeBigHillSweetAssay;
import neqsim.thermo.characterization.OilAssayCharacterisation;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Reusable low-pressure column handoff for the DOE Big Hill Sweet 650 degF+ screening feed.
 *
 * <p>
 * The feed composition comes from {@link DoeBigHillSweetAssay#createVacuumScreeningFeed(SystemInterface)}. Every
 * pressure, temperature, tray, flow, and reflux value is an explicit caller-supplied engineering input because the DOE
 * assay does not report a matching refinery vacuum-column operating case.
 * </p>
 *
 * <p>
 * Factory creation applies the three-cut assay and configures an unsolved MESH-residual column. It does not establish
 * convergence, product yields, cut quality, pressure correction, or plant agreement.
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
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("Case name must be non-blank");
    }
    if (!Double.isFinite(feedMassFlowKgPerHour) || !(feedMassFlowKgPerHour > 0.0)) {
      throw new IllegalArgumentException("Feed mass flow must be finite and positive");
    }
    Objects.requireNonNull(operatingInputs, "operatingInputs");

    SystemInterface feedFluid = new SystemSrkEos(operatingInputs.getFeedTemperatureKelvin(),
        operatingInputs.getFeedPressureBara());
    OilAssayCharacterisation assay = DoeBigHillSweetAssay.createVacuumScreeningFeed(feedFluid);
    assay.apply();
    feedFluid.setMixingRule("classic");

    Stream feed = new Stream(name + " feed", feedFluid);
    feed.setFlowRate(feedMassFlowKgPerHour, "kg/hr");
    feed.setTemperature(operatingInputs.getFeedTemperatureKelvin(), "K");
    feed.setPressure(operatingInputs.getFeedPressureBara(), "bara");
    feed.run();

    DistillationColumn configuredColumn = new DistillationColumn(name + " column", operatingInputs.getSimpleTrayCount(),
        true, true);
    configuredColumn.addFeedStream(feed, operatingInputs.getFeedTrayIndex());
    configuredColumn.setTopPressure(operatingInputs.getTopPressureBara());
    configuredColumn.setBottomPressure(operatingInputs.getBottomPressureBara());
    configuredColumn.setCondenserMode(DistillationColumn.CondenserMode.PARTIAL);
    configuredColumn.getReboiler().setOutTemperature(operatingInputs.getReboilerTemperatureKelvin());
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
