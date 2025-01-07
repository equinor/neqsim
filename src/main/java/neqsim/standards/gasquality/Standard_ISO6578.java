package neqsim.standards.gasquality;

import org.apache.commons.math3.analysis.interpolation.BicubicInterpolatingFunction;
import org.apache.commons.math3.analysis.interpolation.BicubicInterpolator;
import org.apache.commons.math3.analysis.interpolation.LinearInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * Standard_ISO6578 class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class Standard_ISO6578 extends neqsim.standards.Standard {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  double LNGdensity = 0.0;
  String densityUnit = "kg/m^3";
  double KMcorrectionFactor1 = 0.0;
  double KMcorrectionFactor2 = 0.0;
  double[] Vi = null;
  boolean use6578volumeCorrectionFactors = true;
  double[] temperatures = {105.0, 110.0, 115.0, 120.0, 125.0, 130.0, 135.0};
  double[] molarMasses = {16.0, 17.0, 18.0, 19.0, 20.0, 21.0, 22.0, 23.0, 24.0, 25.0};
  double[][] KMKMcorrectionFactor1Matrix = {{-0.007, -0.008, -0.009, -0.01, -0.013, -0.015, -0.017},
      {0.165, 0.19, 0.22, 0.25, 0.295, 0.345, 0.4}, {0.34, 0.375, 0.44, 0.5, 0.59, 0.7, 0.825},
      {0.475, 0.535, 0.61, 0.695, 0.795, 0.92, 1.06}, {0.635, 0.725, 0.81, 0.92, 1.035, 1.2, 1.39},
      {0.735, 0.835, 0.945, 1.055, 1.21, 1.37, 1.59}, {0.84, 0.95, 1.065, 1.205, 1.385, 1.555, 1.8},
      {0.92, 1.055, 1.18, 1.33, 1.525, 1.715, 1.95}, {1.045, 1.155, 1.28, 1.45, 1.64, 1.86, 2.105},
      {1.12, 1.245, 1.38, 1.55, 1.75, 1.99, 2.272}};
  double[][] KMKMcorrectionFactor2Matrix = {{-0.01, -0.015, -0.024, -0.032, -0.043, -0.058, -0.075},
      {0.24, 0.32, 0.41, 0.6, 0.71, 0.95, 1.3}, {0.42, 0.59, 0.72, 0.91, 1.13, 1.46, 2},
      {0.61, 0.77, 0.95, 1.23, 1.48, 1.92, 2.4}, {0.75, 0.92, 1.15, 1.43, 1.73, 2.2, 2.6},
      {0.91, 1.07, 1.22, 1.63, 1.98, 2.42, 3}, {1.05, 1.22, 1.3, 1.85, 2.23, 2.68, 3.4},
      {1.19, 1.37, 1.45, 2.08, 2.48, 3, 3.77}, {1.33, 1.52, 1.65, 2.3, 2.75, 3.32, 3.99},
      {1.45, 1.71, 2, 2.45, 2.9, 3.52, 4.23}};
  double[] ISO6578temperatures =
      {93.15, 98.15, 103.15, 108.15, 113.15, 118.15, 123.15, 128.15, 133.15};
  double[] ISO6578molarMasses =
      {16.0, 17.0, 18.0, 19.0, 20.0, 21.0, 22.0, 23.0, 24.0, 25.0, 26.0, 27.0, 28.0, 29.0, 30.0};
  double[][] ISO6578KMKMcorrectionFactor1Matrix =
      {{-0.01, -0.01, -0.01, -0.01, -0.01, -0.01, -0.01, -0.01, -0.01},
          {0.13, 0.15, 0.16, 0.18, 0.21, 0.24, 0.28, 0.33, 0.38},
          {0.25, 0.29, 0.33, 0.37, 0.41, 0.47, 0.56, 0.66, 0.76},
          {0.37, 0.41, 0.45, 0.51, 0.58, 0.67, 0.76, 0.87, 1.01},
          {0.47, 0.52, 0.59, 0.67, 0.76, 0.86, 0.98, 1.01, 1.3},
          {0.55, 0.62, 0.7, 0.79, 0.89, 1.0, 1.13, 1.29, 1.45},
          {0.64, 0.72, 0.81, 0.90, 1.01, 1.17, 1.32, 1.52, 1.71},
          {0.72, 0.82, 0.92, 1.02, 1.15, 1.33, 1.53, 1.68, 1.84},
          {0.81, 0.92, 1.04, 1.16, 1.3, 1.47, 1.66, 1.87, 2.13},
          {0.88, 1.0, 1.12, 1.25, 1.41, 1.58, 1.78, 2.0, 2.27},
          {0.95, 1.07, 1.19, 1.33, 1.5, 1.68, 1.89, 2.13, 2.41},
          {1.01, 1.13, 1.26, 1.41, 1.58, 1.78, 1.99, 2.24, 2.53},
          {1.06, 1.18, 1.32, 1.47, 1.64, 1.84, 2.06, 2.32, 2.62},
          {1.11, 1.23, 1.37, 1.54, 1.72, 1.92, 2.15, 2.42, 2.73},
          {1.16, 1.29, 1.43, 1.6, 1.79, 2.0, 2.24, 2.51, 2.83}};
  double[][] ISO6578KMKMcorrectionFactor2Matrix =
      {{0, -0.01, -0.01, -0.01, -0.02, -0.03, -0.04, -0.05, -0.07},
          {0.11, 0.15, 0.21, 0.29, 0.46, 0.68, 0.91, 1.21, 1.6},
          {0.26, 0.32, 0.39, 0.53, 0.67, 0.84, 1.05, 1.34, 1.8},
          {0.4, 0.47, 0.57, 0.71, 0.88, 1.13, 1.39, 1.76, 2.22},
          {0.56, 0.62, 0.71, 0.86, 1.06, 1.33, 1.62, 2.03, 2.45},
          {0.67, 0.76, 0.87, 1.01, 1.16, 1.48, 1.85, 2.26, 2.79},
          {0.78, 0.9, 1.01, 1.16, 1.27, 1.65, 2.09, 2.51, 3.13},
          {0.88, 1.03, 1.15, 1.3, 1.42, 1.85, 2.33, 2.81, 3.49},
          {0.98, 1.13, 1.27, 1.45, 1.6, 2.06, 2.58, 3.11, 3.74},
          {1.07, 1.22, 1.38, 1.61, 1.89, 2.28, 2.73, 3.29, 3.97},
          {1.15, 1.31, 1.5, 1.74, 2.04, 2.44, 2.92, 3.48, 4.19},
          {1.22, 1.4, 1.61, 1.87, 2.19, 2.6, 3.1, 3.71, 4.46},
          {1.31, 1.5, 1.72, 1.99, 2.33, 2.77, 3.31, 3.95, 4.74},
          {1.38, 1.59, 1.83, 2.12, 2.48, 2.95, 3.51, 4.19, 5.03},
          {1.47, 1.68, 1.93, 2.24, 2.63, 3.12, 3.72, 4.45, 5.34}};
  BicubicInterpolatingFunction pcs1 = null;
  BicubicInterpolatingFunction pcs2 = null;
  LinearInterpolator liearInterpol = new LinearInterpolator();
  double[] Vitemperatures = ISO6578temperatures; // {-180.0, -175.0, -170.0, -165.0, -160.0,
                                                 // -155.0, -150.0, -145.0,
                                                 // -140.0};
  double[] Vimethane =
      {0.035771, 0.036315, 0.036891, 0.037500, 0.038149, 0.038839, 0.039580, 0.040375, 0.041237};
  double[] Viethane =
      {0.046324, 0.046716, 0.047116, 0.047524, 0.0479422, 0.048369, 0.048806, 0.049253, 0.049711};
  double[] Vipropane =
      {0.060731, 0.061164, 0.061602, 0.062046, 0.062497, 0.062953, 0.063417, 0.063887, 0.064364};
  double[] VinC4 =
      {0.074997, 0.075459, 0.075926, 0.076398, 0.076875, 0.077359, 0.077849, 0.078342, 0.078843};
  double[] ViiC4 =
      {0.076384, 0.076868, 0.077356, 0.077851, 0.078352, 0.078859, 0.079374, 0.079896, 0.080425};
  double[] VinC5 =
      {0.089498, 0.090016, 0.090536, 0.091058, 0.091583, 0.092111, 0.092642, 0.093177, 0.093715};
  double[] ViiC5 =
      {0.089576, 0.090107, 0.090642, 0.091179, 0.091721, 0.092267, 0.092817, 0.093372, 0.093930};
  double[] VinC6 =
      {0.10273, 0.10326, 0.10380, 0.10434, 0.10489, 0.10545, 0.10602, 0.10659, 0.10716};
  double[] Vinitrogen =
      {0.038408, 0.039949, 0.041788, 0.0440143, 0.047019, 0.051022, 0.055897, 0.061767, 0.069064};

  /**
   * <p>
   * Constructor for Standard_ISO6578.
   * </p>
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public Standard_ISO6578(SystemInterface thermoSystem) {
    super("Standard_ISO6578", "LNG density calcuation method", thermoSystem);
    setCorrectionFactors();
  }

  /**
   * <p>
   * useISO6578VolumeCorrectionFacotrs.
   * </p>
   *
   * @param useFactors a boolean
   */
  public void useISO6578VolumeCorrectionFacotrs(boolean useFactors) {
    use6578volumeCorrectionFactors = useFactors;
    setCorrectionFactors();
  }

  /**
   * <p>
   * setCorrectionFactors.
   * </p>
   */
  public void setCorrectionFactors() {
    BicubicInterpolator tempInterp = new BicubicInterpolator();
    if (use6578volumeCorrectionFactors) {
      pcs1 = tempInterp.interpolate(ISO6578molarMasses, ISO6578temperatures,
          ISO6578KMKMcorrectionFactor1Matrix);
      pcs2 = tempInterp.interpolate(ISO6578molarMasses, ISO6578temperatures,
          ISO6578KMKMcorrectionFactor2Matrix);
    } else {
      pcs1 = tempInterp.interpolate(molarMasses, temperatures, KMKMcorrectionFactor1Matrix);
      pcs2 = tempInterp.interpolate(molarMasses, temperatures, KMKMcorrectionFactor2Matrix);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void calculate() {
    double Vmix = 0.0;
    Vi = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
    PolynomialSplineFunction function = null;
    for (int i = 0; i < thermoSystem.getPhase(0).getNumberOfComponents(); i++) {
      // double a, b, c;
      if (thermoSystem.getPhase(0).getComponent(i).getName().equals("methane")) {
        // a = 8.452e-7;
        // b = -5.744e-5;
        // c = 3.383e-2;
        function = liearInterpol.interpolate(Vitemperatures, Vimethane);
      } else if (thermoSystem.getPhase(0).getComponent(i).getName().equals("ethane")) {
        // a = 1.905e-7;
        // b = 4.133e-5;
        // c = 4.083e-2;
        function = liearInterpol.interpolate(Vitemperatures, Viethane);
      } else if (thermoSystem.getPhase(0).getComponent(i).getName().equals("propane")) {
        // a = 1.310e-7;
        // b = 6.102e-5;
        // c = 5.391e-2;
        function = liearInterpol.interpolate(Vitemperatures, Vipropane);
      } else if (thermoSystem.getPhase(0).getComponent(i).getName().equals("i-butane")) {
        // a = 1.25e-7;
        // b = 7.257e-5;
        // c = 6.854e-2;
        function = liearInterpol.interpolate(Vitemperatures, ViiC4);
      } else if (thermoSystem.getPhase(0).getComponent(i).getName().equals("n-butane")) {
        // a = 1.101e-7;
        // b = 7.114e-5;
        // c = 6.742e-2;
        function = liearInterpol.interpolate(Vitemperatures, VinC4);
      } else if (thermoSystem.getPhase(0).getComponent(i).getName().equals("i-pentane")
          || thermoSystem.getPhase(0).getComponent(i).getName().equals("iC5")) {
        // a = 9.524e-8;
        // b = 8.727e-5;
        // c = 8.063e-2;
        function = liearInterpol.interpolate(Vitemperatures, ViiC5);
      } else if (thermoSystem.getPhase(0).getComponent(i).getName().equals("n-pentane")) {
        // a = 5.060e-8;
        // b = 9.386e-5;
        // c = 8.031e-2;
        function = liearInterpol.interpolate(Vitemperatures, VinC5);
      } else if (thermoSystem.getPhase(0).getComponent(i).getName().equals("n-hexane")) {
        // a = 5.060e-8;
        // b = 9.386e-5;
        // c = 8.031e-2;
        function = liearInterpol.interpolate(Vitemperatures, VinC6);
      } else if (thermoSystem.getPhase(0).getComponent(i).getName().equals("nitrogen")) {
        // a = 1.968e-5;
        // b = -3.753e-3;
        // c = 2.198e-1;
        function = liearInterpol.interpolate(Vitemperatures, Vinitrogen);
      } else {
        Vi[i] = 0.036315;
      }
      // Vi[i] = c + a * Math.pow(thermoSystem.getTemperature(), 2.0) + b *
      // thermoSystem.getTemperature();
      try {
        Vi[i] = function.value(thermoSystem.getTemperature());
      } catch (Exception ex) {
        // logger.error(ex.getMessage(), ex);
        // System.out.println("volume "+
        // (thermoSystem.getPhase(0).getMolarVolume())/10e4);
        Vi[i] = thermoSystem.getPhase(1).getMolarVolume() / 100.0;
        // Vi[i] = 0.036315;
      }
      // System.out.println("volume "+
      // (thermoSystem.getPhase(1).getMolarVolume())/100.0);

      Vmix += thermoSystem.getPhase(0).getComponent(i).getx() * Vi[i];
    }

    double xn2 = 0.0;
    double xch4 = 0.0;
    try {
      KMcorrectionFactor1 = pcs1.value(thermoSystem.getPhase(0).getMolarMass() * 1000.0,
          thermoSystem.getTemperature());
      KMcorrectionFactor2 = pcs2.value(thermoSystem.getPhase(0).getMolarMass() * 1000.0,
          thermoSystem.getTemperature());
    } catch (Exception ex) {
      // logger.error(ex.getMessage(), ex);
      KMcorrectionFactor1 = 0.0;
      KMcorrectionFactor2 = 0.0;
    }

    // System.out.println("KMcorrectionFactor1 " + KMcorrectionFactor1);
    // System.out.println("KMcorrectionFactor2 " + KMcorrectionFactor2);
    if (thermoSystem.getPhase(0).hasComponent("nitrogen")) {
      xn2 = thermoSystem.getPhase(0).getComponent("nitrogen").getx();
    }
    if (thermoSystem.getPhase(0).hasComponent("methane")) {
      xch4 = thermoSystem.getPhase(0).getComponent("methane").getx();
    }

    Vmix -= (KMcorrectionFactor1 + (KMcorrectionFactor2 - KMcorrectionFactor1) * (xn2 / 0.0425))
        * xch4 / 1000.0;

    LNGdensity = thermoSystem.getPhase(0).getMolarMass() * 1000.0 / Vmix;
    // System.out.println("LNG density " + LNGdensity);
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter, String returnUnit) {
    return LNGdensity;
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter) {
    return LNGdensity;
  }

  /** {@inheritDoc} */
  @Override
  public String getUnit(String returnParameter) {
    return densityUnit;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isOnSpec() {
    return true;
  }

  /**
   * <p>
   * getCorrFactor1.
   * </p>
   *
   * @return a double
   */
  public double getCorrFactor1() {
    return KMcorrectionFactor1;
  }

  /**
   * <p>
   * getCorrFactor2.
   * </p>
   *
   * @return a double
   */
  public double getCorrFactor2() {
    return KMcorrectionFactor2;
  }
}
