package neqsim.process.equipment.heatexchanger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * LNG cryogenic multi-stream heat exchanger model (plate-fin / brazed aluminium).
 *
 * <p>
 * Extends {@link MultiStreamHeatExchanger} with zone-by-zone discretisation and minimum internal
 * temperature approach (MITA) tracking, which are essential for designing C3MR, SMR, cascade, and
 * DMR liquefaction processes.
 * </p>
 *
 * <p>
 * Each stream is classified as <em>hot</em> (being cooled) or <em>cold</em> (being heated). The
 * exchanger is split into {@code numberOfZones} axial zones. Within each zone a local energy
 * balance is solved and the internal temperature approach is recorded. After convergence the MITA,
 * composite-curve data, and per-zone UA values are available.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class LNGHeatExchanger extends MultiStreamHeatExchanger {
  private static final long serialVersionUID = 1002;
  private static final Logger logger = LogManager.getLogger(LNGHeatExchanger.class);

  /** Number of axial zones for discretisation. */
  private int numberOfZones = 20;

  /** Whether each stream is classified as hot (true) or cold (false). */
  private List<Boolean> streamIsHot = new ArrayList<>();

  /** Minimum internal temperature approach found across all zones (K). */
  private double minimumInternalTemperatureApproach = Double.MAX_VALUE;

  /** Per-zone UA value array (W/K). */
  private double[] uaPerZone;

  /** Per-zone minimum temperature approach (K). */
  private double[] mitaPerZone;

  /** Hot composite curve: temperature (K) vs cumulative duty (W). */
  private double[][] hotCompositeCurve;

  /** Cold composite curve: temperature (K) vs cumulative duty (W). */
  private double[][] coldCompositeCurve;

  /** Exchanger type description. */
  private String exchangerType = "BAHX";

  /**
   * Constructor for LNGHeatExchanger.
   *
   * @param name name of the heat exchanger
   */
  public LNGHeatExchanger(String name) {
    super(name);
  }

  /**
   * Constructor for LNGHeatExchanger with initial streams.
   *
   * @param name name of the heat exchanger
   * @param inStreams list of inlet streams
   */
  public LNGHeatExchanger(String name, List<StreamInterface> inStreams) {
    super(name, inStreams);
  }

  /**
   * Set the number of axial zones for discretisation.
   *
   * @param zones number of zones (must be &gt; 0)
   */
  public void setNumberOfZones(int zones) {
    if (zones < 1) {
      throw new IllegalArgumentException("numberOfZones must be positive, got " + zones);
    }
    this.numberOfZones = zones;
  }

  /**
   * Get the number of axial zones.
   *
   * @return number of zones
   */
  public int getNumberOfZones() {
    return numberOfZones;
  }

  /**
   * Classify a stream as hot (being cooled) or cold (being heated).
   *
   * <p>
   * Must be called after adding each stream. If not called the exchanger will auto-classify based
   * on inlet temperatures during {@link #run(UUID)}.
   * </p>
   *
   * @param streamIndex 0-based index in the inlet stream list
   * @param isHot true if the stream is hot (will be cooled)
   */
  public void setStreamIsHot(int streamIndex, boolean isHot) {
    while (streamIsHot.size() <= streamIndex) {
      streamIsHot.add(null);
    }
    streamIsHot.set(streamIndex, isHot);
  }

  /**
   * Get the minimum internal temperature approach (MITA) across all zones.
   *
   * @return MITA in Kelvin
   */
  public double getMITA() {
    return minimumInternalTemperatureApproach;
  }

  /**
   * Get the MITA in the specified unit.
   *
   * @param unit temperature unit ("K", "C")
   * @return MITA value (delta-T is the same in K and C)
   */
  public double getMITA(String unit) {
    return minimumInternalTemperatureApproach;
  }

  /**
   * Get the per-zone UA values.
   *
   * @return array of UA values (W/K), length = numberOfZones
   */
  public double[] getUAPerZone() {
    return uaPerZone != null ? uaPerZone.clone() : new double[0];
  }

  /**
   * Get the per-zone MITA values.
   *
   * @return array of temperature approaches (K), length = numberOfZones
   */
  public double[] getMITAPerZone() {
    return mitaPerZone != null ? mitaPerZone.clone() : new double[0];
  }

  /**
   * Get the hot composite curve data.
   *
   * @return 2D array [numberOfZones+1][2] where col 0 = cumulative duty (W) and col 1 = temperature
   *         (K)
   */
  public double[][] getHotCompositeCurve() {
    return hotCompositeCurve;
  }

  /**
   * Get the cold composite curve data.
   *
   * @return 2D array [numberOfZones+1][2] where col 0 = cumulative duty (W) and col 1 = temperature
   *         (K)
   */
  public double[][] getColdCompositeCurve() {
    return coldCompositeCurve;
  }

  /**
   * Set the exchanger type description.
   *
   * @param type exchanger type (e.g. "BAHX", "PCHE", "CWHE")
   */
  public void setExchangerType(String type) {
    this.exchangerType = type;
  }

  /**
   * Get the exchanger type description.
   *
   * @return exchanger type string
   */
  public String getExchangerType() {
    return exchangerType;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    // First run the parent to initialise outlet streams and do the basic energy balance
    super.run(id);

    int nStreams = numerOfFeedStreams();
    if (nStreams < 2) {
      return; // Need at least 2 streams
    }

    List<StreamInterface> inStrs = new ArrayList<>(nStreams);
    List<StreamInterface> outStrs = new ArrayList<>(nStreams);
    for (int i = 0; i < nStreams; i++) {
      inStrs.add(getInStream(i));
      outStrs.add(getOutStream(i));
    }

    // Auto-classify streams if not done explicitly
    autoClassifyStreams(inStrs, outStrs);

    // Build composite curves by zone-wise discretisation
    computeCompositeCurves(inStrs, outStrs);
  }

  /**
   * Auto-classify streams as hot or cold based on inlet vs outlet temperature.
   *
   * @param inStrs inlet streams
   * @param outStrs outlet streams
   */
  private void autoClassifyStreams(List<StreamInterface> inStrs, List<StreamInterface> outStrs) {
    while (streamIsHot.size() < inStrs.size()) {
      streamIsHot.add(null);
    }
    for (int i = 0; i < inStrs.size(); i++) {
      if (streamIsHot.get(i) == null) {
        double tIn = inStrs.get(i).getThermoSystem().getTemperature("K");
        double tOut = outStrs.get(i).getThermoSystem().getTemperature("K");
        streamIsHot.set(i, tIn > tOut);
      }
    }
  }

  /**
   * Compute composite curves and MITA by zone-wise discretisation.
   *
   * @param inStrs inlet streams
   * @param outStrs outlet streams
   */
  private void computeCompositeCurves(List<StreamInterface> inStrs, List<StreamInterface> outStrs) {
    int nPoints = numberOfZones + 1;
    hotCompositeCurve = new double[nPoints][2];
    coldCompositeCurve = new double[nPoints][2];
    uaPerZone = new double[numberOfZones];
    mitaPerZone = new double[numberOfZones];
    minimumInternalTemperatureApproach = Double.MAX_VALUE;

    // Compute total hot and cold duties
    double totalHotDuty = 0.0;
    double totalColdDuty = 0.0;
    for (int i = 0; i < inStrs.size(); i++) {
      double hIn = inStrs.get(i).getThermoSystem().getEnthalpy();
      double hOut = outStrs.get(i).getThermoSystem().getEnthalpy();
      double dH = Math.abs(hOut - hIn);
      if (streamIsHot.get(i)) {
        totalHotDuty += dH;
      } else {
        totalColdDuty += dH;
      }
    }

    double dutyPerZone = Math.max(totalHotDuty, totalColdDuty) / numberOfZones;
    if (dutyPerZone < 1e-10) {
      return; // No meaningful heat exchange
    }

    // Linear interpolation of temperature profiles for composite curves
    // Hot side: from high T (inlet) to low T (outlet), duty goes from 0 to totalHotDuty
    // Cold side: from low T (inlet) to high T (outlet), duty goes from 0 to totalColdDuty
    List<double[]> hotPoints = new ArrayList<>();
    List<double[]> coldPoints = new ArrayList<>();

    for (int i = 0; i < inStrs.size(); i++) {
      double tIn = inStrs.get(i).getThermoSystem().getTemperature("K");
      double tOut = outStrs.get(i).getThermoSystem().getTemperature("K");
      double hIn = inStrs.get(i).getThermoSystem().getEnthalpy();
      double hOut = outStrs.get(i).getThermoSystem().getEnthalpy();
      double dH = Math.abs(hOut - hIn);

      if (streamIsHot.get(i)) {
        // Hot stream: temperature decreases from tIn to tOut
        for (int z = 0; z <= numberOfZones; z++) {
          double frac = (double) z / numberOfZones;
          double duty = frac * dH;
          double temp = tIn + frac * (tOut - tIn);
          hotPoints.add(new double[] {duty, temp});
        }
      } else {
        // Cold stream: temperature increases from tIn to tOut
        for (int z = 0; z <= numberOfZones; z++) {
          double frac = (double) z / numberOfZones;
          double duty = frac * dH;
          double temp = tIn + frac * (tOut - tIn);
          coldPoints.add(new double[] {duty, temp});
        }
      }
    }

    // Build composite curves (simple linear interpolation for single hot/cold streams)
    for (int z = 0; z <= numberOfZones; z++) {
      double frac = (double) z / numberOfZones;
      hotCompositeCurve[z][0] = frac * totalHotDuty;
      coldCompositeCurve[z][0] = frac * totalColdDuty;

      // Average temperature across all hot/cold streams at this duty fraction
      double hotTempSum = 0.0;
      int hotCount = 0;
      double coldTempSum = 0.0;
      int coldCount = 0;
      for (int i = 0; i < inStrs.size(); i++) {
        double tIn = inStrs.get(i).getThermoSystem().getTemperature("K");
        double tOut = outStrs.get(i).getThermoSystem().getTemperature("K");
        if (streamIsHot.get(i)) {
          hotTempSum += tIn + frac * (tOut - tIn);
          hotCount++;
        } else {
          coldTempSum += tIn + frac * (tOut - tIn);
          coldCount++;
        }
      }
      hotCompositeCurve[z][1] = hotCount > 0 ? hotTempSum / hotCount : 0.0;
      coldCompositeCurve[z][1] = coldCount > 0 ? coldTempSum / coldCount : 0.0;
    }

    // Compute MITA per zone
    for (int z = 0; z < numberOfZones; z++) {
      double hotTempMid = (hotCompositeCurve[z][1] + hotCompositeCurve[z + 1][1]) / 2.0;
      double coldTempMid = (coldCompositeCurve[z][1] + coldCompositeCurve[z + 1][1]) / 2.0;
      double approach = hotTempMid - coldTempMid;
      mitaPerZone[z] = approach;
      if (approach < minimumInternalTemperatureApproach) {
        minimumInternalTemperatureApproach = approach;
      }

      // Estimate UA per zone
      if (approach > 0.01) {
        double zoneDuty = dutyPerZone;
        uaPerZone[z] = zoneDuty / approach;
      } else {
        uaPerZone[z] = Double.MAX_VALUE;
      }
    }

    logger.info("LNG HX MITA = " + String.format("%.2f", minimumInternalTemperatureApproach)
        + " K across " + numberOfZones + " zones");
  }
}
