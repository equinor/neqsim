package neqsim.util.nucleation;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Population Balance Model (PBM) for tracking the evolution of particle size distributions over
 * time.
 *
 * <p>
 * This class solves the discretized General Dynamic Equation (GDE) for aerosol/particle populations
 * subject to:
 * </p>
 * <ul>
 * <li><b>Nucleation</b>: Formation of new particles at the critical size from CNT</li>
 * <li><b>Condensation growth</b>: Particle size increase by vapor condensation (Fuchs-interpolated
 * growth rate)</li>
 * <li><b>Brownian coagulation</b>: Particle-particle collisions merging smaller particles into
 * larger ones (Smoluchowski kernel)</li>
 * </ul>
 *
 * <p>
 * The size space is discretized into geometrically-spaced sections (bins). The model uses operator
 * splitting: nucleation source, then growth (advection), then coagulation, each sub-step at the
 * current time step.
 * </p>
 *
 * <p>
 * Usage:
 * </p>
 *
 * <pre>
 * {@code
 * // First compute nucleation with CNT
 * ClassicalNucleationTheory cnt = ClassicalNucleationTheory.sulfurS8();
 * cnt.setTemperature(253.15);
 * cnt.setSupersaturationRatio(100.0);
 * cnt.setGasViscosity(1.0e-5);
 * cnt.calculate();
 *
 * // Set up population balance
 * PopulationBalanceModel pbm = new PopulationBalanceModel(cnt);
 * pbm.setNumberOfBins(40);
 * pbm.setMinDiameter(1.0e-9); // 1 nm
 * pbm.setMaxDiameter(100.0e-6); // 100 um
 * pbm.setTotalTime(5.0); // 5 seconds
 * pbm.setTimeSteps(500);
 * pbm.solve();
 *
 * double[] diameters = pbm.getBinDiameters();
 * double[] counts = pbm.getBinNumberDensities();
 * double d50 = pbm.getMedianDiameter();
 * double totalN = pbm.getTotalNumberDensity();
 * }
 * </pre>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Seinfeld, J.H. and Pandis, S.N. (2016). Atmospheric Chemistry and Physics, 3rd ed., Chapters
 * 12-13.</li>
 * <li>Gelbard, F. and Seinfeld, J.H. (1980). Simulation of multicomponent aerosol dynamics. J.
 * Colloid Interface Sci. 78, 485-501.</li>
 * <li>Jacobson, M.Z. (2005). Fundamentals of Atmospheric Modeling, 2nd ed., Chapter 13.</li>
 * </ul>
 *
 * @author esol
 * @version 1.0
 */
public class PopulationBalanceModel {

  /** The CNT model providing nucleation rate and growth parameters. */
  private ClassicalNucleationTheory cnt;

  /** Number of size bins. */
  private int numberOfBins = 30;

  /** Minimum bin diameter in m. */
  private double minDiameter = 1.0e-9;

  /** Maximum bin diameter in m. */
  private double maxDiameter = 100.0e-6;

  /** Total simulation time in seconds. */
  private double totalTime = 1.0;

  /** Number of time steps. */
  private int timeSteps = 200;

  /** Bin center diameters in m. */
  private double[] binDiameters;

  /** Bin center radii in m. */
  private double[] binRadii;

  /** Bin edge diameters (numberOfBins + 1) in m. */
  private double[] binEdges;

  /** Bin widths (diameter range per bin) in m. */
  private double[] binWidths;

  /** Number density per bin in particles/m3. */
  private double[] numberDensity;

  /** Volume density per bin in m3/m3 (volume concentration). */
  private double[] volumeDensity;

  /** Total number density in particles/m3. */
  private double totalNumberDensity = 0.0;

  /** Total volume concentration in m3_particles/m3_gas. */
  private double totalVolumeConcentration = 0.0;

  /** Total mass concentration in kg/m3. */
  private double totalMassConcentration = 0.0;

  /** Median (d50) diameter in m. */
  private double medianDiameter = 0.0;

  /** Mean diameter (number-weighted) in m. */
  private double meanDiameter = 0.0;

  /** Geometric standard deviation. */
  private double geometricStdDev = 1.0;

  /** Current simulation time in seconds. */
  private double currentTime = 0.0;

  /** Whether the model has been solved. */
  private boolean solved = false;

  /**
   * Creates a PopulationBalanceModel using nucleation parameters from a ClassicalNucleationTheory
   * model.
   *
   * <p>
   * The CNT model must have been calculated before passing to this constructor.
   * </p>
   *
   * @param cnt the ClassicalNucleationTheory model (must be calculated)
   */
  public PopulationBalanceModel(ClassicalNucleationTheory cnt) {
    this.cnt = cnt;
  }

  /**
   * Sets the number of size bins for the discretization.
   *
   * @param bins number of bins (typical 20-60)
   */
  public void setNumberOfBins(int bins) {
    this.numberOfBins = Math.max(5, bins);
    this.solved = false;
  }

  /**
   * Sets the minimum diameter of the size distribution.
   *
   * @param diameter minimum diameter in m (typical 1e-9 for nucleation)
   */
  public void setMinDiameter(double diameter) {
    this.minDiameter = diameter;
    this.solved = false;
  }

  /**
   * Sets the maximum diameter of the size distribution.
   *
   * @param diameter maximum diameter in m (typical 100e-6 for grown particles)
   */
  public void setMaxDiameter(double diameter) {
    this.maxDiameter = diameter;
    this.solved = false;
  }

  /**
   * Sets the total simulation time.
   *
   * @param time total time in seconds
   */
  public void setTotalTime(double time) {
    this.totalTime = time;
    this.solved = false;
  }

  /**
   * Sets the number of time steps for the simulation.
   *
   * @param steps number of time steps (higher = more accurate but slower)
   */
  public void setTimeSteps(int steps) {
    this.timeSteps = Math.max(10, steps);
    this.solved = false;
  }

  /**
   * Solves the population balance equation by time-marching with operator splitting.
   *
   * <p>
   * At each time step:
   * </p>
   * <ol>
   * <li>Add nucleated particles at the critical radius bin</li>
   * <li>Apply condensation growth (shift particles to larger bins)</li>
   * <li>Apply Brownian coagulation (merge particles)</li>
   * </ol>
   */
  public void solve() {
    initializeBins();

    double dt = totalTime / timeSteps;
    currentTime = 0.0;

    double nucleationRate = cnt.getNucleationRate();
    double growthRate = cnt.getGrowthRate();
    double coagKernel = cnt.getCoagulationKernel();
    double critRadius = cnt.getCriticalRadius();

    // Find the bin index closest to the critical radius
    int nucleationBin = findBinIndex(critRadius * 2.0); // diameter = 2*r

    for (int step = 0; step < timeSteps; step++) {
      // Step 1: Nucleation source
      applyNucleation(nucleationBin, nucleationRate, dt);

      // Step 2: Condensation growth
      applyCondensationGrowth(growthRate, dt);

      // Step 3: Coagulation
      applyCoagulation(coagKernel, dt);

      currentTime += dt;
    }

    // Compute statistics
    computeStatistics();

    solved = true;
  }

  /**
   * Initializes the geometric bin structure.
   */
  private void initializeBins() {
    binDiameters = new double[numberOfBins];
    binRadii = new double[numberOfBins];
    binEdges = new double[numberOfBins + 1];
    binWidths = new double[numberOfBins];
    numberDensity = new double[numberOfBins];
    volumeDensity = new double[numberOfBins];

    // Geometric spacing: d_i = d_min * (d_max/d_min)^(i/(N-1))
    double logMin = Math.log(minDiameter);
    double logMax = Math.log(maxDiameter);
    double logStep = (logMax - logMin) / numberOfBins;

    for (int i = 0; i <= numberOfBins; i++) {
      binEdges[i] = Math.exp(logMin + i * logStep);
    }

    for (int i = 0; i < numberOfBins; i++) {
      binDiameters[i] = Math.sqrt(binEdges[i] * binEdges[i + 1]); // geometric mean
      binRadii[i] = binDiameters[i] / 2.0;
      binWidths[i] = binEdges[i + 1] - binEdges[i];
      numberDensity[i] = 0.0;
      volumeDensity[i] = 0.0;
    }
  }

  /**
   * Finds the bin index closest to a given diameter.
   *
   * @param diameter target diameter in m
   * @return bin index
   */
  private int findBinIndex(double diameter) {
    if (diameter <= binEdges[0]) {
      return 0;
    }
    if (diameter >= binEdges[numberOfBins]) {
      return numberOfBins - 1;
    }

    for (int i = 0; i < numberOfBins; i++) {
      if (diameter >= binEdges[i] && diameter < binEdges[i + 1]) {
        return i;
      }
    }
    return numberOfBins - 1;
  }

  /**
   * Adds nucleated particles to the nucleation bin.
   *
   * @param nucleationBin the bin index for newly nucleated particles
   * @param rate nucleation rate in particles/(m3*s)
   * @param dt time step in seconds
   */
  private void applyNucleation(int nucleationBin, double rate, double dt) {
    if (rate > 0.0 && nucleationBin >= 0 && nucleationBin < numberOfBins) {
      double newParticles = rate * dt;
      if (newParticles > 1e30) {
        newParticles = 1e30; // Physical cap
      }
      numberDensity[nucleationBin] += newParticles;
    }
  }

  /**
   * Applies condensation growth by shifting particles to larger bins.
   *
   * <p>
   * Uses a first-order upstream differencing scheme. For each bin, the growth velocity determines
   * how many particles move to the next larger bin during the time step.
   * </p>
   *
   * @param growthRateRad growth rate dr/dt in m/s
   * @param dt time step in seconds
   */
  private void applyCondensationGrowth(double growthRateRad, double dt) {
    if (growthRateRad <= 0.0) {
      return;
    }

    // Work from largest bin downward to avoid double-counting
    double[] newDensity = Arrays.copyOf(numberDensity, numberOfBins);

    for (int i = 0; i < numberOfBins - 1; i++) {
      if (numberDensity[i] <= 0.0) {
        continue;
      }

      // Growth velocity in diameter space: dD/dt = 2 * dr/dt
      // Fraction that grows out of bin i during dt
      double diamGrowth = 2.0 * growthRateRad * dt;
      double fractionOut = diamGrowth / binWidths[i];

      // Cap at 1.0 (CFL condition)
      if (fractionOut > 1.0) {
        fractionOut = 1.0;
      }

      double nTransfer = numberDensity[i] * fractionOut;
      newDensity[i] -= nTransfer;
      newDensity[i + 1] += nTransfer;
    }

    // Ensure no negative densities
    for (int i = 0; i < numberOfBins; i++) {
      numberDensity[i] = Math.max(0.0, newDensity[i]);
    }
  }

  /**
   * Applies Brownian coagulation using a simplified sectional approach.
   *
   * <p>
   * Uses the constant kernel approximation (Smoluchowski) for the continuum regime. Coagulation
   * moves volume from two smaller bins into a larger bin.
   * </p>
   *
   * @param kernel coagulation kernel K in m3/s
   * @param dt time step in seconds
   */
  private void applyCoagulation(double kernel, double dt) {
    if (kernel <= 0.0) {
      return;
    }

    // Total number density for self-coagulation estimate
    double totalN = 0.0;
    for (int i = 0; i < numberOfBins; i++) {
      totalN += numberDensity[i];
    }

    if (totalN < 1.0) {
      return;
    }

    double[] newDensity = Arrays.copyOf(numberDensity, numberOfBins);

    // Simplified approach: for each pair (i,j), the coagulation rate is K * N_i * N_j
    // The product has volume V_i + V_j, placed in the bin corresponding to that combined volume
    // For efficiency, use only the self-coagulation (i=j) and near-neighbor terms
    for (int i = 0; i < numberOfBins; i++) {
      if (numberDensity[i] < 1.0) {
        continue;
      }

      // Self-coagulation: rate = 0.5 * K * N_i^2
      double selfRate = 0.5 * kernel * numberDensity[i] * numberDensity[i] * dt;
      if (selfRate > numberDensity[i] * 0.5) {
        selfRate = numberDensity[i] * 0.5; // Don't remove more than half
      }

      // Two particles of size i merge to form one particle of size 2^(1/3)*d_i
      // This roughly goes to bin i+1 (since bins are geometrically spaced)
      newDensity[i] -= 2.0 * selfRate;
      if (i + 1 < numberOfBins) {
        newDensity[i + 1] += selfRate;
      }
    }

    for (int i = 0; i < numberOfBins; i++) {
      numberDensity[i] = Math.max(0.0, newDensity[i]);
    }
  }

  /**
   * Computes statistics from the current size distribution.
   */
  private void computeStatistics() {
    totalNumberDensity = 0.0;
    totalVolumeConcentration = 0.0;
    meanDiameter = 0.0;
    double sumNlogD = 0.0;
    double sumNlogD2 = 0.0;

    for (int i = 0; i < numberOfBins; i++) {
      double n = numberDensity[i];
      double d = binDiameters[i];
      double v = (Math.PI / 6.0) * d * d * d;

      totalNumberDensity += n;
      totalVolumeConcentration += n * v;
      volumeDensity[i] = n * v;
      meanDiameter += n * d;

      if (n > 0.0 && d > 0.0) {
        double logD = Math.log(d);
        sumNlogD += n * logD;
        sumNlogD2 += n * logD * logD;
      }
    }

    if (totalNumberDensity > 0.0) {
      meanDiameter /= totalNumberDensity;

      // Geometric mean diameter
      double logDMean = sumNlogD / totalNumberDensity;

      // Geometric standard deviation
      double logDVar = sumNlogD2 / totalNumberDensity - logDMean * logDMean;
      if (logDVar > 0.0) {
        geometricStdDev = Math.exp(Math.sqrt(logDVar));
      } else {
        geometricStdDev = 1.0;
      }
    }

    // Mass concentration
    totalMassConcentration = totalVolumeConcentration * cnt.getCondensedPhaseDensity();

    // Median diameter (d50)
    computeMedianDiameter();
  }

  /**
   * Computes the median (d50) diameter from the cumulative distribution.
   */
  private void computeMedianDiameter() {
    if (totalNumberDensity <= 0.0) {
      medianDiameter = 0.0;
      return;
    }

    double cumulative = 0.0;
    double halfN = totalNumberDensity / 2.0;

    for (int i = 0; i < numberOfBins; i++) {
      cumulative += numberDensity[i];
      if (cumulative >= halfN) {
        medianDiameter = binDiameters[i];
        return;
      }
    }
    medianDiameter = binDiameters[numberOfBins - 1];
  }

  // ============================================================================
  // Getters
  // ============================================================================

  /**
   * Returns the bin center diameters.
   *
   * @return array of bin center diameters in m
   */
  public double[] getBinDiameters() {
    return binDiameters != null ? Arrays.copyOf(binDiameters, numberOfBins) : new double[0];
  }

  /**
   * Returns the bin edge diameters (numberOfBins + 1 values).
   *
   * @return array of bin edge diameters in m
   */
  public double[] getBinEdges() {
    return binEdges != null ? Arrays.copyOf(binEdges, numberOfBins + 1) : new double[0];
  }

  /**
   * Returns the number density per bin.
   *
   * @return array of number densities in particles/m3
   */
  public double[] getBinNumberDensities() {
    return numberDensity != null ? Arrays.copyOf(numberDensity, numberOfBins) : new double[0];
  }

  /**
   * Returns the volume density per bin.
   *
   * @return array of volume densities in m3/m3
   */
  public double[] getBinVolumeDensities() {
    return volumeDensity != null ? Arrays.copyOf(volumeDensity, numberOfBins) : new double[0];
  }

  /**
   * Returns the total particle number density.
   *
   * @return total number density in particles/m3
   */
  public double getTotalNumberDensity() {
    return totalNumberDensity;
  }

  /**
   * Returns the total particle volume concentration.
   *
   * @return volume concentration in m3_particle/m3_gas
   */
  public double getTotalVolumeConcentration() {
    return totalVolumeConcentration;
  }

  /**
   * Returns the total particle mass concentration.
   *
   * @return mass concentration in kg/m3
   */
  public double getTotalMassConcentration() {
    return totalMassConcentration;
  }

  /**
   * Returns the median (d50) diameter.
   *
   * @return median diameter in m
   */
  public double getMedianDiameter() {
    return medianDiameter;
  }

  /**
   * Returns the number-weighted mean diameter.
   *
   * @return mean diameter in m
   */
  public double getMeanDiameter() {
    return meanDiameter;
  }

  /**
   * Returns the geometric standard deviation of the size distribution.
   *
   * @return geometric standard deviation
   */
  public double getGeometricStdDev() {
    return geometricStdDev;
  }

  /**
   * Returns the current simulation time.
   *
   * @return current time in seconds
   */
  public double getCurrentTime() {
    return currentTime;
  }

  /**
   * Returns whether the model has been solved.
   *
   * @return true if solved
   */
  public boolean isSolved() {
    return solved;
  }

  /**
   * Returns the number of size bins.
   *
   * @return number of bins
   */
  public int getNumberOfBins() {
    return numberOfBins;
  }

  /**
   * Returns the condensed phase density from the underlying CNT model.
   *
   * @return condensed phase density in kg/m3
   */
  private double getCondensedPhaseDensity() {
    // Access through CNT public getter
    return cnt.getCondensedPhaseDensity();
  }

  // ============================================================================
  // Reporting
  // ============================================================================

  /**
   * Returns all results as a Map for serialization.
   *
   * @return map of result names to values
   */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();

    Map<String, Object> config = new LinkedHashMap<String, Object>();
    config.put("numberOfBins", numberOfBins);
    config.put("minDiameter_m", minDiameter);
    config.put("maxDiameter_m", maxDiameter);
    config.put("totalTime_s", totalTime);
    config.put("timeSteps", timeSteps);
    result.put("configuration", config);

    Map<String, Object> stats = new LinkedHashMap<String, Object>();
    stats.put("totalNumberDensity_per_m3", totalNumberDensity);
    stats.put("totalVolumeConcentration_m3_per_m3", totalVolumeConcentration);
    stats.put("totalMassConcentration_kg_m3", totalMassConcentration);
    stats.put("totalMassConcentration_mg_m3", totalMassConcentration * 1e6);
    stats.put("meanDiameter_m", meanDiameter);
    stats.put("meanDiameter_um", meanDiameter * 1e6);
    stats.put("medianDiameter_m", medianDiameter);
    stats.put("medianDiameter_um", medianDiameter * 1e6);
    stats.put("geometricStdDev", geometricStdDev);
    stats.put("simulationTime_s", currentTime);
    result.put("statistics", stats);

    // Bin data (compact)
    if (binDiameters != null) {
      Map<String, Object> bins = new LinkedHashMap<String, Object>();
      double[] dUm = new double[numberOfBins];
      for (int i = 0; i < numberOfBins; i++) {
        dUm[i] = binDiameters[i] * 1e6;
      }
      bins.put("diameters_um", dUm);
      bins.put("numberDensity_per_m3", Arrays.copyOf(numberDensity, numberOfBins));
      result.put("sizeDistribution", bins);
    }

    return result;
  }

  /**
   * Returns a JSON report of all results.
   *
   * @return JSON string
   */
  public String toJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create()
        .toJson(toMap());
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    if (!solved) {
      return "PopulationBalanceModel [not solved]";
    }
    return String.format(
        "PBM: N=%.2e/m3, d_mean=%.2f um, d_50=%.2f um, sigma_g=%.2f, mass=%.3f mg/m3",
        totalNumberDensity, meanDiameter * 1e6, medianDiameter * 1e6, geometricStdDev,
        totalMassConcentration * 1e6);
  }
}
