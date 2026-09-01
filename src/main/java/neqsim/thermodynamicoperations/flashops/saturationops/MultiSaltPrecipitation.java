package neqsim.thermodynamicoperations.flashops.saturationops;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Active-set equilibrium for competing pure COMPSALT minerals.
 *
 * <p>
 * The operation owns an external non-negative solid ledger. It repeatedly precipitates the most supersaturated mineral
 * or redissolves the most undersaturated present mineral, always using the active thermodynamic system's aqueous
 * activities. Mineral names are sorted so the result is independent of caller ordering.
 * </p>
 */
public final class MultiSaltPrecipitation {
  private static final double COMPLEMENTARITY_TOLERANCE = 1.0e-6;
  private static final double ABSOLUTE_BALANCE_TOLERANCE_MOLES = 1.0e-10;
  private static final double REACTIVE_ELEMENT_RELATIVE_BALANCE_TOLERANCE = 1.0e-8;
  private static final double SOLID_ZERO_TOLERANCE_MOLES = 1.0e-14;
  private static final int MAXIMUM_UPDATES = 100;

  private final SystemInterface system;
  private final List<String> mineralNames;
  private final Map<String, CalcSaltSatauration> operations = new LinkedHashMap<String, CalcSaltSatauration>();
  private final Map<String, Double> startingSolidMoles = new LinkedHashMap<String, Double>();

  /**
   * Creates a simultaneous pure-mineral equilibrium operation.
   *
   * @param system thermodynamic system whose dissolved state will be updated
   * @param requestedMineralNames unique COMPSALT mineral names
   * @throws IllegalArgumentException if no mineral is supplied or a name is blank or duplicated
   */
  public MultiSaltPrecipitation(SystemInterface system, String... requestedMineralNames) {
    this(system, null, requestedMineralNames);
  }

  /**
   * Creates a continuation operation with an existing non-negative pure-solid ledger.
   *
   * @param system thermodynamic system containing the matching residual dissolved inventory
   * @param previousResult previous simultaneous-mineral result to re-equilibrate after a state change
   * @throws IllegalArgumentException if the result is null or contains an invalid solid amount
   */
  public MultiSaltPrecipitation(SystemInterface system, MultiSaltPrecipitationResult previousResult) {
    this(system, requirePreviousResult(previousResult),
        previousResult.getMineralResults().keySet().toArray(new String[previousResult.getMineralResults().size()]));
  }

  /** Shared constructor for fresh and continuation operations. */
  private MultiSaltPrecipitation(SystemInterface system, MultiSaltPrecipitationResult previousResult,
      String... requestedMineralNames) {
    if (system == null) {
      throw new IllegalArgumentException("Thermodynamic system cannot be null");
    }
    if (requestedMineralNames == null || requestedMineralNames.length == 0) {
      throw new IllegalArgumentException("At least one COMPSALT mineral must be supplied");
    }
    Set<String> uniqueNames = new LinkedHashSet<String>();
    for (String name : requestedMineralNames) {
      if (name == null || name.trim().isEmpty()) {
        throw new IllegalArgumentException("COMPSALT mineral names cannot be blank");
      }
      if (!uniqueNames.add(name)) {
        throw new IllegalArgumentException("Duplicate COMPSALT mineral name: " + name);
      }
    }
    this.system = system;
    this.mineralNames = new ArrayList<String>(uniqueNames);
    Collections.sort(this.mineralNames);
    for (String name : this.mineralNames) {
      operations.put(name, new CalcSaltSatauration(system, name));
      double startingAmount = previousResult == null ? 0.0
          : previousResult.getMineralResult(name).getPrecipitatedMoles();
      if (!Double.isFinite(startingAmount) || startingAmount < 0.0) {
        throw new IllegalArgumentException("Existing solid amount must be finite and non-negative for " + name);
      }
      startingSolidMoles.put(name, startingAmount);
    }
  }

  /** Validates and returns a continuation result for constructor chaining. */
  private static MultiSaltPrecipitationResult requirePreviousResult(MultiSaltPrecipitationResult previousResult) {
    if (previousResult == null) {
      throw new IllegalArgumentException("Previous simultaneous-mineral result cannot be null");
    }
    return previousResult;
  }

  /**
   * Solves precipitation/dissolution complementarity for all requested minerals.
   *
   * @return immutable final solid ledger and convergence diagnostics
   * @throws IllegalStateException if complementarity or the component/element ledger does not converge
   */
  public MultiSaltPrecipitationResult solve() {
    Map<String, Double> initialSaturationRatios = new LinkedHashMap<String, Double>();
    Map<String, Double> solidMoles = new LinkedHashMap<String, Double>();
    Map<String, Double> initialComponentMoles = new LinkedHashMap<String, Double>();
    for (String name : mineralNames) {
      CalcSaltSatauration mineralOperation = operations.get(name);
      initialSaturationRatios.put(name, mineralOperation.getCurrentSaturationRatio());
      solidMoles.put(name, startingSolidMoles.get(name));
      rememberInitialComponent(initialComponentMoles, mineralOperation.getIon1Name());
      rememberInitialComponent(initialComponentMoles, mineralOperation.getIon2Name());
    }
    addStartingSolidsToInitialLedger(initialComponentMoles);
    Set<String> conservedElementNames = collectConservedElementNames();
    Map<String, Double> initialElementMoles = calculateSystemElementMoles(conservedElementNames);
    addStartingSolidsToInitialElementLedger(initialElementMoles);

    int updates = 0;
    while (updates < MAXIMUM_UPDATES) {
      Violation largestViolation = findLargestViolation(solidMoles);
      if (largestViolation.value <= COMPLEMENTARITY_TOLERANCE) {
        break;
      }

      CalcSaltSatauration mineralOperation = operations.get(largestViolation.mineralName);
      double previousSolidMoles = solidMoles.get(largestViolation.mineralName);
      double updatedSolidMoles;
      if (largestViolation.saturationRatio > 1.0) {
        SaltPrecipitationResult increment = mineralOperation.precipitate();
        updatedSolidMoles = previousSolidMoles + increment.getPrecipitatedMoles();
      } else {
        double dissolvedMoles = mineralOperation.dissolve(previousSolidMoles);
        updatedSolidMoles = Math.max(0.0, previousSolidMoles - dissolvedMoles);
      }
      if (Math.abs(updatedSolidMoles - previousSolidMoles) <= SOLID_ZERO_TOLERANCE_MOLES) {
        throw new IllegalStateException("Mineral complementarity stalled for " + largestViolation.mineralName
            + " at SR=" + largestViolation.saturationRatio);
      }
      solidMoles.put(largestViolation.mineralName, updatedSolidMoles);
      updates++;
    }

    Violation finalViolation = findLargestViolation(solidMoles);
    if (finalViolation.value > COMPLEMENTARITY_TOLERANCE) {
      throw new IllegalStateException("Pure-mineral complementarity did not converge in " + MAXIMUM_UPDATES
          + " updates; maximum violation=" + finalViolation.value + " for " + finalViolation.mineralName);
    }

    BalanceDiagnostics balance = calculateBalanceDiagnostics(initialComponentMoles, initialElementMoles,
        conservedElementNames, solidMoles);
    if (balance.maximumNormalizedResidual > 1.0) {
      throw new IllegalStateException(
          "Pure-mineral component/element ledger did not close for " + balance.limitingQuantity + "; residual="
              + balance.limitingResidualMoles + " mol, tolerance=" + balance.limitingToleranceMoles + " mol");
    }

    Map<String, SaltPrecipitationResult> mineralResults = new LinkedHashMap<String, SaltPrecipitationResult>();
    for (String name : mineralNames) {
      CalcSaltSatauration mineralOperation = operations.get(name);
      double finalSaturationRatio = mineralOperation.getCurrentSaturationRatio();
      double amount = solidMoles.get(name);
      if (amount <= SOLID_ZERO_TOLERANCE_MOLES) {
        amount = 0.0;
      }
      mineralResults.put(name,
          new SaltPrecipitationResult(name, amount, amount * mineralOperation.getSolidMolarMassGrams(),
              initialSaturationRatios.get(name), finalSaturationRatio, balance.maximumAbsoluteResidualMoles));
    }
    return new MultiSaltPrecipitationResult(mineralResults, updates, finalViolation.value,
        balance.maximumAbsoluteResidualMoles, balance.maximumNormalizedResidual);
  }

  /** Records the initial dissolved amount for one component exactly once. */
  private void rememberInitialComponent(Map<String, Double> initialComponentMoles, String componentName) {
    if (!initialComponentMoles.containsKey(componentName)) {
      initialComponentMoles.put(componentName, system.getComponent(componentName).getNumberOfmoles());
    }
  }

  /** Adds the supplied starting solids to the conserved overall component inventory. */
  private void addStartingSolidsToInitialLedger(Map<String, Double> initialComponentMoles) {
    for (String name : mineralNames) {
      CalcSaltSatauration mineralOperation = operations.get(name);
      double amount = startingSolidMoles.get(name);
      initialComponentMoles.put(mineralOperation.getIon1Name(),
          initialComponentMoles.get(mineralOperation.getIon1Name()) + mineralOperation.getIon1Stoichiometry() * amount);
      initialComponentMoles.put(mineralOperation.getIon2Name(),
          initialComponentMoles.get(mineralOperation.getIon2Name()) + mineralOperation.getIon2Stoichiometry() * amount);
    }
  }

  /** Finds the largest KKT complementarity violation in deterministic mineral order. */
  private Violation findLargestViolation(Map<String, Double> solidMoles) {
    Violation largest = new Violation(mineralNames.get(0), 0.0, 0.0);
    for (String name : mineralNames) {
      double saturationRatio = operations.get(name).getCurrentSaturationRatio();
      if (!Double.isFinite(saturationRatio) || saturationRatio < 0.0) {
        throw new IllegalStateException("Invalid saturation ratio for " + name + ": " + saturationRatio);
      }
      double logarithmicSaturation = saturationRatio > 0.0 ? Math.log10(saturationRatio) : Double.NEGATIVE_INFINITY;
      double violation = solidMoles.get(name) > SOLID_ZERO_TOLERANCE_MOLES ? Math.abs(logarithmicSaturation)
          : Math.max(logarithmicSaturation, 0.0);
      if (violation > largest.value) {
        largest = new Violation(name, saturationRatio, violation);
      }
    }
    return largest;
  }

  /** Calculates absolute and scale-aware component/element ledger diagnostics. */
  private BalanceDiagnostics calculateBalanceDiagnostics(Map<String, Double> initialComponentMoles,
      Map<String, Double> initialElementMoles, Set<String> conservedElementNames, Map<String, Double> solidMoles) {
    Map<String, Double> solidComponentMoles = new LinkedHashMap<String, Double>();
    for (String name : mineralNames) {
      CalcSaltSatauration mineralOperation = operations.get(name);
      addToComponentLedger(solidComponentMoles, mineralOperation.getIon1Name(),
          mineralOperation.getIon1Stoichiometry() * solidMoles.get(name));
      addToComponentLedger(solidComponentMoles, mineralOperation.getIon2Name(),
          mineralOperation.getIon2Stoichiometry() * solidMoles.get(name));
    }

    BalanceDiagnostics diagnostics = new BalanceDiagnostics();
    if (!system.isChemicalSystem()) {
      for (Map.Entry<String, Double> initial : initialComponentMoles.entrySet()) {
        double dissolved = system.getComponent(initial.getKey()).getNumberOfmoles();
        Double solid = solidComponentMoles.get(initial.getKey());
        double residual = initial.getValue() - dissolved - (solid == null ? 0.0 : solid.doubleValue());
        diagnostics.record("component " + initial.getKey(), residual, ABSOLUTE_BALANCE_TOLERANCE_MOLES);
      }
    }

    Map<String, Double> finalElementMoles = calculateSystemElementMoles(conservedElementNames);
    addSolidElementsToLedger(finalElementMoles, solidMoles);
    for (Map.Entry<String, Double> initial : initialElementMoles.entrySet()) {
      double finalMoles = finalElementMoles.get(initial.getKey());
      double residual = initial.getValue() - finalMoles;
      double tolerance = ABSOLUTE_BALANCE_TOLERANCE_MOLES;
      if (system.isChemicalSystem()) {
        double inventoryScale = Math.max(Math.abs(initial.getValue()), Math.abs(finalMoles));
        tolerance = Math.max(tolerance, REACTIVE_ELEMENT_RELATIVE_BALANCE_TOLERANCE * inventoryScale);
      }
      diagnostics.record("element " + initial.getKey(), residual, tolerance);
    }
    return diagnostics;
  }

  /** Collects every conserved element appearing in the requested mineral formulas. */
  private Set<String> collectConservedElementNames() {
    Set<String> elementNames = new LinkedHashSet<String>();
    for (String name : mineralNames) {
      CalcSaltSatauration mineralOperation = operations.get(name);
      addComponentElementNames(elementNames, mineralOperation.getIon1Name());
      addComponentElementNames(elementNames, mineralOperation.getIon2Name());
      if (mineralOperation.getWaterStoichiometry() > 0.0) {
        addComponentElementNames(elementNames, "water");
      }
    }
    return elementNames;
  }

  /** Adds one component's elemental symbols to the conserved set, failing closed when absent. */
  private void addComponentElementNames(Set<String> elementNames, String componentName) {
    ComponentInterface component = system.getComponent(componentName);
    if (component.getElements() == null || component.getElements().getElementNames() == null
        || component.getElements().getElementNames().length == 0) {
      throw new IllegalStateException("No elemental composition is available for " + componentName);
    }
    Collections.addAll(elementNames, component.getElements().getElementNames());
  }

  /** Calculates dissolved-system moles for the requested conserved elements. */
  private Map<String, Double> calculateSystemElementMoles(Set<String> conservedElementNames) {
    Map<String, Double> elementMoles = new LinkedHashMap<String, Double>();
    for (String elementName : conservedElementNames) {
      elementMoles.put(elementName, 0.0);
    }
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      PhaseInterface phase = system.getPhase(phaseIndex);
      for (int componentIndex = 0; componentIndex < phase.getNumberOfComponents(); componentIndex++) {
        ComponentInterface component = phase.getComponent(componentIndex);
        addComponentElements(elementMoles, component, component.getNumberOfMolesInPhase());
      }
    }
    return elementMoles;
  }

  /** Adds the supplied starting solids to the conserved overall elemental inventory. */
  private void addStartingSolidsToInitialElementLedger(Map<String, Double> initialElementMoles) {
    addSolidElementsToLedger(initialElementMoles, startingSolidMoles);
  }

  /** Adds all requested pure solids to an elemental material ledger. */
  private void addSolidElementsToLedger(Map<String, Double> elementMoles, Map<String, Double> solidMoles) {
    for (String name : mineralNames) {
      CalcSaltSatauration mineralOperation = operations.get(name);
      double amount = solidMoles.get(name);
      addComponentElements(elementMoles, system.getComponent(mineralOperation.getIon1Name()),
          mineralOperation.getIon1Stoichiometry() * amount);
      addComponentElements(elementMoles, system.getComponent(mineralOperation.getIon2Name()),
          mineralOperation.getIon2Stoichiometry() * amount);
      if (mineralOperation.getWaterStoichiometry() > 0.0) {
        addComponentElements(elementMoles, system.getComponent("water"),
            mineralOperation.getWaterStoichiometry() * amount);
      }
    }
  }

  /** Adds one component amount to the selected elemental ledger. */
  private static void addComponentElements(Map<String, Double> elementMoles, ComponentInterface component,
      double componentMoles) {
    if (component.getElements() == null) {
      return;
    }
    String[] elementNames = component.getElements().getElementNames();
    double[] elementCoefficients = component.getElements().getElementCoefs();
    if (elementNames == null || elementCoefficients == null) {
      return;
    }
    if (elementNames.length != elementCoefficients.length) {
      throw new IllegalStateException("Inconsistent elemental composition for " + component.getComponentName());
    }
    for (int elementIndex = 0; elementIndex < elementNames.length; elementIndex++) {
      Double previous = elementMoles.get(elementNames[elementIndex]);
      if (previous != null) {
        elementMoles.put(elementNames[elementIndex],
            previous.doubleValue() + elementCoefficients[elementIndex] * componentMoles);
      }
    }
  }

  /** Adds a component amount to the solid material ledger. */
  private static void addToComponentLedger(Map<String, Double> ledger, String componentName, double amount) {
    Double previous = ledger.get(componentName);
    ledger.put(componentName, (previous == null ? 0.0 : previous.doubleValue()) + amount);
  }

  /** Internal absolute and normalized balance diagnostics. */
  private static final class BalanceDiagnostics {
    private double maximumAbsoluteResidualMoles;
    private double maximumNormalizedResidual;
    private String limitingQuantity = "none";
    private double limitingResidualMoles;
    private double limitingToleranceMoles = ABSOLUTE_BALANCE_TOLERANCE_MOLES;

    /** Records one residual against its quantity-specific tolerance. */
    private void record(String quantity, double residualMoles, double toleranceMoles) {
      double absoluteResidual = Math.abs(residualMoles);
      maximumAbsoluteResidualMoles = Math.max(maximumAbsoluteResidualMoles, absoluteResidual);
      double normalizedResidual = absoluteResidual / toleranceMoles;
      if (normalizedResidual > maximumNormalizedResidual) {
        maximumNormalizedResidual = normalizedResidual;
        limitingQuantity = quantity;
        limitingResidualMoles = residualMoles;
        limitingToleranceMoles = toleranceMoles;
      }
    }
  }

  /** One mineral's current complementarity violation. */
  private static final class Violation {
    private final String mineralName;
    private final double saturationRatio;
    private final double value;

    private Violation(String mineralName, double saturationRatio, double value) {
      this.mineralName = mineralName;
      this.saturationRatio = saturationRatio;
      this.value = value;
    }
  }
}
