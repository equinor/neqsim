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
 * Utility class for combining and re-characterizing fluids containing pseudo components.
 */
public final class PseudoComponentCombiner {
  private static final Logger logger = LogManager.getLogger(PseudoComponentCombiner.class);
  private static final double MASS_TOLERANCE = 1e-12;

  private PseudoComponentCombiner() {}

  /**
   * Combine one or more reservoir fluids and redistribute their pseudo components into a specified
   * number of new pseudo components. The new pseudo components are calculated using the weighting
   * scheme described in Chapter 5.5 of Pedersen et al. (mixing of multiple fluids), i.e. the
   * properties of each resulting pseudo component are weighted by the fluid fraction and the mole
   * fraction of the contributing pseudo components.
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
   * Combine one or more reservoir fluids and redistribute their pseudo components into a specified
   * number of new pseudo components. The new pseudo components are calculated using the weighting
   * scheme described in Chapter 5.5 of Pedersen et al. (mixing of multiple fluids), i.e. the
   * properties of each resulting pseudo component are weighted by the fluid fraction and the mole
   * fraction of the contributing pseudo components.
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
    List<List<PseudoComponentContribution>> perFluidContributions = new ArrayList<>();
    List<PseudoComponentContribution> allPseudoContributions = new ArrayList<>();

    for (SystemInterface fluid : fluidList) {
      FluidExtraction extraction = extractComponents(fluid);
      baseComponents = mergeBaseComponents(baseComponents, extraction.baseComponents);
      perFluidContributions.add(extraction.pseudoComponents);
      allPseudoContributions.addAll(extraction.pseudoComponents);
    }

    for (Map.Entry<String, Double> entry : baseComponents.entrySet()) {
      combined.addComponent(entry.getKey(), entry.getValue());
    }

    if (allPseudoContributions.isEmpty()) {
      finalizeFluid(combined);
      return combined;
    }

    List<Double> boundaries = determineQuantileBoundaries(allPseudoContributions,
        targetPseudoComponents);

    List<List<PseudoComponentProfile>> perFluidProfiles = new ArrayList<>();
    double[] fluidMassTotals = new double[perFluidContributions.size()];
    double[] fluidMoleTotals = new double[perFluidContributions.size()];

    for (int i = 0; i < perFluidContributions.size(); i++) {
      List<PseudoComponentProfile> profiles = distributeToProfiles(perFluidContributions.get(i),
          boundaries, targetPseudoComponents);
      perFluidProfiles.add(profiles);

      double totalMass = 0.0;
      double totalMoles = 0.0;
      for (PseudoComponentProfile profile : profiles) {
        totalMass += profile.getMass();
        totalMoles += profile.getMoles();
      }
      fluidMassTotals[i] = totalMass;
      fluidMoleTotals[i] = totalMoles;
    }

    List<PseudoComponentProfile> combinedProfiles = combineProfiles(perFluidProfiles,
        fluidMassTotals, fluidMoleTotals);

    int pseudoCounter = 1;
    for (PseudoComponentProfile profile : combinedProfiles) {
      if (!profile.isValid()) {
        continue;
      }

      String baseName = "PC" + pseudoCounter++;
      combined.addTBPfraction(baseName, profile.getMoles(), profile.getMolarMass(),
          profile.getDensity());

      ComponentInterface component = combined.getComponent(baseName + "_PC");
      if (component == null) {
        logger.warn("Failed to locate newly added pseudo component {}", baseName);
        continue;
      }

      profile.applyTo(component);
    }

    finalizeFluid(combined);
    return combined;
  }

  /**
   * Characterize a fluid to another fluid's pseudo component definition (Pedersen et al., Chapter
   * 5.6). The pseudo component cut points are derived from the reference fluid's pseudo component
   * ordering and applied to the source fluid.
   *
   * @param source fluid to characterize
   * @param reference fluid defining the pseudo component characterization
   * @return characterized fluid containing pseudo components compatible with the reference fluid
   */
  public static SystemInterface characterizeToReference(SystemInterface source,
      SystemInterface reference) {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(reference, "reference");

    SystemInterface characterized = source.clone();
    removeAllComponents(characterized);

    FluidExtraction sourceExtraction = extractComponents(source);
    FluidExtraction referenceExtraction = extractComponents(reference);

    for (Map.Entry<String, Double> entry : sourceExtraction.baseComponents.entrySet()) {
      characterized.addComponent(entry.getKey(), entry.getValue());
    }

    if (referenceExtraction.pseudoComponents.isEmpty()) {
      finalizeFluid(characterized);
      return characterized;
    }

    List<Double> boundaries = determineReferenceBoundaries(referenceExtraction.pseudoComponents);
    List<PseudoComponentProfile> profiles = distributeToProfiles(sourceExtraction.pseudoComponents,
        boundaries, referenceExtraction.pseudoComponents.size());

    for (int i = 0; i < referenceExtraction.pseudoComponents.size(); i++) {
      PseudoComponentProfile profile = profiles.get(i);
      if (!profile.isValid()) {
        continue;
      }

      String referenceName = referenceExtraction.pseudoComponents.get(i).name;
      String baseName = stripPcSuffix(referenceName);
      characterized.addTBPfraction(baseName, profile.getMoles(), profile.getMolarMass(),
          profile.getDensity());

      ComponentInterface component = characterized.getComponent(baseName + "_PC");
      if (component == null) {
        logger.warn("Failed to locate newly added pseudo component {}", baseName);
        continue;
      }

      profile.applyTo(component);
    }

    finalizeFluid(characterized);
    return characterized;
  }

  private static String stripPcSuffix(String componentName) {
    if (componentName == null) {
      return "PC";
    }
    int index = componentName.indexOf("_PC");
    return index >= 0 ? componentName.substring(0, index) : componentName;
  }

  private static void finalizeFluid(SystemInterface system) {
    if (system.getNumberOfComponents() > 0) {
      system.createDatabase(true);
      system.setMixingRule(system.getMixingRule());
      system.init(0);
    }
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

  private static FluidExtraction extractComponents(SystemInterface fluid) {
    Map<String, Double> baseComponents = new LinkedHashMap<>();
    List<PseudoComponentContribution> pseudoComponents = new ArrayList<>();

    for (String name : fluid.getComponentNames()) {
      ComponentInterface component = fluid.getComponent(name);
      if (component == null) {
        continue;
      }

      double moles = component.getNumberOfmoles();
      if (!(moles > 0.0)) {
        continue;
      }

      if (component.isIsTBPfraction() || component.isIsPlusFraction()) {
        pseudoComponents.add(new PseudoComponentContribution(name, component));
      } else {
        baseComponents.merge(component.getComponentName(), moles, Double::sum);
      }
    }

    pseudoComponents.sort(Comparator.comparingDouble(PseudoComponentContribution::sortingKey));
    return new FluidExtraction(baseComponents, pseudoComponents);
  }

  private static Map<String, Double> mergeBaseComponents(Map<String, Double> accumulator,
      Map<String, Double> addition) {
    Map<String, Double> result = new LinkedHashMap<>(accumulator);
    for (Map.Entry<String, Double> entry : addition.entrySet()) {
      result.merge(entry.getKey(), entry.getValue(), Double::sum);
    }
    return result;
  }

  private static List<Double> determineQuantileBoundaries(
      List<PseudoComponentContribution> contributions, int targetPseudoComponents) {
    if (targetPseudoComponents <= 1 || contributions.isEmpty()) {
      return Collections.emptyList();
    }

    List<PseudoComponentContribution> sorted = new ArrayList<>(contributions);
    sorted.sort(Comparator.comparingDouble(PseudoComponentContribution::sortingKey));

    double totalMass = 0.0;
    for (PseudoComponentContribution contribution : sorted) {
      totalMass += contribution.mass;
    }

    if (!(totalMass > MASS_TOLERANCE)) {
      return Collections.emptyList();
    }

    double[] targets = new double[targetPseudoComponents - 1];
    for (int i = 0; i < targets.length; i++) {
      targets[i] = totalMass * (i + 1) / targetPseudoComponents;
    }

    List<Double> boundaries = new ArrayList<>(targets.length);
    double cumulative = 0.0;
    int targetIndex = 0;
    for (PseudoComponentContribution contribution : sorted) {
      double nextCumulative = cumulative + contribution.mass;
      while (targetIndex < targets.length && nextCumulative >= targets[targetIndex] - MASS_TOLERANCE) {
        boundaries.add(contribution.sortingKey());
        targetIndex++;
      }
      cumulative = nextCumulative;
    }

    while (boundaries.size() < targets.length) {
      boundaries.add(Double.POSITIVE_INFINITY);
    }

    return boundaries;
  }

  private static List<Double> determineReferenceBoundaries(
      List<PseudoComponentContribution> referenceContributions) {
    if (referenceContributions.size() <= 1) {
      return Collections.emptyList();
    }

    List<Double> boundaries = new ArrayList<>(referenceContributions.size() - 1);
    for (int i = 0; i < referenceContributions.size() - 1; i++) {
      double key1 = referenceContributions.get(i).sortingKey();
      double key2 = referenceContributions.get(i + 1).sortingKey();
      double boundary = Double.isFinite(key1) && Double.isFinite(key2) ? 0.5 * (key1 + key2)
          : Math.max(key1, key2);
      boundaries.add(boundary);
    }
    return boundaries;
  }

  private static List<PseudoComponentProfile> distributeToProfiles(
      List<PseudoComponentContribution> contributions, List<Double> boundaries,
      int targetPseudoComponents) {
    if (targetPseudoComponents <= 0) {
      return Collections.emptyList();
    }

    List<PseudoComponentGroupBuilder> builders = new ArrayList<>(targetPseudoComponents);
    for (int i = 0; i < targetPseudoComponents; i++) {
      builders.add(new PseudoComponentGroupBuilder(MASS_TOLERANCE));
    }

    if (contributions.isEmpty()) {
      List<PseudoComponentProfile> emptyProfiles = new ArrayList<>(targetPseudoComponents);
      for (int i = 0; i < targetPseudoComponents; i++) {
        emptyProfiles.add(PseudoComponentProfile.empty());
      }
      return emptyProfiles;
    }

    int groupIndex = 0;
    double currentBoundary = boundaries.isEmpty() ? Double.POSITIVE_INFINITY : boundaries.get(0);

    for (PseudoComponentContribution contribution : contributions) {
      double key = contribution.sortingKey();
      while (groupIndex < targetPseudoComponents - 1 && key > currentBoundary) {
        groupIndex++;
        currentBoundary = groupIndex < boundaries.size() ? boundaries.get(groupIndex)
            : Double.POSITIVE_INFINITY;
      }
      builders.get(groupIndex).addContribution(contribution, contribution.mass);
    }

    List<PseudoComponentProfile> profiles = new ArrayList<>(targetPseudoComponents);
    for (PseudoComponentGroupBuilder builder : builders) {
      profiles.add(builder.buildWithOverrides(null, null, null, null));
    }
    return profiles;
  }

  private static List<PseudoComponentProfile> combineProfiles(
      List<List<PseudoComponentProfile>> perFluidProfiles, double[] fluidMassTotals,
      double[] fluidMoleTotals) {
    if (perFluidProfiles.isEmpty()) {
      return Collections.emptyList();
    }

    int groupCount = perFluidProfiles.get(0).size();
    List<PseudoComponentProfile> result = new ArrayList<>(groupCount);

    double totalFluidMass = 0.0;
    double totalFluidMoles = 0.0;
    for (double mass : fluidMassTotals) {
      totalFluidMass += mass;
    }
    for (double moles : fluidMoleTotals) {
      totalFluidMoles += moles;
    }

    for (int group = 0; group < groupCount; group++) {
      PseudoComponentGroupBuilder builder = new PseudoComponentGroupBuilder(MASS_TOLERANCE);
      double tbNumerator = 0.0;
      double tbDenominator = 0.0;
      double tcNumerator = 0.0;
      double tcDenominator = 0.0;
      double pcNumerator = 0.0;
      double pcDenominator = 0.0;
      double omegaNumerator = 0.0;
      double omegaDenominator = 0.0;

      for (int fluid = 0; fluid < perFluidProfiles.size(); fluid++) {
        PseudoComponentProfile profile = perFluidProfiles.get(fluid).get(group);
        builder.addProfile(profile);

        double fluidMass = fluidMassTotals[fluid];
        double fluidMoles = fluidMoleTotals[fluid];
        double fluidFraction = totalFluidMass > MASS_TOLERANCE ? fluidMass / totalFluidMass : 0.0;
        double moleFraction = fluidMoles > MASS_TOLERANCE ? profile.getMoles() / fluidMoles : 0.0;

        if (Double.isFinite(profile.getNormalBoilingPoint())) {
          tbNumerator += fluidFraction * moleFraction * profile.getNormalBoilingPoint();
          tbDenominator += fluidFraction * moleFraction;
        }
        if (Double.isFinite(profile.getCriticalTemperature())) {
          tcNumerator += fluidFraction * moleFraction * profile.getCriticalTemperature();
          tcDenominator += fluidFraction * moleFraction;
        }
        if (Double.isFinite(profile.getCriticalPressure())) {
          pcNumerator += fluidFraction * moleFraction * profile.getCriticalPressure();
          pcDenominator += fluidFraction * moleFraction;
        }
        if (Double.isFinite(profile.getAcentricFactor())) {
          omegaNumerator += fluidFraction * moleFraction * profile.getAcentricFactor();
          omegaDenominator += fluidFraction * moleFraction;
        }
      }

      Double tbOverride = tbDenominator > MASS_TOLERANCE ? tbNumerator / tbDenominator : null;
      Double tcOverride = tcDenominator > MASS_TOLERANCE ? tcNumerator / tcDenominator : null;
      Double pcOverride = pcDenominator > MASS_TOLERANCE ? pcNumerator / pcDenominator : null;
      Double omegaOverride = omegaDenominator > MASS_TOLERANCE ? omegaNumerator / omegaDenominator
          : null;

      result.add(builder.buildWithOverrides(tbOverride, tcOverride, pcOverride, omegaOverride));
    }

    return result;
  }

  private static final class FluidExtraction {
    private final Map<String, Double> baseComponents;
    private final List<PseudoComponentContribution> pseudoComponents;

    private FluidExtraction(Map<String, Double> baseComponents,
        List<PseudoComponentContribution> pseudoComponents) {
      this.baseComponents = baseComponents;
      this.pseudoComponents = pseudoComponents;
    }
  }

  private static final class PseudoComponentContribution {
    private final String name;
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

    private PseudoComponentContribution(String name, ComponentInterface component) {
      this.name = name;
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

    private double sortingKey() {
      double key = normalBoilingPoint;
      if (!Double.isFinite(key) || key <= 0.0) {
        key = molarMass;
      }
      return key;
    }
  }

  private static final class PseudoComponentGroupBuilder {
    private final double massTolerance;
    private double mass;
    private double moles;
    private double volume;
    private double densityMass;
    private double tbMass;
    private boolean hasTb;
    private double tcMass;
    private boolean hasTc;
    private double pcMass;
    private boolean hasPc;
    private double omegaMass;
    private boolean hasOmega;
    private double criticalVolumeMass;
    private boolean hasCriticalVolume;
    private double racketZMass;
    private boolean hasRacketZ;
    private double racketZCpaMass;
    private boolean hasRacketZCpa;
    private double parachorMass;
    private boolean hasParachor;
    private double criticalViscosityMass;
    private boolean hasCriticalViscosity;
    private double triplePointMass;
    private boolean hasTriplePoint;
    private double heatOfFusionMass;
    private boolean hasHeatOfFusion;
    private double idealGasEnthalpyMass;
    private boolean hasIdealGasEnthalpy;
    private double cpAMass;
    private boolean hasCpA;
    private double cpBMass;
    private boolean hasCpB;
    private double cpCMass;
    private boolean hasCpC;
    private double cpDMass;
    private boolean hasCpD;
    private double attractiveMMass;
    private boolean hasAttractiveM;

    private PseudoComponentGroupBuilder(double massTolerance) {
      this.massTolerance = massTolerance;
    }

    private void addContribution(PseudoComponentContribution source, double massPortion) {
      if (!(massPortion > massTolerance) || !(source.mass > 0.0)) {
        return;
      }

      double ratio = massPortion / source.mass;
      double molesPortion = source.moles * ratio;

      mass += massPortion;
      moles += molesPortion;

      if (source.density > 0.0) {
        volume += massPortion / source.density;
        densityMass += massPortion * source.density;
      }

      if (Double.isFinite(source.normalBoilingPoint)) {
        tbMass += massPortion * source.normalBoilingPoint;
        hasTb = true;
      }

      if (Double.isFinite(source.criticalTemperature)) {
        tcMass += massPortion * source.criticalTemperature;
        hasTc = true;
      }

      if (Double.isFinite(source.criticalPressure)) {
        pcMass += massPortion * source.criticalPressure;
        hasPc = true;
      }

      if (Double.isFinite(source.acentricFactor)) {
        omegaMass += massPortion * source.acentricFactor;
        hasOmega = true;
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
        triplePointMass += massPortion * source.triplePointTemperature;
        hasTriplePoint = true;
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

    private void addProfile(PseudoComponentProfile profile) {
      if (!profile.isValid()) {
        return;
      }

      double massPortion = profile.getMass();
      mass += massPortion;
      moles += profile.getMoles();

      double density = profile.getDensity();
      if (density > 0.0) {
        volume += massPortion / density;
        densityMass += massPortion * density;
      }

      double tb = profile.getNormalBoilingPoint();
      if (Double.isFinite(tb)) {
        tbMass += massPortion * tb;
        hasTb = true;
      }

      double tc = profile.getCriticalTemperature();
      if (Double.isFinite(tc)) {
        tcMass += massPortion * tc;
        hasTc = true;
      }

      double pc = profile.getCriticalPressure();
      if (Double.isFinite(pc)) {
        pcMass += massPortion * pc;
        hasPc = true;
      }

      double omega = profile.getAcentricFactor();
      if (Double.isFinite(omega)) {
        omegaMass += massPortion * omega;
        hasOmega = true;
      }

      double criticalVol = profile.getCriticalVolume();
      if (Double.isFinite(criticalVol)) {
        criticalVolumeMass += massPortion * criticalVol;
        hasCriticalVolume = true;
      }

      double racket = profile.getRacketZ();
      if (Double.isFinite(racket)) {
        racketZMass += massPortion * racket;
        hasRacketZ = true;
      }

      double racketCpa = profile.getRacketZCpa();
      if (Double.isFinite(racketCpa)) {
        racketZCpaMass += massPortion * racketCpa;
        hasRacketZCpa = true;
      }

      double parachor = profile.getParachor();
      if (Double.isFinite(parachor)) {
        parachorMass += massPortion * parachor;
        hasParachor = true;
      }

      double criticalVisc = profile.getCriticalViscosity();
      if (Double.isFinite(criticalVisc)) {
        criticalViscosityMass += massPortion * criticalVisc;
        hasCriticalViscosity = true;
      }

      double triple = profile.getTriplePointTemperature();
      if (Double.isFinite(triple)) {
        triplePointMass += massPortion * triple;
        hasTriplePoint = true;
      }

      double heatFusion = profile.getHeatOfFusion();
      if (Double.isFinite(heatFusion)) {
        heatOfFusionMass += massPortion * heatFusion;
        hasHeatOfFusion = true;
      }

      double idealGas = profile.getIdealGasEnthalpyOfFormation();
      if (Double.isFinite(idealGas)) {
        idealGasEnthalpyMass += massPortion * idealGas;
        hasIdealGasEnthalpy = true;
      }

      double cpAValue = profile.getCpA();
      if (Double.isFinite(cpAValue)) {
        cpAMass += massPortion * cpAValue;
        hasCpA = true;
      }

      double cpBValue = profile.getCpB();
      if (Double.isFinite(cpBValue)) {
        cpBMass += massPortion * cpBValue;
        hasCpB = true;
      }

      double cpCValue = profile.getCpC();
      if (Double.isFinite(cpCValue)) {
        cpCMass += massPortion * cpCValue;
        hasCpC = true;
      }

      double cpDValue = profile.getCpD();
      if (Double.isFinite(cpDValue)) {
        cpDMass += massPortion * cpDValue;
        hasCpD = true;
      }

      double attractive = profile.getAttractiveM();
      if (Double.isFinite(attractive)) {
        attractiveMMass += massPortion * attractive;
        hasAttractiveM = true;
      }
    }

    private PseudoComponentProfile buildWithOverrides(Double tbOverride, Double tcOverride,
        Double pcOverride, Double omegaOverride) {
      if (!(mass > massTolerance) || !(moles > massTolerance)) {
        return PseudoComponentProfile.empty();
      }

      double molarMass = mass / moles;
      double density = Double.NaN;
      if (volume > massTolerance) {
        density = mass / volume;
      } else if (densityMass > massTolerance) {
        density = densityMass / mass;
      }

      double tb = tbOverride != null ? tbOverride.doubleValue()
          : hasTb ? tbMass / mass : Double.NaN;
      double tc = tcOverride != null ? tcOverride.doubleValue()
          : hasTc ? tcMass / mass : Double.NaN;
      double pc = pcOverride != null ? pcOverride.doubleValue()
          : hasPc ? pcMass / mass : Double.NaN;
      double omega = omegaOverride != null ? omegaOverride.doubleValue()
          : hasOmega ? omegaMass / mass : Double.NaN;

      double criticalVol = hasCriticalVolume ? criticalVolumeMass / mass : Double.NaN;
      double racket = hasRacketZ ? racketZMass / mass : Double.NaN;
      double racketCpa = hasRacketZCpa ? racketZCpaMass / mass : Double.NaN;
      double parachor = hasParachor ? parachorMass / mass : Double.NaN;
      double criticalVisc = hasCriticalViscosity ? criticalViscosityMass / mass : Double.NaN;
      double triple = hasTriplePoint ? triplePointMass / mass : Double.NaN;
      double heatFusion = hasHeatOfFusion ? heatOfFusionMass / mass : Double.NaN;
      double idealGas = hasIdealGasEnthalpy ? idealGasEnthalpyMass / mass : Double.NaN;
      double cpAValue = hasCpA ? cpAMass / mass : Double.NaN;
      double cpBValue = hasCpB ? cpBMass / mass : Double.NaN;
      double cpCValue = hasCpC ? cpCMass / mass : Double.NaN;
      double cpDValue = hasCpD ? cpDMass / mass : Double.NaN;
      double attractive = hasAttractiveM ? attractiveMMass / mass : Double.NaN;

      return new PseudoComponentProfile(moles, molarMass, density, tb, tc, pc, omega, criticalVol,
          racket, racketCpa, parachor, criticalVisc, triple, heatFusion, idealGas, cpAValue,
          cpBValue, cpCValue, cpDValue, attractive);
    }
  }

  private static final class PseudoComponentProfile {
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

    private PseudoComponentProfile(double moles, double molarMass, double density,
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

    private static PseudoComponentProfile empty() {
      return new PseudoComponentProfile(0.0, 0.0, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
          Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
          Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
    }

    private boolean isValid() {
      return moles > 0.0 && molarMass > 0.0 && density > 0.0;
    }

    private double getMoles() {
      return moles;
    }

    private double getMass() {
      return moles * molarMass;
    }

    private double getMolarMass() {
      return molarMass;
    }

    private double getDensity() {
      return density;
    }

    private double getNormalBoilingPoint() {
      return normalBoilingPoint;
    }

    private double getCriticalTemperature() {
      return criticalTemperature;
    }

    private double getCriticalPressure() {
      return criticalPressure;
    }

    private double getAcentricFactor() {
      return acentricFactor;
    }

    private double getCriticalVolume() {
      return criticalVolume;
    }

    private double getRacketZ() {
      return racketZ;
    }

    private double getRacketZCpa() {
      return racketZCpa;
    }

    private double getParachor() {
      return parachor;
    }

    private double getCriticalViscosity() {
      return criticalViscosity;
    }

    private double getTriplePointTemperature() {
      return triplePointTemperature;
    }

    private double getHeatOfFusion() {
      return heatOfFusion;
    }

    private double getIdealGasEnthalpyOfFormation() {
      return idealGasEnthalpyOfFormation;
    }

    private double getCpA() {
      return cpA;
    }

    private double getCpB() {
      return cpB;
    }

    private double getCpC() {
      return cpC;
    }

    private double getCpD() {
      return cpD;
    }

    private double getAttractiveM() {
      return attractiveM;
    }

    private void applyTo(ComponentInterface component) {
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
