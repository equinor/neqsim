package neqsim.process.equipment.well.allocation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Allocates commingled production back to individual wells.
 *
 * <p>
 * Implements multiple allocation methods commonly used in production accounting:
 * </p>
 * <ul>
 * <li>Well test allocation: Uses periodic well test data</li>
 * <li>VFM-based allocation: Uses virtual flow meter estimates</li>
 * <li>Choke model allocation: Uses choke performance curves</li>
 * <li>Combined method: Weighted combination of above</li>
 * </ul>
 *
 * <p>
 * Designed for integration with AI optimization platforms that require real-time production
 * allocation for reservoir management.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class WellProductionAllocator implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String name;
  private final List<WellData> wells;
  private AllocationMethod method = AllocationMethod.WELL_TEST;
  private double reconciliationTolerance = 0.01;

  /**
   * Allocation methods.
   */
  public enum AllocationMethod {
    WELL_TEST, VFM_BASED, CHOKE_MODEL, COMBINED
  }

  /**
   * Data for a single well.
   */
  public static class WellData implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String wellName;
    private StreamInterface wellStream;
    private double testOilRate;
    private double testGasRate;
    private double testWaterRate;
    private double vfmOilRate;
    private double vfmGasRate;
    private double vfmWaterRate;
    private double chokePosition;
    private double reservoirPressure;
    private double productivityIndex;
    private double weight = 1.0;

    public WellData(String wellName) {
      this.wellName = wellName;
    }

    public String getWellName() {
      return wellName;
    }

    public void setWellStream(StreamInterface stream) {
      this.wellStream = stream;
    }

    public StreamInterface getWellStream() {
      return wellStream;
    }

    public void setTestRates(double oil, double gas, double water) {
      this.testOilRate = oil;
      this.testGasRate = gas;
      this.testWaterRate = water;
    }

    public void setVFMRates(double oil, double gas, double water) {
      this.vfmOilRate = oil;
      this.vfmGasRate = gas;
      this.vfmWaterRate = water;
    }

    public void setChokePosition(double position) {
      this.chokePosition = position;
    }

    public void setReservoirPressure(double pressure) {
      this.reservoirPressure = pressure;
    }

    public void setProductivityIndex(double pi) {
      this.productivityIndex = pi;
    }

    public void setWeight(double weight) {
      this.weight = weight;
    }

    public double getTestOilRate() {
      return testOilRate;
    }

    public double getTestGasRate() {
      return testGasRate;
    }

    public double getTestWaterRate() {
      return testWaterRate;
    }

    public double getVfmOilRate() {
      return vfmOilRate;
    }

    public double getVfmGasRate() {
      return vfmGasRate;
    }

    public double getVfmWaterRate() {
      return vfmWaterRate;
    }

    public double getChokePosition() {
      return chokePosition;
    }

    public double getReservoirPressure() {
      return reservoirPressure;
    }

    public double getProductivityIndex() {
      return productivityIndex;
    }

    public double getWeight() {
      return weight;
    }
  }

  /**
   * Creates a well production allocator.
   *
   * @param name allocator name
   */
  public WellProductionAllocator(String name) {
    this.name = name;
    this.wells = new ArrayList<>();
  }

  /**
   * Gets the allocator name.
   *
   * @return name
   */
  public String getName() {
    return name;
  }

  /**
   * Adds a well to the allocator.
   *
   * @param wellName well name
   * @return well data object for configuration
   */
  public WellData addWell(String wellName) {
    WellData well = new WellData(wellName);
    wells.add(well);
    return well;
  }

  /**
   * Gets a well by name.
   *
   * @param wellName well name
   * @return well data or null
   */
  public WellData getWell(String wellName) {
    for (WellData well : wells) {
      if (well.getWellName().equals(wellName)) {
        return well;
      }
    }
    return null;
  }

  /**
   * Sets the allocation method.
   *
   * @param method allocation method
   */
  public void setAllocationMethod(AllocationMethod method) {
    this.method = method;
  }

  /**
   * Sets the reconciliation tolerance.
   *
   * @param tolerance relative tolerance for balancing (e.g., 0.01 for 1%)
   */
  public void setReconciliationTolerance(double tolerance) {
    this.reconciliationTolerance = tolerance;
  }

  /**
   * Allocates total production to individual wells.
   *
   * @param totalOilRate total oil rate at separator (Sm3/day)
   * @param totalGasRate total gas rate at separator (Sm3/day)
   * @param totalWaterRate total water rate at separator (Sm3/day)
   * @return allocation result
   */
  public AllocationResult allocate(double totalOilRate, double totalGasRate,
      double totalWaterRate) {

    Map<String, Double> oilRates = new HashMap<>();
    Map<String, Double> gasRates = new HashMap<>();
    Map<String, Double> waterRates = new HashMap<>();
    Map<String, Double> uncertainties = new HashMap<>();

    // Calculate initial allocation based on method
    switch (method) {
      case WELL_TEST:
        allocateByWellTest(oilRates, gasRates, waterRates, uncertainties);
        break;
      case VFM_BASED:
        allocateByVFM(oilRates, gasRates, waterRates, uncertainties);
        break;
      case CHOKE_MODEL:
        allocateByChokeModel(oilRates, gasRates, waterRates, uncertainties);
        break;
      case COMBINED:
        allocateCombined(oilRates, gasRates, waterRates, uncertainties);
        break;
    }

    // Reconcile to match totals
    reconcileRates(oilRates, totalOilRate);
    reconcileRates(gasRates, totalGasRate);
    reconcileRates(waterRates, totalWaterRate);

    // Calculate allocation error
    double sumOil = oilRates.values().stream().mapToDouble(Double::doubleValue).sum();
    double error = (totalOilRate > 0) ? Math.abs(sumOil - totalOilRate) / totalOilRate : 0;

    return new AllocationResult(oilRates, gasRates, waterRates, uncertainties, error);
  }

  /**
   * Allocates based on well test data.
   *
   * @param oilRates map to store allocated oil rates by well name
   * @param gasRates map to store allocated gas rates by well name
   * @param waterRates map to store allocated water rates by well name
   * @param uncertainties map to store allocation uncertainties by well name
   */
  private void allocateByWellTest(Map<String, Double> oilRates, Map<String, Double> gasRates,
      Map<String, Double> waterRates, Map<String, Double> uncertainties) {

    double totalTestOil = 0;
    double totalTestGas = 0;
    double totalTestWater = 0;

    for (WellData well : wells) {
      totalTestOil += well.getTestOilRate();
      totalTestGas += well.getTestGasRate();
      totalTestWater += well.getTestWaterRate();
    }

    for (WellData well : wells) {
      String name = well.getWellName();
      oilRates.put(name, well.getTestOilRate());
      gasRates.put(name, well.getTestGasRate());
      waterRates.put(name, well.getTestWaterRate());
      uncertainties.put(name, 0.1); // Well test has ~10% uncertainty
    }
  }

  /**
   * Allocates based on VFM estimates.
   */
  private void allocateByVFM(Map<String, Double> oilRates, Map<String, Double> gasRates,
      Map<String, Double> waterRates, Map<String, Double> uncertainties) {

    for (WellData well : wells) {
      String name = well.getWellName();
      oilRates.put(name, well.getVfmOilRate());
      gasRates.put(name, well.getVfmGasRate());
      waterRates.put(name, well.getVfmWaterRate());
      uncertainties.put(name, 0.05); // VFM has ~5% uncertainty
    }
  }

  /**
   * Allocates based on choke model.
   */
  private void allocateByChokeModel(Map<String, Double> oilRates, Map<String, Double> gasRates,
      Map<String, Double> waterRates, Map<String, Double> uncertainties) {

    // Simple choke-based allocation using position and PI
    double totalCapacity = 0;
    for (WellData well : wells) {
      double capacity =
          well.getChokePosition() * well.getProductivityIndex() * well.getReservoirPressure();
      totalCapacity += capacity;
    }

    for (WellData well : wells) {
      String name = well.getWellName();
      double capacity =
          well.getChokePosition() * well.getProductivityIndex() * well.getReservoirPressure();
      double fraction = (totalCapacity > 0) ? capacity / totalCapacity : 0;

      // Use fraction of test rates
      oilRates.put(name, well.getTestOilRate() * fraction * wells.size());
      gasRates.put(name, well.getTestGasRate() * fraction * wells.size());
      waterRates.put(name, well.getTestWaterRate() * fraction * wells.size());
      uncertainties.put(name, 0.15); // Choke model has ~15% uncertainty
    }
  }

  /**
   * Allocates using weighted combination of methods.
   */
  private void allocateCombined(Map<String, Double> oilRates, Map<String, Double> gasRates,
      Map<String, Double> waterRates, Map<String, Double> uncertainties) {

    // Weight: VFM 50%, Well test 30%, Choke 20%
    double wVFM = 0.5;
    double wTest = 0.3;
    double wChoke = 0.2;

    for (WellData well : wells) {
      String name = well.getWellName();

      double oilVFM = well.getVfmOilRate();
      double oilTest = well.getTestOilRate();
      double oilChoke = well.getTestOilRate(); // Simplified

      oilRates.put(name, wVFM * oilVFM + wTest * oilTest + wChoke * oilChoke);
      gasRates.put(name, wVFM * well.getVfmGasRate() + wTest * well.getTestGasRate()
          + wChoke * well.getTestGasRate());
      waterRates.put(name, wVFM * well.getVfmWaterRate() + wTest * well.getTestWaterRate()
          + wChoke * well.getTestWaterRate());
      uncertainties.put(name, 0.07); // Combined has ~7% uncertainty
    }
  }

  /**
   * Reconciles rates to match total.
   */
  private void reconcileRates(Map<String, Double> rates, double total) {
    double sum = rates.values().stream().mapToDouble(Double::doubleValue).sum();

    if (sum > 0 && Math.abs(sum - total) > reconciliationTolerance * total) {
      double factor = total / sum;
      for (String key : rates.keySet()) {
        rates.put(key, rates.get(key) * factor);
      }
    }
  }

  /**
   * Gets the number of wells.
   *
   * @return well count
   */
  public int getWellCount() {
    return wells.size();
  }

  /**
   * Gets the well names.
   *
   * @return list of well names
   */
  public List<String> getWellNames() {
    List<String> names = new ArrayList<>();
    for (WellData well : wells) {
      names.add(well.getWellName());
    }
    return names;
  }
}
