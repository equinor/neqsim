package neqsim.process.equipment.compressor;

import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Locates where solid deposits form along a multi-impeller centrifugal compressor by marching the pressure-temperature
 * path stage by stage and running a solid (TP-solid) precipitation flash at each impeller's local conditions.
 *
 * <p>
 * Deposition happens where the local pressure and temperature cross the solubility / saturation limit of the depositing
 * species. For a single compressor body the temperature rises monotonically from inlet to discharge, so a
 * solubility-limited species such as elemental sulfur (S8) is <em>most</em> likely to drop out at the cold first
 * impeller and re-dissolve toward the hotter last impeller; across a multi-stage train the gas re-cools in each
 * intercooler and can re-cross saturation on the first impeller after each cooler. This profile makes that spatial
 * picture explicit.
 * </p>
 *
 * <p>
 * The stage pressures are interpolated geometrically between suction and discharge, and the stage temperatures linearly
 * along the polytropic path (screening-level). For a rigorous per-stage fluid state, use the compressor's detailed
 * polytropic method with a {@link CompressorPropertyProfile} and flash each captured stage fluid.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class CompressorDepositProfile {

  private static final Logger logger = LogManager.getLogger(CompressorDepositProfile.class);

  /**
   * Per-impeller deposition result.
   */
  public static class StageDeposit {
    private final int stage;
    private final double pressureBara;
    private final double temperatureC;
    private final double solidRateKgHr;
    private final boolean supersaturated;

    /**
     * Constructor.
     *
     * @param stage impeller index (1 = first/suction impeller)
     * @param pressureBara local stage pressure in bara
     * @param temperatureC local stage temperature in Celsius
     * @param solidRateKgHr solid drop-out rate at the local conditions in kg/hr
     * @param supersaturated true if a solid phase is present at the local conditions
     */
    public StageDeposit(int stage, double pressureBara, double temperatureC, double solidRateKgHr,
        boolean supersaturated) {
      this.stage = stage;
      this.pressureBara = pressureBara;
      this.temperatureC = temperatureC;
      this.solidRateKgHr = solidRateKgHr;
      this.supersaturated = supersaturated;
    }

    /**
     * Impeller index.
     *
     * @return stage number (1-based)
     */
    public int getStage() {
      return stage;
    }

    /**
     * Local stage pressure.
     *
     * @return pressure in bara
     */
    public double getPressureBara() {
      return pressureBara;
    }

    /**
     * Local stage temperature.
     *
     * @return temperature in Celsius
     */
    public double getTemperatureC() {
      return temperatureC;
    }

    /**
     * Solid drop-out rate at the local stage conditions.
     *
     * @return solid rate in kg/hr
     */
    public double getSolidRateKgHr() {
      return solidRateKgHr;
    }

    /**
     * Whether a solid phase is present at the local conditions.
     *
     * @return true if supersaturated
     */
    public boolean isSupersaturated() {
      return supersaturated;
    }
  }

  private CompressorDepositProfile() {
  }

  /**
   * Compute the per-impeller solid-deposition profile between suction and discharge conditions.
   *
   * @param inlet compressor suction stream (defines the composition and suction T/P)
   * @param dischargeTemperatureC discharge temperature in Celsius
   * @param dischargePressureBara discharge pressure in bara
   * @param numberOfImpellers number of impellers (stages) to resolve (at least 1)
   * @param solidComponent name of the solid-forming component, for example "S8"
   * @return one {@link StageDeposit} per impeller from suction (stage 1) to discharge
   */
  public static List<StageDeposit> compute(StreamInterface inlet, double dischargeTemperatureC,
      double dischargePressureBara, int numberOfImpellers, String solidComponent) {
    List<StageDeposit> profile = new ArrayList<>();
    if (inlet == null || inlet.getThermoSystem() == null) {
      return profile;
    }
    int stages = Math.max(1, numberOfImpellers);
    double p1 = inlet.getPressure("bara");
    double t1 = inlet.getTemperature("C");
    for (int i = 1; i <= stages; i++) {
      double frac = stages == 1 ? 1.0 : (double) i / stages;
      double pStage = p1 * Math.pow(dischargePressureBara / p1, frac);
      double tStage = t1 + (dischargeTemperatureC - t1) * frac;
      double solidRate = 0.0;
      boolean supersat = false;
      try {
        SystemInterface sys = inlet.getThermoSystem().clone();
        if (sys.getComponent(solidComponent) != null) {
          sys.setTemperature(tStage, "C");
          sys.setPressure(pStage, "bara");
          sys.setMultiPhaseCheck(true);
          sys.setSolidPhaseCheck(solidComponent);
          ThermodynamicOperations ops = new ThermodynamicOperations(sys);
          ops.TPSolidflash();
          if (sys.hasPhaseType("solid")) {
            supersat = true;
            solidRate = sys.getPhaseOfType("solid").getFlowRate("kg/hr");
          }
        }
      } catch (Exception e) {
        logger.warn("Stage {} solid flash failed: {}", i, e.getMessage());
      }
      profile.add(new StageDeposit(i, pStage, tStage, solidRate, supersat));
    }
    return profile;
  }

  /**
   * Compute the per-impeller solid-deposition profile for a compressor that has been run.
   *
   * @param compressor a compressor whose inlet stream and outlet temperature/pressure define the path (run the
   * compressor before calling)
   * @param numberOfImpellers number of impellers (stages) to resolve (at least 1)
   * @param solidComponent name of the solid-forming component, for example "S8"
   * @return one {@link StageDeposit} per impeller from suction to discharge
   */
  public static List<StageDeposit> compute(Compressor compressor, int numberOfImpellers, String solidComponent) {
    if (compressor == null || compressor.getInletStream() == null || compressor.getOutletStream() == null) {
      return new ArrayList<>();
    }
    double outT = compressor.getOutletStream().getTemperature("C");
    double outP = compressor.getOutletStream().getPressure("bara");
    return compute(compressor.getInletStream(), outT, outP, numberOfImpellers, solidComponent);
  }

  /**
   * Index (1-based) of the impeller with the highest solid drop-out (the worst fouling stage).
   *
   * @param profile per-impeller profile from {@link #compute}
   * @return worst stage number, or -1 if no stage is supersaturated
   */
  public static int worstStage(List<StageDeposit> profile) {
    int worst = -1;
    double max = 0.0;
    for (StageDeposit s : profile) {
      if (s.getSolidRateKgHr() > max) {
        max = s.getSolidRateKgHr();
        worst = s.getStage();
      }
    }
    return worst;
  }

  /**
   * Compute the per-impeller solid-deposition profile using the compressor's rigorous per-step fluid states captured by
   * its {@link CompressorPropertyProfile}.
   *
   * <p>
   * Unlike {@link #compute(Compressor, int, String)}, which interpolates the stage pressures and temperatures
   * (screening), this method flashes the <em>actual</em> fluid state that the compressor computed along the polytropic
   * path, so each impeller uses the true local pressure, temperature and composition. Enable it before running the
   * compressor:
   * </p>
   *
   * <pre>{@code
   * compressor.setPolytropicMethod("detailed");
   * compressor.getPropertyProfile().setActive(true);
   * compressor.run();
   * List<StageDeposit> profile = CompressorDepositProfile.computeFromPropertyProfile(compressor, 5, "S8");
   * }</pre>
   *
   * <p>
   * The captured steps ({@code numberOfCompressorCalcSteps}, default 40) are mapped onto the requested number of
   * impellers. If the property profile is not active or empty this falls back to the screening
   * {@link #compute(Compressor, int, String)}.
   * </p>
   *
   * @param compressor a compressor run with the detailed polytropic method and an active property profile
   * @param numberOfImpellers number of impellers (stages) to resolve (at least 1)
   * @param solidComponent name of the solid-forming component, for example "S8"
   * @return one {@link StageDeposit} per impeller from suction to discharge
   */
  public static List<StageDeposit> computeFromPropertyProfile(Compressor compressor, int numberOfImpellers,
      String solidComponent) {
    if (compressor == null) {
      return new ArrayList<>();
    }
    CompressorPropertyProfile pp = compressor.getPropertyProfile();
    if (pp == null || !pp.isActive() || pp.getFluid() == null || pp.getFluid().isEmpty()) {
      return compute(compressor, numberOfImpellers, solidComponent);
    }
    List<SystemInterface> steps = pp.getFluid();
    int nSteps = steps.size();
    int stages = Math.max(1, numberOfImpellers);
    List<StageDeposit> profile = new ArrayList<>();
    for (int i = 1; i <= stages; i++) {
      int idx = (int) Math.round((double) i / stages * nSteps) - 1;
      idx = Math.max(0, Math.min(nSteps - 1, idx));
      SystemInterface stepFluid = steps.get(idx);
      double pStage = stepFluid.getPressure("bara");
      double tStage = stepFluid.getTemperature("C");
      double solidRate = 0.0;
      boolean supersat = false;
      try {
        SystemInterface sys = stepFluid.clone();
        if (sys.getComponent(solidComponent) != null) {
          sys.setMultiPhaseCheck(true);
          sys.setSolidPhaseCheck(solidComponent);
          ThermodynamicOperations ops = new ThermodynamicOperations(sys);
          ops.TPSolidflash();
          if (sys.hasPhaseType("solid")) {
            supersat = true;
            solidRate = sys.getPhaseOfType("solid").getFlowRate("kg/hr");
          }
        }
      } catch (Exception e) {
        logger.warn("Stage {} property-profile solid flash failed: {}", i, e.getMessage());
      }
      profile.add(new StageDeposit(i, pStage, tStage, solidRate, supersat));
    }
    return profile;
  }
}
