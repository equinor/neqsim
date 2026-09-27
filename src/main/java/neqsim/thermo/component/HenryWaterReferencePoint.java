package neqsim.thermo.component;

import java.io.Serializable;

/**
 * Immutable, reference-temperature-only Henry datum for a neutral solute in water.
 *
 * <p>
 * The stored quantity is the solubility convention {@code Hsbp = molality / partial
 * pressure} in mol/(kg atm). The converted volatility {@code Hm = partial pressure / molality} is in bar kg/mol. It is
 * not the mole-fraction volatility {@code Hx}. No temperature correlation is implied by a reference point.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class HenryWaterReferencePoint implements Serializable, Cloneable {
  private static final long serialVersionUID = 1000L;
  private static final double ATM_IN_BAR = 1.01325;

  private final String componentName;
  private final String sourceSpeciesName;
  private final String casNumber;
  private final double solubilityMolalityPerAtm;
  private final double referenceTemperatureK;
  private final double referencePressureMPa;
  private final String referenceId;
  private final String status;
  private final String solvent;
  private final String convention;
  private final String source;
  private final String compilationDoi;
  private final String compilationLicense;
  private final String originalReference;
  private final String originalReferenceDoi;
  private final String uncertainty;
  private final String temperatureScope;

  HenryWaterReferencePoint(String componentName, String sourceSpeciesName, String casNumber,
      double solubilityMolalityPerAtm, double referenceTemperatureK, double referencePressureMPa, String referenceId,
      String status, String solvent, String convention, String source, String compilationDoi, String compilationLicense,
      String originalReference, String originalReferenceDoi, String uncertainty, String temperatureScope) {
    this.componentName = componentName;
    this.sourceSpeciesName = sourceSpeciesName;
    this.casNumber = casNumber;
    this.solubilityMolalityPerAtm = solubilityMolalityPerAtm;
    this.referenceTemperatureK = referenceTemperatureK;
    this.referencePressureMPa = referencePressureMPa;
    this.referenceId = referenceId;
    this.status = status;
    this.solvent = solvent;
    this.convention = convention;
    this.source = source;
    this.compilationDoi = compilationDoi;
    this.compilationLicense = compilationLicense;
    this.originalReference = originalReference;
    this.originalReferenceDoi = originalReferenceDoi;
    this.uncertainty = uncertainty;
    this.temperatureScope = temperatureScope;
  }

  /** @return exact NeqSim component name */
  public String getComponentName() {
    return componentName;
  }

  /** @return species name used by the source compilation */
  public String getSourceSpeciesName() {
    return sourceSpeciesName;
  }

  /** @return exact CAS registry number */
  public String getCasNumber() {
    return casNumber;
  }

  /** @return {@code Hsbp = m/p} in mol/(kg atm) */
  public double getSolubilityMolalityPerAtm() {
    return solubilityMolalityPerAtm;
  }

  /** @return reference temperature in kelvin */
  public double getReferenceTemperatureK() {
    return referenceTemperatureK;
  }

  /** @return reference pressure in MPa */
  public double getReferencePressureMPa() {
    return referencePressureMPa;
  }

  /** @return Sander bibliography identifier */
  public String getReferenceId() {
    return referenceId;
  }

  /** @return qualification status */
  public String getStatus() {
    return status;
  }

  /** @return explicitly qualified solvent */
  public String getSolvent() {
    return solvent;
  }

  /** @return stored Henry convention and units */
  public String getConvention() {
    return convention;
  }

  /** @return machine-readable compilation source */
  public String getSource() {
    return source;
  }

  /** @return DOI of the source compilation */
  public String getCompilationDoi() {
    return compilationDoi;
  }

  /** @return license of the source compilation */
  public String getCompilationLicense() {
    return compilationLicense;
  }

  /** @return cited original source */
  public String getOriginalReference() {
    return originalReference;
  }

  /** @return DOI of the cited original source */
  public String getOriginalReferenceDoi() {
    return originalReferenceDoi;
  }

  /** @return explicit uncertainty limitation */
  public String getUncertainty() {
    return uncertainty;
  }

  /** @return qualified temperature scope */
  public String getTemperatureScope() {
    return temperatureScope;
  }

  /**
   * Returns whether this point is available at the supplied temperature.
   *
   * @param temperatureK temperature in kelvin
   * @return true only for the exact published reference temperature
   */
  public boolean isAvailableAt(double temperatureK) {
    return Double.compare(temperatureK, referenceTemperatureK) == 0;
  }

  /**
   * Returns the molality volatility at the published reference point.
   *
   * @return {@code Hm = p/m} in bar kg/mol
   */
  public double getMolalityVolatilityBarKgPerMol() {
    return ATM_IN_BAR / solubilityMolalityPerAtm;
  }

  /**
   * Returns the molality volatility only at the exact published reference temperature.
   *
   * @param temperatureK temperature in kelvin
   * @return {@code Hm} in bar kg/mol, or {@link Double#NaN} outside the point contract
   */
  public double getMolalityVolatilityBarKgPerMol(double temperatureK) {
    return isAvailableAt(temperatureK) ? getMolalityVolatilityBarKgPerMol() : Double.NaN;
  }

  /**
   * A single reference point does not define a temperature derivative.
   *
   * @param temperatureK temperature in kelvin
   * @return always {@link Double#NaN}
   */
  public double getMolalityVolatilityTemperatureDerivative(double temperatureK) {
    return Double.NaN;
  }

  /** An immutable reference point can safely return itself when cloned. */
  @Override
  public HenryWaterReferencePoint clone() {
    return this;
  }
}
