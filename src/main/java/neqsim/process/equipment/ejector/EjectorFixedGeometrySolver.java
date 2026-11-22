package neqsim.process.equipment.ejector;

import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.PhysicalPropertyType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Fixed-geometry (off-design) solver for gas ejectors.
 *
 * <p>
 * The geometry is given by the nozzle throat area and the constant-area mixing section area. The motive mass flow is
 * the choked flow of the nozzle, the entrained flow follows from the one-dimensional constant-pressure mixing model
 * (Keenan et al. 1950, ESDU 86030) with nozzle, suction, mixing and diffuser efficiencies, and the discharge pressure
 * follows from the momentum and energy balances. Real-gas states are obtained with PS and PH flashes.
 * </p>
 *
 * <ol>
 * <li>Motive flow: maximum of the mass flux rho v over the throat pressure (isentropic efficiency of the nozzle) times
 * the throat area.</li>
 * <li>At a mixing pressure the primary jet occupies A_p = m_p / (rho_p v_p); the entrained stream gets the remaining
 * mixing area and accelerates from the suction pressure with the suction nozzle efficiency. The maximum over the mixing
 * pressure is the critical (double-choked) entrainment.</li>
 * <li>Momentum balance with the mixing efficiency, energy balance for the static mixed state.</li>
 * <li>The diffuser recovers diffuser-efficiency times the kinetic energy of the mixed stream. The critical back
 * pressure is reached by isentropic compression from the mixed static state to that enthalpy. For a discharge pressure
 * above it the mixing pressure rises until the recoverable pressure equals the discharge pressure (entrainment falls);
 * if this is not possible at any entrainment the ejector is in breakdown.</li>
 * </ol>
 *
 * <p>
 * The ejector entrains vapour only; for a two-phase suction or motive stream the vapour phase is used.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class EjectorFixedGeometrySolver {
  private static final Logger logger = LogManager.getLogger(EjectorFixedGeometrySolver.class);

  private static final double GOLDEN = (Math.sqrt(5.0) - 1.0) / 2.0;

  private final double etaNozzle;
  private final double etaSuction;
  private final double etaMixing;
  private final double etaDiffuser;
  private final Map<String, double[]> expansionCache = new HashMap<String, double[]>();
  private int flashCount = 0;

  /**
   * Unit-mass-flow (1 kg/s) vapour fluid and the vapour mass fraction of the stream it was taken from.
   *
   * @author esol
   * @version 1.0
   */
  public static final class UnitFluid {
    private final SystemInterface fluid;
    private final double vaporMassFraction;

    /**
     * Creates a unit fluid.
     *
     * @param fluid fluid scaled to 1 kg/s
     * @param vaporMassFraction vapour mass fraction of the source stream
     */
    UnitFluid(SystemInterface fluid, double vaporMassFraction) {
      this.fluid = fluid;
      this.vaporMassFraction = vaporMassFraction;
    }

    /**
     * Gets the unit fluid.
     *
     * @return fluid scaled to 1 kg/s
     */
    public SystemInterface getFluid() {
      return fluid;
    }

    /**
     * Gets the vapour mass fraction of the source stream.
     *
     * @return mass fraction between 0 and 1
     */
    public double getVaporMassFraction() {
      return vaporMassFraction;
    }
  }

  /**
   * Creates a solver with the given efficiencies.
   *
   * @param etaNozzle isentropic efficiency of the motive nozzle
   * @param etaSuction isentropic efficiency of the suction nozzle
   * @param etaMixing momentum-transfer efficiency of the mixing section
   * @param etaDiffuser pressure-recovery efficiency of the diffuser
   */
  public EjectorFixedGeometrySolver(double etaNozzle, double etaSuction, double etaMixing, double etaDiffuser) {
    this.etaNozzle = etaNozzle;
    this.etaSuction = etaSuction;
    this.etaMixing = etaMixing;
    this.etaDiffuser = etaDiffuser;
  }

  /**
   * Gets the number of thermodynamic flashes used so far.
   *
   * @return flash count
   */
  public int getFlashCount() {
    return flashCount;
  }

  /**
   * Creates a vapour-phase unit fluid (1 kg/s) of a stream fluid, optionally at a new pressure (temperature kept).
   *
   * @param fluid stream fluid
   * @param pressureBara pressure in bara, or NaN to keep the pressure of the fluid
   * @return unit fluid and vapour mass fraction of the feed
   * @throws IllegalArgumentException if the fluid has no vapour phase
   */
  public static UnitFluid vaporUnitFluid(SystemInterface fluid, double pressureBara) {
    SystemInterface f = fluid.clone();
    if (!Double.isNaN(pressureBara)) {
      f.setPressure(pressureBara, "bara");
    }
    new ThermodynamicOperations(f).TPflash();
    f.init(3);
    double massFraction = 1.0;
    SystemInterface g = f;
    if (f.getNumberOfPhases() > 1) {
      if (!f.hasPhaseType("gas")) {
        throw new IllegalArgumentException("ejector stream has no vapour phase");
      }
      double gasMass = f.getPhase("gas").getFlowRate("kg/hr");
      double total = f.getFlowRate("kg/hr");
      massFraction = total > 0.0 ? Math.min(Math.max(gasMass / total, 0.0), 1.0) : 1.0;
      g = f.phaseToSystem("gas");
    }
    SystemInterface u = g.clone();
    u.setTotalFlowRate(1.0, "kg/sec");
    new ThermodynamicOperations(u).TPflash();
    u.init(3);
    return new UnitFluid(u, massFraction);
  }

  /**
   * Specific enthalpy of a fluid.
   *
   * @param f fluid
   * @return enthalpy in J/kg
   */
  private static double enthalpy(SystemInterface f) {
    return f.getEnthalpy("J/kg");
  }

  /**
   * Mass density of a fluid after refreshing the density only.
   *
   * @param f fluid
   * @return density in kg/m3 (at least 1e-6)
   */
  private static double density(SystemInterface f) {
    f.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
    return Math.max(f.getDensity("kg/m3"), 1.0e-6);
  }

  /**
   * Vapour (mole) fraction of a flashed fluid.
   *
   * @param f flashed fluid
   * @return vapour fraction, NaN if it cannot be determined
   */
  private static double vaporFraction(SystemInterface f) {
    try {
      if (f.hasPhaseType("gas")) {
        return f.getPhase("gas").getBeta();
      }
    } catch (RuntimeException e) {
      logger.debug("vapour fraction not available: {}", e.getMessage());
    }
    return f.getNumberOfPhases() == 1 ? 1.0 : Double.NaN;
  }

  /**
   * Specific enthalpy of the isentropic state of a unit fluid at a pressure. If the PS flash fails the ideal-gas
   * isentrope with the real kappa is used for the temperature.
   *
   * @param f unit fluid
   * @param pressureBara pressure in bara
   * @return enthalpy in J/kg
   */
  double isentropicEnthalpy(SystemInterface f, double pressureBara) {
    flashCount++;
    try {
      SystemInterface g = f.clone();
      double s = f.getEntropy();
      g.setPressure(pressureBara, "bara");
      new ThermodynamicOperations(g).PSflash(s);
      g.init(3);
      double h = enthalpy(g);
      if (!Double.isNaN(h)) {
        return h;
      }
    } catch (RuntimeException e) {
      logger.debug("PS flash failed, using ideal-gas isentrope: {}", e.getMessage());
    }
    double kappa = Math.min(Math.max(f.getKappa(), 1.05), 1.8);
    double t2 = f.getTemperature() * Math.pow(pressureBara / f.getPressure(), (kappa - 1.0) / kappa);
    SystemInterface g = f.clone();
    g.setTemperature(t2);
    g.setPressure(pressureBara, "bara");
    new ThermodynamicOperations(g).TPflash();
    g.init(3);
    return enthalpy(g);
  }

  /**
   * Clone of a fluid flashed at a pressure and specific enthalpy.
   *
   * @param f fluid
   * @param pressureBara pressure in bara
   * @param h specific enthalpy in J/kg
   * @return flashed clone
   */
  private SystemInterface flashPH(SystemInterface f, double pressureBara, double h) {
    flashCount++;
    SystemInterface g = f.clone();
    g.setPressure(pressureBara, "bara");
    new ThermodynamicOperations(g).PHflash(h, "J/kg");
    g.init(3);
    return g;
  }

  /**
   * Expansion of a unit fluid at rest to a pressure with an isentropic efficiency (cached per call of solvePoint).
   *
   * @param f0 unit fluid at rest
   * @param pressureBara target pressure in bara
   * @param eta isentropic efficiency
   * @param tag cache tag
   * @return array {h J/kg, velocity m/s, density kg/m3, temperature C, vapour fraction}
   */
  private double[] expand(SystemInterface f0, double pressureBara, double eta, String tag) {
    String key = tag + "@" + Math.round(pressureBara * 1.0e5);
    double[] cached = expansionCache.get(key);
    if (cached != null) {
      return cached;
    }
    double h0 = enthalpy(f0);
    double h = h0 - eta * (h0 - isentropicEnthalpy(f0, pressureBara));
    SystemInterface g = flashPH(f0, pressureBara, h);
    double v = Math.sqrt(Math.max(2.0 * (h0 - h), 0.0));
    double[] out = new double[] {h, v, density(g), g.getTemperature("C"), vaporFraction(g)};
    expansionCache.put(key, out);
    return out;
  }

  /**
   * Maximum mass flux of the motive nozzle (golden-section search on the throat pressure).
   *
   * @param f0 motive unit fluid at rest
   * @param p0 stagnation pressure in bara
   * @return array {mass flux kg/m2s, throat pressure bara}
   */
  double[] chokedFlux(SystemInterface f0, double p0) {
    double a = 0.15 * p0;
    double b = 0.98 * p0;
    double c = b - GOLDEN * (b - a);
    double d = a + GOLDEN * (b - a);
    double gc = flux(f0, c);
    double gd = flux(f0, d);
    for (int i = 0; i < 14; i++) {
      if (gc > gd) {
        b = d;
        d = c;
        gd = gc;
        c = b - GOLDEN * (b - a);
        gc = flux(f0, c);
      } else {
        a = c;
        c = d;
        gc = gd;
        d = a + GOLDEN * (b - a);
        gd = flux(f0, d);
      }
    }
    double p = 0.5 * (a + b);
    return new double[] {flux(f0, p), p};
  }

  /**
   * Mass flux rho v of the motive nozzle at a throat pressure.
   *
   * @param f0 motive unit fluid
   * @param pressureBara throat pressure in bara
   * @return mass flux in kg/m2s
   */
  private double flux(SystemInterface f0, double pressureBara) {
    double[] e = expand(f0, pressureBara, etaNozzle, "p");
    return e[2] * e[1];
  }

  /**
   * Entrained mass flow at a mixing pressure.
   *
   * @param fp0 motive unit fluid
   * @param fs0 suction unit fluid
   * @param mP motive mass flow in kg/s
   * @param aMix mixing section area in m2
   * @param pMix mixing pressure in bara
   * @return array {m_s kg/s, primary velocity m/s, suction velocity m/s}
   */
  private double[] entrained(SystemInterface fp0, SystemInterface fs0, double mP, double aMix, double pMix) {
    double[] p = expand(fp0, pMix, etaNozzle, "p");
    double aP = mP / (p[2] * Math.max(p[1], 1.0e-9));
    double aS = aMix - aP;
    double[] s = expand(fs0, pMix, etaSuction, "s");
    double mS = Math.max(s[2] * s[1] * aS, 0.0);
    return new double[] {mS, p[1], s[1]};
  }

  /**
   * Mixed static state at a mixing pressure.
   *
   * @param fp0 motive unit fluid
   * @param fs0 suction unit fluid
   * @param mP motive flow in kg/s
   * @param mS entrained flow in kg/s
   * @param vP primary velocity in m/s
   * @param vS suction velocity in m/s
   * @param pMix mixing pressure in bara
   * @return mixed state (fluid, velocity, static enthalpy)
   */
  private MixState mixState(SystemInterface fp0, SystemInterface fs0, double mP, double mS, double vP, double vS,
      double pMix) {
    double m = mP + mS;
    double v3 = etaMixing * (mP * vP + mS * vS) / m;
    double hTot = (mP * enthalpy(fp0) + mS * enthalpy(fs0)) / m;
    double h3 = hTot - 0.5 * v3 * v3;
    SystemInterface mixed = mixUnit(fp0, mP, fs0, mS);
    mixed.setPressure(pMix, "bara");
    new ThermodynamicOperations(mixed).TPflash();
    SystemInterface g = flashPH(mixed, pMix, h3);
    return new MixState(g, v3, h3, mS, vP, vS);
  }

  /**
   * Mixed state at a mixing pressure.
   *
   * @author esol
   * @version 1.0
   */
  private static final class MixState {
    final SystemInterface fluid;
    final double v3;
    final double h3;
    final double mS;
    final double vP;
    final double vS;

    /**
     * Creates a mixed state.
     *
     * @param fluid mixed fluid (1 kg/s)
     * @param v3 mixed velocity in m/s
     * @param h3 static enthalpy in J/kg
     * @param mS entrained flow in kg/s
     * @param vP primary velocity in m/s
     * @param vS suction velocity in m/s
     */
    MixState(SystemInterface fluid, double v3, double h3, double mS, double vP, double vS) {
      this.fluid = fluid;
      this.v3 = v3;
      this.h3 = h3;
      this.mS = mS;
      this.vP = vP;
      this.vS = vS;
    }
  }

  /**
   * Composition-only mixture of two unit fluids scaled to 1 kg/s total.
   *
   * @param fa first unit fluid
   * @param ma flow of the first fluid in kg/s
   * @param fb second unit fluid
   * @param mb flow of the second fluid in kg/s
   * @return mixture of 1 kg/s (not flashed)
   */
  static SystemInterface mixUnit(SystemInterface fa, double ma, SystemInterface fb, double mb) {
    SystemInterface a = fa.clone();
    a.setTotalFlowRate(Math.max(ma, 1.0e-12), "kg/sec");
    SystemInterface b = fb.clone();
    b.setTotalFlowRate(Math.max(mb, 1.0e-12), "kg/sec");
    a.addFluid(b);
    a.setTotalFlowRate(1.0, "kg/sec");
    return a;
  }

  /**
   * Pressure reached by isentropic compression of the mixed static state with the enthalpy rise of the diffuser.
   *
   * @param g3 mixed static state
   * @param v3 mixed velocity in m/s
   * @param h3 static enthalpy in J/kg
   * @param pMix mixing pressure in bara
   * @return critical back pressure in bara
   */
  private double criticalBackPressureOf(SystemInterface g3, double v3, double h3, double pMix) {
    double target = h3 + etaDiffuser * 0.5 * v3 * v3;
    double lo = pMix;
    double hi = pMix * 1.5;
    while (isentropicEnthalpy(g3, hi) < target && hi < 400.0) {
      lo = hi;
      hi *= 1.6;
    }
    for (int i = 0; i < 11; i++) {
      double mid = 0.5 * (lo + hi);
      if (isentropicEnthalpy(g3, mid) < target) {
        lo = mid;
      } else {
        hi = mid;
      }
    }
    return 0.5 * (lo + hi);
  }

  /**
   * Critical back pressure and mixed state at a mixing pressure.
   *
   * @param fp0 motive unit fluid
   * @param fs0 suction unit fluid
   * @param mP motive flow in kg/s
   * @param aMix mixing area in m2
   * @param pMix mixing pressure in bara
   * @return the mixed state with its critical back pressure, or null if nothing is entrained
   */
  private CbState cbAt(SystemInterface fp0, SystemInterface fs0, double mP, double aMix, double pMix) {
    double[] e = entrained(fp0, fs0, mP, aMix, pMix);
    if (e[0] <= 0.0) {
      return null;
    }
    MixState ms = mixState(fp0, fs0, mP, e[0], e[1], e[2], pMix);
    return new CbState(criticalBackPressureOf(ms.fluid, ms.v3, ms.h3, pMix), ms);
  }

  /**
   * Critical back pressure with its mixed state.
   *
   * @author esol
   * @version 1.0
   */
  private static final class CbState {
    final double pCb;
    final MixState state;

    /**
     * Creates a state.
     *
     * @param pCb critical back pressure in bara
     * @param state mixed state
     */
    CbState(double pCb, MixState state) {
      this.pCb = pCb;
      this.state = state;
    }
  }

  /**
   * Solves the operating point of one fixed-geometry ejector.
   *
   * @param fp0 motive unit fluid (1 kg/s) at the motive state
   * @param fs0 suction unit fluid (1 kg/s) at the suction state
   * @param dischargePressure discharge pressure in bara
   * @param throatArea nozzle throat area in m2
   * @param mixingArea mixing section area in m2
   * @return the operating point (the suction flow is the result)
   */
  public EjectorOperatingPoint solvePoint(SystemInterface fp0, SystemInterface fs0, double dischargePressure,
      double throatArea, double mixingArea) {
    expansionCache.clear();
    double pS = fs0.getPressure("bara");
    double pP = fp0.getPressure("bara");
    EjectorOperatingPoint op = new EjectorOperatingPoint();
    op.setAreas(throatArea, mixingArea);
    double[] cf = chokedFlux(fp0, pP);
    double mP = throatArea * cf[0];
    double hpIn = enthalpy(fp0);
    double hsIn = enthalpy(fs0);

    // critical (double-choked) entrainment: maximum of the entrained flow over the mixing pressure
    double[] factors = {0.25, 0.4, 0.55, 0.7, 0.85, 0.97};
    int best = 0;
    double bestVal = -1.0;
    for (int i = 0; i < factors.length; i++) {
      double v = entrained(fp0, fs0, mP, mixingArea, pS * factors[i])[0];
      if (v > bestVal) {
        bestVal = v;
        best = i;
      }
    }
    double a = pS * factors[Math.max(best - 1, 0)];
    double b = pS * factors[Math.min(best + 1, factors.length - 1)];
    double c = b - GOLDEN * (b - a);
    double d = a + GOLDEN * (b - a);
    double fc = entrained(fp0, fs0, mP, mixingArea, c)[0];
    double fd = entrained(fp0, fs0, mP, mixingArea, d)[0];
    for (int i = 0; i < 9; i++) {
      if (fc > fd) {
        b = d;
        d = c;
        fd = fc;
        c = b - GOLDEN * (b - a);
        fc = entrained(fp0, fs0, mP, mixingArea, c)[0];
      } else {
        a = c;
        c = d;
        fc = fd;
        d = a + GOLDEN * (b - a);
        fd = entrained(fp0, fs0, mP, mixingArea, d)[0];
      }
    }
    double pMixCrit = 0.5 * (a + b);
    CbState crit = cbAt(fp0, fs0, mP, mixingArea, pMixCrit);
    double pCbCrit = crit == null ? 0.0 : crit.pCb;

    EjectorOperatingPoint.Regime regime = EjectorOperatingPoint.Regime.CRITICAL;
    double pMix = pMixCrit;
    double pCb = pCbCrit;
    MixState state = crit == null ? null : crit.state;
    double mS = state == null ? 0.0 : state.mS;
    if (pCbCrit < dischargePressure) {
      double lo = pMixCrit;
      double hi = pS * 0.999;
      CbState atHi = cbAt(fp0, fs0, mP, mixingArea, hi);
      if (atHi == null || atHi.pCb < dischargePressure) {
        regime = EjectorOperatingPoint.Regime.BREAKDOWN;
        pMix = hi;
        pCb = atHi == null ? 0.0 : atHi.pCb;
        state = null;
        mS = 0.0;
      } else {
        pCb = atHi.pCb;
        state = atHi.state;
        for (int i = 0; i < 10; i++) {
          double mid = 0.5 * (lo + hi);
          CbState cs = cbAt(fp0, fs0, mP, mixingArea, mid);
          if (cs == null || cs.pCb < dischargePressure) {
            lo = mid;
          } else {
            hi = mid;
            pCb = cs.pCb;
            state = cs.state;
          }
        }
        pMix = hi;
        mS = state.mS;
        regime = EjectorOperatingPoint.Regime.SUBCRITICAL;
      }
    }

    double hOut = (mP + mS) > 0.0 ? (mP * hpIn + mS * hsIn) / (mP + mS) : hpIn;
    SystemInterface mixed = mixUnit(fp0, mP, fs0, Math.max(mS, 1.0e-12));
    mixed.setPressure(dischargePressure, "bara");
    new ThermodynamicOperations(mixed).TPflash();
    SystemInterface out = flashPH(mixed, dischargePressure, hOut);
    double[] nz = expand(fp0, pMix, etaNozzle, "p");
    double[] sc = expand(fs0, pMix, etaSuction, "s");

    op.setRegime(regime);
    op.setMotiveFlow(mP);
    op.setSuctionFlow(mS);
    op.setMotivePressure(pP);
    op.setSuctionPressure(pS);
    op.setDischargePressure(dischargePressure);
    op.setMixingPressure(pMix);
    op.setThroatPressure(cf[1]);
    op.setEntrainmentRatio(mP > 0.0 ? mS / mP : 0.0);
    op.setCompressionRatio(dischargePressure / pS);
    op.setExpansionRatio(pP / dischargePressure);
    op.setCriticalBackPressure(pCb);
    op.setBreakdownMargin(pCbCrit / dischargePressure - 1.0);
    op.setMotiveChoked(cf[1] < pP * 0.9);
    op.setDischargeTemperatureC(out.getTemperature("C"));
    op.setMixedMolarMass(out.getMolarMass("kg/mol") * 1000.0);
    op.setNozzleExitTemperatureC(nz[3]);
    op.setNozzleVaporFraction(nz[4]);
    op.setVelocities(nz[1], sc[1], state == null ? 0.0 : state.v3);
    if (state != null) {
      op.setMixingTemperatureC(state.fluid.getTemperature("C"));
      try {
        op.setMixingMach(state.v3 / state.fluid.getSoundSpeed());
      } catch (RuntimeException e) {
        logger.debug("mixing Mach number not available: {}", e.getMessage());
      }
    }
    if (!Double.isNaN(nz[4]) && nz[4] < 0.98) {
      op.getWarnings().add(String.format(java.util.Locale.US,
          "condensation in the motive nozzle (vapour fraction %.3f, T %.1f C)", nz[4], nz[3]));
    }
    if (regime == EjectorOperatingPoint.Regime.BREAKDOWN) {
      op.getWarnings().add("breakdown: back pressure above what the motive flow can recover, no entrainment");
    } else if (regime == EjectorOperatingPoint.Regime.SUBCRITICAL) {
      op.getWarnings().add("back pressure above the critical value: entrainment reduced");
    }
    return op;
  }

  /**
   * Builds the mixed outlet fluid of an operating point at the actual flows (adiabatic mixing at the discharge
   * pressure, inlet enthalpies).
   *
   * @param fp0 motive unit fluid
   * @param mP motive flow in kg/s
   * @param fs0 suction unit fluid
   * @param mS entrained flow in kg/s (may be zero)
   * @param dischargePressure discharge pressure in bara
   * @return flashed mixed fluid at the actual total flow
   */
  public SystemInterface buildMixedFluid(SystemInterface fp0, double mP, SystemInterface fs0, double mS,
      double dischargePressure) {
    double m = mP + Math.max(mS, 0.0);
    double hOut = (mP * enthalpy(fp0) + Math.max(mS, 0.0) * enthalpy(fs0)) / m;
    SystemInterface mixed = mixUnit(fp0, mP, fs0, Math.max(mS, 1.0e-12));
    mixed.setPressure(dischargePressure, "bara");
    new ThermodynamicOperations(mixed).TPflash();
    SystemInterface out = flashPH(mixed, dischargePressure, hOut);
    out.setTotalFlowRate(m, "kg/sec");
    new ThermodynamicOperations(out).PHflash(hOut, "J/kg");
    out.init(3);
    return out;
  }

  /**
   * Sizes a fixed geometry for a design suction flow: the area ratio gives a critical back pressure of at least (1 +
   * margin) times the discharge pressure, the absolute areas scale to the suction flow.
   *
   * @param fp0 motive unit fluid
   * @param fs0 suction unit fluid
   * @param dischargePressure discharge pressure in bara
   * @param suctionFlow design entrained flow in kg/s
   * @param backPressureMargin required relative margin of the critical back pressure over the discharge pressure
   * @return array {throat area m2, mixing area m2}
   */
  public double[] autoSize(SystemInterface fp0, SystemInterface fs0, double dischargePressure, double suctionFlow,
      double backPressureMargin) {
    double target = dischargePressure * (1.0 + backPressureMargin);
    double at = 1.0e-3;
    double lo = 2.0;
    double hi = 400.0;
    double phi;
    EjectorOperatingPoint rHi = solvePoint(fp0, fs0, dischargePressure, at, at * hi);
    if (rHi.getCriticalBackPressure() >= target) {
      phi = hi;
    } else {
      for (int i = 0; i < 9; i++) {
        double mid = Math.sqrt(lo * hi);
        EjectorOperatingPoint r = solvePoint(fp0, fs0, dischargePressure, at, at * mid);
        if (r.getCriticalBackPressure() >= target && r.getRegime() == EjectorOperatingPoint.Regime.CRITICAL) {
          lo = mid;
        } else {
          hi = mid;
        }
      }
      phi = lo;
    }
    EjectorOperatingPoint r = solvePoint(fp0, fs0, dischargePressure, at, at * phi);
    double scale = suctionFlow / Math.max(r.getSuctionFlow(), 1.0e-12);
    return new double[] {at * scale, at * scale * phi};
  }
}
