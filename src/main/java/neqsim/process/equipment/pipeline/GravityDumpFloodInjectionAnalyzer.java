package neqsim.process.equipment.pipeline;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Screening analyzer for <em>gravity dump-flood</em> seawater injection wells — i.e. pump-less injection where the
 * hydrostatic head of a seawater column from the seabed (or sea surface) down to a strongly depleted reservoir provides
 * the entire driving pressure.
 *
 * <p>
 * The analyzer answers the central flow-assurance questions that arise for this concept:
 * </p>
 * <ul>
 * <li><b>Can gravity head overcome the reservoir back-pressure?</b> It integrates the hydrostatic head of the
 * sub-seabed seawater column and compares it with the (depleted, but rising) reservoir pore pressure.</li>
 * <li><b>Where does the excess head have to be dissipated?</b> If the column head exceeds the reservoir pressure, the
 * required wellhead (seabed) pressure to hold the well liquid-full would be negative. The well then cannot be throttled
 * at the wellhead: the upper tubing partially empties and a low-pressure vapour cavity forms. The analyzer locates the
 * vapour-cavity onset depth and reports the down-hole back-pressure that a deep choke / ICD / tail-pipe must absorb
 * instead.</li>
 * <li><b>Will a wellhead choke cavitate?</b> It evaluates the ISA-75 / IEC 60534 cavitation index (sigma) for a
 * hypothetical wellhead choke taking the full pressure drop, using the true seawater vapour pressure (a few mbar —
 * <em>not</em> 0.01 bar of liquid).</li>
 * <li><b>Could friction alone dissipate the head?</b> It sizes the tail-pipe inner diameter whose frictional pressure
 * drop at the design rate equals the excess head.</li>
 * </ul>
 *
 * <p>
 * Sign convention: flow is downward (injection). Going downward, gravity adds pressure (\(+\rho g L\)) and friction
 * subtracts pressure in the flow direction (\(-\Delta p_f\)). The steady sandface pressure is therefore
 * </p>
 *
 * <pre>
 * p_sandface = p_wellhead + rho * g * L - dp_friction
 * </pre>
 *
 * <p>
 * so the wellhead pressure required to land the sandface on the reservoir pressure is
 * </p>
 *
 * <pre>
 * p_wellhead_required = p_res + dp_friction - rho * g * L
 * </pre>
 *
 * <p>
 * A negative value flags a free-falling (vapour-capped) column.
 * </p>
 *
 * <p>
 * Seawater density, viscosity and vapour pressure may be supplied directly via
 * {@link #setSeawaterProperties(double, double, double)} or auto-computed from a NeqSim fluid via
 * {@link #computePropertiesFromFluid(SystemInterface)}.
 * </p>
 *
 * <p>
 * Standards informational: ISA-75.01.01 / IEC 60534-2-1 (control-valve cavitation index), API RP 14E (erosional
 * velocity), Joukowsky (1900) surge equation for transient screening done elsewhere.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * GravityDumpFloodInjectionAnalyzer a = new GravityDumpFloodInjectionAnalyzer("Verdande WI");
 * a.setWaterDepth(370.0);
 * a.setReservoirDepthTvd(3000.0);
 * a.setReservoirPressure(200.0);
 * a.setTubingId(0.1524);
 * a.setSeabedTemperature(2.5);
 * a.setInjectionRate(1500.0);
 * a.setSeawaterProperties(1012.9, 1.6, 0.00751);
 * a.analyze();
 * boolean freeFall = a.isFreeFalling();
 * double cavityDepth = a.getVapourCavityDepthBelowSeabed();
 * Map&lt;String, Object&gt; results = a.getResults();
 * </pre>
 *
 * @author NeqSim
 * @version 1.0
 */
public class GravityDumpFloodInjectionAnalyzer implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Standard gravitational acceleration [m/s^2]. */
  public static final double G = 9.80665;

  /** Default ISA-75 cavitation index threshold below which severe cavitation is flagged. */
  public static final double DEFAULT_CAVITATION_THRESHOLD = 1.5;

  // ─── Identification ─────────────────────────────────────
  private final String name;

  // ─── Geometry ───────────────────────────────────────────
  /** Water depth (sea surface to seabed) [m]. */
  private double waterDepthM = 370.0;
  /** Reservoir (sandface) depth below mean sea level [m TVD]. */
  private double reservoirDepthTvdM = 3000.0;
  /** Tubing inner diameter [m]. */
  private double tubingIdM = 0.1524;
  /** Pipe absolute roughness [m]. */
  private double roughnessM = 4.5e-5;

  // ─── Operating conditions ───────────────────────────────
  /** Reservoir pore (sandface) pressure [bara]. */
  private double reservoirPressureBara = 200.0;
  /** Seabed / injection-water temperature [C]. */
  private double seabedTemperatureC = 2.5;
  /** Target injection rate at standard conditions [Sm3/day]. */
  private double injectionRateSm3PerDay = 1500.0;
  /** Pressure available at the top of the tubing at the seabed (wellhead) [bara]. */
  private double wellheadSupplyPressureBara = Double.NaN;
  /** Cavitation index threshold for the wellhead-choke screen. */
  private double cavitationThreshold = DEFAULT_CAVITATION_THRESHOLD;

  // ─── Seawater properties ────────────────────────────────
  /** Seawater density [kg/m3]. */
  private double seawaterDensityKgM3 = 1025.0;
  /** Seawater viscosity [cP]. */
  private double seawaterViscosityCp = 1.6;
  /** Seawater (true) vapour pressure at seabed temperature [bara]. */
  private double vapourPressureBara = 0.0075;
  /** Whether seawater properties have been supplied / computed. */
  private boolean propertiesSet = false;

  // ─── Results ────────────────────────────────────────────
  private final Map<String, Object> results = new LinkedHashMap<String, Object>();
  private final List<String> recommendations = new ArrayList<String>();
  private boolean analysisComplete = false;

  private double subSeabedColumnHeadBar = 0.0;
  private double staticSandfacePressureBara = 0.0;
  private double gravityOverpressureBar = 0.0;
  private double frictionPressureDropBar = 0.0;
  private double wellheadPressureRequiredBara = 0.0;
  private boolean freeFalling = false;
  private double vapourCavityDepthBelowSeabedM = 0.0;
  private double downholeBackPressureRequiredBar = 0.0;
  private double frictionTailpipeIdMm = 0.0;
  private double cavitationIndex = 0.0;

  /**
   * Constructs a gravity dump-flood injection analyzer.
   *
   * @param name well name / tag
   */
  public GravityDumpFloodInjectionAnalyzer(String name) {
    this.name = name == null ? "GravityDumpFloodWell" : name;
  }

  // ─── Setters ────────────────────────────────────────────

  /**
   * Sets the water depth (sea surface to seabed).
   *
   * @param m water depth [m], must be positive
   */
  public void setWaterDepth(double m) {
    this.waterDepthM = m;
  }

  /**
   * Sets the reservoir (sandface) true vertical depth below mean sea level.
   *
   * @param mTvd reservoir depth [m TVD MSL], must exceed the water depth
   */
  public void setReservoirDepthTvd(double mTvd) {
    this.reservoirDepthTvdM = mTvd;
  }

  /**
   * Sets the tubing inner diameter.
   *
   * @param m tubing ID [m], must be positive
   */
  public void setTubingId(double m) {
    this.tubingIdM = m;
  }

  /**
   * Sets the pipe absolute roughness used in the friction calculation.
   *
   * @param m roughness [m], must be non-negative
   */
  public void setRoughness(double m) {
    this.roughnessM = m;
  }

  /**
   * Sets the reservoir pore (sandface) pressure.
   *
   * @param bara reservoir pressure [bara], must be positive
   */
  public void setReservoirPressure(double bara) {
    this.reservoirPressureBara = bara;
  }

  /**
   * Sets the seabed / injection-water temperature.
   *
   * @param c temperature [C]
   */
  public void setSeabedTemperature(double c) {
    this.seabedTemperatureC = c;
  }

  /**
   * Sets the target injection rate at standard conditions.
   *
   * @param sm3PerDay injection rate [Sm3/day], must be non-negative
   */
  public void setInjectionRate(double sm3PerDay) {
    this.injectionRateSm3PerDay = sm3PerDay;
  }

  /**
   * Overrides the pressure available at the top of the tubing (wellhead) at the seabed. When not set, the analyzer
   * reports the wellhead pressure that would be required, and the down-hole back-pressure to absorb is computed
   * assuming a low (≈ vapour-pressure) wellhead pressure.
   *
   * @param bara wellhead supply pressure [bara]
   */
  public void setWellheadSupplyPressure(double bara) {
    this.wellheadSupplyPressureBara = bara;
  }

  /**
   * Sets the cavitation-index threshold for the wellhead-choke screen.
   *
   * @param sigma threshold (typical 1.5–2.0)
   */
  public void setCavitationThreshold(double sigma) {
    this.cavitationThreshold = sigma;
  }

  /**
   * Supplies seawater physical properties directly (bypasses the NeqSim fluid calculation).
   *
   * @param densityKgM3 seawater density [kg/m3], must be positive
   * @param viscosityCp seawater dynamic viscosity [cP], must be positive
   * @param vapourPressureBara true seawater vapour pressure at seabed temperature [bara]
   */
  public void setSeawaterProperties(double densityKgM3, double viscosityCp, double vapourPressureBara) {
    this.seawaterDensityKgM3 = densityKgM3;
    this.seawaterViscosityCp = viscosityCp;
    this.vapourPressureBara = vapourPressureBara;
    this.propertiesSet = true;
  }

  /**
   * Auto-computes seawater density, viscosity and vapour pressure from a NeqSim fluid at seabed conditions. The fluid
   * is cloned; the original is not modified. On any thermodynamic failure the previously set (or default) properties
   * are retained and a note is added to the recommendations.
   *
   * @param fluid seawater fluid (e.g. SystemElectrolyteCPAstatoil with major ions)
   */
  public void computePropertiesFromFluid(SystemInterface fluid) {
    if (fluid == null) {
      recommendations.add("computePropertiesFromFluid: fluid was null; using supplied properties.");
      return;
    }
    try {
      SystemInterface f = fluid.clone();
      double seabedHydrostaticBara = 1.01325 + seawaterDensityKgM3 * G * waterDepthM / 1.0e5;
      f.setTemperature(seabedTemperatureC + 273.15);
      f.setPressure(seabedHydrostaticBara);
      ThermodynamicOperations ops = new ThermodynamicOperations(f);
      ops.TPflash();
      f.initProperties();
      int aq = 0;
      this.seawaterDensityKgM3 = f.getPhase(aq).getDensity("kg/m3");
      this.seawaterViscosityCp = f.getPhase(aq).getViscosity("cP");
      this.propertiesSet = true;
    } catch (Exception ex) {
      recommendations.add("computePropertiesFromFluid: density/viscosity flash failed (" + ex.getMessage()
	  + "); using supplied properties.");
    }
    // Vapour pressure: use a robust pure-water CPA bubble point with a salinity activity reduction.
    try {
      SystemInterface w = new neqsim.thermo.system.SystemSrkCPAstatoil(seabedTemperatureC + 273.15, 1.0);
      w.addComponent("water", 1.0);
      w.setMixingRule(10);
      ThermodynamicOperations wops = new ThermodynamicOperations(w);
      wops.bubblePointPressureFlash(false);
      double pvapPure = w.getPressure();
      // Raoult-type activity reduction for ~3.5 wt% salts (x_water ≈ 0.98).
      this.vapourPressureBara = 0.98 * pvapPure;
    } catch (Exception ex) {
      recommendations.add("computePropertiesFromFluid: vapour-pressure flash failed (" + ex.getMessage()
	  + "); using supplied vapour pressure.");
    }
  }

  // ─── Core analysis ──────────────────────────────────────

  /**
   * Runs the full gravity dump-flood screening. Populates the results map, typed getters, and the recommendations list.
   *
   * @return this analyzer (for chaining)
   */
  public GravityDumpFloodInjectionAnalyzer analyze() {
    validateInputs();
    recommendations.clear();

    double subSeabedLengthM = reservoirDepthTvdM - waterDepthM;
    double rho = seawaterDensityKgM3;

    // Hydrostatic head of the sub-seabed seawater column [bar].
    subSeabedColumnHeadBar = rho * G * subSeabedLengthM / 1.0e5;

    // Wellhead (seabed) pressure from the seawater column above the seabed [bara].
    double seabedHydrostaticBara = 1.01325 + rho * G * waterDepthM / 1.0e5;

    // Friction at the design rate over the sub-seabed length.
    frictionPressureDropBar = frictionDropBar(injectionRateSm3PerDay, tubingIdM, subSeabedLengthM);

    // Static (no-flow) sandface pressure if the well were full of seawater to the sea surface.
    staticSandfacePressureBara = 1.01325 + rho * G * reservoirDepthTvdM / 1.0e5;
    gravityOverpressureBar = staticSandfacePressureBara - reservoirPressureBara;

    // Wellhead pressure required to land the sandface exactly on reservoir pressure (incl. friction).
    // p_sandface = p_wh + rho*g*L - dp_fric => p_wh = p_res + dp_fric - rho*g*L
    wellheadPressureRequiredBara = reservoirPressureBara + frictionPressureDropBar - subSeabedColumnHeadBar;
    freeFalling = wellheadPressureRequiredBara < seabedHydrostaticBara
	|| wellheadPressureRequiredBara < vapourPressureBara;

    // Vapour-cavity onset: liquid-full height that the reservoir can support from below.
    // rho*g*(L - d) + Pvap ≈ p_res => d = L - (p_res - Pvap)/(rho*g)
    double liquidSupportHeightM = (reservoirPressureBara - vapourPressureBara) * 1.0e5 / (rho * G);
    vapourCavityDepthBelowSeabedM = Math.max(0.0, subSeabedLengthM - liquidSupportHeightM);

    // Down-hole back-pressure that a deep choke / ICD / tail-pipe must dissipate.
    double whSupply = Double.isNaN(wellheadSupplyPressureBara) ? vapourPressureBara : wellheadSupplyPressureBara;
    downholeBackPressureRequiredBar = Math.max(0.0,
	whSupply + subSeabedColumnHeadBar - frictionPressureDropBar - reservoirPressureBara);

    // Tail-pipe ID whose friction equals the excess head to dissipate.
    frictionTailpipeIdMm = sizeFrictionTailpipeIdMm(downholeBackPressureRequiredBar, injectionRateSm3PerDay,
	subSeabedLengthM);

    // Wellhead-choke cavitation index for a hypothetical choke taking the full drop.
    // sigma = (p2 - pv) / (p1 - p2), with p1 = seabed supply, p2 = vapour-limited downstream.
    double p1 = seabedHydrostaticBara;
    double p2 = Math.max(vapourPressureBara, 0.0);
    double denom = p1 - p2;
    cavitationIndex = denom > 0.0 ? (p2 - vapourPressureBara) / denom : Double.POSITIVE_INFINITY;

    buildRecommendations();
    populateResults(subSeabedLengthM);
    analysisComplete = true;
    return this;
  }

  /**
   * Validates the geometric and operating inputs.
   *
   * @throws IllegalStateException if the configuration is physically inconsistent
   */
  private void validateInputs() {
    if (reservoirDepthTvdM <= waterDepthM) {
      throw new IllegalStateException(
	  "reservoir depth (" + reservoirDepthTvdM + " m) must exceed water depth (" + waterDepthM + " m)");
    }
    if (tubingIdM <= 0.0) {
      throw new IllegalStateException("tubing ID must be positive");
    }
    if (reservoirPressureBara <= 0.0) {
      throw new IllegalStateException("reservoir pressure must be positive");
    }
    if (!propertiesSet) {
      recommendations
	  .add("Seawater properties not explicitly set; using defaults (rho=1025, mu=1.6 cP, Pvap=0.0075 bara).");
    }
  }

  /**
   * Darcy-Weisbach frictional pressure drop using the explicit Haaland friction factor.
   *
   * @param rateSm3PerDay volumetric rate [Sm3/day] (≈ actual for near-incompressible water)
   * @param idM pipe inner diameter [m]
   * @param lengthM pipe length [m]
   * @return frictional pressure drop [bar]
   */
  private double frictionDropBar(double rateSm3PerDay, double idM, double lengthM) {
    double q = rateSm3PerDay / 86400.0; // m3/s
    double area = Math.PI * idM * idM / 4.0;
    double v = q / area; // m/s
    double rho = seawaterDensityKgM3;
    double mu = seawaterViscosityCp * 1.0e-3; // Pa.s
    double re = rho * v * idM / mu;
    double f;
    if (re < 2300.0 && re > 0.0) {
      f = 64.0 / re;
    } else {
      double relRough = roughnessM / idM;
      // Haaland explicit approximation to Colebrook.
      double term = Math.pow(relRough / 3.7, 1.11) + 6.9 / re;
      double invSqrtF = -1.8 * Math.log10(term);
      f = 1.0 / (invSqrtF * invSqrtF);
    }
    double dpPa = f * (lengthM / idM) * 0.5 * rho * v * v;
    return dpPa / 1.0e5;
  }

  /**
   * Sizes the tail-pipe inner diameter whose friction over the sub-seabed length equals the target back-pressure at the
   * design rate. Solves by bisection on diameter.
   *
   * @param targetBar target frictional dissipation [bar]
   * @param rateSm3PerDay design rate [Sm3/day]
   * @param lengthM available length [m]
   * @return tail-pipe inner diameter [mm], or 0 if no dissipation is required
   */
  private double sizeFrictionTailpipeIdMm(double targetBar, double rateSm3PerDay, double lengthM) {
    if (targetBar <= 0.0 || rateSm3PerDay <= 0.0) {
      return 0.0;
    }
    double lo = 0.003; // 3 mm
    double hi = 0.3; // 300 mm
    for (int i = 0; i < 80; i++) {
      double mid = 0.5 * (lo + hi);
      double dp = frictionDropBar(rateSm3PerDay, mid, lengthM);
      if (dp > targetBar) {
	lo = mid; // too much friction → larger ID
      } else {
	hi = mid; // too little friction → smaller ID
      }
    }
    return 0.5 * (lo + hi) * 1000.0;
  }

  /**
   * Builds the engineering recommendations based on the computed screening.
   */
  private void buildRecommendations() {
    if (freeFalling) {
      recommendations.add("The sub-seabed seawater column head (" + round(subSeabedColumnHeadBar)
	  + " bar) exceeds the reservoir pressure (" + round(reservoirPressureBara)
	  + " bara). The well cannot be controlled by a wellhead/seabed choke — the required " + "wellhead pressure is "
	  + round(wellheadPressureRequiredBara) + " bara (below the seabed hydrostatic / vapour pressure).");
      recommendations.add("The upper tubing will partially empty; a low-pressure vapour cavity " + "forms down to ~"
	  + round(vapourCavityDepthBelowSeabedM) + " m below the seabed (~"
	  + round(waterDepthM + vapourCavityDepthBelowSeabedM) + " m TVD).");
      recommendations.add("Dissipate the excess head DOWN-HOLE near the sandface: a down-hole choke, "
	  + "ICD/AICD completion, or small-ID tail-pipe absorbing ~" + round(downholeBackPressureRequiredBar)
	  + " bar. Friction-only sizing needs an ID of ~" + round(frictionTailpipeIdMm)
	  + " mm (erosion / plugging trade-off — API RP 14E).");
    } else {
      recommendations.add("Gravity head does not over-pressure the reservoir; a wellhead choke can "
	  + "in principle set the rate. Verify cavitation margin (sigma=" + round(cavitationIndex) + ").");
    }
    if (cavitationIndex < cavitationThreshold) {
      recommendations.add("A wellhead choke taking the full drop would cavitate hard (sigma=" + round(cavitationIndex)
	  + " < " + cavitationThreshold
	  + "): severe flashing / flow-induced vibration / erosion. Do not dissipate the head at a "
	  + "single wellhead choke.");
    }
    recommendations.add(
	"True seawater vapour pressure at " + round(seabedTemperatureC) + " C is ~" + round(vapourPressureBara * 1000.0)
	    + " mbar — a near-vacuum vapour, NOT 0.01 bar of liquid. Joule-Thomson cooling of "
	    + "near-incompressible liquid water across a throttle is negligible; icing/hydrate risk only "
	    + "arises locally if flashing occurs and a hydrocarbon source is present.");
    recommendations.add("Gradual near-well plugging raises back-pressure over time and is "
	+ "self-regulating / beneficial; injection is ultimately formation-injectivity limited.");
  }

  /**
   * Populates the results map for handoff / reporting.
   *
   * @param subSeabedLengthM sub-seabed column length [m]
   */
  private void populateResults(double subSeabedLengthM) {
    results.clear();
    results.put("well_name", name);
    results.put("sub_seabed_length_m", round(subSeabedLengthM));
    results.put("seawater_density_kgm3", round(seawaterDensityKgM3));
    results.put("seawater_viscosity_cP", round(seawaterViscosityCp));
    results.put("vapour_pressure_mbar", round(vapourPressureBara * 1000.0));
    results.put("sub_seabed_column_head_bar", round(subSeabedColumnHeadBar));
    results.put("static_sandface_pressure_bara", round(staticSandfacePressureBara));
    results.put("reservoir_pressure_bara", round(reservoirPressureBara));
    results.put("gravity_overpressure_bar", round(gravityOverpressureBar));
    results.put("friction_pressure_drop_bar", round(frictionPressureDropBar));
    results.put("wellhead_pressure_required_bara", round(wellheadPressureRequiredBara));
    results.put("free_falling", freeFalling);
    results.put("vapour_cavity_depth_below_seabed_m", round(vapourCavityDepthBelowSeabedM));
    results.put("vapour_cavity_depth_tvd_m", round(waterDepthM + vapourCavityDepthBelowSeabedM));
    results.put("downhole_back_pressure_required_bar", round(downholeBackPressureRequiredBar));
    results.put("friction_tailpipe_id_mm", round(frictionTailpipeIdMm));
    results.put("wellhead_choke_cavitation_index", round(cavitationIndex));
    results.put("wellhead_choke_cavitates", cavitationIndex < cavitationThreshold);
    results.put("recommendations", new ArrayList<String>(recommendations));
  }

  private double round(double v) {
    if (Double.isNaN(v) || Double.isInfinite(v)) {
      return v;
    }
    return Math.round(v * 100.0) / 100.0;
  }

  // ─── Getters ────────────────────────────────────────────

  /**
   * Returns the well name / tag.
   *
   * @return well name
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the hydrostatic head of the sub-seabed seawater column.
   *
   * @return sub-seabed column head [bar]
   */
  public double getSubSeabedColumnHeadBar() {
    return subSeabedColumnHeadBar;
  }

  /**
   * Returns the static (no-flow) sandface pressure of a full seawater column from the sea surface.
   *
   * @return static sandface pressure [bara]
   */
  public double getStaticSandfacePressureBara() {
    return staticSandfacePressureBara;
  }

  /**
   * Returns the gravity over-pressure relative to the reservoir pressure.
   *
   * @return gravity over-pressure [bar]
   */
  public double getGravityOverpressureBar() {
    return gravityOverpressureBar;
  }

  /**
   * Returns the frictional pressure drop at the design rate over the sub-seabed length.
   *
   * @return friction pressure drop [bar]
   */
  public double getFrictionPressureDropBar() {
    return frictionPressureDropBar;
  }

  /**
   * Returns the wellhead pressure required to land the sandface on the reservoir pressure. A negative value indicates a
   * free-falling / vapour-capped column.
   *
   * @return required wellhead pressure [bara]
   */
  public double getWellheadPressureRequiredBara() {
    return wellheadPressureRequiredBara;
  }

  /**
   * Indicates whether the well is free-falling (vapour-capped) under gravity dump-flood operation.
   *
   * @return true if the column over-pressures the reservoir and the upper tubing empties
   */
  public boolean isFreeFalling() {
    return freeFalling;
  }

  /**
   * Returns the depth below the seabed at which the falling-column vapour cavity onsets.
   *
   * @return vapour-cavity onset depth below seabed [m]
   */
  public double getVapourCavityDepthBelowSeabedM() {
    return vapourCavityDepthBelowSeabedM;
  }

  /**
   * Returns the down-hole back-pressure that a deep choke / ICD / tail-pipe must dissipate.
   *
   * @return required down-hole back-pressure [bar]
   */
  public double getDownholeBackPressureRequiredBar() {
    return downholeBackPressureRequiredBar;
  }

  /**
   * Returns the tail-pipe inner diameter whose friction equals the excess head at the design rate.
   *
   * @return friction tail-pipe inner diameter [mm]
   */
  public double getFrictionTailpipeIdMm() {
    return frictionTailpipeIdMm;
  }

  /**
   * Returns the ISA-75 cavitation index for a hypothetical wellhead choke taking the full drop.
   *
   * @return cavitation index sigma [-]
   */
  public double getCavitationIndex() {
    return cavitationIndex;
  }

  /**
   * Returns the engineering recommendations produced by the screening.
   *
   * @return list of recommendation strings
   */
  public List<String> getRecommendations() {
    return new ArrayList<String>(recommendations);
  }

  /**
   * Returns the full results map.
   *
   * @return results map (defensive copy)
   */
  public Map<String, Object> getResults() {
    return new LinkedHashMap<String, Object>(results);
  }

  /**
   * Indicates whether {@link #analyze()} has completed.
   *
   * @return true if the analysis has run
   */
  public boolean isAnalysisComplete() {
    return analysisComplete;
  }
}
