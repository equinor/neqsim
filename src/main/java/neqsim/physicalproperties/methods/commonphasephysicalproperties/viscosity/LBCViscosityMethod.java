package neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.system.PhysicalProperties;
import neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity.FrictionTheoryViscosityMethod;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseType;

/**
 * <p>
 * LBCViscosityMethod class.
 * </p>
 *
 * @author esol
 * @version Method was checked on 2.8.2001 - seems to be correct - Even Solbraa
 */
public class LBCViscosityMethod extends Viscosity {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(LBCViscosityMethod.class);
  private static final double FT3_PER_LBMOL_TO_CM3_PER_MOL = 62.42796;
  private static final double[] DEFAULT_DENSE_CONTRIBUTION_PARAMETERS = {0.10230, 0.023364,
      0.058533, -0.040758, 0.0093324};

  double[] denseContributionParameters = DEFAULT_DENSE_CONTRIBUTION_PARAMETERS.clone();

  /**
   * <p>
   * Constructor for LBCViscosityMethod.
   * </p>
   *
   * @param phase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public LBCViscosityMethod(PhysicalProperties phase) {
    super(phase);
  }

  /** {@inheritDoc} */
  @Override
  public double calcViscosity() {
    double volumeMixSum = 0.0;
    double epsilonMixSum = 0.0;
    double mixtureMolarMassSqrt = 0.0;
    double weightedGasViscosity = 0.0;

    for (int i = 0; i < phase.getPhase().getNumberOfComponents(); i++) {
      ComponentInterface component = phase.getPhase().getComponent(i);
      double criticalVolume = getOrEstimateCriticalVolume(component);
      volumeMixSum += component.getx() * criticalVolume;

      double molarMass = component.getMolarMass() * 1000.0; // g/mol
      double tc = component.getTC();
      double tr = phase.getPhase().getTemperature() / tc;
      double pc = component.getPC(); // bar
      double pcAtm = pc / 1.01325;
      double temp2Epsilon =
          Math.pow(tc, 1.0 / 6.0) / (Math.pow(molarMass, 1.0 / 2.0) * Math.pow(pcAtm, 2.0 / 3.0));

      epsilonMixSum += component.getx() * Math.pow(temp2Epsilon, 6.0);

      double temp2Gas =
          Math.pow(tc, 1.0 / 6.0) / (Math.pow(molarMass, 1.0 / 2.0) * Math.pow(pcAtm, 2.0 / 3.0));
      double lowPressureCorrelation = tr < 1.5 ? 34.0e-5 / temp2Gas * Math.pow(tr, 0.94)
          : 17.78e-5 / temp2Gas * Math.pow(4.58 * tr - 1.67, 5.0 / 8.0);
      lowPressureCorrelation *= 1.0e4; // cP -> micropoise

      double molarMassSqrt = Math.pow(molarMass, 1.0 / 2.0);
      weightedGasViscosity += component.getx() * lowPressureCorrelation * molarMassSqrt;
      mixtureMolarMassSqrt += component.getx() * molarMassSqrt;
    }

    double lowPressureViscosityMicropoise = selectReferenceViscosity(weightedGasViscosity,
        mixtureMolarMassSqrt == 0.0 ? 1.0 : mixtureMolarMassSqrt);

    double pseudoCriticalVolume = volumeMixSum; // cm3/mol
    double critDens = pseudoCriticalVolume > 0.0 ? 1.0 / pseudoCriticalVolume : 0.0; // mol/cm3
    double epsilonMix = Math.pow(epsilonMixSum, 1.0 / 6.0);
    double reducedDensity =
        critDens > 0.0
            ? phase.getPhase().getPhysicalProperties().getDensity()
                / phase.getPhase().getMolarMass() / critDens / 1.0e6
            : 0.0;

    PhaseType phaseType = phase.getPhase().getType();
    double denseContribution = 0.0;
    if (phaseType != PhaseType.AQUEOUS && epsilonMix > 0.0 && reducedDensity > 0.0) {
      double poly = denseContributionParameters[0] + denseContributionParameters[1] * reducedDensity
          + denseContributionParameters[2] * Math.pow(reducedDensity, 2.0)
          + denseContributionParameters[3] * Math.pow(reducedDensity, 3.0)
          + denseContributionParameters[4] * Math.pow(reducedDensity, 4.0);
      denseContribution = Math.max(0.0, (Math.pow(poly, 4.0) - 1.0e-4) / epsilonMix);
      denseContribution *= 1.0e4; // cP -> micropoise
    }

    return (denseContribution + lowPressureViscosityMicropoise) / 1.0e7;
  }

  private double selectReferenceViscosity(double weightedGasViscosity,
      double mixtureMolarMassSqrt) {
    PhaseType phaseType = phase.getPhase().getType();
    if (phaseType == PhaseType.AQUEOUS) {
      return waterViscosityMicropoise();
    } else
      return leeGonzalezEakinGasViscosityMicropoise(weightedGasViscosity, mixtureMolarMassSqrt);
  }


  /**
   * The Lee-Gonzalez-Eakin dilute-gas correlation returns viscosity in micropoise when critical
   * pressure is supplied in bar. This weighted mixture estimate stays in micropoise to match the
   * units expected by the LBC dense-fluid correction.
   *
   * @param weightedGasViscosity weighted gas viscosity in micropoise
   * @param mixtureMolarMassSqrt square root of mixture molar mass
   * @return gas viscosity in micropoise
   */
  private double leeGonzalezEakinGasViscosityMicropoise(double weightedGasViscosity,
      double mixtureMolarMassSqrt) {
    if (mixtureMolarMassSqrt <= 0.0) {
      return 0.0;
    }

    return weightedGasViscosity / mixtureMolarMassSqrt; // micropoise
  }

  private double waterViscosityMicropoise() {
    double temperatureC = phase.getPhase().getTemperature() - 273.15;
    double baseViscosityPaS = 2.414e-5 * Math.pow(10.0, 247.8 / (temperatureC + 133.15));
    double pressureBar = phase.getPhase().getPressure();
    double pressureCorrection = Math.exp(0.002 * Math.max(pressureBar, 0.0));
    return baseViscosityPaS * pressureCorrection * 1.0e7; // Pa·s -> micropoise
  }

  /** {@inheritDoc} */
  @Override
  public double getPureComponentViscosity(int i) {
    return 0;
  }

  /**
   * Set the dense-fluid contribution parameters used by the LBC correlation.
   *
   * @param parameters array of five coefficients for the dense-fluid polynomial term
   */
  public void setDenseContributionParameters(double[] parameters) {
    if (parameters == null || parameters.length != DEFAULT_DENSE_CONTRIBUTION_PARAMETERS.length) {
      throw new IllegalArgumentException(
          "LBC dense contribution requires exactly "
              + DEFAULT_DENSE_CONTRIBUTION_PARAMETERS.length + " parameters");
    }
    denseContributionParameters = parameters.clone();
  }

  /**
   * Set an individual dense-fluid contribution parameter used by the LBC correlation.
   *
   * @param index coefficient index (0-4)
   * @param value coefficient value
   */
  public void setDenseContributionParameter(int index, double value) {
    if (index < 0 || index >= denseContributionParameters.length) {
      throw new IllegalArgumentException(
          "LBC dense contribution parameter index must be between 0 and "
              + (denseContributionParameters.length - 1));
    }
    denseContributionParameters[index] = value;
  }

  /**
   * Get a copy of the current dense-fluid contribution parameters.
   *
   * @return array of dense-fluid contribution coefficients
   */
  public double[] getDenseContributionParameters() {
    return denseContributionParameters.clone();
  }

  /**
   * Estimate the critical volume when it is missing from component data.
   *
   * <p>
   * The LBC method requires critical volume in cm^3/mol. For pseudo components this property is
   * rarely tabulated, so we fall back to the Racket compressibility if available, or the widely
   * used Lee-Kesler style correlation (Zc = 0.29 - 0.087·ω) described by Twu (1984) for petroleum
   * fractions. The correlation keeps Zc within physically reasonable bounds for heavy oils while
   * allowing the viscosity model to operate without bespoke input data.
   * </p>
   *
   * @param component the component to evaluate
   * @return critical volume in cm3/mol
   */
  private double getOrEstimateCriticalVolume(ComponentInterface component) {
    double tbpCriticalVolume = estimateTbpCriticalVolume(component);
    if (tbpCriticalVolume > 0.0) {
      return tbpCriticalVolume;
    }

    double criticalVolume = component.getCriticalVolume();
    if (criticalVolume > 0) {
      return convertToCm3PerMol(criticalVolume);
    }

    double criticalCompressibility = component.getCriticalCompressibilityFactor();
    double acentricFactor = component.getAcentricFactor();

    // Literature correlation for heavy-oil fractions (Twu, 1984). Maintain bounds to avoid
    // unrealistic volumes for extreme pseudo components.
    if (criticalCompressibility <= 0.0) {
      criticalCompressibility = 0.29 - 0.087 * acentricFactor;
      criticalCompressibility = Math.max(0.15, Math.min(criticalCompressibility, 0.35));
    }

    double tc = component.getTC();
    double pc = component.getPC();
    criticalVolume =
        criticalCompressibility * ThermodynamicConstantsInterface.R * tc / (pc * 1.0e5);
    return convertToCm3PerMol(criticalVolume);
  }

  /**
   * Estimate critical volume for TBP fractions using the Whitson correlation (ft3/lbmol) converted
   * to cm3/mol.
   *
   * @param component the component to estimate critical volume for
   * @return critical volume in cm3/mol, or -1.0 if not applicable
   */
  private double estimateTbpCriticalVolume(ComponentInterface component) {
    if (!component.isIsTBPfraction()) {
      return -1.0;

    }

    double liquidDensity = component.getNormalLiquidDensity(); // g/cm3
    double molarMassGPerMol = component.getMolarMass() * 1.0e3; // g/mol
    if (liquidDensity <= 0.0 || molarMassGPerMol <= 0.0) {
      return -1.0;
    }

    double criticalVolumeFt3PerLbmol = 21.573 + 0.015122 * molarMassGPerMol - 27.656 * liquidDensity
        + 0.070615 * molarMassGPerMol * liquidDensity;
    double criticalVolumeCm3PerMol = criticalVolumeFt3PerLbmol * FT3_PER_LBMOL_TO_CM3_PER_MOL;

    if (criticalVolumeCm3PerMol <= 0.0) {
      return -1.0;
    }

    double molarVolumeCm3 = molarMassGPerMol / liquidDensity; // cm3/mol
    return Math.max(criticalVolumeCm3PerMol, molarVolumeCm3 * 1.1);
  }

  private double convertToCm3PerMol(double criticalVolume) {
    // Book correlation requires critical volume in cm3/mol. Component data may be stored in m3/mol,
    // cm3/mol, or occasionally m3/kmol. Use magnitude checks to avoid over-scaling pseudo-component
    // properties.
    if (criticalVolume <= 0.0) {
      return criticalVolume;
    }

    // Values in the 0.01-10 m3 range are typically reported on a kmol basis for TBP cuts.
    if (criticalVolume > 1.0e-2 && criticalVolume < 10.0) {
      return criticalVolume / 1.0e3 * 1.0e6; // m3/kmol -> m3/mol -> cm3/mol
    }

    // m3/mol input
    if (criticalVolume < 1.0e-2) {
      return criticalVolume * 1.0e6; // convert from m3/mol to cm3/mol
    }

    // Already in cm3/mol
    return criticalVolume;
  }

  /** {@inheritDoc} */
  @Override
  public LBCViscosityMethod clone() {
    LBCViscosityMethod method = (LBCViscosityMethod) super.clone();
    method.denseContributionParameters = denseContributionParameters.clone();
    return method;
  }
}
