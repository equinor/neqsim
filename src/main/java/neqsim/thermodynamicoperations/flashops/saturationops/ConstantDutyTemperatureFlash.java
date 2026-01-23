package neqsim.thermodynamicoperations.flashops.saturationops;

import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * constantDutyTemperatureFlash class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class ConstantDutyTemperatureFlash extends ConstantDutyFlash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for constantDutyTemperatureFlash.
   * </p>
   */
  public ConstantDutyTemperatureFlash() {}

  /**
   * <p>
   * Constructor for constantDutyTemperatureFlash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public ConstantDutyTemperatureFlash(SystemInterface system) {
    super(system);
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    // If you have chemical reactions coupled to T, keep this hook.
    if (system.isChemicalSystem()) {
      system.getChemicalReactionOperations().solveChemEq(0);
    }

    final int maxIter = 300;
    final double tolRel = 1e-10;
    final double damp2P = 0.47; // damping for 2-phase Newton
    final double damp1P = 0.60; // damping for 1-phase (incipient) Newton
    final double maxStep = 20.0; // max |ΔT| per iteration [K]
    final double tiny = 1e-16;

    int iterations = 0;
    double dT = 0.0;

    do {
      iterations++;
      system.init(2);

      final int nc = system.getPhases()[0].getNumberOfComponents();
      double Told = system.getTemperature();

      // ---------- SPECIAL CASE: pure component ----------
      if (nc == 1) {
        // For a pure component at fixed P, saturation condition is: ln(phi^V) - ln(phi^L) = 0
        // Use phase[1] as vapor, phase[0] as liquid (swap if your convention differs).
        double lnphiV = system.getPhases()[1].getComponent(0).getLogFugacityCoefficient();
        double lnphiL = system.getPhases()[0].getComponent(0).getLogFugacityCoefficient();
        double dlnphiVdT = system.getPhases()[1].getComponent(0).getdfugdt();
        double dlnphiLdT = system.getPhases()[0].getComponent(0).getdfugdt();

        double g = (lnphiV - lnphiL);
        double dg = (dlnphiVdT - dlnphiLdT);
        if (Math.abs(dg) < tiny || Double.isNaN(dg)) {
          dg = Math.copySign(tiny, (dg == 0.0 ? 1.0 : dg));
        }

        dT = -damp1P * (g / dg);
        if (Double.isNaN(dT) || Double.isInfinite(dT)) {
          dT = -1.0;
        }
        if (dT > maxStep) {
          dT = maxStep;
        }
        if (dT < -maxStep) {
          dT = -maxStep;
        }

        system.setTemperature(Told + dT);
        continue;
      }

      // ---------- MULTICOMPONENT ----------
      // Build K and dK/dT using fugacity coefficients (match your pressure code's orientation)
      final double[] Ki = new double[nc];
      final double[] dKidt = new double[nc];
      final double[] zi = new double[nc];

      for (int i = 0; i < nc; i++) {
        neqsim.thermo.component.ComponentInterface cL = system.getPhases()[0].getComponent(i); // treat
                                                                                               // [0]=liq
        neqsim.thermo.component.ComponentInterface cV = system.getPhases()[1].getComponent(i); // treat
                                                                                               // [1]=vap

        double phiL = cL.getFugacityCoefficient();
        double phiV = cV.getFugacityCoefficient();

        double dlnphiLdT = cL.getdfugdt(); // assumed d(ln phi)/dT
        double dlnphiVdT = cV.getdfugdt();

        // Keep the same K-orientation as your pressure routine: K = phiV / phiL (i.e., y/x)
        Ki[i] = phiV / phiL;
        dKidt[i] = Ki[i] * (dlnphiVdT - dlnphiLdT);
        zi[i] = cV.getz(); // overall z (same on both phases)

        // Store K where your RR/x-y uses it
        cL.setK(Ki[i]);
        cV.setK(Ki[i]);
      }

      // Rachford–Rice endpoint checks to detect if a physical split exists (β in (0,1))
      double f0 = 0.0; // sum z_i (K_i - 1)
      double f1 = -1.0; // sum z_i / K_i - 1
      for (int i = 0; i < nc; i++) {
        f0 += zi[i] * (Ki[i] - 1.0);
        f1 += zi[i] / Ki[i];
      }

      boolean twoPhasePossible = (f0 * f1) < 0.0;

      if (twoPhasePossible) {
        // ---------- Two-phase Newton on Σ(y - x) ----------
        system.calc_x_y_nonorm();

        double funk = 0.0;
        double deriv = 0.0;

        double beta = system.getBeta();
        for (int i = 0; i < nc; i++) {
          neqsim.thermo.component.ComponentInterface cL = system.getPhases()[0].getComponent(i);
          neqsim.thermo.component.ComponentInterface cV = system.getPhases()[1].getComponent(i);

          double K = Ki[i];
          double dKdT = dKidt[i];
          double z = zi[i];

          double denom = 1.0 - beta + beta * K;
          double dxidT = -z * beta * dKdT / (denom * denom);
          double dyidT = dKdT * cV.getx() + K * dxidT; // note: uses x from phase[1] as in your P
                                                       // solver

          funk += cL.getx() - cV.getx(); // match your pressure code sign convention
          deriv += dyidT - dxidT;
        }

        if (Math.abs(deriv) < tiny || Double.isNaN(deriv)) {
          deriv = Math.copySign(tiny, (deriv == 0.0 ? 1.0 : deriv));
        }

        dT = -damp2P * (funk / deriv);
      } else {
        // ---------- Single-phase: move to incipient boundary directly ----------
        // Decide bubble (liquid-like) vs dew (vapor-like)
        boolean bubbleSide;
        if ((f0 < 0.0 && f1 < 0.0) || (f0 > 0.0 && f1 > 0.0)) {
          // both negative -> bubble; both positive -> dew
          bubbleSide = (f0 < 0.0);
        } else {
          // rare numerical ambiguity
          bubbleSide = Math.abs(f0) < Math.abs(f1);
        }

        double g = 0.0;
        double dg = 0.0;
        if (bubbleSide) {
          // Bubble condition: Σ z_i K_i - 1 = 0
          for (int i = 0; i < nc; i++) {
            g += zi[i] * Ki[i];
            dg += zi[i] * dKidt[i];
          }
          g -= 1.0;
        } else {
          // Dew condition: Σ z_i / K_i - 1 = 0
          for (int i = 0; i < nc; i++) {
            g += zi[i] / Ki[i];
            dg += -zi[i] * dKidt[i] / (Ki[i] * Ki[i]);
          }
          g -= 1.0;
        }

        if (Math.abs(dg) < tiny || Double.isNaN(dg)) {
          dg = Math.copySign(tiny, (dg == 0.0 ? 1.0 : dg));
        }
        dT = -damp1P * (g / dg);
      }

      // Step-limit, update, and loop
      if (Double.isNaN(dT) || Double.isInfinite(dT)) {
        dT = -1.0;
      }
      if (dT > maxStep) {
        dT = maxStep;
      }
      if (dT < -maxStep) {
        dT = -maxStep;
      }

      system.setTemperature(Told + dT);

      // If you couple chemistry strongly to T, you may want:
      // if (system.isChemicalSystem()) system.getChemicalReactionOperations().solveChemEq(0);
    } while ((((Math.abs(dT) / Math.max(1.0, system.getTemperature())) > tolRel)
        && iterations < maxIter) || iterations < 3);
  }

  /** {@inheritDoc} */
  @Override
  public void printToFile(String name) {}

  /** {@inheritDoc} */
  @Override
  public org.jfree.chart.JFreeChart getJFreeChart(String name) {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface getThermoSystem() {
    return system;
  }
}
