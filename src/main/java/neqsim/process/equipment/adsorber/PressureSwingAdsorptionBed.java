package neqsim.process.equipment.adsorber;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.interfaceproperties.solidadsorption.IsothermType;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Pressure-Swing-Adsorption (PSA) bed pre-configured for hydrogen purification.
 *
 * <p>
 * Thin specialisation of {@link AdsorptionBed} that picks defaults appropriate for industrial H2
 * purification trains downstream of steam-methane-reforming + water-gas-shift, e.g. the Skarstrom /
 * 4-bed / 6-bed configurations described in Yang (Adsorbents: Fundamentals and Applications, 2003)
 * and Ruthven (Principles of Adsorption and Adsorption Processes, 1984).
 * </p>
 *
 * <p>
 * Hydrogen has a much lower Langmuir affinity than CO, CO2, CH4, N2 on activated carbon and zeolite
 * 13X, so H2 passes through the bed largely unadsorbed while the heavier components are captured.
 * The bed therefore behaves as an inverted separator: the {@link #getOutletStream() outlet stream}
 * is the H2 product, and the adsorbed components are reported as the
 * {@link #getTailGasComposition() tail-gas composition}.
 * </p>
 *
 * <p>
 * Convenience methods:
 * </p>
 * <ul>
 * <li>{@link #setSorbent(SorbentType)} - select a textbook sorbent + bulk density default</li>
 * <li>{@link #setRecoveryTarget(double)} - cycle-averaged H2 recovery factor (0..1)</li>
 * <li>{@link #getH2Purity()} - mole fraction H2 in the product stream</li>
 * <li>{@link #getH2Recovery()} - mole flow H2 in product / mole flow H2 in feed</li>
 * </ul>
 *
 * <p>
 * Only the steady-state, cyclic-steady-state representation is implemented here; cycle-resolved
 * transient simulation is provided by the inherited {@link AdsorptionBed#runTransient runTransient}
 * method together with {@link AdsorptionCycleController}.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class PressureSwingAdsorptionBed extends AdsorptionBed {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger for this class. */
  private static final Logger logger = LogManager.getLogger(PressureSwingAdsorptionBed.class);

  /**
   * Sorbent selection for H2-purification service.
   */
  public enum SorbentType {
    /** Activated carbon — strong CO2/CH4/HC affinity, used as the bulk-removal layer. */
    ACTIVATED_CARBON("AC", 500.0),
    /**
     * Zeolite 13X — used as a polishing layer for N2 and residual CO/CO2 in industrial H2-PSA
     * skids. Higher bulk density than activated carbon.
     */
    ZEOLITE_13X("Zeolite 13X", 700.0);

    private final String material;
    private final double defaultBulkDensity;

    SorbentType(String material, double defaultBulkDensity) {
      this.material = material;
      this.defaultBulkDensity = defaultBulkDensity;
    }

    /**
     * Get the adsorbent-database identifier used by NeqSim's {@code adsorptionparameters} table.
     *
     * @return database identifier (e.g. {@code "AC"} or {@code "Zeolite 13X"})
     */
    public String getMaterial() {
      return material;
    }

    /**
     * Get the typical bulk density of the packed sorbent (kg/m3).
     *
     * @return bulk density in kg/m3
     */
    public double getDefaultBulkDensity() {
      return defaultBulkDensity;
    }
  }

  /** Cycle-averaged H2 recovery factor (0..1). */
  private double recoveryTarget = 0.85;

  /** Cached H2 component index (in feed-stream order). */
  private int hydrogenComponentIndex = -1;

  /** H2 moles in the feed (mol/s), captured during {@link #run(UUID)}. */
  private double feedH2MoleFlow = 0.0;

  /** Hydrogen mole fraction in the product (set by {@link #run(UUID)}). */
  private double h2Purity = 0.0;

  /** Hydrogen recovery, product/feed (set by {@link #run(UUID)}). */
  private double h2Recovery = 0.0;

  /**
   * Tail-gas mole flow per component (mol/s), in feed-stream component order. The tail gas is the
   * difference between feed and product.
   */
  private double[] tailGasMoleFlow;

  /** Sorbent currently selected. */
  private SorbentType sorbent = SorbentType.ACTIVATED_CARBON;

  // --------------------------------------------------------------------------
  // Constructors
  // --------------------------------------------------------------------------

  /**
   * Construct a PSA bed with the given name and Skarstrom-cycle defaults.
   *
   * @param name name of the unit operation
   */
  public PressureSwingAdsorptionBed(String name) {
    super(name);
    applyPsaDefaults();
  }

  /**
   * Construct a PSA bed with the given name, inlet stream, and Skarstrom-cycle defaults.
   *
   * @param name name of the unit operation
   * @param inletStream feed stream (typically shifted syngas at 20-30 bara)
   */
  public PressureSwingAdsorptionBed(String name, StreamInterface inletStream) {
    super(name, inletStream);
    applyPsaDefaults();
  }

  /**
   * Apply typical industrial H2-PSA defaults: Langmuir isotherm, activated carbon, 2 m diameter by
   * 4 m bed height, 35% void fraction, Ergun pressure drop on.
   */
  private void applyPsaDefaults() {
    setIsothermType(IsothermType.LANGMUIR);
    setSorbent(SorbentType.ACTIVATED_CARBON);
    setBedDiameter(2.0);
    setBedLength(4.0);
    setVoidFraction(0.35);
    setCalculatePressureDrop(true);
  }

  // --------------------------------------------------------------------------
  // Configuration
  // --------------------------------------------------------------------------

  /**
   * Select sorbent type. Updates the adsorbent material identifier and the bulk-density default.
   *
   * @param sorbent sorbent selection (activated carbon, zeolite 13X)
   */
  public void setSorbent(SorbentType sorbent) {
    if (sorbent == null) {
      throw new IllegalArgumentException("sorbent must not be null");
    }
    this.sorbent = sorbent;
    setAdsorbentMaterial(sorbent.getMaterial());
    setAdsorbentBulkDensity(sorbent.getDefaultBulkDensity());
  }

  /**
   * Get the currently selected sorbent.
   *
   * @return sorbent enum value
   */
  public SorbentType getSorbent() {
    return sorbent;
  }

  /**
   * Set the cycle-averaged H2 recovery factor. Industrial H2-PSA skids typically achieve 0.75-0.90
   * depending on the number of pressure-equalisation steps.
   *
   * @param recoveryTarget recovery fraction, 0..1
   */
  public void setRecoveryTarget(double recoveryTarget) {
    if (recoveryTarget <= 0.0 || recoveryTarget > 1.0) {
      throw new IllegalArgumentException("recoveryTarget must be in (0,1], got " + recoveryTarget);
    }
    this.recoveryTarget = recoveryTarget;
  }

  /**
   * Get the cycle-averaged H2 recovery target.
   *
   * @return recovery fraction, 0..1
   */
  public double getRecoveryTarget() {
    return recoveryTarget;
  }

  // --------------------------------------------------------------------------
  // Run
  // --------------------------------------------------------------------------

  /**
   * {@inheritDoc}
   *
   * <p>
   * Steps:
   * </p>
   * <ol>
   * <li>Captures feed H2 mole flow.</li>
   * <li>Delegates to {@link AdsorptionBed#run(UUID)} to perform the equilibrium-NTU calculation,
   * which adsorbs heavier components and lets H2 pass through.</li>
   * <li>Applies the cycle-averaged recovery factor by venting a fraction of the H2 in the product
   * back into the tail gas (purge / pressure-equalisation losses).</li>
   * <li>Computes H2 purity, H2 recovery, and the tail-gas mole-flow vector.</li>
   * </ol>
   *
   * @param id calculation identifier
   */
  @Override
  public void run(UUID id) {
    SystemInterface feed = getInletStream().getThermoSystem();
    int numComp = feed.getPhase(0).getNumberOfComponents();
    hydrogenComponentIndex = findHydrogenIndex(feed);

    // Per-component feed mole flow (mol/s) before adsorption.
    double[] feedMoles = new double[numComp];
    for (int i = 0; i < numComp; i++) {
      feedMoles[i] = feed.getPhase(0).getComponent(i).getNumberOfmoles();
    }
    feedH2MoleFlow = hydrogenComponentIndex >= 0 ? feedMoles[hydrogenComponentIndex] : 0.0;

    // Delegate equilibrium-NTU calculation in the base class.
    super.run(id);

    // Apply recovery target — vent (1-recovery) of the H2 from the product into the tail gas.
    SystemInterface product = getOutletStream().getThermoSystem();
    double[] productMoles = new double[numComp];
    for (int i = 0; i < numComp; i++) {
      productMoles[i] = product.getPhase(0).getComponent(i).getNumberOfmoles();
    }

    if (hydrogenComponentIndex >= 0 && feedH2MoleFlow > 0.0) {
      double recoveredH2 = feedH2MoleFlow * recoveryTarget;
      double currentProductH2 = productMoles[hydrogenComponentIndex];
      // Cannot recover more than what the base-class calculation left in the product.
      double targetProductH2 = Math.min(recoveredH2, currentProductH2);
      double h2ToVent = currentProductH2 - targetProductH2;
      if (h2ToVent > 1e-15) {
        product.addComponent(hydrogenComponentIndex, -h2ToVent);
        productMoles[hydrogenComponentIndex] = targetProductH2;
      }
    }

    // Tail gas mole flow per component (mol/s).
    tailGasMoleFlow = new double[numComp];
    double tailGasTotal = 0.0;
    for (int i = 0; i < numComp; i++) {
      double tail = feedMoles[i] - productMoles[i];
      if (tail < 0.0) {
        // Numerical noise; clip to zero.
        tail = 0.0;
      }
      tailGasMoleFlow[i] = tail;
      tailGasTotal += tail;
    }

    // Product totals.
    double productTotal = 0.0;
    for (int i = 0; i < numComp; i++) {
      productTotal += productMoles[i];
    }

    h2Purity = productTotal > 0.0 && hydrogenComponentIndex >= 0
        ? productMoles[hydrogenComponentIndex] / productTotal
        : 0.0;
    h2Recovery = feedH2MoleFlow > 0.0 && hydrogenComponentIndex >= 0
        ? productMoles[hydrogenComponentIndex] / feedH2MoleFlow
        : 0.0;

    if (tailGasTotal <= 0.0) {
      logger.debug("PSA tail-gas total flow is zero — feed may contain only H2.");
    }
  }

  // --------------------------------------------------------------------------
  // Results accessors
  // --------------------------------------------------------------------------

  /**
   * Get the H2 mole fraction in the product stream, computed during the last {@link #run(UUID)}.
   *
   * @return hydrogen purity, mole fraction (0..1)
   */
  public double getH2Purity() {
    return h2Purity;
  }

  /**
   * Get the H2 recovery (product H2 / feed H2), computed during the last {@link #run(UUID)}.
   *
   * @return hydrogen recovery, dimensionless (0..1)
   */
  public double getH2Recovery() {
    return h2Recovery;
  }

  /**
   * Get the tail-gas mole flow per component (mol/s), in feed-stream component order. Returns
   * {@code null} if {@link #run(UUID)} has not been called.
   *
   * @return per-component tail-gas mole flow (mol/s), or null
   */
  public double[] getTailGasMoleFlow() {
    return tailGasMoleFlow == null ? null : tailGasMoleFlow.clone();
  }

  /**
   * Get the tail-gas composition (mole fractions), in feed-stream component order. Returns
   * {@code null} if {@link #run(UUID)} has not been called or the tail gas is empty.
   *
   * @return per-component tail-gas mole fractions (sum to 1), or null
   */
  public double[] getTailGasComposition() {
    if (tailGasMoleFlow == null) {
      return null;
    }
    double total = 0.0;
    for (double m : tailGasMoleFlow) {
      total += m;
    }
    if (total <= 0.0) {
      return null;
    }
    double[] frac = new double[tailGasMoleFlow.length];
    for (int i = 0; i < tailGasMoleFlow.length; i++) {
      frac[i] = tailGasMoleFlow[i] / total;
    }
    return frac;
  }

  /**
   * Find the index of hydrogen in the system component list. Recognises common naming variants.
   *
   * @param system thermo system to inspect
   * @return component index of hydrogen, or -1 if not present
   */
  private static int findHydrogenIndex(SystemInterface system) {
    int n = system.getPhase(0).getNumberOfComponents();
    for (int i = 0; i < n; i++) {
      String name = system.getPhase(0).getComponent(i).getComponentName();
      if (name == null) {
        continue;
      }
      String lower = name.toLowerCase();
      if (lower.equals("hydrogen") || lower.equals("h2")) {
        return i;
      }
    }
    return -1;
  }
}
