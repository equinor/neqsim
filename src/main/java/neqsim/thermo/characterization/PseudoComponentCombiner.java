package neqsim.thermo.characterization;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.component.attractiveeosterm.AttractiveTermInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Utility class for combining fluids containing pseudo components.
 */
public final class PseudoComponentCombiner {
  private static final Logger logger = LogManager.getLogger(PseudoComponentCombiner.class);

  private PseudoComponentCombiner() {}

  /**
   * Combine one or more reservoir fluids and (re)distribute the pseudo components into a specified
   * number of new pseudo components.
   *
   * @param targetPseudoComponents number of pseudo components in the combined fluid
   * @param fluids input fluids
   * @return combined fluid with the requested number of pseudo components
   */
  public static SystemInterface combineReservoirFluids(int targetPseudoComponents,
      SystemInterface... fluids) {
    return combineReservoirFluids(targetPseudoComponents, Arrays.asList(fluids));
  }

  /**
   * Combine one or more reservoir fluids and (re)distribute the pseudo components into a specified
   * number of new pseudo components.
   *
   * @param targetPseudoComponents number of pseudo components in the combined fluid
   * @param fluids input fluids
   * @return combined fluid with the requested number of pseudo components
   */
  public static SystemInterface combineReservoirFluids(int targetPseudoComponents,
      Collection<SystemInterface> fluids) {
    if (targetPseudoComponents <= 0) {
      throw new IllegalArgumentException("Number of pseudo components must be positive");
    }

    if (fluids == null || fluids.isEmpty()) {
      throw new IllegalArgumentException("At least one fluid is required");
    }

    List<SystemInterface> fluidList = new ArrayList<>();
    for (SystemInterface fluid : fluids) {
      if (fluid != null) {
        fluidList.add(fluid);
      }
    }

    if (fluidList.isEmpty()) {
      throw new IllegalArgumentException("Input fluids are null");
    }

    SystemInterface combined = fluidList.get(0).clone();
    removeAllComponents(combined);

    Map<String, Double> baseComponents = new LinkedHashMap<>();
    List<PseudoComponentContribution> pseudoComponents = new ArrayList<>();

    for (SystemInterface fluid : fluidList) {
      for (int i = 0; i < fluid.getPhase(0).getNumberOfComponents(); i++) {
        ComponentInterface component = fluid.getPhase(0).getComponent(i);
        double moles = component.getNumberOfmoles();
        if (moles <= 0.0) {
          continue;
        }

        if (component.isIsTBPfraction() || component.isIsPlusFraction()) {
          pseudoComponents.add(new PseudoComponentContribution(component));
        } else {
          baseComponents.merge(component.getComponentName(), moles, Double::sum);
        }
      }
    }

    for (Map.Entry<String, Double> entry : baseComponents.entrySet()) {
      combined.addComponent(entry.getKey(), entry.getValue());
    }

    List<PseudoComponentProperties> lumpedPseudoComponents =
        lumpPseudoComponents(pseudoComponents, targetPseudoComponents);

    int pseudoCounter = 1;
    for (PseudoComponentProperties properties : lumpedPseudoComponents) {
      if (!properties.isValid()) {
        continue;
      }

      String componentName = "PC" + pseudoCounter++;
      combined.addTBPfraction(componentName, properties.getMoles(), properties.getMolarMass(),
          properties.getDensity());

      ComponentInterface component = combined.getComponent(componentName + "_PC");
      if (component == null) {
        logger.warn("Failed to locate newly added pseudo component {}", componentName);
        continue;
      }

      properties.applyTo(component);
    }

    if (combined.getNumberOfComponents() > 0) {
      combined.createDatabase(true);
      combined.setMixingRule(combined.getMixingRule());
      combined.init(0);
    }

    return combined;
  }

  private static void removeAllComponents(SystemInterface system) {
    String[] names = system.getComponentNames();
    if (names == null) {
      return;
    }
    for (String name : names) {
      system.removeComponent(name);
    }
  }

  private static List<PseudoComponentProperties> lumpPseudoComponents(
      List<PseudoComponentContribution> pseudoComponents, int targetPseudoComponents) {
    if (pseudoComponents.isEmpty()) {
      return Collections.emptyList();
    }

    List<PseudoComponentContribution> sorted = new ArrayList<>(pseudoComponents);
    sorted.removeIf(contribution -> contribution.mass <= 0.0);
    sorted.sort(Comparator.comparingDouble(PseudoComponentContribution::sortingKey));

    if (sorted.isEmpty()) {
      return Collections.emptyList();
    }

    double totalMass = 0.0;
    for (PseudoComponentContribution contribution : sorted) {
      totalMass += contribution.mass;
    }

    if (!(totalMass > 0.0)) {
      return Collections.emptyList();
    }

    double massPerGroup = totalMass / targetPseudoComponents;
    double massTolerance = Math.max(Math.abs(massPerGroup) * 1e-9, 1e-12);

    List<PseudoComponentProperties> result = new ArrayList<>(targetPseudoComponents);
    int componentIndex = 0;
    double massUsedFromCurrent = 0.0;

    for (int group = 0; group < targetPseudoComponents; group++) {
      double upperMass = totalMass * (group + 1) / targetPseudoComponents;
      double lowerMass = totalMass * group / targetPseudoComponents;
      double targetMass = upperMass - lowerMass;
      double massHandled = 0.0;
      PseudoComponentAccumulator accumulator = new PseudoComponentAccumulator(massTolerance);

      while (massHandled < targetMass - massTolerance && componentIndex < sorted.size()) {
        PseudoComponentContribution contribution = sorted.get(componentIndex);
        double availableMass = contribution.mass - massUsedFromCurrent;
        if (availableMass <= massTolerance) {
          componentIndex++;
          massUsedFromCurrent = 0.0;
          continue;
        }

        double massToTake = Math.min(targetMass - massHandled, availableMass);
        accumulator.add(contribution, massToTake);

        massHandled += massToTake;
        massUsedFromCurrent += massToTake;

        if (massUsedFromCurrent >= contribution.mass - massTolerance) {
          componentIndex++;
          massUsedFromCurrent = 0.0;
        }
      }

      result.add(accumulator.toProperties());
    }

    return result;
  }

  private static final class PseudoComponentContribution {
    private final double moles;
    private final double molarMass;
    private final double density;
    private final double normalBoilingPoint;
    private final double criticalTemperature;
    private final double criticalPressure;
    private final double acentricFactor;
    private final double criticalVolume;
    private final double racketZ;
    private final double racketZCpa;
    private final double parachor;
    private final double criticalViscosity;
    private final double triplePointTemperature;
    private final double heatOfFusion;
    private final double idealGasEnthalpyOfFormation;
    private final double cpA;
    private final double cpB;
    private final double cpC;
    private final double cpD;
    private final double attractiveM;
    private final double mass;

    PseudoComponentContribution(ComponentInterface component) {
      moles = component.getNumberOfmoles();
      molarMass = component.getMolarMass();
      density = component.getNormalLiquidDensity();
      normalBoilingPoint = component.getNormalBoilingPoint();
      criticalTemperature = component.getTC();
      criticalPressure = component.getPC();
      acentricFactor = component.getAcentricFactor();
      criticalVolume = component.getCriticalVolume();
      racketZ = component.getRacketZ();
      racketZCpa = component.getRacketZCPA();
      parachor = component.getParachorParameter();
      criticalViscosity = component.getCriticalViscosity();
      triplePointTemperature = component.getTriplePointTemperature();
      heatOfFusion = component.getHeatOfFusion();
      idealGasEnthalpyOfFormation = component.getIdealGasEnthalpyOfFormation();
      cpA = component.getCpA();
      cpB = component.getCpB();
      cpC = component.getCpC();
      cpD = component.getCpD();
      AttractiveTermInterface attractiveTerm = component.getAttractiveTerm();
      attractiveM = attractiveTerm != null ? attractiveTerm.getm() : Double.NaN;
      mass = moles * molarMass;
    }

    double sortingKey() {
      double key = normalBoilingPoint;
      if (!Double.isFinite(key) || key <= 0.0) {
        key = molarMass;
      }
      return key;
    }
  }

  private static final class PseudoComponentAccumulator {
    private final double massTolerance;
    private double mass;
    private double moles;
    private double volume;
    private double densityMass;
    private double boilingMass;
    private double criticalTemperatureMass;
    private double criticalPressureMass;
    private double acentricFactorMass;
    private double criticalVolumeMass;
    private double racketZMass;
    private double racketZCpaMass;
    private double parachorMass;
    private double criticalViscosityMass;
    private double triplePointTemperatureMass;
    private double heatOfFusionMass;
    private double idealGasEnthalpyMass;
    private double cpAMass;
    private double cpBMass;
    private double cpCMass;
    private double cpDMass;
    private double attractiveMMass;
    private boolean hasBoiling;
    private boolean hasCriticalTemperature;
    private boolean hasCriticalPressure;
    private boolean hasAcentricFactor;
    private boolean hasCriticalVolume;
    private boolean hasRacketZ;
    private boolean hasRacketZCpa;
    private boolean hasParachor;
    private boolean hasCriticalViscosity;
    private boolean hasTriplePointTemperature;
    private boolean hasHeatOfFusion;
    private boolean hasIdealGasEnthalpy;
    private boolean hasCpA;
    private boolean hasCpB;
    private boolean hasCpC;
    private boolean hasCpD;
    private boolean hasAttractiveM;

    PseudoComponentAccumulator(double massTolerance) {
      this.massTolerance = massTolerance;
    }

    void add(PseudoComponentContribution source, double massPortion) {
      if (!(massPortion > 0.0)) {
        return;
      }

      double molesPortion = source.molarMass > 0.0 ? massPortion / source.molarMass : 0.0;

      mass += massPortion;
      moles += molesPortion;

      if (source.density > 0.0) {
        volume += massPortion / source.density;
        densityMass += massPortion * source.density;
      }

      if (Double.isFinite(source.normalBoilingPoint)) {
        boilingMass += massPortion * source.normalBoilingPoint;
        hasBoiling = true;
      }

      if (Double.isFinite(source.criticalTemperature)) {
        criticalTemperatureMass += massPortion * source.criticalTemperature;
        hasCriticalTemperature = true;
      }

      if (Double.isFinite(source.criticalPressure)) {
        criticalPressureMass += massPortion * source.criticalPressure;
        hasCriticalPressure = true;
      }

      if (Double.isFinite(source.acentricFactor)) {
        acentricFactorMass += massPortion * source.acentricFactor;
        hasAcentricFactor = true;
      }

      if (Double.isFinite(source.criticalVolume)) {
        criticalVolumeMass += massPortion * source.criticalVolume;
        hasCriticalVolume = true;
      }

      if (Double.isFinite(source.racketZ)) {
        racketZMass += massPortion * source.racketZ;
        hasRacketZ = true;
      }

      if (Double.isFinite(source.racketZCpa)) {
        racketZCpaMass += massPortion * source.racketZCpa;
        hasRacketZCpa = true;
      }

      if (Double.isFinite(source.parachor)) {
        parachorMass += massPortion * source.parachor;
        hasParachor = true;
      }

      if (Double.isFinite(source.criticalViscosity)) {
        criticalViscosityMass += massPortion * source.criticalViscosity;
        hasCriticalViscosity = true;
      }

      if (Double.isFinite(source.triplePointTemperature)) {
        triplePointTemperatureMass += massPortion * source.triplePointTemperature;
        hasTriplePointTemperature = true;
      }

      if (Double.isFinite(source.heatOfFusion)) {
        heatOfFusionMass += massPortion * source.heatOfFusion;
        hasHeatOfFusion = true;
      }

      if (Double.isFinite(source.idealGasEnthalpyOfFormation)) {
        idealGasEnthalpyMass += massPortion * source.idealGasEnthalpyOfFormation;
        hasIdealGasEnthalpy = true;
      }

      if (Double.isFinite(source.cpA)) {
        cpAMass += massPortion * source.cpA;
        hasCpA = true;
      }

      if (Double.isFinite(source.cpB)) {
        cpBMass += massPortion * source.cpB;
        hasCpB = true;
      }

      if (Double.isFinite(source.cpC)) {
        cpCMass += massPortion * source.cpC;
        hasCpC = true;
      }

      if (Double.isFinite(source.cpD)) {
        cpDMass += massPortion * source.cpD;
        hasCpD = true;
      }

      if (Double.isFinite(source.attractiveM)) {
        attractiveMMass += massPortion * source.attractiveM;
        hasAttractiveM = true;
      }
    }

    PseudoComponentProperties toProperties() {
      if (!(mass > massTolerance) || !(moles > massTolerance)) {
        return PseudoComponentProperties.empty();
      }

      double molarMass = mass / moles;
      double density = 0.0;
      if (volume > massTolerance) {
        density = mass / volume;
      } else if (densityMass > massTolerance) {
        density = densityMass / mass;
      }

      if (!(density > 0.0)) {
        return PseudoComponentProperties.empty();
      }

      return new PseudoComponentProperties(moles, molarMass, density,
          hasBoiling ? boilingMass / mass : Double.NaN,
          hasCriticalTemperature ? criticalTemperatureMass / mass : Double.NaN,
          hasCriticalPressure ? criticalPressureMass / mass : Double.NaN,
          hasAcentricFactor ? acentricFactorMass / mass : Double.NaN,
          hasCriticalVolume ? criticalVolumeMass / mass : Double.NaN,
          hasRacketZ ? racketZMass / mass : Double.NaN,
          hasRacketZCpa ? racketZCpaMass / mass : Double.NaN,
          hasParachor ? parachorMass / mass : Double.NaN,
          hasCriticalViscosity ? criticalViscosityMass / mass : Double.NaN,
          hasTriplePointTemperature ? triplePointTemperatureMass / mass : Double.NaN,
          hasHeatOfFusion ? heatOfFusionMass / mass : Double.NaN,
          hasIdealGasEnthalpy ? idealGasEnthalpyMass / mass : Double.NaN,
          hasCpA ? cpAMass / mass : Double.NaN, hasCpB ? cpBMass / mass : Double.NaN,
          hasCpC ? cpCMass / mass : Double.NaN, hasCpD ? cpDMass / mass : Double.NaN,
          hasAttractiveM ? attractiveMMass / mass : Double.NaN);
    }
  }

  private static final class PseudoComponentProperties {
    private final double moles;
    private final double molarMass;
    private final double density;
    private final double normalBoilingPoint;
    private final double criticalTemperature;
    private final double criticalPressure;
    private final double acentricFactor;
    private final double criticalVolume;
    private final double racketZ;
    private final double racketZCpa;
    private final double parachor;
    private final double criticalViscosity;
    private final double triplePointTemperature;
    private final double heatOfFusion;
    private final double idealGasEnthalpyOfFormation;
    private final double cpA;
    private final double cpB;
    private final double cpC;
    private final double cpD;
    private final double attractiveM;

    PseudoComponentProperties(double moles, double molarMass, double density,
        double normalBoilingPoint, double criticalTemperature, double criticalPressure,
        double acentricFactor, double criticalVolume, double racketZ, double racketZCpa,
        double parachor, double criticalViscosity, double triplePointTemperature,
        double heatOfFusion, double idealGasEnthalpyOfFormation, double cpA, double cpB,
        double cpC, double cpD, double attractiveM) {
      this.moles = moles;
      this.molarMass = molarMass;
      this.density = density;
      this.normalBoilingPoint = normalBoilingPoint;
      this.criticalTemperature = criticalTemperature;
      this.criticalPressure = criticalPressure;
      this.acentricFactor = acentricFactor;
      this.criticalVolume = criticalVolume;
      this.racketZ = racketZ;
      this.racketZCpa = racketZCpa;
      this.parachor = parachor;
      this.criticalViscosity = criticalViscosity;
      this.triplePointTemperature = triplePointTemperature;
      this.heatOfFusion = heatOfFusion;
      this.idealGasEnthalpyOfFormation = idealGasEnthalpyOfFormation;
      this.cpA = cpA;
      this.cpB = cpB;
      this.cpC = cpC;
      this.cpD = cpD;
      this.attractiveM = attractiveM;
    }

    static PseudoComponentProperties empty() {
      return new PseudoComponentProperties(0.0, 0.0, 0.0, Double.NaN, Double.NaN, Double.NaN,
          Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
          Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
    }

    boolean isValid() {
      return moles > 0.0 && molarMass > 0.0 && density > 0.0;
    }

    double getMoles() {
      return moles;
    }

    double getMolarMass() {
      return molarMass;
    }

    double getDensity() {
      return density;
    }

    void applyTo(ComponentInterface component) {
      Objects.requireNonNull(component, "component");

      if (Double.isFinite(normalBoilingPoint)) {
        component.setNormalBoilingPoint(normalBoilingPoint);
      }
      if (Double.isFinite(criticalTemperature)) {
        component.setTC(criticalTemperature);
      }
      if (Double.isFinite(criticalPressure)) {
        component.setPC(criticalPressure);
      }
      if (Double.isFinite(acentricFactor)) {
        component.setAcentricFactor(acentricFactor);
      }
      if (Double.isFinite(criticalVolume)) {
        component.setCriticalVolume(criticalVolume);
      }
      if (Double.isFinite(racketZ)) {
        component.setRacketZ(racketZ);
      }
      if (Double.isFinite(racketZCpa)) {
        component.setRacketZCPA(racketZCpa);
      }
      if (Double.isFinite(parachor)) {
        component.setParachorParameter(parachor);
      }
      if (Double.isFinite(criticalViscosity)) {
        component.setCriticalViscosity(criticalViscosity);
      }
      if (Double.isFinite(triplePointTemperature)) {
        component.setTriplePointTemperature(triplePointTemperature);
      }
      if (Double.isFinite(heatOfFusion)) {
        component.setHeatOfFusion(heatOfFusion);
      }
      if (Double.isFinite(idealGasEnthalpyOfFormation)) {
        component.setIdealGasEnthalpyOfFormation(idealGasEnthalpyOfFormation);
      }
      if (Double.isFinite(cpA)) {
        component.setCpA(cpA);
      }
      if (Double.isFinite(cpB)) {
        component.setCpB(cpB);
      }
      if (Double.isFinite(cpC)) {
        component.setCpC(cpC);
      }
      if (Double.isFinite(cpD)) {
        component.setCpD(cpD);
      }
      if (Double.isFinite(attractiveM) && component.getAttractiveTerm() != null) {
        component.getAttractiveTerm().setm(attractiveM);
      }
    }
  }
}

