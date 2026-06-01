package neqsim.process.chemistry.scavenger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import neqsim.process.chemistry.util.StandardsRegistry;

/**
 * One-dimensional plug-flow packed-bed model for liquid H2S scavenger contactors.
 *
 * <p>
 * Models a vertical packed bed loaded with a solid scavenger (typically iron-oxide pellets or
 * triazine-impregnated alumina) that progressively saturates as the H2S-laden hydrocarbon stream
 * passes through it. The bed is discretised axially into {@code N} cells; each cell tracks the
 * remaining active scavenger inventory and depletes proportionally to local H2S consumption.
 *
 * <p>
 * Governing equations (per cell):
 * 
 * <pre>
 * dC/dz = - k_eff * (q/q0) * C       (steady-state plug flow; q = remaining capacity)
 * dq/dt = - r * Q * C / V_cell        (scavenger depletion)
 * </pre>
 * 
 * with stoichiometric ratio {@code r} (mol H2S per mol active sites, typical 1.0 for triazine,
 * 0.5-0.7 for Fe2O3) and volumetric rate constant {@code k_eff} fitted to bed geometry.
 *
 * <p>
 * Outputs the breakthrough curve C(t)/C_in at the bed outlet, the total H2S removed up to
 * breakthrough, and the cumulative bed utilisation. Breakthrough is defined as the time at which
 * outlet concentration first exceeds {@code breakthroughFraction * C_in}.
 *
 * <p>
 * Standards informational: NACE TM0284 (sour service), API RP 945 (amine systems).
 *
 * @author ESOL
 * @version 1.0
 */
public class PackedBedScavengerReactor implements Serializable {

  private static final long serialVersionUID = 1000L;

  // Inputs
  private double bedDiameterM = 0.5;
  private double bedHeightM = 2.0;
  private double bedVoidage = 0.4;
  private double scavengerLoadingMolPerKg = 5.0;
  private double scavengerBulkDensityKgM3 = 1100.0;
  private double stoichiometricRatio = 1.0;
  private double rateConstantPerS = 5.0;
  private double inletConcentrationMolM3 = 1.0;
  private double volumetricFlowM3PerS = 0.01;
  private int numberOfCells = 50;
  private double simulationTimeS = 3600.0 * 24.0 * 30.0;
  private int timeSteps = 200;
  private double breakthroughFraction = 0.05;

  // Outputs
  private final List<Double> timeSeriesS = new ArrayList<Double>();
  private final List<Double> outletConcentrationProfile = new ArrayList<Double>();
  private final List<Double> bedUtilisationProfile = new ArrayList<Double>();
  private double breakthroughTimeS = Double.POSITIVE_INFINITY;
  private double totalH2sRemovedKg = 0.0;
  private double finalBedUtilisation = 0.0;
  private boolean evaluated = false;

  // Atomic weight H2S (g/mol)
  private static final double MM_H2S = 34.08;

  /**
   * Default constructor.
   */
  public PackedBedScavengerReactor() {}

  // ─── Setters ────────────────────────────────────────────

  /**
   * Sets bed geometry.
   *
   * @param diameterM bed inner diameter [m]
   * @param heightM bed packed height [m]
   * @param voidage bed voidage (0..1)
   * @return this for chaining
   */
  public PackedBedScavengerReactor setGeometry(double diameterM, double heightM, double voidage) {
    this.bedDiameterM = diameterM;
    this.bedHeightM = heightM;
    this.bedVoidage = Math.max(0.05, Math.min(0.95, voidage));
    return this;
  }

  /**
   * Sets scavenger media properties.
   *
   * @param loadingMolPerKg active site loading [mol/kg media]
   * @param bulkDensityKgM3 packed bulk density [kg/m3]
   * @param stoichiometricRatio mol H2S consumed per mol active site (~1.0 triazine, ~0.6 Fe2O3)
   * @return this for chaining
   */
  public PackedBedScavengerReactor setMedia(double loadingMolPerKg, double bulkDensityKgM3,
      double stoichiometricRatio) {
    this.scavengerLoadingMolPerKg = loadingMolPerKg;
    this.scavengerBulkDensityKgM3 = bulkDensityKgM3;
    this.stoichiometricRatio = stoichiometricRatio;
    return this;
  }

  /**
   * Sets the volumetric reaction rate constant (lumped first-order in H2S) at fresh bed.
   *
   * @param kPerS rate constant [1/s], typical 1-50 for triazine, 0.1-5 for Fe oxide
   * @return this for chaining
   */
  public PackedBedScavengerReactor setRateConstant(double kPerS) {
    this.rateConstantPerS = kPerS;
    return this;
  }

  /**
   * Sets feed conditions.
   *
   * @param cInletMolM3 inlet H2S molar concentration [mol/m3]
   * @param qM3PerS volumetric flow rate [m3/s]
   * @return this for chaining
   */
  public PackedBedScavengerReactor setFeed(double cInletMolM3, double qM3PerS) {
    this.inletConcentrationMolM3 = cInletMolM3;
    this.volumetricFlowM3PerS = qM3PerS;
    return this;
  }

  /**
   * Sets discretisation (axial cells, time steps).
   *
   * @param nCells number of axial cells (10..1000)
   * @param nTimeSteps number of time steps for breakthrough curve
   * @return this for chaining
   */
  public PackedBedScavengerReactor setDiscretisation(int nCells, int nTimeSteps) {
    this.numberOfCells = Math.max(5, Math.min(1000, nCells));
    this.timeSteps = Math.max(10, nTimeSteps);
    return this;
  }

  /**
   * Sets simulation horizon and breakthrough threshold.
   *
   * @param simTimeS total simulated service time [s]
   * @param breakFrac outlet concentration fraction defining breakthrough (0..1)
   * @return this for chaining
   */
  public PackedBedScavengerReactor setSimulationTime(double simTimeS, double breakFrac) {
    this.simulationTimeS = simTimeS;
    this.breakthroughFraction = Math.max(1e-3, Math.min(0.5, breakFrac));
    return this;
  }

  // ─── Calculation ────────────────────────────────────────

  /**
   * Simulates the breakthrough curve and saturation history.
   *
   * @return this for chaining
   */
  public PackedBedScavengerReactor evaluate() {
    timeSeriesS.clear();
    outletConcentrationProfile.clear();
    bedUtilisationProfile.clear();

    double bedVolumeM3 = Math.PI / 4.0 * bedDiameterM * bedDiameterM * bedHeightM;
    double cellVolumeM3 = bedVolumeM3 / numberOfCells;
    double mediaMassPerCellKg = cellVolumeM3 * (1.0 - bedVoidage) * scavengerBulkDensityKgM3;
    double initialCapacityPerCellMol =
        mediaMassPerCellKg * scavengerLoadingMolPerKg / Math.max(stoichiometricRatio, 1e-6);
    double totalCapacityMol = initialCapacityPerCellMol * numberOfCells;

    // Cell residence time tau = V_cell * voidage / Q
    double tauPerCellS = cellVolumeM3 * bedVoidage / Math.max(volumetricFlowM3PerS, 1e-12);

    double[] remainingCapacity = new double[numberOfCells];
    for (int i = 0; i < numberOfCells; i++) {
      remainingCapacity[i] = initialCapacityPerCellMol;
    }

    double dtS = simulationTimeS / timeSteps;
    double totalRemovedMol = 0.0;
    breakthroughTimeS = Double.POSITIVE_INFINITY;

    for (int step = 0; step < timeSteps; step++) {
      double tNow = (step + 1) * dtS;
      double cIn = inletConcentrationMolM3;
      double cellInletC = cIn;
      double cellOutletC = cIn;

      for (int i = 0; i < numberOfCells; i++) {
        if (remainingCapacity[i] <= 0.0) {
          // exhausted cell — no removal
          continue;
        }
        double activityFraction = remainingCapacity[i] / initialCapacityPerCellMol;
        double kLocal = rateConstantPerS * activityFraction;
        // Plug-flow first-order: C_out = C_in * exp(-k * tau)
        cellOutletC = cellInletC * Math.exp(-kLocal * tauPerCellS);
        double removedMolS = volumetricFlowM3PerS * (cellInletC - cellOutletC);
        double consumedMol = removedMolS * dtS;
        if (consumedMol > remainingCapacity[i]) {
          consumedMol = remainingCapacity[i];
          // partial breakthrough through this cell
          cellOutletC = cellInletC - consumedMol / (volumetricFlowM3PerS * dtS);
        }
        remainingCapacity[i] -= consumedMol;
        totalRemovedMol += consumedMol;
        cellInletC = cellOutletC;
      }
      timeSeriesS.add(tNow);
      outletConcentrationProfile.add(cellOutletC);
      double consumedTotal = 0.0;
      for (int i = 0; i < numberOfCells; i++) {
        consumedTotal += (initialCapacityPerCellMol - remainingCapacity[i]);
      }
      double util = totalCapacityMol > 0.0 ? consumedTotal / totalCapacityMol : 0.0;
      bedUtilisationProfile.add(util);

      if (Double.isInfinite(breakthroughTimeS) && cellOutletC >= breakthroughFraction * cIn) {
        breakthroughTimeS = tNow;
      }
    }

    totalH2sRemovedKg = totalRemovedMol * MM_H2S / 1000.0;
    finalBedUtilisation = bedUtilisationProfile.isEmpty() ? 0.0
        : bedUtilisationProfile.get(bedUtilisationProfile.size() - 1);
    evaluated = true;
    return this;
  }

  // ─── Getters ────────────────────────────────────────────

  /**
   * Returns the simulated time series.
   *
   * @return list of times [s]
   */
  public List<Double> getTimeSeriesS() {
    return new ArrayList<Double>(timeSeriesS);
  }

  /**
   * Returns the outlet concentration history.
   *
   * @return list of outlet H2S concentrations [mol/m3]
   */
  public List<Double> getOutletConcentrationProfile() {
    return new ArrayList<Double>(outletConcentrationProfile);
  }

  /**
   * Returns the bed utilisation profile (fraction of capacity consumed at each time).
   *
   * @return list of utilisation fractions in [0,1]
   */
  public List<Double> getBedUtilisationProfile() {
    return new ArrayList<Double>(bedUtilisationProfile);
  }

  /**
   * Returns the breakthrough time.
   *
   * @return breakthrough time [s]; positive infinity if no breakthrough within simulated horizon
   */
  public double getBreakthroughTimeS() {
    return breakthroughTimeS;
  }

  /**
   * Returns the cumulative H2S mass removed.
   *
   * @return removed mass [kg]
   */
  public double getTotalH2sRemovedKg() {
    return totalH2sRemovedKg;
  }

  /**
   * Returns the final bed utilisation at the end of simulation.
   *
   * @return utilisation fraction
   */
  public double getFinalBedUtilisation() {
    return finalBedUtilisation;
  }

  /**
   * Returns true if {@link #evaluate()} has been invoked.
   *
   * @return true if evaluated
   */
  public boolean isEvaluated() {
    return evaluated;
  }

  /**
   * Returns standards used by this reactor model.
   *
   * @return list of standard reference maps
   */
  public List<Map<String, Object>> getStandardsApplied() {
    return StandardsRegistry.toMapList(StandardsRegistry.NACE_TM0169);
  }

  /**
   * Returns a structured map representation.
   *
   * @return ordered map
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("bedDiameterM", bedDiameterM);
    map.put("bedHeightM", bedHeightM);
    map.put("bedVoidage", bedVoidage);
    map.put("scavengerLoadingMolPerKg", scavengerLoadingMolPerKg);
    map.put("scavengerBulkDensityKgM3", scavengerBulkDensityKgM3);
    map.put("stoichiometricRatio", stoichiometricRatio);
    map.put("rateConstantPerS", rateConstantPerS);
    map.put("inletConcentrationMolM3", inletConcentrationMolM3);
    map.put("volumetricFlowM3PerS", volumetricFlowM3PerS);
    map.put("numberOfCells", numberOfCells);
    map.put("breakthroughFraction", breakthroughFraction);
    map.put("breakthroughTimeS", breakthroughTimeS);
    map.put("totalH2sRemovedKg", totalH2sRemovedKg);
    map.put("finalBedUtilisation", finalBedUtilisation);
    map.put("standardsApplied", getStandardsApplied());
    return map;
  }

  /**
   * Returns a JSON representation.
   *
   * @return pretty-printed JSON string
   */
  public String toJson() {
    Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls()
        .serializeSpecialFloatingPointValues().create();
    return gson.toJson(toMap());
  }
}
