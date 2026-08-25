package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhasePitzer;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.phase.PitzerParameterDatasets;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPitzer;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Scientific and compatibility tests for bracketed electrolyte VLE/VLLE boundaries. */
class ElectrolytePhaseBoundaryFlashTest extends neqsim.NeqSimTest {
  private static final double LOWER_TEMPERATURE_K = 313.15;
  private static final double UPPER_TEMPERATURE_K = 700.0;
  private static final double BOUNDARY_TOLERANCE_K = 0.5;

  @Test
  void pitzerOilAppearanceBoundaryIsDeterministicAndKeepsAutomaticCatalog() throws Exception {
    SystemPitzer firstSystem = createPitzerSystem();
    ElectrolytePhaseBoundaryResult first = solveOilBoundary(firstSystem);

    assertBoundaryContract(first, firstSystem);
    PhasePitzer aqueous = (PhasePitzer) firstSystem.getGeLiquidPhase();
    assertEquals(PitzerParameterDatasets.PHREEQC_PITZER_CATALOG_ID,
        aqueous.getParameterDatasetId());
    double scalePotential =
        new ThermodynamicOperations(firstSystem).getRelativeScalePotential("NaCl");
    assertTrue(Double.isFinite(scalePotential) && scalePotential > 0.0);

    SystemPitzer repeatedSystem = createPitzerSystem();
    ElectrolytePhaseBoundaryResult repeated = solveOilBoundary(repeatedSystem);
    assertEquals(first.getLowerBound(), repeated.getLowerBound(), 0.0);
    assertEquals(first.getUpperBound(), repeated.getUpperBound(), 0.0);
    assertEquals(first.getTargetPresentValue(), repeated.getTargetPresentValue(), 0.0);

    SystemPitzer clonedSystem = createPitzerSystem().clone();
    ElectrolytePhaseBoundaryResult cloned = solveOilBoundary(clonedSystem);
    assertEquals(first.getBoundaryValue(), cloned.getBoundaryValue(), 0.0);

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(first);
    }
    ElectrolytePhaseBoundaryResult restored;
    try (ObjectInputStream input =
        new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (ElectrolytePhaseBoundaryResult) input.readObject();
    }
    assertEquals(first.getBoundaryValue(), restored.getBoundaryValue(), 0.0);
    assertEquals(first.getLowerTopology(), restored.getLowerTopology());
    assertEquals(first.getUpperTopology(), restored.getUpperTopology());
  }

  @Test
  void electrolyteCpaUsesSameVlleBoundaryContract() throws Exception {
    SystemInterface system = createElectrolyteCpaSystem();
    ElectrolytePhaseBoundaryResult result = solveOilBoundary(system);

    assertBoundaryContract(result, system);
    double scalePotential =
        new ThermodynamicOperations(system).getRelativeScalePotential("NaCl");
    assertTrue(Double.isFinite(scalePotential) && scalePotential > 0.0);
  }

  @Test
  void invalidOrUnbracketedRequestsFailClosedWithoutChangingTheFeed() {
    SystemPitzer system = createPitzerSystem();
    double initialTemperature = system.getTemperature();
    assertThrows(IllegalArgumentException.class,
        () -> new ElectrolytePhaseBoundaryFlash(system,
            ElectrolytePhaseBoundaryResult.Specification.TEMPERATURE, PhaseType.OIL,
            400.0, 300.0, 0.1, 20));
    assertEquals(initialTemperature, system.getTemperature(), 0.0);

    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> new ElectrolytePhaseBoundaryFlash(system,
            ElectrolytePhaseBoundaryResult.Specification.TEMPERATURE, PhaseType.AQUEOUS,
            LOWER_TEMPERATURE_K, UPPER_TEMPERATURE_K, BOUNDARY_TOLERANCE_K, 20).solve());
    assertTrue(error.getMessage().contains("do not bracket"));
    assertEquals(initialTemperature, system.getTemperature(), 0.0);
  }

  private static ElectrolytePhaseBoundaryResult solveOilBoundary(SystemInterface system) {
    return new ThermodynamicOperations(system).electrolytePhaseBoundaryTemperatureFlash(
        PhaseType.OIL, LOWER_TEMPERATURE_K, UPPER_TEMPERATURE_K,
        BOUNDARY_TOLERANCE_K, 20);
  }

  private static void assertBoundaryContract(ElectrolytePhaseBoundaryResult result,
      SystemInterface system) {
    assertEquals(ElectrolytePhaseBoundaryResult.Specification.TEMPERATURE,
        result.getSpecification());
    assertEquals(PhaseType.OIL, result.getTargetPhase());
    assertTrue(result.getBoundaryValue() > LOWER_TEMPERATURE_K);
    assertTrue(result.getBoundaryValue() < UPPER_TEMPERATURE_K);
    assertTrue(result.getBracketWidth() <= BOUNDARY_TOLERANCE_K);
    assertNotEquals(result.getLowerTopology(), result.getUpperTopology());
    assertTrue(result.getLowerTopology().contains("oil")
        || result.getUpperTopology().contains("oil"));
    assertTrue(result.getTargetPhaseFraction() > 1.0e-10);
    assertTrue(result.getFlashEvaluations() <= result.getIterations() + 3);
    assertTrue(result.getMaximumMaterialBalanceResidual() <= 1.0e-7);
    assertTrue(result.getMaximumPhaseNormalizationResidual() <= 1.0e-10);
    assertTrue(Math.abs(result.getAqueousChargeMolality()) <= 1.0e-8);
    assertTrue(result.getMaximumIonMoleFractionOutsideAqueous() <= 1.0e-30);
    assertTrue(result.getMaximumLogFugacityResidual() <= 1.0e-5);
    assertTrue(hasPhase(system, PhaseType.OIL));
  }

  private static SystemPitzer createPitzerSystem() {
    SystemPitzer system = new SystemPitzer(LOWER_TEMPERATURE_K, 50.0);
    addGasOilBrine(system);
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(true);
    return system;
  }

  private static SystemInterface createElectrolyteCpaSystem() {
    SystemInterface system = new SystemElectrolyteCPAstatoil(LOWER_TEMPERATURE_K, 50.0);
    addGasOilBrine(system);
    system.chemicalReactionInit();
    system.createDatabase(true);
    system.setMixingRule(10);
    system.setMultiPhaseCheck(true);
    return system;
  }

  private static void addGasOilBrine(SystemInterface system) {
    system.addComponent("methane", 5.0);
    system.addComponent("n-heptane", 2.0);
    system.addComponent("water", 55.508);
    system.addComponent("Na+", 1.0);
    system.addComponent("Cl-", 1.0);
  }

  private static boolean hasPhase(SystemInterface system, PhaseType type) {
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      if (system.getPhase(phase).getType() == type && system.getBeta(phase) > 1.0e-10) {
        return true;
      }
    }
    return false;
  }
}
