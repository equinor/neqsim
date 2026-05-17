package neqsim.process.equipment.pump;

import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoints;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.compressor.CompressorChartAlternativeMapLookupExtrapolate;

/**
 * Alternative pump chart implementation extending the compressor chart with map lookup and
 * extrapolation, adding pump-specific features: NPSH curves, density correction, viscosity
 * correction (HI method), BEP identification, specific speed, and operating status monitoring.
 *
 * <p>
 * Delegates base head and efficiency to the compressor chart's polytropic methods, then applies
 * pump-specific corrections on top.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class PumpChartAlternativeMapLookupExtrapolate
    extends CompressorChartAlternativeMapLookupExtrapolate implements PumpChartInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PumpChartAlternativeMapLookupExtrapolate.class);

  /** Whether pump chart mode is active. */
  private boolean usePumpChart = false;

  /** Reference density for density correction in kg/m3 (-1 means disabled). */
  private double referenceDensity = -1.0;

  /** Reference viscosity in cSt (-1 means not set). */
  private double referenceViscosity = -1.0;

  /** Whether viscosity correction is enabled. */
  private boolean useViscosityCorrection = false;

  /** Cached HI correction factors. */
  private double cQ = 1.0;
  private double cH = 1.0;
  private double cEta = 1.0;
  private double lastViscosity = -1.0;

  /** NPSH curve data. */
  private double[][] npshData = null;
  private transient PolynomialFunction npshFunc = null;
  private boolean npshCurveAvailable = false;

  /** Cached BEP flow (lazy). */
  private double cachedBEPFlow = -1.0;

  /** {@inheritDoc} */
  @Override
  public double getHead(double flow, double speed) {
    return getPolytropicHead(flow, speed);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isUsePumpChart() {
    return usePumpChart;
  }

  /** {@inheritDoc} */
  @Override
  public double getEfficiency(double flow, double speed) {
    return getPolytropicEfficiency(flow, speed);
  }

  /** {@inheritDoc} */
  @Override
  public void setUsePumpChart(boolean usePumpChart) {
    this.usePumpChart = usePumpChart;
  }

  /** {@inheritDoc} */
  @Override
  public double getBestEfficiencyFlowRate() {
    if (cachedBEPFlow > 0) {
      return cachedBEPFlow;
    }
    // Scan across the flow range of the underlying compressor chart to find peak efficiency.
    // The parent chart stores speed curves; we evaluate at the first available speed.
    double bestFlow = 0.0;
    double bestEff = -1.0;
    double[] speeds = getSpeeds();
    if (speeds == null || speeds.length == 0) {
      return 0.0;
    }
    double testSpeed = speeds[0];
    double minFlow = getMinimumFlow();
    double maxFlow = getMaximumFlow();
    if (maxFlow <= minFlow || maxFlow <= 0) {
      return 0.0;
    }
    int nPoints = 50;
    for (int i = 0; i <= nPoints; i++) {
      double f = minFlow + (maxFlow - minFlow) * i / nPoints;
      double eff = getEfficiency(f, testSpeed);
      if (eff > bestEff) {
        bestEff = eff;
        bestFlow = f;
      }
    }
    cachedBEPFlow = bestFlow;
    return bestFlow;
  }

  /** {@inheritDoc} */
  @Override
  public double getSpecificSpeed() {
    double flowBEP = getBestEfficiencyFlowRate();
    if (flowBEP <= 0) {
      return 0.0;
    }
    double[] speeds = getSpeeds();
    if (speeds == null || speeds.length == 0) {
      return 0.0;
    }
    double speed = speeds[0];
    double headBEP = getHead(flowBEP, speed);
    if (headBEP <= 0) {
      return 0.0;
    }
    // Convert head to meters if in kJ/kg: H_m = H_kJ/kg * 1000 / g
    String headUnit = getHeadUnit();
    double headMeters = headBEP;
    if ("kJ/kg".equals(headUnit)) {
      headMeters = headBEP * 1000.0 / 9.80665;
    }
    // Ns = N * sqrt(Q_m3s) / H_m^0.75
    double flowM3s = flowBEP / 3600.0;
    return speed * Math.sqrt(flowM3s) / Math.pow(headMeters, 0.75);
  }

  /** {@inheritDoc} */
  @Override
  public String getOperatingStatus(double flow, double speed) {
    double[] speeds = getSpeeds();
    if (speeds == null || speeds.length == 0) {
      return "UNKNOWN";
    }
    double maxFlow = getMaximumFlow();
    if (maxFlow > 0 && flow > maxFlow * 1.1) {
      return "STONEWALL";
    }
    double minFlow = getMinimumFlow();
    if (minFlow > 0 && flow < minFlow * 0.9) {
      return "SURGE";
    }
    double eff = getEfficiency(flow, speed);
    if (eff < 30.0) {
      return "LOW_EFFICIENCY";
    }
    double bepFlow = getBestEfficiencyFlowRate();
    if (bepFlow > 0 && Math.abs(flow - bepFlow) / bepFlow < 0.2) {
      return "OPTIMAL";
    }
    return "NORMAL";
  }

  /** {@inheritDoc} */
  @Override
  public void setNPSHCurve(double[][] npshRequired) {
    if (npshRequired == null || npshRequired.length == 0) {
      logger.warn("Empty NPSH data provided");
      return;
    }
    this.npshData = npshRequired;
    // Fit a 2nd-order polynomial to the first speed's NPSH vs flow data.
    // NPSH typically increases with flow (minimum at low flow).
    WeightedObservedPoints points = new WeightedObservedPoints();
    double[] speedArr = getSpeeds();
    if (speedArr != null && speedArr.length > 0) {
      double refSpeed = speedArr[0];
      for (int i = 0; i < npshRequired.length; i++) {
        double s = (speedArr != null && i < speedArr.length) ? speedArr[i] : refSpeed;
        double[][] flowArr = getFlows();
        if (flowArr != null && i < flowArr.length) {
          for (int j = 0; j < npshRequired[i].length && j < flowArr[i].length; j++) {
            // Reduced NPSH: NPSH / N^2
            double rFlow = flowArr[i][j] / s;
            double rNpsh = npshRequired[i][j] / (s * s);
            points.add(rFlow, rNpsh);
          }
        }
      }
    }
    if (!points.toList().isEmpty()) {
      PolynomialCurveFitter fitter = PolynomialCurveFitter.create(2);
      npshFunc = new PolynomialFunction(fitter.fit(points.toList()));
      npshCurveAvailable = true;
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getNPSHRequired(double flow, double speed) {
    if (!npshCurveAvailable || npshFunc == null) {
      return 0.0;
    }
    double rFlow = flow / speed;
    double rNpsh = npshFunc.value(rFlow);
    if (rNpsh < 0.0) {
      rNpsh = 0.01;
    }
    return rNpsh * speed * speed;
  }

  /** {@inheritDoc} */
  @Override
  public boolean hasNPSHCurve() {
    return npshCurveAvailable;
  }

  /** {@inheritDoc} */
  @Override
  public double getReferenceDensity() {
    return referenceDensity;
  }

  /** {@inheritDoc} */
  @Override
  public void setReferenceDensity(double referenceDensity) {
    this.referenceDensity = referenceDensity;
  }

  /** {@inheritDoc} */
  @Override
  public boolean hasDensityCorrection() {
    return referenceDensity > 0;
  }

  /** {@inheritDoc} */
  @Override
  public double getCorrectedHead(double flow, double speed, double actualDensity) {
    double chartHead = getHead(flow, speed);
    if (referenceDensity > 0 && actualDensity > 0) {
      return chartHead * (referenceDensity / actualDensity);
    }
    return chartHead;
  }

  /** {@inheritDoc} */
  @Override
  public void calculateViscosityCorrection(double viscosity, double flowBEP, double headBEP,
      double speed) {
    if (viscosity <= 1.0) {
      cQ = 1.0;
      cH = 1.0;
      cEta = 1.0;
      lastViscosity = viscosity;
      return;
    }
    // HI method: B = 26.6 * nu^0.5 * H_ft^0.0625 / (Q_gpm^0.375 * N^0.25)
    double qGpm = flowBEP * 4.40287;
    double hFt = headBEP * 3.28084;
    if (qGpm <= 0 || hFt <= 0) {
      cQ = 1.0;
      cH = 1.0;
      cEta = 1.0;
      return;
    }
    double paramB = 26.6 * Math.pow(viscosity, 0.5) * Math.pow(hFt, 0.0625)
        / (Math.pow(qGpm, 0.375) * Math.pow(speed, 0.25));
    if (paramB <= 1.0) {
      cQ = 1.0;
      cH = 1.0;
      cEta = 1.0;
    } else if (paramB <= 40.0) {
      cQ = Math.max(0.6, 1.0 - 0.01 * Math.pow(paramB, 0.9));
      cH = Math.max(0.7, 1.0 - 0.008 * paramB);
      cEta = Math.max(0.4, 1.0 - 0.015 * Math.pow(paramB, 0.85));
    } else {
      cQ = 0.6;
      cH = 0.7;
      cEta = 0.4;
    }
    lastViscosity = viscosity;
    useViscosityCorrection = true;
  }

  /** {@inheritDoc} */
  @Override
  public double getFullyCorrectedHead(double flow, double speed, double actualDensity,
      double actualViscosity) {
    if (useViscosityCorrection && actualViscosity > 1.0) {
      if (Math.abs(actualViscosity - lastViscosity) > 0.1) {
        double flowBEP = getBestEfficiencyFlowRate();
        double headBEP = getHead(flowBEP, speed);
        calculateViscosityCorrection(actualViscosity, flowBEP, headBEP, speed);
      }
      double waterFlow = flow / cQ;
      double waterHead = getHead(waterFlow, speed);
      double corrHead = waterHead * cH;
      if (referenceDensity > 0 && actualDensity > 0) {
        return corrHead * (referenceDensity / actualDensity);
      }
      return corrHead;
    }
    return getCorrectedHead(flow, speed, actualDensity);
  }

  /** {@inheritDoc} */
  @Override
  public double getCorrectedEfficiency(double flow, double speed, double actualViscosity) {
    double baseEff = getEfficiency(flow, speed);
    if (useViscosityCorrection && actualViscosity > 1.0) {
      if (Math.abs(actualViscosity - lastViscosity) > 0.1) {
        double flowBEP = getBestEfficiencyFlowRate();
        double headBEP = getHead(flowBEP, speed);
        calculateViscosityCorrection(actualViscosity, flowBEP, headBEP, speed);
      }
      return baseEff * cEta;
    }
    return baseEff;
  }

  /** {@inheritDoc} */
  @Override
  public void setReferenceViscosity(double referenceViscosity) {
    this.referenceViscosity = referenceViscosity;
  }

  /** {@inheritDoc} */
  @Override
  public double getReferenceViscosity() {
    return referenceViscosity;
  }

  /** {@inheritDoc} */
  @Override
  public void setUseViscosityCorrection(boolean useViscosityCorrection) {
    this.useViscosityCorrection = useViscosityCorrection;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isUseViscosityCorrection() {
    return useViscosityCorrection;
  }

  /** {@inheritDoc} */
  @Override
  public double getFlowCorrectionFactor() {
    return cQ;
  }

  /** {@inheritDoc} */
  @Override
  public double getHeadCorrectionFactor() {
    return cH;
  }

  /** {@inheritDoc} */
  @Override
  public double getEfficiencyCorrectionFactor() {
    return cEta;
  }

  /**
   * Get minimum flow across all curves.
   *
   * @return minimum flow rate
   */
  private double getMinimumFlow() {
    double[][] flows = getFlows();
    if (flows == null) {
      return 0.0;
    }
    double min = Double.MAX_VALUE;
    for (double[] row : flows) {
      if (row != null) {
        for (double f : row) {
          if (f < min) {
            min = f;
          }
        }
      }
    }
    return min == Double.MAX_VALUE ? 0.0 : min;
  }

  /**
   * Get maximum flow across all curves.
   *
   * @return maximum flow rate
   */
  private double getMaximumFlow() {
    double[][] flows = getFlows();
    if (flows == null) {
      return 0.0;
    }
    double max = -Double.MAX_VALUE;
    for (double[] row : flows) {
      if (row != null) {
        for (double f : row) {
          if (f > max) {
            max = f;
          }
        }
      }
    }
    return max == -Double.MAX_VALUE ? 0.0 : max;
  }
}
