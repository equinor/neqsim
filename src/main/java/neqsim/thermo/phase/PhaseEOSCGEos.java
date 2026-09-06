package neqsim.thermo.phase;

import org.netlib.util.doubleW;
import neqsim.thermo.component.ComponentEOSCGEos;
import neqsim.thermo.component.ComponentEosInterface;
import neqsim.thermo.util.gerg.NeqSimEOSCG;
import neqsim.util.exception.IsNaNException;
import neqsim.util.exception.TooManyIterationsException;

/** Phase implementation using the EOS-CG mixture model. */
public class PhaseEOSCGEos extends PhaseGERG2008Eos {
  private static final long serialVersionUID = 1000;
  private static final double PRESSURE_RELATIVE_TOLERANCE = 1.0e-6;

  private transient boolean initializationInProgress;
  private transient PhaseType resolvedRootType;
  private transient PhaseType propertyRootType;

  /** Create an EOS-CG phase. */
  public PhaseEOSCGEos() {
    thermoPropertyModelName = "EOS-CG";
  }

  /** {@inheritDoc} */
  @Override
  public PhaseEOSCGEos clone() {
    PhaseEOSCGEos clonedPhase = (PhaseEOSCGEos) super.clone();
    if (clonedPhase != null) {
      clonedPhase.invalidateCache();
      clonedPhase.initializationInProgress = false;
      clonedPhase.resolvedRootType = null;
      clonedPhase.propertyRootType = null;
    }
    return clonedPhase;
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int componentNumber) {
    super.addComponent(name, moles, molesInPhase, componentNumber);
    componentArray[componentNumber] = new ComponentEOSCGEos(name, moles, molesInPhase, componentNumber);
  }

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType phaseType, double beta) {
    resolvedRootType = referenceRootType(phaseType);
    initializationInProgress = true;
    try {
      super.init(totalNumberOfMoles, numberOfComponents, initType, phaseType, beta);
    } finally {
      initializationInProgress = false;
    }

    setType(resolvedRootType);
    if (initType >= 1) {
      refreshProperties(resolvedRootType);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double molarVolume(double pressure, double temperature, double attractionParameter, double covolumeParameter,
      PhaseType phaseType) throws IsNaNException, TooManyIterationsException {
    double molarDensityMolPerL = resolveMolarDensity(referenceRootType(phaseType));
    double densityKgPerM3 = molarDensityMolPerL * getMolarMass() * 1000.0;
    if (!Double.isFinite(densityKgPerM3) || densityKgPerM3 <= 0.0) {
      throw new IsNaNException(this, "molarVolume", "EOS-CG density");
    }
    return getMolarMass() * 1.0e5 / densityKgPerM3;
  }

  /** {@inheritDoc} */
  @Override
  public double getDensity_GERG2008() {
    double molarDensityMolPerL = resolveMolarDensity(activeRootType());
    return molarDensityMolPerL * getMolarMass() * 1000.0;
  }

  /** {@inheritDoc} */
  @Override
  public double[] getProperties_GERG2008() {
    NeqSimEOSCG eosCG = new NeqSimEOSCG(this);
    return eosCG.propertiesEOSCG(resolveMolarDensity(eosCG, activeRootType()));
  }

  /** {@inheritDoc} */
  @Override
  public doubleW[] getAlpha0_GERG2008() {
    NeqSimEOSCG eosCG = new NeqSimEOSCG(this);
    return eosCG.getAlpha0_EOSCG(resolveMolarDensity(eosCG, activeRootType()));
  }

  /** {@inheritDoc} */
  @Override
  public doubleW[][] getAlphares_GERG2008() {
    NeqSimEOSCG eosCG = new NeqSimEOSCG(this);
    return eosCG.getAlphares_EOSCG(resolveMolarDensity(eosCG, activeRootType()));
  }

  /** {@inheritDoc} */
  @Override
  public double dFdN(int componentNumber) {
    return ((ComponentEosInterface) getComponent(componentNumber)).dFdN(this, getNumberOfComponents(), temperature,
        pressure);
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdN(int componentNumber, int otherComponentNumber) {
    return ((ComponentEosInterface) getComponent(componentNumber)).dFdNdN(otherComponentNumber, this,
        getNumberOfComponents(), temperature, pressure);
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdV(int componentNumber) {
    return ((ComponentEosInterface) getComponent(componentNumber)).dFdNdV(this, getNumberOfComponents(), temperature,
        pressure);
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdT(int componentNumber) {
    return ((ComponentEosInterface) getComponent(componentNumber)).dFdNdT(this, getNumberOfComponents(), temperature,
        pressure);
  }

  /** {@inheritDoc} */
  @Override
  public double getdPdTVn() {
    return (getNumberOfMolesInPhase() / getVolume()) * R * (1 + ar[0][1].val - ar[1][1].val);
  }

  /** {@inheritDoc} */
  @Override
  protected double residualHelmholtzEnergy(double molarDensity, double[] composition) {
    PhaseEOSCGEos evaluationPhase = clone();
    for (int componentNumber = 0; componentNumber < composition.length; componentNumber++) {
      evaluationPhase.getComponent(componentNumber).setx(composition[componentNumber]);
    }
    NeqSimEOSCG eosCG = new NeqSimEOSCG(evaluationPhase);
    return eosCG.getResidualHelmholtzEnergy(temperature, molarDensity);
  }

  /** {@inheritDoc} */
  @Override
  public double getMolarVolume() {
    ensurePropertiesMatchPhaseType();
    return super.getMolarVolume();
  }

  /** {@inheritDoc} */
  @Override
  public double getZ() {
    ensurePropertiesMatchPhaseType();
    return super.getZ();
  }

  /** {@inheritDoc} */
  @Override
  public double getGibbsEnergy() {
    ensurePropertiesMatchPhaseType();
    return super.getGibbsEnergy();
  }

  /** {@inheritDoc} */
  @Override
  public double getEnthalpy() {
    ensurePropertiesMatchPhaseType();
    return super.getEnthalpy();
  }

  /** {@inheritDoc} */
  @Override
  public double getEntropy() {
    ensurePropertiesMatchPhaseType();
    return super.getEntropy();
  }

  /** {@inheritDoc} */
  @Override
  public double getInternalEnergy() {
    ensurePropertiesMatchPhaseType();
    return super.getInternalEnergy();
  }

  /** {@inheritDoc} */
  @Override
  public double getCp() {
    ensurePropertiesMatchPhaseType();
    return super.getCp();
  }

  /** {@inheritDoc} */
  @Override
  public double getCv() {
    ensurePropertiesMatchPhaseType();
    return super.getCv();
  }

  /** {@inheritDoc} */
  @Override
  public double getJouleThomsonCoefficient() {
    ensurePropertiesMatchPhaseType();
    return super.getJouleThomsonCoefficient();
  }

  private void ensurePropertiesMatchPhaseType() {
    if (!initializationInProgress && getNumberOfComponents() > 0 && propertyRootType != referenceRootType(getType())) {
      refreshProperties(referenceRootType(getType()));
    }
  }

  private void refreshProperties(PhaseType requestedRootType) {
    NeqSimEOSCG eosCG = new NeqSimEOSCG(this);
    double molarDensityMolPerL = resolveMolarDensity(eosCG, requestedRootType);
    setType(resolvedRootType);
    double densityKgPerM3 = molarDensityMolPerL * getMolarMass() * 1000.0;
    double[] properties = eosCG.propertiesEOSCG(molarDensityMolPerL);
    validatePropertyState(properties, densityKgPerM3, requestedRootType);

    a0 = eosCG.getAlpha0_EOSCG(molarDensityMolPerL);
    ar = eosCG.getAlphares_EOSCG(molarDensityMolPerL);
    molarVolume = getMolarMass() * 1.0e5 / densityKgPerM3;
    Z = properties[1];
    internalEnery = properties[6];
    enthalpy = properties[7];
    entropy = properties[8];
    CvGERG2008 = properties[9];
    CpGERG2008 = properties[10];
    W = properties[11];
    gibbsEnergy = properties[12];
    JTcoef = properties[13];
    kappa = properties[14];
    propertyRootType = resolvedRootType;
  }

  private void validatePropertyState(double[] properties, double densityKgPerM3, PhaseType requestedRootType) {
    if (!Double.isFinite(densityKgPerM3) || densityKgPerM3 <= 0.0) {
      throw new IllegalStateException("EOS-CG density is invalid for phase type " + requestedRootType);
    }
    double calculatedPressureBar = properties[0] / 100.0;
    double pressureTolerance = PRESSURE_RELATIVE_TOLERANCE * Math.max(Math.abs(pressure), 1.0);
    if (!Double.isFinite(calculatedPressureBar) || Math.abs(calculatedPressureBar - pressure) > pressureTolerance) {
      throw new IllegalStateException("EOS-CG density root does not reproduce the specified pressure: specified="
          + pressure + " bar, calculated=" + calculatedPressureBar + " bar, phase type=" + requestedRootType);
    }
  }

  private double resolveMolarDensity(PhaseType requestedRootType) {
    return resolveMolarDensity(new NeqSimEOSCG(this), requestedRootType);
  }

  private double resolveMolarDensity(NeqSimEOSCG eosCG, PhaseType requestedRootType) {
    try {
      double molarDensity = eosCG.getMolarDensity(requestedRootType);
      resolvedRootType = requestedRootType;
      return molarDensity;
    } catch (IllegalStateException requestedRootFailure) {
      PhaseType fallbackRootType = isLiquidRoot(requestedRootType) ? PhaseType.GAS : PhaseType.LIQUID;
      try {
        double molarDensity = eosCG.getMolarDensity(fallbackRootType);
        resolvedRootType = fallbackRootType;
        return molarDensity;
      } catch (IllegalStateException fallbackRootFailure) {
        requestedRootFailure.addSuppressed(fallbackRootFailure);
        throw requestedRootFailure;
      }
    }
  }

  private PhaseType activeRootType() {
    if (initializationInProgress && resolvedRootType != null) {
      return resolvedRootType;
    }
    return referenceRootType(getType());
  }

  private static PhaseType referenceRootType(PhaseType phaseType) {
    return isLiquidRoot(phaseType) ? PhaseType.LIQUID : PhaseType.GAS;
  }

  private static boolean isLiquidRoot(PhaseType phaseType) {
    return phaseType == PhaseType.LIQUID || phaseType == PhaseType.OIL || phaseType == PhaseType.AQUEOUS;
  }
}
