package neqsim.process.equipment.adsorber;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Multi-bed Pressure-Swing-Adsorption (PSA) cascade for continuous H2 purification.
 *
 * <p>
 * Industrial H2-PSA skids never use a single bed: a Skarstrom-style 2-bed unit gives only batch
 * production, while modern hydrogen plants deploy 4-, 6-, 8-, 10- or 12-bed cascades to (a) deliver
 * continuous product flow and (b) recover more H2 via co-current pressure-equalisation steps.
 * </p>
 *
 * <p>
 * This class is a thin time-averaged wrapper around a template {@link PressureSwingAdsorptionBed}:
 * </p>
 * <ol>
 * <li>The feed stream is split equally among {@code numberOfBeds} identical beds.</li>
 * <li>At cyclic steady state one bed is always in adsorption — so the product flow rate equals the
 * feed flow rate scaled by the cascade-level recovery.</li>
 * <li>Recovery improves with bed count because more beds allow more pressure-equalisation steps.
 * The cascade applies a configuration-dependent uplift on top of the single-bed recovery target;
 * see {@link CascadeConfiguration} for the bed-count → number-of-equalisations mapping.</li>
 * <li>Purity is taken from the single-bed simulation — equalisation primarily affects recovery
 * (less H2 vented with tail), not the product purity which is set by sorbent selectivity.</li>
 * </ol>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Yang, R.T. <em>Adsorbents: Fundamentals and Applications</em>. Wiley, 2003 — ch. 7.</li>
 * <li>Ruthven, D.M., Farooq, S., Knaebel, K.S. <em>Pressure Swing Adsorption</em>. VCH, 1994.</li>
 * <li>Sircar, S. "Pressure swing adsorption". <em>Ind. Eng. Chem. Res.</em> 41 (2002)
 * 1389-1392.</li>
 * <li>Voss, C. "Applications of pressure swing adsorption technology". <em>Adsorption</em> 11
 * (2005) 527-529.</li>
 * </ul>
 *
 * <p>
 * Provides one outlet stream (the cycle-averaged H2 product) plus a {@code tailGasStream} accessor
 * for the off-gas fed to the SMR furnace fuel system.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class PSACascade extends TwoPortEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger for this class. */
  private static final Logger logger = LogManager.getLogger(PSACascade.class);

  /**
   * Standard industrial PSA cascade configurations and their recovery-uplift maps.
   *
   * <p>
   * Recovery uplift is added to the per-bed recovery target to model the additional H2 captured
   * back into the product during pressure-equalisation steps. Public data on industrial PSA skids
   * (Linde, Air Products, Air Liquide) and the references above support the following bracketing:
   * </p>
   *
   * <table>
   * <caption>Bed-count -&gt; pressure-equalisations and cycle-averaged recovery uplift</caption>
   * <tr>
   * <th>Beds</th>
   * <th>Pressure equalisations</th>
   * <th>Recovery uplift</th>
   * </tr>
   * <tr>
   * <td>2</td>
   * <td>0</td>
   * <td>0.00</td>
   * </tr>
   * <tr>
   * <td>4</td>
   * <td>1</td>
   * <td>+0.05</td>
   * </tr>
   * <tr>
   * <td>6</td>
   * <td>2</td>
   * <td>+0.08</td>
   * </tr>
   * <tr>
   * <td>8</td>
   * <td>3</td>
   * <td>+0.10</td>
   * </tr>
   * <tr>
   * <td>10</td>
   * <td>4</td>
   * <td>+0.11</td>
   * </tr>
   * <tr>
   * <td>12</td>
   * <td>5</td>
   * <td>+0.12</td>
   * </tr>
   * </table>
   *
   * <p>
   * Cycle-averaged recovery is capped at 0.93 (no industrial unit exceeds ~93% on a wet shifted
   * syngas feed).
   * </p>
   */
  public enum CascadeConfiguration {
    /** Two-bed Skarstrom cycle — batch operation only, no equalisations. */
    BEDS_2(2, 0, 0.00),
    /** Four-bed cycle with 1 PEQ step — entry-level continuous operation. */
    BEDS_4(4, 1, 0.05),
    /** Six-bed cycle with 2 PEQ steps — common SMR-plant configuration. */
    BEDS_6(6, 2, 0.08),
    /** Eight-bed cycle with 3 PEQ steps — high-recovery configuration. */
    BEDS_8(8, 3, 0.10),
    /** Ten-bed cycle with 4 PEQ steps — large refinery / blue-H2 plants. */
    BEDS_10(10, 4, 0.11),
    /** Twelve-bed cycle with 5 PEQ steps — Polybed-style maximum recovery. */
    BEDS_12(12, 5, 0.12);

    private final int beds;
    private final int equalisations;
    private final double recoveryUplift;

    CascadeConfiguration(int beds, int equalisations, double recoveryUplift) {
      this.beds = beds;
      this.equalisations = equalisations;
      this.recoveryUplift = recoveryUplift;
    }

    /**
     * Get the number of beds in the cascade.
     *
     * @return number of beds
     */
    public int getBeds() {
      return beds;
    }

    /**
     * Get the number of pressure-equalisation steps per cycle.
     *
     * @return number of pressure-equalisation steps
     */
    public int getEqualisations() {
      return equalisations;
    }

    /**
     * Get the recovery uplift added to the single-bed recovery target.
     *
     * @return recovery uplift (dimensionless)
     */
    public double getRecoveryUplift() {
      return recoveryUplift;
    }
  }

  /** Hard ceiling on cascade-level recovery (industry benchmark). */
  private static final double MAX_RECOVERY = 0.93;

  /** Cascade configuration (bed count + equalisations). */
  private CascadeConfiguration configuration = CascadeConfiguration.BEDS_4;

  /** Template bed providing sorbent and per-bed geometry. */
  private PressureSwingAdsorptionBed templateBed;

  /** Cycle time per bed (s). Typical industrial values 180-600 s. */
  private double cycleTime = 300.0;

  /** Per-bed single-bed recovery target (passed to the template bed). */
  private double perBedRecoveryTarget = 0.85;

  /** Tail-gas stream (set by {@link #run(UUID)}). */
  private Stream tailGasStream;

  /** Cascade-level H2 purity from the last run. */
  private double cascadeH2Purity = 0.0;

  /** Cascade-level H2 recovery from the last run. */
  private double cascadeH2Recovery = 0.0;

  // --------------------------------------------------------------------------
  // Constructors
  // --------------------------------------------------------------------------

  /**
   * Construct a PSA cascade with the given name.
   *
   * @param name name of the unit operation
   */
  public PSACascade(String name) {
    super(name);
    this.templateBed = new PressureSwingAdsorptionBed(name + "-bed");
  }

  /**
   * Construct a PSA cascade with the given name and feed stream.
   *
   * @param name name of the unit operation
   * @param inletStream feed stream (typically shifted syngas at 20-30 bara)
   */
  public PSACascade(String name, StreamInterface inletStream) {
    super(name, inletStream);
    this.templateBed = new PressureSwingAdsorptionBed(name + "-bed");
  }

  // --------------------------------------------------------------------------
  // Configuration
  // --------------------------------------------------------------------------

  /**
   * Set the cascade configuration (number of beds + equalisation steps).
   *
   * @param configuration cascade configuration
   */
  public void setConfiguration(CascadeConfiguration configuration) {
    if (configuration == null) {
      throw new IllegalArgumentException("configuration must not be null");
    }
    this.configuration = configuration;
  }

  /**
   * Get the cascade configuration.
   *
   * @return cascade configuration
   */
  public CascadeConfiguration getConfiguration() {
    return configuration;
  }

  /**
   * Get the number of beds in the cascade.
   *
   * @return number of beds
   */
  public int getNumberOfBeds() {
    return configuration.getBeds();
  }

  /**
   * Set the per-cycle time per bed in seconds.
   *
   * @param cycleTime cycle time (s); must be positive
   */
  public void setCycleTime(double cycleTime) {
    if (cycleTime <= 0.0) {
      throw new IllegalArgumentException("cycleTime must be positive, got " + cycleTime);
    }
    this.cycleTime = cycleTime;
  }

  /**
   * Get the per-cycle time per bed.
   *
   * @return cycle time (s)
   */
  public double getCycleTime() {
    return cycleTime;
  }

  /**
   * Set the single-bed recovery target. The cascade uplifts this with the configuration-dependent
   * pressure-equalisation factor (see {@link CascadeConfiguration}).
   *
   * @param target single-bed recovery target (0,1]
   */
  public void setPerBedRecoveryTarget(double target) {
    if (target <= 0.0 || target > 1.0) {
      throw new IllegalArgumentException("perBedRecoveryTarget must be in (0,1], got " + target);
    }
    this.perBedRecoveryTarget = target;
  }

  /**
   * Get the single-bed recovery target.
   *
   * @return single-bed recovery target
   */
  public double getPerBedRecoveryTarget() {
    return perBedRecoveryTarget;
  }

  /**
   * Set sorbent on the template bed (all beds share the same sorbent).
   *
   * @param sorbent sorbent selection
   */
  public void setSorbent(PressureSwingAdsorptionBed.SorbentType sorbent) {
    templateBed.setSorbent(sorbent);
  }

  /**
   * Get the sorbent of the template bed.
   *
   * @return sorbent type
   */
  public PressureSwingAdsorptionBed.SorbentType getSorbent() {
    return templateBed.getSorbent();
  }

  /**
   * Set the per-bed diameter (m).
   *
   * @param diameter bed diameter (m)
   */
  public void setBedDiameter(double diameter) {
    templateBed.setBedDiameter(diameter);
  }

  /**
   * Set the per-bed length (m).
   *
   * @param length bed length (m)
   */
  public void setBedLength(double length) {
    templateBed.setBedLength(length);
  }

  /**
   * Get the template bed used for sorbent and per-bed geometry.
   *
   * @return template PSA bed
   */
  public PressureSwingAdsorptionBed getTemplateBed() {
    return templateBed;
  }

  /**
   * Get the predicted cascade-level recovery.
   *
   * @return cascade recovery, dimensionless (0..1)
   */
  public double getCascadeRecoveryTarget() {
    return Math.min(MAX_RECOVERY, perBedRecoveryTarget + configuration.getRecoveryUplift());
  }

  // --------------------------------------------------------------------------
  // Run
  // --------------------------------------------------------------------------

  /**
   * {@inheritDoc}
   *
   * <p>
   * Runs the template bed at the cascade-level recovery target, then exposes the result on the
   * outlet stream and constructs a separate tail-gas stream.
   * </p>
   *
   * @param id calculation identifier
   */
  @Override
  public void run(UUID id) {
    if (getInletStream() == null) {
      throw new IllegalStateException("PSACascade " + getName() + " has no inlet stream");
    }
    StreamInterface feed = getInletStream();

    // Configure the template bed at the cascade-level recovery target.
    double effectiveRecovery = getCascadeRecoveryTarget();
    templateBed.setRecoveryTarget(effectiveRecovery);
    templateBed.setInletStream(feed);
    templateBed.run(id);

    // Expose the bed product as the cascade outlet.
    StreamInterface bedProduct = templateBed.getOutletStream();
    setOutletStream(bedProduct);

    cascadeH2Purity = templateBed.getH2Purity();
    cascadeH2Recovery = templateBed.getH2Recovery();

    // Build a tail-gas stream from the per-component tail flows.
    tailGasStream = buildTailGasStream(feed);

    if (logger.isDebugEnabled()) {
      logger.debug("PSACascade {} ({} beds): purity={}, recovery={}", getName(),
          configuration.getBeds(), cascadeH2Purity, cascadeH2Recovery);
    }
    setCalculationIdentifier(id);
  }

  /**
   * Build the tail-gas stream by cloning the feed and overwriting the per-component flows with the
   * vented amounts from the template bed.
   *
   * @param feed cascade feed stream
   * @return tail-gas stream at feed temperature and 1 bara (typical blowdown pressure)
   */
  private Stream buildTailGasStream(StreamInterface feed) {
    double[] tailMoles = templateBed.getTailGasMoleFlow();
    if (tailMoles == null) {
      return null;
    }
    SystemInterface tailSys = feed.getThermoSystem().clone();
    // Zero out, then set tail-gas mole flows.
    int n = tailSys.getPhase(0).getNumberOfComponents();
    double totalTail = 0.0;
    for (int i = 0; i < n; i++) {
      totalTail += tailMoles[i];
    }
    if (totalTail <= 0.0) {
      return null;
    }
    for (int i = 0; i < n; i++) {
      double current = tailSys.getPhase(0).getComponent(i).getNumberOfmoles();
      double delta = tailMoles[i] - current;
      if (Math.abs(delta) > 1e-18) {
        tailSys.addComponent(i, delta);
      }
    }
    tailSys.setPressure(1.2, "bara");
    Stream tail = new Stream(getName() + "-tailGas", tailSys);
    tail.run();
    return tail;
  }

  // --------------------------------------------------------------------------
  // Results accessors
  // --------------------------------------------------------------------------

  /**
   * Get the cycle-averaged H2 mole fraction in the cascade product.
   *
   * @return cascade H2 purity (0..1)
   */
  public double getH2Purity() {
    return cascadeH2Purity;
  }

  /**
   * Get the cycle-averaged H2 recovery in the cascade product.
   *
   * @return cascade H2 recovery (0..1)
   */
  public double getH2Recovery() {
    return cascadeH2Recovery;
  }

  /**
   * Get the tail-gas stream (off-gas, typically routed to SMR furnace fuel system). Returns
   * {@code null} until {@link #run(UUID)} has been called.
   *
   * @return tail-gas stream, or null
   */
  public Stream getTailGasStream() {
    return tailGasStream;
  }
}
