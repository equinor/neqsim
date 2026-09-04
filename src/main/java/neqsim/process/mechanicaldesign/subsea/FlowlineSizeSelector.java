package neqsim.process.mechanicaldesign.subsea;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Selects the smallest standard flowline size that stays inside the API RP 14E erosional limit.
 *
 * <p>
 * The erosional velocity in SI units is
 * </p>
 *
 * <p>
 * <i>v<sub>e</sub></i> = 1.22 <i>c</i> / &radic;<i>&rho;</i>
 * </p>
 *
 * <p>
 * with <i>v<sub>e</sub></i> in m/s, <i>&rho;</i> the mixture density in kg/m&sup3;, and <i>c</i> = 100 for continuous
 * service (API RP 14E). The check is evaluated at the condition where the mixture is <b>least dense</b> - normally the
 * arrival, not the inlet - because that is where the velocity is highest. Sizing on inlet density under-sizes the line.
 * </p>
 *
 * <p>
 * This is a velocity screen only. It says nothing about pressure drop, liquid hold-up, slugging, sand erosion rate or
 * pressure containment; a size that passes here still has to deliver the required arrival pressure and pass
 * DNV-ST-F101.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class FlowlineSizeSelector implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** API RP 14E c-factor for continuous service. */
  public static final double C_FACTOR_CONTINUOUS = 100.0;

  /** API RP 14E c-factor for intermittent service. */
  public static final double C_FACTOR_INTERMITTENT = 125.0;

  /** Candidate outside diameters in metres, ASME B36.10 line pipe. */
  private static final double[] DEFAULT_OD_M = { 0.1683, 0.2191, 0.2731, 0.3239, 0.3556, 0.4064, 0.4572, 0.5080,
      0.6096 };

  /** Nominal size labels matching {@link #DEFAULT_OD_M}. */
  private static final double[] DEFAULT_NOMINAL_INCH = { 6.0, 8.0, 10.0, 12.0, 14.0, 16.0, 18.0, 20.0, 24.0 };

  /** Mass flow rate in kg/s. */
  private double massFlowRateKgPerSec = Double.NaN;

  /** Mixture density at the sizing condition in kg/m3. */
  private double mixtureDensityKgPerM3 = Double.NaN;

  /** API RP 14E c-factor. */
  private double cFactor = C_FACTOR_CONTINUOUS;

  /** Wall thickness used to convert outside to inside diameter, in metres. */
  private double wallThicknessM = 0.0159;

  /** Optional target velocity ceiling in m/s, NaN when only the erosional limit applies. */
  private double targetVelocityMPerSec = Double.NaN;

  /** Candidate outside diameters in metres. */
  private double[] candidateOutsideDiametersM = DEFAULT_OD_M.clone();

  /** Nominal size labels for the candidates. */
  private double[] candidateNominalInch = DEFAULT_NOMINAL_INCH.clone();

  /** Evaluated candidates, populated by {@link #select()}. */
  private final List<Map<String, Object>> evaluated = new ArrayList<Map<String, Object>>();

  /** The selected candidate, or null when nothing passes. */
  private Map<String, Object> selected = null;

  /** Erosional velocity at the sizing condition in m/s. */
  private double erosionalVelocityMPerSec = Double.NaN;

  /**
   * Computes the API RP 14E erosional velocity.
   *
   * @param densityKgPerM3 mixture density in kg/m3, must be positive
   * @param cFactorIn c-factor, 100 for continuous service
   * @return erosional velocity in m/s
   */
  public static double erosionalVelocity(double densityKgPerM3, double cFactorIn) {
    if (densityKgPerM3 <= 0.0) {
      throw new IllegalArgumentException("density must be positive, got " + densityKgPerM3);
    }
    return 1.22 * cFactorIn / Math.sqrt(densityKgPerM3);
  }

  /**
   * Takes the sizing basis from a fluid flashed at the condition where it is least dense.
   *
   * <p>
   * Flash the fluid at the <b>arrival</b> pressure and temperature before calling this, not the inlet.
   * </p>
   *
   * @param fluid the flowing fluid, already at the sizing condition
   * @param massFlowKgPerSec mass flow rate in kg/s
   * @return this selector, for chaining
   */
  public FlowlineSizeSelector setBasisFromFluid(SystemInterface fluid, double massFlowKgPerSec) {
    SystemInterface work = fluid.clone();
    ThermodynamicOperations ops = new ThermodynamicOperations(work);
    ops.TPflash();
    work.initProperties();
    this.mixtureDensityKgPerM3 = work.getDensity("kg/m3");
    this.massFlowRateKgPerSec = massFlowKgPerSec;
    return this;
  }

  /**
   * Runs the selection.
   *
   * @return the selected candidate as a map, or null when no candidate passes
   * @throws IllegalStateException if the mass flow or density basis has not been set
   */
  public Map<String, Object> select() {
    if (Double.isNaN(massFlowRateKgPerSec) || Double.isNaN(mixtureDensityKgPerM3)) {
      throw new IllegalStateException(
          "set the mass flow and mixture density, or call setBasisFromFluid, before select()");
    }
    evaluated.clear();
    selected = null;
    erosionalVelocityMPerSec = erosionalVelocity(mixtureDensityKgPerM3, cFactor);
    double volumetricFlow = massFlowRateKgPerSec / mixtureDensityKgPerM3;

    for (int i = 0; i < candidateOutsideDiametersM.length; i++) {
      double od = candidateOutsideDiametersM[i];
      double id = od - 2.0 * wallThicknessM;
      if (id <= 0.0) {
        continue;
      }
      double area = Math.PI * id * id / 4.0;
      double velocity = volumetricFlow / area;
      boolean withinErosional = velocity <= erosionalVelocityMPerSec;
      boolean withinTarget = Double.isNaN(targetVelocityMPerSec) || velocity <= targetVelocityMPerSec;

      Map<String, Object> row = new LinkedHashMap<String, Object>();
      row.put("nominalSize_inch", Double.valueOf(candidateNominalInch[i]));
      row.put("outsideDiameter_m", Double.valueOf(od));
      row.put("wallThickness_m", Double.valueOf(wallThicknessM));
      row.put("insideDiameter_m", Double.valueOf(id));
      row.put("velocity_m_per_s", Double.valueOf(velocity));
      row.put("erosionalVelocity_m_per_s", Double.valueOf(erosionalVelocityMPerSec));
      row.put("erosionalUtilisation_pct", Double.valueOf(100.0 * velocity / erosionalVelocityMPerSec));
      row.put("withinErosionalLimit", Boolean.valueOf(withinErosional));
      row.put("withinTargetVelocity", Boolean.valueOf(withinTarget));
      row.put("acceptable", Boolean.valueOf(withinErosional && withinTarget));
      evaluated.add(row);

      if (selected == null && withinErosional && withinTarget) {
        selected = row;
      }
    }
    return selected;
  }

  /**
   * Returns every evaluated candidate, in increasing size order.
   *
   * @return the candidate list, empty until {@link #select()} has been called
   */
  public List<Map<String, Object>> getCandidates() {
    return evaluated;
  }

  /**
   * Returns the selected candidate.
   *
   * @return the selected candidate, or null when nothing passed
   */
  public Map<String, Object> getSelected() {
    return selected;
  }

  /**
   * Returns the erosional velocity at the sizing condition.
   *
   * @return erosional velocity in m/s, NaN before {@link #select()}
   */
  public double getErosionalVelocity() {
    return erosionalVelocityMPerSec;
  }

  /**
   * Returns the selection as JSON.
   *
   * @return JSON string with the basis, every candidate and the selection
   */
  public String toJson() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    Map<String, Object> basis = new LinkedHashMap<String, Object>();
    basis.put("massFlow_kg_per_s", Double.valueOf(massFlowRateKgPerSec));
    basis.put("mixtureDensity_kg_per_m3", Double.valueOf(mixtureDensityKgPerM3));
    basis.put("cFactor", Double.valueOf(cFactor));
    basis.put("wallThickness_m", Double.valueOf(wallThicknessM));
    basis.put("erosionalVelocity_m_per_s", Double.valueOf(erosionalVelocityMPerSec));
    basis.put("standard", "API RP 14E");
    result.put("basis", basis);
    result.put("candidates", evaluated);
    result.put("selected", selected);
    Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create();
    return gson.toJson(result);
  }

  /**
   * Sets the mass flow rate.
   *
   * @param massFlowKgPerSec mass flow in kg/s
   * @return this selector, for chaining
   */
  public FlowlineSizeSelector setMassFlowRate(double massFlowKgPerSec) {
    this.massFlowRateKgPerSec = massFlowKgPerSec;
    return this;
  }

  /**
   * Sets the mixture density at the sizing condition.
   *
   * @param densityKgPerM3 density in kg/m3
   * @return this selector, for chaining
   */
  public FlowlineSizeSelector setMixtureDensity(double densityKgPerM3) {
    this.mixtureDensityKgPerM3 = densityKgPerM3;
    return this;
  }

  /**
   * Sets the API RP 14E c-factor.
   *
   * @param cFactorIn c-factor, typically 100 continuous or 125 intermittent
   * @return this selector, for chaining
   */
  public FlowlineSizeSelector setCFactor(double cFactorIn) {
    this.cFactor = cFactorIn;
    return this;
  }

  /**
   * Sets the wall thickness used to convert outside to inside diameter.
   *
   * @param thicknessM wall thickness in metres
   * @return this selector, for chaining
   */
  public FlowlineSizeSelector setWallThickness(double thicknessM) {
    this.wallThicknessM = thicknessM;
    return this;
  }

  /**
   * Sets an additional velocity ceiling below the erosional limit.
   *
   * @param velocityMPerSec target velocity in m/s, NaN to disable
   * @return this selector, for chaining
   */
  public FlowlineSizeSelector setTargetVelocity(double velocityMPerSec) {
    this.targetVelocityMPerSec = velocityMPerSec;
    return this;
  }

  /**
   * Replaces the candidate size list.
   *
   * @param outsideDiametersM candidate outside diameters in metres, increasing
   * @param nominalInch matching nominal size labels in inches
   * @return this selector, for chaining
   * @throws IllegalArgumentException if the arrays differ in length
   */
  public FlowlineSizeSelector setCandidates(double[] outsideDiametersM, double[] nominalInch) {
    if (outsideDiametersM.length != nominalInch.length) {
      throw new IllegalArgumentException("candidate arrays must have the same length");
    }
    this.candidateOutsideDiametersM = outsideDiametersM.clone();
    this.candidateNominalInch = nominalInch.clone();
    return this;
  }
}
