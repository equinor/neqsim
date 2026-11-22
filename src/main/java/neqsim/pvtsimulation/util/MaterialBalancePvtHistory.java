package neqsim.pvtsimulation.util;

import java.io.Serializable;
import java.util.Optional;
import neqsim.blackoil.BlackOilPVTTable.Record;
import neqsim.pvtsimulation.reservoirproperties.materialbalance.GeneralizedMaterialBalance;
import neqsim.pvtsimulation.reservoirproperties.materialbalance.GeneralizedMaterialBalance.SinglePhasePvt;

/**
 * Pressure-aligned, explicitly attributed PVT handoff from independent laboratory calibration to measured history.
 *
 * <p>
 * Records must be evaluated externally from the accepted tuned model along the declared path. This container validates
 * alignment, numeric inputs and the common separator basis; it does not generate depletion PVT or prove that
 * caller-supplied records reproduce the declared model/path. Such evidence belongs in the required path reference.
 * Constant-composition tables are rejected for depletion-history matching. Injection that changes composition
 * substantially still requires full compositional replay, even when represented by an externally evaluated path.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class MaterialBalancePvtHistory implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Declared origin of the pressure-aligned PVT records. */
  public enum Path {
    /** Complete constant-volume depletion and separator interpretation. */
    CVD,
    /** Complete differential-liberation and separator interpretation. */
    DLE,
    /** Externally evaluated, composition-aware production/injection path. */
    COMPOSITIONAL_HISTORY,
    /** Independent fixed-composition flashes; not a depletion experiment. */
    CONSTANT_COMPOSITION,
    /** Actual closed-mass constant-composition expansion of one laboratory cell, without withdrawal. */
    CCE
  }

  private final CalibratedPvtModel model;
  private final Path path;
  private final String pathReference;
  private final double temperatureK;
  private final double[] separatorPressures;
  private final double[] separatorTemperatures;
  private final Record[] records;
  private final SinglePhasePvt singlePhaseInitial;

  /**
   * Freezes PVT records, their laboratory-calibrated model and separator/depletion provenance.
   *
   * @param model original/tuned model checkpoints with calibration provenance
   * @param path protocol used to evaluate the records
   * @param pathReference evaluation report, software revision and depletion/separator interpretation evidence
   * @param temperatureK isothermal reservoir temperature in K
   * @param separatorPressuresBara ordered separator stages ending at 1.01325 bara
   * @param separatorTemperaturesK stage temperatures ending at 288.15 K
   * @param records pressure-aligned Rs/Rv/Bo/Bg/Bw, including the initial reservoir state
   */
  public MaterialBalancePvtHistory(CalibratedPvtModel model, Path path, String pathReference, double temperatureK,
      double[] separatorPressuresBara, double[] separatorTemperaturesK, Record[] records) {
    this(model, path, pathReference, temperatureK, separatorPressuresBara, separatorTemperaturesK, null, records);
  }

  /**
   * Freezes an externally evaluated path with a single original hydrocarbon phase and later two-phase records.
   *
   * @param model original/tuned model checkpoints with calibration provenance
   * @param path protocol used to evaluate the records
   * @param pathReference evaluation report, software revision and depletion/separator interpretation evidence
   * @param temperatureK isothermal reservoir temperature in K
   * @param separatorPressuresBara ordered separator stages ending at 1.01325 bara
   * @param separatorTemperaturesK stage temperatures ending at 288.15 K
   * @param initial original oil or gas PVT on the same basis
   * @param observations subsequent pressure-aligned two-phase observations with Rs/Rv/Bo/Bg/Bw
   * @return PVT path for measured histories with an explicit original state
   */
  public static MaterialBalancePvtHistory fromSinglePhase(CalibratedPvtModel model, Path path, String pathReference,
      double temperatureK, double[] separatorPressuresBara, double[] separatorTemperaturesK, SinglePhasePvt initial,
      Record[] observations) {
    if (initial == null) {
      throw new IllegalArgumentException("Single-phase initial PVT is required");
    }
    if (path == Path.DLE && initial.isGas()) {
      throw new IllegalArgumentException("Differential liberation requires an initially present oil phase");
    }
    return new MaterialBalancePvtHistory(model, path, pathReference, temperatureK, separatorPressuresBara,
        separatorTemperaturesK, initial, observations);
  }

  /**
   * Validates and copies the declared path with either form of original PVT.
   *
   * @param model original/tuned model checkpoints with calibration provenance
   * @param path protocol used to evaluate the records
   * @param pathReference external evaluation and depletion/separator interpretation evidence
   * @param temperatureK isothermal reservoir temperature in K
   * @param separatorPressuresBara ordered separator pressures in bara
   * @param separatorTemperaturesK ordered separator temperatures in K
   * @param initial optional phase-explicit original PVT
   * @param records two-phase records, including the original state when initial is omitted
   */
  private MaterialBalancePvtHistory(CalibratedPvtModel model, Path path, String pathReference, double temperatureK,
      double[] separatorPressuresBara, double[] separatorTemperaturesK, SinglePhasePvt initial, Record[] records) {
    if (model == null || path == null || !Double.isFinite(temperatureK) || temperatureK <= 0.0 || records == null
        || records.length < (initial == null ? 3 : 2)) {
      throw new IllegalArgumentException("Model, path, temperature and initial plus two historical PVT rows required");
    }
    requireSeparatorBasis(separatorPressuresBara, separatorTemperaturesK);
    this.model = model;
    this.path = path;
    this.pathReference = CalibratedPvtModel.reference(pathReference);
    this.temperatureK = temperatureK;
    this.separatorPressures = separatorPressuresBara.clone();
    this.separatorTemperatures = separatorTemperaturesK.clone();
    this.records = records.clone();
    this.singlePhaseInitial = initial;
    double previousPressure = initial == null ? Double.POSITIVE_INFINITY : initial.getPressureBara();
    for (int i = 0; i < this.records.length; i++) {
      new GeneralizedMaterialBalance(this.records[i]);
      if (path != Path.COMPOSITIONAL_HISTORY && this.records[i].p >= previousPressure) {
        throw new IllegalArgumentException("Depletion/expansion PVT schedule must decrease strictly");
      }
      previousPressure = this.records[i].p;
    }
  }

  /**
   * Constructs the generalized balance from measured pressures and cumulative net withdrawals.
   *
   * <p>
   * Each array starts at the original reservoir state. With a phase-explicit anchor the remaining entries correspond to
   * the selected two-phase PVT rows. Include production and injection before the first selected row in the cumulative
   * volumes.
   * </p>
   *
   * @param pressuresBara measured average reservoir pressures including the initial state
   * @param netOilSm3 cumulative produced-minus-injected total surface oil from the initial state
   * @param netGasSm3 cumulative produced-minus-injected total surface gas from the initial state
   * @param netWaterSm3 cumulative produced-minus-injected surface water from the initial state
   * @param influxM3 known cumulative aquifer influx in reservoir m3, excluding injected water
   * @param expansionFraction rock/connate-water expansion fraction of initial hydrocarbon volume
   * @return composable material-balance builder with no EOS or aquifer fitting
   */
  public GeneralizedMaterialBalance materialBalance(double[] pressuresBara, double[] netOilSm3, double[] netGasSm3,
      double[] netWaterSm3, double[] influxM3, double[] expansionFraction) {
    if (path == Path.CONSTANT_COMPOSITION) {
      throw new IllegalArgumentException("Constant-composition flash tables are not depletion-history PVT");
    }
    int offset = singlePhaseInitial == null ? 0 : 1;
    double[][] history = {pressuresBara, netOilSm3, netGasSm3, netWaterSm3, influxM3, expansionFraction};
    for (double[] values : history) {
      if (values == null || values.length != records.length + offset) {
        throw new IllegalArgumentException("Every measured-history array must align with the complete PVT path");
      }
      for (double value : values) {
        if (!Double.isFinite(value)) {
          throw new IllegalArgumentException("Measured-history values must be finite");
        }
      }
    }
    for (int i = 1; i < history.length; i++) {
      if (history[i][0] != 0.0) {
        throw new IllegalArgumentException("Initial cumulative withdrawals and support must be zero");
      }
    }
    GeneralizedMaterialBalance balance;
    if (singlePhaseInitial == null) {
      balance = new GeneralizedMaterialBalance(records[0]);
    } else {
      requireAlignedPressure(pressuresBara[0], singlePhaseInitial.getPressureBara(), 0);
      balance = GeneralizedMaterialBalance.fromSinglePhase(singlePhaseInitial);
    }
    for (int i = 0; i < records.length; i++) {
      int row = i + offset;
      requireAlignedPressure(pressuresBara[row], records[i].p, row);
      if (row > 0) {
        balance.addObservation(records[i], netOilSm3[row], netGasSm3[row], netWaterSm3[row], influxM3[row],
            expansionFraction[row]);
      }
    }
    return balance;
  }

  /**
   * Checks a measured pressure against its original or subsequent PVT row.
   *
   * @param measured measured average reservoir pressure in bara
   * @param reference PVT pressure in bara
   * @param row index in the measured array, starting at the original state
   */
  private static void requireAlignedPressure(double measured, double reference, int row) {
    if (Math.abs(measured - reference) > 1.0e-8 * Math.max(1.0, reference)) {
      throw new IllegalArgumentException("Measured pressure does not match PVT path at row " + row);
    }
  }

  /**
   * Returns the immutable calibration checkpoint bundle.
   *
   * @return original/tuned model bundle
   */
  public CalibratedPvtModel getModel() {
    return model;
  }

  /**
   * Returns the declared depletion protocol.
   *
   * @return path identity
   */
  public Path getPath() {
    return path;
  }

  /**
   * Returns the caller's path-validation evidence reference.
   *
   * @return evaluation/protocol reference
   */
  public String getPathReference() {
    return pathReference;
  }

  /**
   * Returns the isothermal reservoir temperature.
   *
   * @return temperature in K
   */
  public double getTemperatureK() {
    return temperatureK;
  }

  /**
   * Returns a copy of the ordered separator pressure path.
   *
   * @return separator pressures in bara
   */
  public double[] getSeparatorPressuresBara() {
    return separatorPressures.clone();
  }

  /**
   * Returns a copy of the ordered separator temperature path.
   *
   * @return separator temperatures in K
   */
  public double[] getSeparatorTemperaturesK() {
    return separatorTemperatures.clone();
  }

  /**
   * Returns a copy of the array of immutable PVT records.
   *
   * @return two-phase records, including the original state for a path with no phase-explicit anchor
   */
  public Record[] getRecords() {
    return records.clone();
  }

  /**
   * Returns the original single hydrocarbon phase for an explicitly anchored path.
   *
   * @return original oil or gas PVT, or empty when the original state is in the two-phase records
   */
  public Optional<SinglePhasePvt> getSinglePhaseInitial() {
    return Optional.ofNullable(singlePhaseInitial);
  }

  /**
   * Enforces the standard basis used by the existing PVT solvers and SimpleReservoir recorder.
   *
   * @param pressures ordered separator pressures in bara
   * @param temperatures corresponding separator temperatures in K
   */
  static void requireSeparatorBasis(double[] pressures, double[] temperatures) {
    if (pressures == null || temperatures == null || pressures.length == 0 || pressures.length != temperatures.length) {
      throw new IllegalArgumentException("Every separator stage requires pressure and temperature");
    }
    for (int i = 0; i < pressures.length; i++) {
      if (!Double.isFinite(pressures[i]) || pressures[i] <= 0.0 || !Double.isFinite(temperatures[i])
          || temperatures[i] <= 0.0 || (i > 0 && pressures[i] >= pressures[i - 1])) {
        throw new IllegalArgumentException("Separator pressures must decrease with positive finite P/T");
      }
    }
    int last = pressures.length - 1;
    if (Math.abs(pressures[last] - 1.01325) > 1.0e-8 || Math.abs(temperatures[last] - 288.15) > 1.0e-8) {
      throw new IllegalArgumentException("Stock-tank basis must be 288.15 K and 1.01325 bara");
    }
  }
}
