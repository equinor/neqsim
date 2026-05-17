package neqsim.process.mechanicaldesign.heatexchanger;

/**
 * Bell-Delaware method for shell-side heat transfer and pressure drop in shell and tube heat
 * exchangers.
 *
 * <p>
 * The Bell-Delaware method is an industry-standard approach that corrects the ideal tube-bank heat
 * transfer coefficient using geometry-specific correction factors. It accounts for baffle window
 * flow, baffle leakage, bundle bypass, unequal baffle spacing, and adverse temperature gradients.
 * </p>
 *
 * <p>
 * The corrected shell-side coefficient is:
 * </p>
 *
 * <pre>
 * h_shell = h_ideal * J_c * J_l * J_b * J_s * J_r
 * </pre>
 *
 * <p>
 * The corrected shell-side pressure drop is:
 * </p>
 *
 * <pre>
 * dP_shell = dP_crossflow * R_l * R_b + dP_window * R_l + dP_endzone
 * </pre>
 *
 * <p>
 * Reference: Bell, K.J. (1981), "Delaware Method for Shell-Side Design", in Heat Exchangers:
 * Thermal-Hydraulic Fundamentals and Design, Hemisphere Publishing.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public final class BellDelawareMethod {

  /**
   * Private constructor to prevent instantiation.
   */
  private BellDelawareMethod() {}

  /**
   * Calculates the ideal crossflow heat transfer coefficient for a tube bank in crossflow.
   *
   * <p>
   * Uses the Zhukauskas correlation for inline and staggered tube banks:
   * </p>
   * <ul>
   * <li>Staggered (triangular): Nu = 0.35 * (p_t/p_l)^0.2 * Re^0.6 * Pr^0.36 * (Pr/Pr_w)^0.25</li>
   * <li>Inline (square): Nu = 0.27 * Re^0.63 * Pr^0.36 * (Pr/Pr_w)^0.25</li>
   * </ul>
   *
   * @param massFlux shell-side mass flux through crossflow area (kg/(m2*s))
   * @param tubeOD tube outer diameter (m)
   * @param mu shell-side fluid viscosity (Pa*s)
   * @param cp shell-side fluid heat capacity (J/(kg*K))
   * @param k shell-side fluid thermal conductivity (W/(m*K))
   * @param muWall viscosity at tube wall temperature (Pa*s), use mu if unknown
   * @param triangularPitch true for triangular (staggered), false for square (inline)
   * @return ideal crossflow heat transfer coefficient (W/(m2*K))
   */
  public static double calcIdealCrossflowHTC(double massFlux, double tubeOD, double mu, double cp,
      double k, double muWall, double triangularPitch) {
    if (mu <= 0 || k <= 0 || cp <= 0 || tubeOD <= 0) {
      return 0.0;
    }

    double Re = massFlux * tubeOD / mu;
    double Pr = cp * mu / k;
    double PrW = cp * muWall / k;

    // Viscosity correction
    double viscCorr = (PrW > 0) ? Math.pow(Pr / PrW, 0.25) : 1.0;

    double Nu;
    if (triangularPitch > 0.5) {
      // Staggered (triangular) layout - Zhukauskas
      if (Re < 100) {
        Nu = 0.9 * Math.pow(Re, 0.4) * Math.pow(Pr, 0.36) * viscCorr;
      } else if (Re < 1000) {
        Nu = 0.52 * Math.pow(Re, 0.5) * Math.pow(Pr, 0.36) * viscCorr;
      } else if (Re < 200000) {
        Nu = 0.27 * Math.pow(Re, 0.63) * Math.pow(Pr, 0.36) * viscCorr;
      } else {
        Nu = 0.033 * Math.pow(Re, 0.8) * Math.pow(Pr, 0.36) * viscCorr;
      }
    } else {
      // Inline (square) layout
      if (Re < 100) {
        Nu = 0.9 * Math.pow(Re, 0.4) * Math.pow(Pr, 0.36) * viscCorr;
      } else if (Re < 1000) {
        Nu = 0.52 * Math.pow(Re, 0.5) * Math.pow(Pr, 0.36) * viscCorr;
      } else if (Re < 200000) {
        Nu = 0.27 * Math.pow(Re, 0.63) * Math.pow(Pr, 0.36) * viscCorr;
      } else {
        Nu = 0.021 * Math.pow(Re, 0.84) * Math.pow(Pr, 0.36) * viscCorr;
      }
    }

    return Nu * k / tubeOD;
  }

  /**
   * Calculates the ideal crossflow heat transfer coefficient using the simplified Kern method.
   *
   * <p>
   * Kern correlation: h = 0.36 * (k/D_e) * Re^0.55 * Pr^(1/3) * (mu/mu_w)^0.14
   * </p>
   *
   * @param massFlux mass flux based on shell-side crossflow area (kg/(m2*s))
   * @param shellEquivDiameter shell-side equivalent diameter (m)
   * @param mu shell-side fluid viscosity (Pa*s)
   * @param cp shell-side fluid heat capacity (J/(kg*K))
   * @param k shell-side fluid thermal conductivity (W/(m*K))
   * @param muWall viscosity at wall temperature (Pa*s), use mu if unknown
   * @return shell-side heat transfer coefficient (W/(m2*K))
   */
  public static double calcKernShellSideHTC(double massFlux, double shellEquivDiameter, double mu,
      double cp, double k, double muWall) {
    if (mu <= 0 || k <= 0 || cp <= 0 || shellEquivDiameter <= 0) {
      return 0.0;
    }

    double Re = massFlux * shellEquivDiameter / mu;
    double Pr = cp * mu / k;
    double viscCorr = (muWall > 0) ? Math.pow(mu / muWall, 0.14) : 1.0;

    double Nu = 0.36 * Math.pow(Re, 0.55) * Math.pow(Pr, 1.0 / 3.0) * viscCorr;
    return Nu * k / shellEquivDiameter;
  }

  /**
   * Calculates the Kern shell-side pressure drop.
   *
   * <p>
   * dP = f * D_s * (N_b + 1) * rho * v^2 / (2 * D_e * (mu/mu_w)^0.14)
   * </p>
   *
   * @param massFlux mass flux based on crossflow area (kg/(m2*s))
   * @param shellEquivDiameter shell-side equivalent diameter (m)
   * @param shellDiameter shell inside diameter (m)
   * @param baffleCount number of baffles
   * @param rho shell-side fluid density (kg/m3)
   * @param mu shell-side fluid viscosity (Pa*s)
   * @param muWall viscosity at wall temperature (Pa*s), use mu if unknown
   * @return shell-side pressure drop (Pa)
   */
  public static double calcKernShellSidePressureDrop(double massFlux, double shellEquivDiameter,
      double shellDiameter, int baffleCount, double rho, double mu, double muWall) {
    if (mu <= 0 || rho <= 0 || shellEquivDiameter <= 0) {
      return 0.0;
    }

    double Re = massFlux * shellEquivDiameter / mu;
    double viscCorr = (muWall > 0) ? Math.pow(mu / muWall, 0.14) : 1.0;

    // Friction factor (Kern)
    double f;
    if (Re < 10) {
      f = 64.0 / Math.max(Re, 0.1);
    } else if (Re < 1000) {
      f = 5.6 * Math.pow(Re, -0.344);
    } else {
      f = 0.228 * Math.pow(Re, -0.1574);
    }

    double velocity = massFlux / rho;
    return f * shellDiameter * (baffleCount + 1) * rho * velocity * velocity
        / (2.0 * shellEquivDiameter * viscCorr);
  }

  /**
   * Calculates the shell-side equivalent diameter for use in the Kern method.
   *
   * @param tubeOD tube outer diameter (m)
   * @param tubePitch tube pitch (m)
   * @param triangularPitch true for triangular layout, false for square layout
   * @return equivalent diameter (m)
   */
  public static double calcShellEquivDiameter(double tubeOD, double tubePitch,
      boolean triangularPitch) {
    if (tubeOD <= 0 || tubePitch <= 0) {
      return 0.0;
    }

    if (triangularPitch) {
      // Triangular pitch: De = 4 * (sqrt(3)/4 * Pt^2 - pi/8 * do^2) / (pi/2 * do)
      double flowArea =
          Math.sqrt(3.0) / 4.0 * tubePitch * tubePitch - Math.PI / 8.0 * tubeOD * tubeOD;
      double wetPerimeter = Math.PI / 2.0 * tubeOD;
      return 4.0 * flowArea / wetPerimeter;
    } else {
      // Square pitch: De = 4 * (Pt^2 - pi/4 * do^2) / (pi * do)
      double flowArea = tubePitch * tubePitch - Math.PI / 4.0 * tubeOD * tubeOD;
      double wetPerimeter = Math.PI * tubeOD;
      return 4.0 * flowArea / wetPerimeter;
    }
  }

  /**
   * Calculates the shell-side crossflow area at the bundle centerline.
   *
   * @param shellID shell inside diameter (m)
   * @param baffleSpacing central baffle spacing (m)
   * @param tubeOD tube outer diameter (m)
   * @param tubePitch tube pitch (m)
   * @return crossflow area (m2)
   */
  public static double calcCrossflowArea(double shellID, double baffleSpacing, double tubeOD,
      double tubePitch) {
    if (shellID <= 0 || baffleSpacing <= 0 || tubePitch <= 0) {
      return 0.0;
    }
    return baffleSpacing * shellID * (1.0 - tubeOD / tubePitch);
  }

  // ==========================================================================
  // Bell-Delaware Correction Factors
  // ==========================================================================

  /**
   * Calculates J_c, the baffle-cut correction factor for heat transfer.
   *
   * <p>
   * J_c accounts for the heat transfer in the baffle window zone compared to the crossflow zone.
   * Typical range: 0.65 to 1.15.
   * </p>
   *
   * @param baffleCut fractional baffle cut (e.g., 0.25 for 25%)
   * @return J_c correction factor
   */
  public static double calcJc(double baffleCut) {
    // Simplified correlation based on Bell's data
    // J_c = 0.55 + 0.72 * Fc where Fc is fraction of tubes in crossflow
    double Fc = estimateCrossflowFraction(baffleCut);
    return 0.55 + 0.72 * Fc;
  }

  /**
   * Calculates J_l, the baffle leakage correction factor for heat transfer.
   *
   * <p>
   * J_l accounts for fluid bypassing through the tube-to-baffle and shell-to-baffle clearances.
   * Typical range: 0.6 to 0.9.
   * </p>
   *
   * @param tubeToBaffleClearance tube-to-baffle hole clearance (m)
   * @param shellToBaffleClearance shell-to-baffle clearance (m)
   * @param crossflowArea crossflow area at bundle centerline (m2)
   * @param tubeCount number of tubes
   * @param tubeOD tube outer diameter (m)
   * @param baffleSpacing baffle spacing (m)
   * @return J_l correction factor
   */
  public static double calcJl(double tubeToBaffleClearance, double shellToBaffleClearance,
      double crossflowArea, int tubeCount, double tubeOD, double baffleSpacing) {
    if (crossflowArea <= 0) {
      return 0.7;
    }

    // Leakage areas
    double tubeBaffleArea = tubeCount * Math.PI * tubeOD * tubeToBaffleClearance;
    double shellBaffleArea = Math.PI * baffleSpacing * shellToBaffleClearance;

    double totalLeakageRatio = (tubeBaffleArea + shellBaffleArea) / crossflowArea;
    double shellLeakageFraction =
        shellBaffleArea / Math.max(tubeBaffleArea + shellBaffleArea, 1e-10);

    // Bell's correlation
    double Jl = 0.44 * (1.0 - shellLeakageFraction)
        + (1.0 - 0.44 * (1.0 - shellLeakageFraction)) * Math.exp(-2.2 * totalLeakageRatio);

    return Math.max(0.2, Math.min(1.0, Jl));
  }

  /**
   * Calculates J_b, the bundle bypass correction factor for heat transfer.
   *
   * <p>
   * J_b accounts for the bypass flow between the outermost tubes and the shell wall. Typical range:
   * 0.7 to 1.0.
   * </p>
   *
   * @param bypassArea bypass flow area between bundle and shell (m2)
   * @param crossflowArea crossflow area at bundle centerline (m2)
   * @param hasSealing true if sealing strips are installed
   * @param sealingPairs number of sealing strip pairs
   * @param tubeRowsCrossflow number of tube rows in crossflow
   * @return J_b correction factor
   */
  public static double calcJb(double bypassArea, double crossflowArea, boolean hasSealing,
      int sealingPairs, int tubeRowsCrossflow) {
    if (crossflowArea <= 0) {
      return 0.8;
    }

    double Fbp = bypassArea / crossflowArea;
    double C = 1.35; // Bell's constant for no sealing

    if (hasSealing && tubeRowsCrossflow > 0) {
      double Nss = sealingPairs;
      double Nc = tubeRowsCrossflow;
      double ratio = Nss / Nc;
      if (ratio >= 0.5) {
        return 1.0;
      }
    }

    double Jb = Math.exp(-C * Fbp);
    return Math.max(0.5, Math.min(1.0, Jb));
  }

  /**
   * Calculates J_s, the unequal baffle spacing correction factor.
   *
   * <p>
   * J_s corrects for inlet and outlet baffle spacings that differ from the central spacing. Typical
   * range: 0.85 to 1.0.
   * </p>
   *
   * @param centralSpacing central baffle spacing (m)
   * @param inletSpacing inlet baffle spacing (m)
   * @param outletSpacing outlet baffle spacing (m)
   * @param baffleCount number of baffles
   * @return J_s correction factor
   */
  public static double calcJs(double centralSpacing, double inletSpacing, double outletSpacing,
      int baffleCount) {
    if (centralSpacing <= 0 || baffleCount < 1) {
      return 1.0;
    }

    double Nb = baffleCount;
    double inletRatio = centralSpacing / inletSpacing;
    double outletRatio = centralSpacing / outletSpacing;

    double numerator = Nb - 1.0 + Math.pow(inletRatio, 0.6) + Math.pow(outletRatio, 0.6);
    double denominator = Nb - 1.0 + inletRatio + outletRatio;

    if (denominator <= 0) {
      return 1.0;
    }

    double Js = numerator / denominator;
    return Math.max(0.5, Math.min(1.0, Js));
  }

  /**
   * Calculates J_r, the adverse temperature gradient correction factor for laminar flow.
   *
   * <p>
   * J_r accounts for the reduction in heat transfer in laminar flow due to adverse temperature
   * gradients across the tube bank. For turbulent flow (Re &gt; 100), J_r = 1.0.
   * </p>
   *
   * @param Re shell-side Reynolds number based on tube OD
   * @param tubeRowsCrossflow number of effective tube rows in crossflow
   * @return J_r correction factor
   */
  public static double calcJr(double Re, int tubeRowsCrossflow) {
    if (Re >= 100 || tubeRowsCrossflow <= 10) {
      return 1.0;
    }

    // For deep laminar flow
    if (Re < 20) {
      double Jr = Math.pow(10.0 / tubeRowsCrossflow, 0.18);
      return Math.max(0.4, Math.min(1.0, Jr));
    }

    // Transition (20 <= Re < 100)
    double JrLam = Math.pow(10.0 / tubeRowsCrossflow, 0.18);
    double f = (Re - 20.0) / 80.0;
    double Jr = JrLam + f * (1.0 - JrLam);
    return Math.max(0.4, Math.min(1.0, Jr));
  }

  /**
   * Calculates the fully corrected Bell-Delaware shell-side heat transfer coefficient.
   *
   * @param hIdeal ideal crossflow HTC from calcIdealCrossflowHTC (W/(m2*K))
   * @param Jc baffle cut correction factor
   * @param Jl leakage correction factor
   * @param Jb bypass correction factor
   * @param Js spacing correction factor
   * @param Jr laminar temperature gradient correction factor
   * @return corrected shell-side HTC (W/(m2*K))
   */
  public static double calcCorrectedHTC(double hIdeal, double Jc, double Jl, double Jb, double Js,
      double Jr) {
    return hIdeal * Jc * Jl * Jb * Js * Jr;
  }

  // ==========================================================================
  // Pressure Drop Correction Factors
  // ==========================================================================

  /**
   * Calculates R_l, the leakage correction factor for pressure drop.
   *
   * <p>
   * R_l is typically more significant for pressure drop than for heat transfer. Typical range: 0.4
   * to 0.8.
   * </p>
   *
   * @param tubeToBaffleClearance tube-to-baffle hole clearance (m)
   * @param shellToBaffleClearance shell-to-baffle clearance (m)
   * @param crossflowArea crossflow area at bundle centerline (m2)
   * @param tubeCount number of tubes
   * @param tubeOD tube outer diameter (m)
   * @param baffleSpacing baffle spacing (m)
   * @return R_l pressure drop leakage correction factor
   */
  public static double calcRl(double tubeToBaffleClearance, double shellToBaffleClearance,
      double crossflowArea, int tubeCount, double tubeOD, double baffleSpacing) {
    if (crossflowArea <= 0) {
      return 0.5;
    }

    double tubeBaffleArea = tubeCount * Math.PI * tubeOD * tubeToBaffleClearance;
    double shellBaffleArea = Math.PI * baffleSpacing * shellToBaffleClearance;

    double totalLeakageRatio = (tubeBaffleArea + shellBaffleArea) / crossflowArea;
    double shellLeakageFraction =
        shellBaffleArea / Math.max(tubeBaffleArea + shellBaffleArea, 1e-10);

    double Rl = Math.exp(-1.33 * (1.0 + shellLeakageFraction) * Math.pow(totalLeakageRatio, 0.8));
    return Math.max(0.1, Math.min(1.0, Rl));
  }

  /**
   * Calculates R_b, the bypass correction factor for pressure drop.
   *
   * @param bypassArea bypass flow area (m2)
   * @param crossflowArea crossflow area (m2)
   * @param hasSealing true if sealing strips are installed
   * @param sealingPairs number of sealing strip pairs
   * @param tubeRowsCrossflow number of tube rows in crossflow
   * @return R_b pressure drop bypass correction factor
   */
  public static double calcRb(double bypassArea, double crossflowArea, boolean hasSealing,
      int sealingPairs, int tubeRowsCrossflow) {
    if (crossflowArea <= 0) {
      return 0.7;
    }

    double Fbp = bypassArea / crossflowArea;
    double C = 3.7; // For pressure drop (more severe than heat transfer)

    if (hasSealing && tubeRowsCrossflow > 0) {
      double ratio = (double) sealingPairs / tubeRowsCrossflow;
      if (ratio >= 0.5) {
        return 1.0;
      }
    }

    double Rb = Math.exp(-C * Fbp);
    return Math.max(0.3, Math.min(1.0, Rb));
  }

  /**
   * Calculates the ideal crossflow pressure drop for one baffle compartment.
   *
   * @param tubeRowsCrossflow number of tube rows crossed
   * @param massFlux mass flux through crossflow area (kg/(m2*s))
   * @param rho fluid density (kg/m3)
   * @param Re Reynolds number based on tube OD
   * @param triangularPitch true for triangular layout
   * @return ideal crossflow pressure drop for one compartment (Pa)
   */
  public static double calcIdealCrossflowDP(int tubeRowsCrossflow, double massFlux, double rho,
      double Re, boolean triangularPitch) {
    if (rho <= 0 || massFlux <= 0) {
      return 0.0;
    }

    // Friction factor for tube bank
    double f;
    if (triangularPitch) {
      if (Re < 1000) {
        f = 48.0 / Math.max(Re, 0.1);
      } else {
        f = 0.35 * Math.pow(Re, -0.15);
      }
    } else {
      if (Re < 1000) {
        f = 35.0 / Math.max(Re, 0.1);
      } else {
        f = 0.18 * Math.pow(Re, -0.15);
      }
    }

    return f * tubeRowsCrossflow * massFlux * massFlux / (2.0 * rho);
  }

  /**
   * Calculates the window-zone pressure drop per baffle.
   *
   * @param windowArea net baffle window flow area (m2)
   * @param massFlowRate total shell-side mass flow rate (kg/s)
   * @param rho fluid density (kg/m3)
   * @param tubeRowsWindow number of effective tube rows in window
   * @return window pressure drop per baffle (Pa)
   */
  public static double calcWindowDP(double windowArea, double massFlowRate, double rho,
      int tubeRowsWindow) {
    if (windowArea <= 0 || rho <= 0) {
      return 0.0;
    }

    double velocityWindow = massFlowRate / (rho * windowArea);
    // Window DP = (2 + 0.6 * Nrw) * rho * v_w^2 / 2
    return (2.0 + 0.6 * tubeRowsWindow) * rho * velocityWindow * velocityWindow / 2.0;
  }

  /**
   * Estimates the fraction of tubes in the crossflow zone between baffle tips.
   *
   * @param baffleCut fractional baffle cut (e.g., 0.25 for 25%)
   * @return fraction of tubes in crossflow (0 to 1)
   */
  public static double estimateCrossflowFraction(double baffleCut) {
    if (baffleCut <= 0.15) {
      return 0.9;
    }
    if (baffleCut >= 0.45) {
      return 0.3;
    }
    // Linear interpolation
    return 0.9 - (baffleCut - 0.15) / (0.45 - 0.15) * 0.6;
  }

  /**
   * Estimates the number of tube rows in the crossflow zone.
   *
   * @param shellID shell inside diameter (m)
   * @param baffleCut fractional baffle cut
   * @param tubePitch tube pitch (m)
   * @param triangularPitch true for triangular layout
   * @return estimated number of tube rows in crossflow
   */
  public static int estimateTubeRowsCrossflow(double shellID, double baffleCut, double tubePitch,
      boolean triangularPitch) {
    if (shellID <= 0 || tubePitch <= 0) {
      return 1;
    }

    // Distance between baffle tips
    double distBetweenTips = shellID * (1.0 - 2.0 * baffleCut);
    double rowPitch = triangularPitch ? tubePitch * Math.sqrt(3.0) / 2.0 : tubePitch;

    return Math.max(1, (int) (distBetweenTips / rowPitch));
  }

  /**
   * Calculates the bypass flow area between the outermost tubes and the shell wall.
   *
   * @param shellID shell inside diameter (m)
   * @param bundleDiameter outer tube limit diameter (m)
   * @param baffleSpacing baffle spacing (m)
   * @return bypass area (m2)
   */
  public static double calcBypassArea(double shellID, double bundleDiameter, double baffleSpacing) {
    if (shellID <= 0 || bundleDiameter <= 0 || baffleSpacing <= 0) {
      return 0.0;
    }
    return (shellID - bundleDiameter) * baffleSpacing;
  }
}
