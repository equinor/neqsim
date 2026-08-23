package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.Comparator;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Literature-anchored qualification for hydrogen-rich binary TP flashes.
 *
 * <p>
 * The experimental tie lines are from Sagara, Arai, and Saito, J. Chem. Eng. Japan 5 (1972), 339-348,
 * doi:10.1252/jcej.5.339. Their midpoints are unambiguously inside the experimental two-phase region, while
 * compositions beyond either endpoint anchor the adjacent one-phase regions. The cubic-EOS composition tolerance is a
 * model qualification envelope, not the experimental uncertainty reported by the authors.
 */
class TPflashHydrogenLiteratureConsistencyTest extends neqsim.NeqSimTest {
  private static final double ATM_TO_BAR = 1.01325;
  private static final double MODEL_COMPOSITION_TOLERANCE = 0.04;
  private static final double MATERIAL_BALANCE_TOLERANCE = 1.0e-10;
  private static final double FUGACITY_TOLERANCE = 1.0e-8;
  private static final double BOUNDARY_COMPOSITION_OFFSET = 1.0e-4;
  private static final TieLine[] TIE_LINES = { new TieLine("methane", 123.15, 20.0, 0.0192, 0.818),
      new TieLine("methane", 143.05, 40.2, 0.0477, 0.721), new TieLine("methane", 173.65, 59.9, 0.0709, 0.362),
      new TieLine("ethane", 148.15, 20.0, 0.00618, 0.986), new TieLine("ethane", 173.15, 40.0, 0.0168, 0.977) };

  @Test
  void experimentalTieLinesRemainTwoPhaseAndCrossAlgorithmConsistent() {
    for (Eos eos : Eos.values()) {
      for (TieLine tieLine : TIE_LINES) {
        double overallHydrogen = tieLine.midpoint();
        SystemInterface ordinary = flash(createSystem(eos, tieLine, overallHydrogen, false), false);
        SystemInterface multiphase = flash(createSystem(eos, tieLine, overallHydrogen, true), false);

        assertGasOilEquilibrium(ordinary, tieLine.label(eos));
        assertEquivalent(ordinary, multiphase, tieLine.label(eos));
        assertLiteratureAgreement(ordinary, tieLine, tieLine.label(eos));
      }
    }
  }

  @Test
  void experimentalTieLinesBracketTheAdjacentSinglePhaseRegions() {
    for (Eos eos : Eos.values()) {
      for (TieLine tieLine : TIE_LINES) {
        double liquidSideHydrogen = 0.5 * tieLine.liquidHydrogen;
        double vaporSideHydrogen = Math.min(0.999, tieLine.vaporHydrogen + 0.05);

        assertSinglePhaseAcrossAlgorithms(eos, tieLine, liquidSideHydrogen,
            tieLine.label(eos) + " liquid-composition side");
        assertSinglePhaseAcrossAlgorithms(eos, tieLine, vaporSideHydrogen,
            tieLine.label(eos) + " vapor-composition side");
      }
    }
  }

  @Test
  void calculatedBoundariesPreserveTinyPhasesAndAdjacentSinglePhaseStates() {
    for (Eos eos : Eos.values()) {
      for (TieLine tieLine : TIE_LINES) {
        SystemInterface midpoint = flash(createSystem(eos, tieLine, tieLine.midpoint(), false), false);
        Integer[] order = phaseOrder(midpoint);
        double vaporHydrogen = midpoint.getPhase(order[0]).getComponent("hydrogen").getx();
        double liquidHydrogen = midpoint.getPhase(order[1]).getComponent("hydrogen").getx();

        assertBoundaryStateAcrossAlgorithms(eos, tieLine, liquidHydrogen - BOUNDARY_COMPOSITION_OFFSET, 1,
            tieLine.label(eos) + " below calculated liquid boundary");
        assertBoundaryStateAcrossAlgorithms(eos, tieLine, liquidHydrogen + BOUNDARY_COMPOSITION_OFFSET, 2,
            tieLine.label(eos) + " inside calculated liquid boundary");
        assertBoundaryStateAcrossAlgorithms(eos, tieLine, vaporHydrogen - BOUNDARY_COMPOSITION_OFFSET, 2,
            tieLine.label(eos) + " inside calculated vapor boundary");
        assertBoundaryStateAcrossAlgorithms(eos, tieLine, vaporHydrogen + BOUNDARY_COMPOSITION_OFFSET, 1,
            tieLine.label(eos) + " above calculated vapor boundary");
      }
    }
  }

  @Test
  void calculatedBoundaryTransitionsDoNotRetainStalePhaseState() {
    for (TieLine tieLine : new TieLine[] { TIE_LINES[1], TIE_LINES[2] }) {
      for (Eos eos : Eos.values()) {
        SystemInterface midpoint = flash(createSystem(eos, tieLine, tieLine.midpoint(), false), false);
        double vaporHydrogen = midpoint.getPhase(phaseOrder(midpoint)[0]).getComponent("hydrogen").getx();
        double insideHydrogen = vaporHydrogen - BOUNDARY_COMPOSITION_OFFSET;
        double outsideHydrogen = vaporHydrogen + BOUNDARY_COMPOSITION_OFFSET;

        for (boolean multiphase : new boolean[] { false, true }) {
          SystemInterface insideReference = flash(createSystem(eos, tieLine, insideHydrogen, multiphase), false);
          SystemInterface poorGuess = flash(createSystem(eos, tieLine, insideHydrogen, multiphase), true);
          assertEquivalent(insideReference, poorGuess, tieLine.label(eos) + " boundary poor initialization");

          SystemInterface repeatedReference = insideReference.clone();
          flash(insideReference, false);
          assertEquivalent(repeatedReference, insideReference, tieLine.label(eos) + " boundary repeat");

          insideReference.setMolarComposition(new double[] { outsideHydrogen, 1.0 - outsideHydrogen });
          flash(insideReference, false);
          SystemInterface outsideReference = flash(createSystem(eos, tieLine, outsideHydrogen, multiphase), false);
          assertEquals(1, outsideReference.getNumberOfPhases(), tieLine.label(eos) + " boundary disappearance");
          assertEquivalent(outsideReference, insideReference, tieLine.label(eos) + " boundary disappearance");

          insideReference.setMolarComposition(new double[] { insideHydrogen, 1.0 - insideHydrogen });
          flash(insideReference, false);
          assertEquivalent(poorGuess, insideReference, tieLine.label(eos) + " boundary reappearance");
        }
      }
    }
  }

  @Test
  void poorInitializationRepeatsAndChangedStatesRecoverTheSameEquilibrium() {
    TieLine initialPoint = TIE_LINES[1];
    TieLine changedPoint = TIE_LINES[2];
    for (Eos eos : Eos.values()) {
      for (boolean multiphase : new boolean[] { false, true }) {
        SystemInterface reference = flash(createSystem(eos, initialPoint, initialPoint.midpoint(), multiphase), false);
        SystemInterface continued = flash(createSystem(eos, initialPoint, initialPoint.midpoint(), multiphase), true);
        assertEquivalent(reference, continued, initialPoint.label(eos) + " poor initialization");

        SystemInterface repeatedReference = reference.clone();
        flash(reference, false);
        assertEquivalent(repeatedReference, reference, initialPoint.label(eos) + " repeated flash");

        continued.setTemperature(changedPoint.temperature, "K");
        continued.setPressure(changedPoint.pressureBar(), "bara");
        continued.setMolarComposition(new double[] { changedPoint.midpoint(), 1.0 - changedPoint.midpoint() });
        flash(continued, false);

        SystemInterface changedReference = flash(createSystem(eos, changedPoint, changedPoint.midpoint(), multiphase),
            false);
        assertEquivalent(changedReference, continued, changedPoint.label(eos) + " changed state");
      }
    }
  }

  private static void assertSinglePhaseAcrossAlgorithms(Eos eos, TieLine tieLine, double hydrogenFraction,
      String label) {
    SystemInterface ordinary = flash(createSystem(eos, tieLine, hydrogenFraction, false), false);
    SystemInterface multiphase = flash(createSystem(eos, tieLine, hydrogenFraction, true), false);

    assertEquals(1, ordinary.getNumberOfPhases(), label);
    assertEquivalent(ordinary, multiphase, label);
  }

  private static void assertBoundaryStateAcrossAlgorithms(Eos eos, TieLine tieLine, double hydrogenFraction,
      int expectedPhases, String label) {
    SystemInterface ordinary = flash(createSystem(eos, tieLine, hydrogenFraction, false), false);
    SystemInterface multiphase = flash(createSystem(eos, tieLine, hydrogenFraction, true), false);

    assertEquals(expectedPhases, ordinary.getNumberOfPhases(), label + " ordinary path");
    assertEquals(expectedPhases, multiphase.getNumberOfPhases(), label + " multiphase path");
    assertEquivalent(ordinary, multiphase, label);
  }

  private static void assertGasOilEquilibrium(SystemInterface system, String label) {
    assertEquals(2, system.getNumberOfPhases(), label + " literature tie-line midpoint");
    assertTrue(system.hasPhaseType(PhaseType.GAS), label);
    assertTrue(system.hasPhaseType(PhaseType.OIL), label);
    assertClosure(system, label);
  }

  private static void assertLiteratureAgreement(SystemInterface system, TieLine tieLine, String label) {
    Integer[] order = phaseOrder(system);
    double calculatedVaporHydrogen = system.getPhase(order[0]).getComponent("hydrogen").getx();
    double calculatedLiquidHydrogen = system.getPhase(order[1]).getComponent("hydrogen").getx();
    assertEquals(tieLine.vaporHydrogen, calculatedVaporHydrogen, MODEL_COMPOSITION_TOLERANCE,
        label + " vapor hydrogen composition");
    assertEquals(tieLine.liquidHydrogen, calculatedLiquidHydrogen, MODEL_COMPOSITION_TOLERANCE,
        label + " liquid hydrogen composition");
  }

  private static SystemInterface createSystem(Eos eos, TieLine tieLine, double hydrogenFraction, boolean multiphase) {
    SystemInterface system = eos == Eos.PR ? new SystemPrEos(tieLine.temperature, tieLine.pressureBar())
        : new SystemSrkEos(tieLine.temperature, tieLine.pressureBar());
    system.addComponent("hydrogen", hydrogenFraction);
    system.addComponent(tieLine.hydrocarbon, 1.0 - hydrogenFraction);
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(multiphase);
    return system;
  }

  private static SystemInterface flash(SystemInterface system, boolean poorGuess) {
    if (poorGuess) {
      system.setBeta(0, 1.0e-10);
      system.setBeta(1, 1.0 - 1.0e-10);
    }
    new ThermodynamicOperations(system).TPflash();
    system.init(1);
    return system;
  }

  private static void assertEquivalent(SystemInterface expected, SystemInterface actual, String label) {
    assertEquals(expected.getNumberOfPhases(), actual.getNumberOfPhases(), label);
    assertClosure(expected, label + " reference");
    assertClosure(actual, label + " comparison");
    Integer[] expectedOrder = phaseOrder(expected);
    Integer[] actualOrder = phaseOrder(actual);
    for (int orderedPhase = 0; orderedPhase < expectedOrder.length; orderedPhase++) {
      int expectedPhase = expectedOrder[orderedPhase];
      int actualPhase = actualOrder[orderedPhase];
      assertEquals(expected.getPhase(expectedPhase).getType(), actual.getPhase(actualPhase).getType(), label);
      assertEquals(expected.getBeta(expectedPhase), actual.getBeta(actualPhase), 1.0e-10, label);
      assertEquals(expected.getPhase(expectedPhase).getZ(), actual.getPhase(actualPhase).getZ(), 1.0e-8, label);
      for (int component = 0; component < 2; component++) {
        assertEquals(expected.getPhase(expectedPhase).getComponent(component).getx(),
            actual.getPhase(actualPhase).getComponent(component).getx(), 1.0e-10, label);
      }
    }
    assertEquals(expected.getGibbsEnergy(), actual.getGibbsEnergy(),
        Math.max(1.0e-7, 1.0e-8 * Math.abs(expected.getGibbsEnergy())), label);
  }

  private static Integer[] phaseOrder(SystemInterface system) {
    Integer[] order = new Integer[system.getNumberOfPhases()];
    Arrays.setAll(order, index -> index);
    Arrays.sort(order, Comparator.comparingDouble(index -> -system.getPhase(index).getComponent("hydrogen").getx()));
    return order;
  }

  private static void assertClosure(SystemInterface system, String label) {
    double betaSum = 0.0;
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      double beta = system.getBeta(phase);
      assertTrue(Double.isFinite(beta) && beta >= 0.0 && beta <= 1.0, label);
      betaSum += beta;
      double compositionSum = 0.0;
      for (int component = 0; component < 2; component++) {
        double composition = system.getPhase(phase).getComponent(component).getx();
        assertTrue(Double.isFinite(composition) && composition >= 0.0 && composition <= 1.0, label);
        compositionSum += composition;
      }
      assertEquals(1.0, compositionSum, 1.0e-12, label);
      assertTrue(Double.isFinite(system.getPhase(phase).getZ()) && system.getPhase(phase).getZ() > 0.0, label);
    }
    assertEquals(1.0, betaSum, 1.0e-12, label);

    double maximumMaterialBalanceResidual = 0.0;
    for (int component = 0; component < 2; component++) {
      double recovered = 0.0;
      for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
        recovered += system.getBeta(phase) * system.getPhase(phase).getComponent(component).getx();
      }
      maximumMaterialBalanceResidual = Math.max(maximumMaterialBalanceResidual,
          Math.abs(system.getPhase(0).getComponent(component).getz() - recovered));
    }
    assertTrue(maximumMaterialBalanceResidual < MATERIAL_BALANCE_TOLERANCE,
        label + " material balance residual " + maximumMaterialBalanceResidual);

    if (system.getNumberOfPhases() == 2) {
      double maximumFugacityResidual = 0.0;
      for (int component = 0; component < 2; component++) {
        double firstFugacity = system.getPhase(0).getComponent(component).getx()
            * system.getPhase(0).getComponent(component).getFugacityCoefficient();
        double secondFugacity = system.getPhase(1).getComponent(component).getx()
            * system.getPhase(1).getComponent(component).getFugacityCoefficient();
        maximumFugacityResidual = Math.max(maximumFugacityResidual, Math.abs(Math.log(firstFugacity / secondFugacity)));
      }
      assertTrue(maximumFugacityResidual < FUGACITY_TOLERANCE, label + " fugacity residual " + maximumFugacityResidual);
    }
  }

  private enum Eos {
    SRK, PR
  }

  private static final class TieLine {
    private final String hydrocarbon;
    private final double temperature;
    private final double pressureAtmosphere;
    private final double liquidHydrogen;
    private final double vaporHydrogen;

    private TieLine(String hydrocarbon, double temperature, double pressureAtmosphere, double liquidHydrogen,
        double vaporHydrogen) {
      this.hydrocarbon = hydrocarbon;
      this.temperature = temperature;
      this.pressureAtmosphere = pressureAtmosphere;
      this.liquidHydrogen = liquidHydrogen;
      this.vaporHydrogen = vaporHydrogen;
    }

    private double midpoint() {
      return 0.5 * (liquidHydrogen + vaporHydrogen);
    }

    private double pressureBar() {
      return pressureAtmosphere * ATM_TO_BAR;
    }

    private String label(Eos eos) {
      return eos + " hydrogen+" + hydrocarbon + " at " + temperature + " K and " + pressureAtmosphere + " atm";
    }
  }
}
