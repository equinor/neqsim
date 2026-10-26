package neqsim.pvtsimulation.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import neqsim.pvtsimulation.simulation.ConstantMassExpansion;
import neqsim.pvtsimulation.simulation.ViscositySim;
import neqsim.thermo.component.ComponentEos;
import neqsim.thermo.component.ComponentPR;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression coverage for model evidence, independent replay and rejected lossy delivery.
 *
 * @author ESOL
 * @version 1.0
 */
class PVTFluidStudyTest extends neqsim.NeqSimTest {
  @TempDir
  Path directory;

  /**
   * Create a synthetic mixed-phase hydrocarbon model with explicitly exportable transport settings.
   *
   * @param pr true for PR, false for SRK
   * @return initialized, normalized synthetic model
   */
  private SystemInterface fluid(boolean pr) {
    SystemInterface fluid = pr ? new SystemPrEos(323.15, 250.0) : new SystemSrkEos(323.15, 250.0);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-decane", 0.15);
    fluid.setMixingRule("classic");
    fluid.useVolumeCorrection(true);
    fluid.init(0);
    PVTFluidStudy.configureViscosity(fluid, "LBC");
    return fluid;
  }

  /**
   * Parse a utility result for assertions on physical evidence.
   *
   * @param text utility JSON output
   * @return parsed result object
   */
  private JsonObject json(String text) {
    return JsonParser.parseString(text).getAsJsonObject();
  }

  /**
   * Preserve fitted cubic parameters in a native checkpoint without changing the final model.
   *
   * @throws IOException if a temporary checkpoint cannot be written or read
   */
  @Test
  void captureAndNativeCheckpointPreserveFittedParametersWithoutMutation() throws IOException {
    SystemInterface fluid = fluid(true);
    for (int p = 0; p < fluid.getMaxNumberOfPhases(); p++) {
      ((ComponentEos) fluid.getPhase(p).getComponent(3)).setOmegaA(0.49);
      ((ComponentPR) fluid.getPhase(p).getComponent(3)).setOmegaB(0.082);
    }
    fluid.init(0);
    double temperature = fluid.getTemperature();
    double pressure = fluid.getPressure();
    JsonObject capture = json(PVTFluidStudy.capture(fluid));
    JsonObject component = capture.getAsJsonArray("components").get(3).getAsJsonObject();
    assertEquals(0.49, component.get("omegaAOverride").getAsDouble(), 0.0);
    assertEquals(0.082, component.get("omegaB").getAsDouble(), 0.0);
    assertTrue(component.has("attractiveParameters"));
    assertEquals(16,
        capture.getAsJsonArray("phases").get(0).getAsJsonObject().getAsJsonArray("binaryInteractionParameters").size());
    Path checkpoint = directory.resolve("model.ser");
    PVTFluidStudy.writeNativeSnapshot(fluid, checkpoint.toString());
    SystemInterface restored = PVTFluidStudy.readNativeSnapshot(checkpoint.toString());
    assertNotSame(fluid, restored);
    assertEquals(0.49, ((ComponentEos) restored.getComponent(3)).getOmegaAOverride(), 0.0);
    assertEquals(0.082, ((ComponentPR) restored.getComponent(3)).getOmegaB(), 0.0);
    restored.setTemperature(400.0);
    assertEquals(temperature, fluid.getTemperature(), 0.0);
    assertEquals(pressure, fluid.getPressure(), 0.0);
  }

  /** Verify existing CCE predictions, pressure ordering and immutable input ownership. */
  @Test
  void cceUsesExistingExperimentAndLeavesTheFinalModelUntouched() {
    SystemInterface fluid = fluid(false);
    double[] pressures = {300.0, 150.0, 50.0};
    ConstantMassExpansion reference = new ConstantMassExpansion(fluid.clone());
    reference.setTemperature(323.15, "K");
    reference.setPressures(pressures.clone());
    reference.runCalc();
    JsonObject result = json(PVTFluidStudy.simulate(fluid, "CCE", 323.15, pressures));
    assertEquals(reference.getRelativeVolume()[2],
        result.getAsJsonObject("values").getAsJsonArray("relativeVolume").get(2).getAsDouble(), 1.0e-12);
    assertTrue(result.get("legacyReportMarkdown").getAsString().contains("Constant Composition Expansion"));
    assertTrue(result.get("legacyReportLimitations").getAsString().contains("not verified study findings"));
    assertEquals(250.0, fluid.getPressure(), 0.0);
    assertEquals(323.15, fluid.getTemperature(), 0.0);
    assertEquals(300.0, pressures[0], 0.0);
  }

  /** Keep dry-fluid phase absence separate from the legacy numerical zero convention. */
  @Test
  void dryViscosityUsesNullForAbsentPhasesWithoutChangingLegacyValues() {
    SystemInterface fluid = fluid(false);
    double[] pressures = {300.0, 150.0, 50.0};
    JsonObject result = json(PVTFluidStudy.simulate(fluid, "VISCOSITY", 323.15, pressures));
    JsonArray aqueous = result.getAsJsonObject("values").getAsJsonArray("aqueousViscosityPaS");
    for (int i = 0; i < pressures.length; i++) {
      assertTrue(aqueous.get(i).isJsonNull(), "Absent aqueous viscosity must be unavailable");
      assertFalse(result.getAsJsonObject("phaseAvailability").getAsJsonArray("aqueous").get(i).getAsBoolean());
    }
    ViscositySim legacy = new ViscositySim(fluid.clone());
    legacy.setTemperaturesAndPressures(new double[] {323.15, 323.15, 323.15}, pressures);
    legacy.runCalc();
    for (double viscosity : legacy.getAqueousViscosity()) {
      assertEquals(0.0, viscosity, 0.0);
    }
    assertEquals(250.0, fluid.getPressure(), 0.0);
  }

  /** Retain point-wise phase presence when a real aqueous phase disappears and returns. */
  @Test
  void viscosityAvailabilityFollowsFlashedWaterPhaseAtEveryPoint() {
    SystemInterface fluid = new SystemSrkEos(298.15, 1.0);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule("classic");
    fluid.init(0);
    double[] pressures = {1.0, 0.001, 1.0};
    JsonObject result = json(PVTFluidStudy.simulate(fluid, "VISCOSITY", 298.15, pressures));
    JsonObject availability = result.getAsJsonObject("phaseAvailability");
    JsonObject values = result.getAsJsonObject("values");
    SystemInterface reference = fluid.clone();
    ThermodynamicOperations operations = new ThermodynamicOperations(reference);
    for (int i = 0; i < pressures.length; i++) {
      reference.setPressure(pressures[i]);
      operations.TPflash();
      reference.initPhysicalProperties();
      for (String phase : new String[] {"gas", "oil", "aqueous"}) {
        boolean present = reference.hasPhaseType(phase);
        assertEquals(present, availability.getAsJsonArray(phase).get(i).getAsBoolean());
        if (present) {
          double viscosity = reference.getPhase(phase).getPhysicalProperties().getViscosity();
          assertTrue(Double.isFinite(viscosity));
          assertTrue(viscosity > 0.0);
          assertEquals(viscosity, values.getAsJsonArray(phase + "ViscosityPaS").get(i).getAsDouble(), 1.0e-12);
        } else {
          assertTrue(values.getAsJsonArray(phase + "ViscosityPaS").get(i).isJsonNull());
        }
      }
    }
    assertTrue(availability.getAsJsonArray("aqueous").get(0).getAsBoolean());
    assertFalse(availability.getAsJsonArray("aqueous").get(1).getAsBoolean());
    assertTrue(availability.getAsJsonArray("aqueous").get(2).getAsBoolean());

    ViscositySim repeated = new ViscositySim(fluid.clone());
    repeated.setTemperaturesAndPressures(new double[] {298.15, 298.15}, new double[] {1.0, 0.001});
    repeated.runCalc();
    assertFalse(repeated.getAqueousPhaseAvailable()[1]);
    assertEquals(0.0, repeated.getAqueousViscosity()[1], 0.0);
    repeated.setTemperaturesAndPressures(new double[] {298.15}, new double[] {1.0});
    repeated.runCalc();
    assertEquals(1, repeated.getGasPhaseAvailable().length);
    assertEquals(1, repeated.getOilPhaseAvailable().length);
    assertEquals(1, repeated.getAqueousPhaseAvailable().length);
    assertTrue(repeated.getAqueousPhaseAvailable()[0]);
    assertTrue(repeated.getAqueousViscosity()[0] > 0.0);
    assertFalse(repeated.getGasPhaseAvailable()[0]);
    assertEquals(0.0, repeated.getGasViscosity()[0], 0.0);
  }

  /**
   * Qualify both cubic EOS families against actual phase states and a complete expansion experiment.
   *
   * @throws IOException if an export cannot be written
   */
  @Test
  void srkAndPrFilesReplayActualPhaseStatesAndCce() throws IOException {
    for (boolean pr : new boolean[] {false, true}) {
      SystemInterface fluid = fluid(pr);
      Path file = directory.resolve(pr ? "pr.e300" : "srk.e300");
      PVTFluidStudy.exportE300(fluid, file.toString(), 323.15);
      JsonObject audit = json(
          PVTFluidStudy.auditE300(fluid, file.toString(), 323.15, new double[] {300.0, 150.0, 50.0}));
      assertTrue(audit.get("passed").getAsBoolean(), audit.toString());
      assertEquals(3, audit.getAsJsonArray("importedTP").size());
      assertTrue(audit.getAsJsonObject("importedCCE").has("relativeVolume"));
      assertEquals(250.0, fluid.getPressure(), 0.0);
    }
  }

  /**
   * Reject a delivered file whose normalized composition no longer matches the final model.
   *
   * @throws IOException if a temporary file cannot be read or written
   */
  @Test
  void replayRejectsAChangedCompositionInTheDeliveredFile() throws IOException {
    SystemInterface fluid = fluid(false);
    Path file = directory.resolve("changed.e300");
    PVTFluidStudy.exportE300(fluid, file.toString(), 323.15);
    String exported = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    assertTrue(exported.contains("0.7000000000"));
    Files.write(file, exported.replace("0.7000000000", "0.6000000000").replace("0.1500000000", "0.2500000000")
        .getBytes(StandardCharsets.UTF_8));
    JsonObject audit = json(PVTFluidStudy.auditE300(fluid, file.toString(), 323.15, new double[] {300.0, 150.0, 50.0}));
    assertFalse(audit.get("passed").getAsBoolean());
    assertTrue(audit.getAsJsonArray("differences").toString().contains("moleFraction"));
  }

  /** Ensure unsupported physics and unrepresented fitted parameters never produce a delivery file. */
  @Test
  void rejectsUnsupportedModelsAndParametersBeforeCreatingAnE300File() {
    Path file = directory.resolve("unsupported.e300");
    SystemInterface cpa = new SystemSrkCPAstatoil(323.15, 100.0);
    cpa.addComponent("methane", 1.0);
    cpa.setMixingRule(10);
    assertThrows(IllegalArgumentException.class, () -> PVTFluidStudy.exportE300(cpa, file.toString(), 323.15));
    SystemInterface temperatureDependent = fluid(false);
    ((PhaseEosInterface) temperatureDependent.getPhase(0)).getEosMixingRule().setBinaryInteractionParameterT1(0, 1,
        0.001);
    assertThrows(IllegalArgumentException.class,
        () -> PVTFluidStudy.exportE300(temperatureDependent, file.toString(), 323.15));
    SystemInterface fittedViscosity = fluid(false);
    for (int p = 0; p < fittedViscosity.getNumberOfPhases(); p++) {
      fittedViscosity.getPhase(p).getPhysicalProperties().setViscosityModel("PFCT");
      fittedViscosity.getPhase(p).getPhysicalProperties()
          .setCspViscosityCorrectionFactors(new double[] {1.1, 1.0, 1.0, 1.0});
    }
    assertThrows(IllegalArgumentException.class,
        () -> PVTFluidStudy.exportE300(fittedViscosity, file.toString(), 323.15));
    assertFalse(Files.exists(file));
  }

  /** Exercise all thermodynamic checks and confirm that their perturbations do not mutate the model. */
  @Test
  void thermodynamicChecksReturnTheSixIndividualResultsOnAnIndependentState() {
    SystemInterface fluid = fluid(false);
    new ThermodynamicOperations(fluid).TPflash();
    double pressure = fluid.getPressure();
    JsonObject checks = json(PVTFluidStudy.checkThermodynamics(fluid));
    assertTrue(checks.has("fugacityGibbsIdentity"));
    assertTrue(checks.has("compositionIdentity"));
    assertTrue(checks.has("compositionSymmetry"));
    assertTrue(checks.has("pressureIdentity"));
    assertTrue(checks.has("temperatureIdentity"));
    assertTrue(checks.has("numericalDerivatives"));
    assertTrue(checks.get("passed").getAsBoolean(), checks.toString());
    assertEquals(pressure, fluid.getPressure(), 0.0);
  }

  /** Reject unsupported experiment kinds and invalid coordinates before solver execution. */
  @Test
  void rejectsInvalidExperimentCoordinatesAndUnimplementedKinds() {
    SystemInterface fluid = fluid(false);
    assertThrows(IllegalArgumentException.class,
        () -> PVTFluidStudy.simulate(fluid, "CCE", 323.15, new double[] {50.0, 150.0}));
    assertThrows(IllegalArgumentException.class,
        () -> PVTFluidStudy.simulate(fluid, "CCE", Double.NaN, new double[] {150.0}));
    assertThrows(IllegalArgumentException.class,
        () -> PVTFluidStudy.simulate(fluid, "MMP", 323.15, new double[] {150.0}));
  }

  /** Reject parameters on an inactive phase that would otherwise be silently replaced on import. */
  @Test
  void rejectsPhaseSpecificParametersEvenWhenTheirPhaseIsCurrentlyInactive() {
    SystemInterface fluid = fluid(true);
    fluid.setPressure(500.0);
    new ThermodynamicOperations(fluid).TPflash();
    assertEquals(1, fluid.getNumberOfPhases());
    ((ComponentEos) fluid.getPhase(1).getComponent(3)).setOmegaA(0.55);
    assertThrows(IllegalArgumentException.class,
        () -> PVTFluidStudy.exportE300(fluid, directory.resolve("phase-specific.e300").toString(), 323.15));
  }

  /** Reject a latent transport model instead of silently normalizing it during export. */
  @Test
  void rejectsLatentTransportDifferencesAndRestoresTheSelectedPhaseType() {
    SystemInterface fluid = fluid(false);
    PhaseInterface phase = fluid.getPhase(0);
    PhaseType originalType = phase.getType();
    PhaseType latentType = originalType == PhaseType.GAS ? PhaseType.OIL : PhaseType.GAS;
    phase.setType(latentType);
    phase.getPhysicalProperties().setViscosityModel("PFCT");
    phase.setType(originalType);
    Path file = directory.resolve("latent-transport.e300");
    assertThrows(IllegalArgumentException.class, () -> PVTFluidStudy.exportE300(fluid, file.toString(), 323.15));
    assertEquals(originalType, phase.getType());
    assertTrue(phase.getPhysicalProperties().isLBCViscosityModel());
    phase.setType(latentType);
    assertTrue(phase.getPhysicalProperties().isPFCTViscosityModel());
    phase.setType(originalType);
    assertFalse(Files.exists(file));

    SystemInterface coefficientsFluid = fluid(false);
    PhaseInterface coefficientsPhase = coefficientsFluid.getPhase(0);
    PhaseType coefficientsType = coefficientsPhase.getType();
    coefficientsPhase.setType(coefficientsType == PhaseType.GAS ? PhaseType.OIL : PhaseType.GAS);
    double[] coefficients = coefficientsPhase.getPhysicalProperties().getLbcParameters().clone();
    coefficients[0] += 0.01;
    coefficientsPhase.getPhysicalProperties().setLbcParameters(coefficients);
    coefficientsPhase.setType(coefficientsType);
    assertThrows(IllegalArgumentException.class,
        () -> PVTFluidStudy.exportE300(coefficientsFluid, file.toString(), 323.15));
    assertEquals(coefficientsType, coefficientsPhase.getType());
    assertFalse(Files.exists(file));
  }
}
