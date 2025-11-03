package neqsim.thermo.characterization;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * Utility for characterising an oil system from assay information.
 */
public class OilAssayCharacterisation implements Cloneable, Serializable {
  private static final long serialVersionUID = 1000L;
  private static final Logger logger = LogManager.getLogger(OilAssayCharacterisation.class);
  private static final double FRACTION_TOLERANCE = 1e-10;
  private static final double KELVIN_OFFSET = 273.15;
  private static final double WATER_DENSITY_60F_G_CC = 0.999016; // API definition reference
                                                                 // density.

  private transient SystemInterface system;
  private double totalAssayMass = 1.0; // kg basis when converting mass fraction to moles.
  private List<AssayCut> cuts = new ArrayList<>();

  public OilAssayCharacterisation(SystemInterface system) {
    setThermoSystem(system);
  }

  public void setThermoSystem(SystemInterface system) {
    this.system = Objects.requireNonNull(system, "system");
  }

  public double getTotalAssayMass() {
    return totalAssayMass;
  }

  public void setTotalAssayMass(double totalAssayMass) {
    if (!(totalAssayMass > 0.0)) {
      throw new IllegalArgumentException("Total assay mass must be positive");
    }
    this.totalAssayMass = totalAssayMass;
  }

  public void clearCuts() {
    cuts.clear();
  }

  public void addCut(AssayCut cut) {
    cuts.add(Objects.requireNonNull(cut, "cut"));
  }

  public void addCuts(Collection<AssayCut> cuts) {
    if (cuts == null) {
      return;
    }
    for (AssayCut cut : cuts) {
      addCut(cut);
    }
  }

  public List<AssayCut> getCuts() {
    return Collections.unmodifiableList(cuts);
  }

  public void apply() {
    if (system == null) {
      throw new IllegalStateException("Thermodynamic system not attached to assay data");
    }
    if (cuts.isEmpty()) {
      logger.warn("No assay cuts supplied – nothing to characterise");
      return;
    }

    double[] massFractions = resolveMassFractions();
    for (int i = 0; i < cuts.size(); i++) {
      AssayCut cut = cuts.get(i);
      double massFraction = massFractions[i];
      if (!(massFraction > FRACTION_TOLERANCE)) {
        continue;
      }

      double density = cut.resolveDensity();
      double molarMass;
      if (cut.hasMolarMass()) {
        // Use explicit molar mass - no boiling point needed
        molarMass = cut.resolveMolarMass(0.0, 0.0);
      } else {
        // Calculate molar mass from density and boiling point
        double boilingPoint = cut.resolveAverageBoilingPoint();
        molarMass = cut.resolveMolarMass(density, boilingPoint);
      }
      double moles = totalAssayMass * massFraction / molarMass;

      if (moles <= 0.0 || Double.isNaN(moles) || Double.isInfinite(moles)) {
        throw new IllegalStateException(
            "Calculated mole amount for assay cut " + cut.getName() + " is not finite");
      }

      system.addTBPfraction(cut.getName(), moles, molarMass, density);
    }
  }

  private double[] resolveMassFractions() {
    double[] massFractions = new double[cuts.size()];
    double specifiedMass = 0.0;
    double volumeMass = 0.0;
    boolean hasVolumeFractions = false;

    for (int i = 0; i < cuts.size(); i++) {
      AssayCut cut = cuts.get(i);
      if (cut.hasMassFraction()) {
        double massFraction = cut.getMassFraction();
        specifiedMass += massFraction;
        massFractions[i] = massFraction;
      } else if (cut.hasVolumeFraction()) {
        hasVolumeFractions = true;
        double density = cut.resolveDensity();
        volumeMass += cut.getVolumeFraction() * density;
      } else {
        throw new IllegalStateException(
            "Assay cut " + cut.getName() + " must define a mass or volume fraction");
      }
    }

    if (specifiedMass > 1.0 + 1e-6) {
      throw new IllegalStateException("Specified mass fractions exceed unity: " + specifiedMass);
    }

    double remainingMass = Math.max(0.0, 1.0 - specifiedMass);

    if (hasVolumeFractions) {
      if (!(volumeMass > 0.0)) {
        throw new IllegalStateException("Unable to derive mass fractions from volume data");
      }
      for (int i = 0; i < cuts.size(); i++) {
        AssayCut cut = cuts.get(i);
        if (!cut.hasMassFraction() && cut.hasVolumeFraction()) {
          double density = cut.resolveDensity();
          double cutMass = cut.getVolumeFraction() * density;
          massFractions[i] = cutMass / volumeMass * remainingMass;
        }
      }
    }

    double totalMassFraction = 0.0;
    for (double fraction : massFractions) {
      totalMassFraction += fraction;
    }

    if (!(totalMassFraction > 0.0)) {
      throw new IllegalStateException("No valid mass fractions derived from assay data");
    }

    if (Math.abs(totalMassFraction - 1.0) > 1.0e-8) {
      for (int i = 0; i < massFractions.length; i++) {
        massFractions[i] /= totalMassFraction;
      }
    }
    return massFractions;
  }

  @Override
  public OilAssayCharacterisation clone() {
    try {
      OilAssayCharacterisation clone = (OilAssayCharacterisation) super.clone();
      clone.cuts = new ArrayList<>();
      for (AssayCut cut : cuts) {
        clone.cuts.add(cut.clone());
      }
      clone.system = system;
      return clone;
    } catch (CloneNotSupportedException ex) {
      throw new IllegalStateException("Clone not supported", ex);
    }
  }

  public static final class AssayCut implements Cloneable, Serializable {
    private static final long serialVersionUID = 1000L;
    private final String name;
    private Double massFraction;
    private Double volumeFraction;
    private Double density;
    private Double apiGravity;
    private Double averageBoilingPointKelvin;
    private Double molarMass;

    public AssayCut(String name) {
      this.name = Objects.requireNonNull(name, "name");
    }

    public String getName() {
      return name;
    }

    public AssayCut withMassFraction(double massFraction) {
      this.massFraction = sanitiseFraction(massFraction);
      return this;
    }

    public AssayCut withWeightPercent(double weightPercent) {
      this.massFraction = sanitiseFraction(weightPercent / 100.0);
      return this;
    }

    public AssayCut withVolumeFraction(double volumeFraction) {
      this.volumeFraction = sanitiseFraction(volumeFraction);
      return this;
    }

    public AssayCut withVolumePercent(double volumePercent) {
      this.volumeFraction = sanitiseFraction(volumePercent / 100.0);
      return this;
    }

    public AssayCut withDensity(double density) {
      if (!(density > 0.0)) {
        throw new IllegalArgumentException("Density must be positive");
      }
      this.density = density;
      return this;
    }

    public AssayCut withApiGravity(double apiGravity) {
      if (!(apiGravity > 0.0)) {
        throw new IllegalArgumentException("API gravity must be positive");
      }
      this.apiGravity = apiGravity;
      return this;
    }

    public AssayCut withAverageBoilingPointKelvin(double temperatureKelvin) {
      if (!(temperatureKelvin > 0.0)) {
        throw new IllegalArgumentException("Boiling point must be positive");
      }
      this.averageBoilingPointKelvin = temperatureKelvin;
      return this;
    }

    public AssayCut withAverageBoilingPointCelsius(double temperatureCelsius) {
      return withAverageBoilingPointKelvin(temperatureCelsius + KELVIN_OFFSET);
    }

    public AssayCut withAverageBoilingPointFahrenheit(double temperatureFahrenheit) {
      double temperatureCelsius = (temperatureFahrenheit - 32.0) * 5.0 / 9.0;
      return withAverageBoilingPointKelvin(temperatureCelsius + KELVIN_OFFSET);
    }

    public AssayCut withMolarMass(double molarMass) {
      if (!(molarMass > 0.0)) {
        throw new IllegalArgumentException("Molar mass must be positive");
      }
      this.molarMass = molarMass;
      return this;
    }

    public boolean hasMassFraction() {
      return massFraction != null;
    }

    public double getMassFraction() {
      if (massFraction == null) {
        throw new IllegalStateException("Mass fraction not set");
      }
      return massFraction;
    }

    public boolean hasVolumeFraction() {
      return volumeFraction != null;
    }

    public double getVolumeFraction() {
      if (volumeFraction == null) {
        throw new IllegalStateException("Volume fraction not set");
      }
      return volumeFraction;
    }

    public boolean hasMolarMass() {
      return molarMass != null;
    }

    public double resolveDensity() {
      if (density != null) {
        return density;
      }
      if (apiGravity != null) {
        double specificGravity = 141.5 / (apiGravity + 131.5);
        return specificGravity * WATER_DENSITY_60F_G_CC;
      }
      throw new IllegalStateException("Density or API gravity required for cut " + name);
    }

    public double resolveAverageBoilingPoint() {
      if (averageBoilingPointKelvin == null) {
        throw new IllegalStateException("Average boiling point missing for cut " + name);
      }
      return averageBoilingPointKelvin;
    }

    public double resolveMolarMass(double density, double boilingPointKelvin) {
      if (molarMass != null) {
        return molarMass;
      }
      if (!(density > 0.0) || !(boilingPointKelvin > 0.0)) {
        throw new IllegalStateException(
            "Cannot derive molar mass without density and boiling point");
      }
      double exponent = 2.3776;
      double densityExponent = 0.9371;
      double molarMassKgPerMol =
          5.805e-5 * Math.pow(boilingPointKelvin, exponent) / Math.pow(density, densityExponent);
      return molarMassKgPerMol;
    }

    @Override
    public AssayCut clone() throws CloneNotSupportedException {
      return (AssayCut) super.clone();
    }

    private static double sanitiseFraction(double fraction) {
      if (fraction < 0.0) {
        throw new IllegalArgumentException("Fraction cannot be negative");
      }
      double candidate = fraction;
      if (candidate > 1.0 + 1e-9) {
        candidate = candidate / 100.0;
      }
      if (candidate < 0.0 || candidate > 1.0 + 1e-9) {
        throw new IllegalArgumentException("Fraction must be between 0 and 1");
      }
      return candidate;
    }
  }
}
