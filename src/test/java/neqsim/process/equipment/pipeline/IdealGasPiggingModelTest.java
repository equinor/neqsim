package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.IdealGasPiggingModel.Location;
import neqsim.process.equipment.pipeline.IdealGasPiggingModel.State;
import neqsim.process.equipment.pipeline.IdealGasPiggingModel.Step;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Conservation, event, choking, refinement and API evidence for ideal-gas pig coupling.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class IdealGasPiggingModelTest {
  /**
   * Compiles, executes and text-checks the documented coupled example.
   *
   * @throws Exception if source or documentation cannot be read
   */
  @Test
  void documentedCoupledExampleMatchesGuide() throws Exception {
    // COUPLED_DOC_START
    SystemInterface gas = new SystemSrkEos(293.15, 5.0);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule(2);
    PigMotion pig = new PigMotion(10.0, Math.PI * 0.052 * 0.052 / 4.0, 25.0, 15.0);
    IdealGasPiggingModel model = new IdealGasPiggingModel(pig, gas, 62.0, 0.05, 0.05, 0.003, 0.7, 0.0, 0.01, 0.01,
        0.05);
    IdealGasPiggingModel.State state = model.initialise(0.0, 0.0, 3.0e5, 1.5e5, 293.15);
    IdealGasPiggingModel.Step result = model.advance(state, 2.0);
    // COUPLED_DOC_END
    assertTrue(result.getFinalState().getPigPositionM() > 0.0);
    assertEquals(0.0, result.getTotalMassResidualKg(), 1.0e-14);

    String source = new String(
        Files.readAllBytes(Paths.get("src/test/java/neqsim/process/equipment/pipeline/IdealGasPiggingModelTest.java")),
        StandardCharsets.UTF_8);
    String snippet = source.substring(source.indexOf("// COUPLED_DOC_START") + "// COUPLED_DOC_START".length(),
        source.indexOf("// COUPLED_DOC_END"));
    String guide = new String(Files.readAllBytes(Paths.get("docs/process/pig-motion.md")), StandardCharsets.UTF_8);
    String marker = "<!-- COUPLED_EXAMPLE -->";
    String fence = guide.substring(guide.indexOf("```java", guide.indexOf(marker)) + "```java".length());
    fence = fence.substring(0, fence.indexOf("```"));
    assertEquals(snippet.replaceAll("\\s+", " ").trim(), fence.replaceAll("\\s+", " ").trim());
  }

  /** A sealed pig must exchange equal and opposite pressure/displacement work with the gas chambers. */
  @Test
  void sealedMovingPigClosesCombinedMassAndEnergy() {
    IdealGasPiggingModel model = model(0.0, 0.02, 0.05, 0.05, 0.0, 0.0);
    State initial = model.initialise(1.0, 0.0, 4.0e5, 2.0e5, 293.15);
    Step step = model.advance(initial, 0.5);

    assertTrue(step.getFinalState().getPigPositionM() > initial.getPigPositionM());
    assertEquals(initial.getUpstreamMassKg(), step.getFinalState().getUpstreamMassKg(), 0.0);
    assertEquals(initial.getDownstreamMassKg(), step.getFinalState().getDownstreamMassKg(), 0.0);
    assertEquals(0.0, step.getPressureWorkJ() + step.getGasBoundaryWorkJ(), 1.0e-10);
    assertEquals(0.0, step.getTotalMassResidualKg(), 1.0e-14);
    assertEquals(0.0, step.getCombinedEnergyResidualJ(), 1.0e-8);
    assertTrue(model.getUpstreamPressurePa(step.getFinalState()) < 4.0e5);
    assertTrue(model.getDownstreamPressurePa(step.getFinalState()) > 2.0e5);
  }

  /** Choked forward bypass must conserve mass and enthalpy while reducing the pressure gap. */
  @Test
  void heldPigHasConservativeChokedForwardBypass() {
    IdealGasPiggingModel model = model(0.004, 0.01, 1.0e9, 1.0e9, 0.0, 0.0);
    State initial = model.initialise(0.0, 0.0, 10.0e5, 1.0e5, 293.15);
    double initialGap = model.getUpstreamPressurePa(initial) - model.getDownstreamPressurePa(initial);
    Step step = model.advance(initial, 0.25);
    State end = step.getFinalState();

    assertEquals(Location.LAUNCHER, end.getLocation());
    assertEquals(0.0, end.getPigPositionM(), 0.0);
    assertTrue(step.getSignedBypassMassKg() > 0.0);
    assertEquals(step.getSignedBypassMassKg(), step.getAbsoluteBypassMassKg(), 0.0);
    assertTrue(step.getChokedSeconds() > 0.0);
    assertTrue(model.getUpstreamPressurePa(end) - model.getDownstreamPressurePa(end) < initialGap);
    assertEquals(0.0, step.getTotalMassResidualKg(), 1.0e-14);
    assertEquals(0.0, step.getCombinedEnergyResidualJ(), 1.0e-8);
    assertTrue(step.getMaximumAcceptedDonorMassFraction() <= 0.01 * (1.0 + 1.0e-12));
  }

  /** The same bypass law must reverse direction without changing the conservation convention. */
  @Test
  void bypassDirectionFollowsPressureWithoutOneWayClipping() {
    IdealGasPiggingModel model = model(0.003, 0.02, 1.0e9, 1.0e9, 0.0, 0.0);
    State initial = model.initialise(0.0, 0.0, 1.2e5, 5.0e5, 293.15);
    Step step = model.advance(initial, 0.1);

    assertTrue(step.getSignedBypassMassKg() < 0.0);
    assertTrue(step.getFinalState().getUpstreamMassKg() > initial.getUpstreamMassKg());
    assertTrue(step.getFinalState().getDownstreamMassKg() < initial.getDownstreamMassKg());
    assertEquals(0.0, step.getTotalMassResidualKg(), 1.0e-14);
    assertEquals(0.0, step.getCombinedEnergyResidualJ(), 1.0e-8);
  }

  /** A nearby high-backpressure point must remain subcritical while preserving the same signed ledgers. */
  @Test
  void nearbyBackpressureSelectsSubcriticalBypass() {
    IdealGasPiggingModel model = model(0.003, 0.01, 1.0e9, 1.0e9, 0.0, 0.0);
    State initial = model.initialise(0.0, 0.0, 2.0e5, 1.8e5, 293.15);
    Step step = model.advance(initial, 0.05);

    assertTrue(step.getSignedBypassMassKg() > 0.0);
    assertEquals(0.0, step.getChokedSeconds(), 0.0);
    assertEquals(0.0, step.getTotalMassResidualKg(), 1.0e-14);
    assertEquals(0.0, step.getCombinedEnergyResidualJ(), 1.0e-8);
  }

  /** Static friction equality holds, while the next representable pressure increment releases the coupled pig. */
  @Test
  void coupledBreakawayRetainsInclusiveStaticThreshold() {
    PigMotion mechanics = new PigMotion(10.0, 0.01, 100.0, 50.0);
    IdealGasPiggingModel model = new IdealGasPiggingModel(mechanics, methane(), 20.0, 1000.0, 1000.0, 0.0, 0.7, 0.0,
        0.01, 0.02, 0.05);
    State held = model.initialise(0.0, 0.0, 2.0e5 - 1.0, 1.9e5, 293.15);
    assertEquals(0.0, model.advance(held, 0.1).getFinalState().getPigPositionM(), 0.0);

    State released = model.initialise(0.0, 0.0, 2.0e5 + 1.0, 1.9e5, 293.15);
    assertTrue(model.advance(released, 0.1).getFinalState().getPigPositionM() > 0.0);
  }

  /** Receiver crossing is split to the contact event and its kinetic energy is retained as impact dissipation. */
  @Test
  void receiverEventClampsPositionAndAccountsForImpact() {
    IdealGasPiggingModel model = model(0.0, 0.01, 0.0, 0.0, 1000.0, 0.0);
    State initial = model.initialise(61.9, 2.0, 1.0e5, 1.0e5, 293.15);
    Step step = model.advance(initial, 1.0);
    State received = step.getFinalState();

    assertEquals(Location.RECEIVER, received.getLocation());
    assertEquals(62.0, received.getPigPositionM(), 0.0);
    assertEquals(0.0, received.getPigVelocityMps(), 0.0);
    assertTrue(step.getContactImpactEnergyJ() > 0.0);
    assertEquals(1.0, received.getElapsedTimeSeconds(), 1.0e-15);
    assertEquals(0.0, step.getCombinedEnergyResidualJ(), 1.0e-7);

    Step terminal = model.advance(received, 10.0);
    assertEquals(received.getPigPositionM(), terminal.getFinalState().getPigPositionM(), 0.0);
    assertEquals(received.getUpstreamMassKg(), terminal.getFinalState().getUpstreamMassKg(), 0.0);
    assertEquals(11.0, terminal.getFinalState().getElapsedTimeSeconds(), 0.0);
  }

  /** Halving the explicit maximum step must converge position and chamber pressures on the public 62 m geometry. */
  @Test
  void hendrixGeometryHasTimestepAndNearbyBypassEvidence() throws Exception {
    IdealGasPiggingModel coarse = model(0.0030, 0.02, 25.0, 15.0, 0.05, 0.05);
    IdealGasPiggingModel fine = model(0.0030, 0.01, 25.0, 15.0, 0.05, 0.05);
    IdealGasPiggingModel finer = model(0.0030, 0.005, 25.0, 15.0, 0.05, 0.05);
    State coarseInitial = coarse.initialise(0.0, 0.0, 3.0e5, 1.5e5, 293.15);
    State fineInitial = fine.initialise(0.0, 0.0, 3.0e5, 1.5e5, 293.15);
    State finerInitial = finer.initialise(0.0, 0.0, 3.0e5, 1.5e5, 293.15);
    State coarseEnd = coarse.advance(coarseInitial, 2.0).getFinalState();
    State fineEnd = fine.advance(fineInitial, 2.0).getFinalState();
    State finerEnd = finer.advance(finerInitial, 2.0).getFinalState();

    assertTrue(Math.abs(fineEnd.getPigPositionM() - finerEnd.getPigPositionM()) < Math
        .abs(coarseEnd.getPigPositionM() - fineEnd.getPigPositionM()));
    assertTrue(Math.abs(fine.getUpstreamPressurePa(fineEnd) - finer.getUpstreamPressurePa(finerEnd)) < Math
        .abs(coarse.getUpstreamPressurePa(coarseEnd) - fine.getUpstreamPressurePa(fineEnd)));
    assertTrue(Math.abs(fine.getDownstreamPressurePa(fineEnd) - finer.getDownstreamPressurePa(finerEnd)) < Math
        .abs(coarse.getDownstreamPressurePa(coarseEnd) - fine.getDownstreamPressurePa(fineEnd)));

    IdealGasPiggingModel smaller = model(0.0025, 0.01, 25.0, 15.0, 0.05, 0.05);
    IdealGasPiggingModel larger = model(0.0035, 0.01, 25.0, 15.0, 0.05, 0.05);
    Step smallerStep = smaller.advance(smaller.initialise(0.0, 0.0, 3.0e5, 1.5e5, 293.15), 0.2);
    Step largerStep = larger.advance(larger.initialise(0.0, 0.0, 3.0e5, 1.5e5, 293.15), 0.2);
    assertTrue(largerStep.getAbsoluteBypassMassKg() > smallerStep.getAbsoluteBypassMassKg());

    String csv = "maximum_substep_s,position_m,upstream_pressure_pa,downstream_pressure_pa\n" + "0.02,"
        + coarseEnd.getPigPositionM() + "," + coarse.getUpstreamPressurePa(coarseEnd) + ","
        + coarse.getDownstreamPressurePa(coarseEnd) + "\n0.01," + fineEnd.getPigPositionM() + ","
        + fine.getUpstreamPressurePa(fineEnd) + "," + fine.getDownstreamPressurePa(fineEnd) + "\n0.005,"
        + finerEnd.getPigPositionM() + "," + finer.getUpstreamPressurePa(finerEnd) + ","
        + finer.getDownstreamPressurePa(finerEnd) + "\nnearby_bypass_diameter_m,absolute_bypass_mass_kg,,\n0.0025,"
        + smallerStep.getAbsoluteBypassMassKg() + ",,\n0.0035," + largerStep.getAbsoluteBypassMassKg() + ",,\n";
    Files.write(Paths.get("target/ideal-gas-pigging-refinement.csv"), csv.getBytes(StandardCharsets.UTF_8));

    String receiptText = new String(
        Files.readAllBytes(
            Paths.get("src/test/resources/neqsim/process/equipment/pipeline/ideal-gas-pigging/validation.json")),
        StandardCharsets.UTF_8);
    JsonObject receipt = JsonParser.parseString(receiptText).getAsJsonObject();
    JsonArray refinement = receipt.getAsJsonArray("refinement");
    State[] states = {coarseEnd, fineEnd, finerEnd};
    IdealGasPiggingModel[] models = {coarse, fine, finer};
    for (int i = 0; i < states.length; i++) {
      JsonObject point = refinement.get(i).getAsJsonObject();
      assertEquals(states[i].getPigPositionM(), point.get("positionM").getAsDouble(), 0.0);
      assertEquals(models[i].getUpstreamPressurePa(states[i]), point.get("upstreamPressurePa").getAsDouble(), 0.0);
      assertEquals(models[i].getDownstreamPressurePa(states[i]), point.get("downstreamPressurePa").getAsDouble(), 0.0);
    }
    JsonArray nearby = receipt.getAsJsonArray("nearbyBypass");
    assertEquals(smallerStep.getAbsoluteBypassMassKg(),
        nearby.get(0).getAsJsonObject().get("absoluteTransferredMassKgAt0p2s").getAsDouble(), 0.0);
    assertEquals(largerStep.getAbsoluteBypassMassKg(),
        nearby.get(1).getAsJsonObject().get("absoluteTransferredMassKgAt0p2s").getAsDouble(), 0.0);
  }

  /** Model, state and ledger must serialize without changing the replayed accepted result. */
  @Test
  void serializationPreservesTransactionalReplay() throws Exception {
    IdealGasPiggingModel original = model(0.003, 0.01, 25.0, 15.0, 0.05, 0.05);
    State initial = original.initialise(0.0, 0.0, 3.0e5, 1.5e5, 293.15);
    Step expected = original.advance(initial, 0.5);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(original);
      output.writeObject(initial);
      output.writeObject(expected);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      IdealGasPiggingModel restored = (IdealGasPiggingModel) input.readObject();
      State restoredInitial = (State) input.readObject();
      Step restoredExpected = (Step) input.readObject();
      Step replay = restored.advance(restoredInitial, 0.5);
      assertEquals(expected.getFinalState().getPigPositionM(), replay.getFinalState().getPigPositionM(), 0.0);
      assertEquals(expected.getSignedBypassMassKg(), restoredExpected.getSignedBypassMassKg(), 0.0);
      assertEquals(expected.getCombinedEnergyResidualJ(), replay.getCombinedEnergyResidualJ(), 0.0);
    }
  }

  /** One requested interval and the same externally partitioned intervals must publish the same accepted state. */
  @Test
  void externalPartitionMatchesInternalTransactionSteps() {
    IdealGasPiggingModel model = model(0.003, 0.01, 25.0, 15.0, 0.05, 0.05);
    State initial = model.initialise(0.0, 0.0, 3.0e5, 1.5e5, 293.15);
    State whole = model.advance(initial, 1.0).getFinalState();
    State partitioned = initial;
    for (int i = 0; i < 100; i++) {
      partitioned = model.advance(partitioned, 0.01).getFinalState();
    }

    assertEquals(whole.getPigPositionM(), partitioned.getPigPositionM(), 1.0e-12);
    assertEquals(whole.getPigVelocityMps(), partitioned.getPigVelocityMps(), 1.0e-12);
    assertEquals(whole.getUpstreamMassKg(), partitioned.getUpstreamMassKg(), 1.0e-14);
    assertEquals(whole.getDownstreamMassKg(), partitioned.getDownstreamMassKg(), 1.0e-14);
    assertEquals(model.getUpstreamPressurePa(whole), model.getUpstreamPressurePa(partitioned), 1.0e-8);
    assertEquals(model.getDownstreamPressurePa(whole), model.getDownstreamPressurePa(partitioned), 1.0e-8);
    assertEquals(0.0, initial.getElapsedTimeSeconds(), 0.0);
    assertEquals(0.0, initial.getPigPositionM(), 0.0);
  }

  /** Invalid geometries, safeguards and gas states fail before a calculation can publish partial state. */
  @Test
  void invalidInputsFailClosed() {
    PigMotion mechanics = new PigMotion(10.0, 0.01, 10.0, 5.0);
    assertThrows(IllegalArgumentException.class,
        () -> new IdealGasPiggingModel(null, methane(), 1.0, 1.0, 1.0, 0.0, 0.7, 0.0, 0.1, 0.1, 0.1));
    assertThrows(IllegalArgumentException.class,
        () -> new IdealGasPiggingModel(mechanics, methane(), 0.0, 1.0, 1.0, 0.0, 0.7, 0.0, 0.1, 0.1, 0.1));
    assertThrows(IllegalArgumentException.class,
        () -> new IdealGasPiggingModel(mechanics, methane(), 1.0, 1.0, 1.0, -1.0, 0.7, 0.0, 0.1, 0.1, 0.1));
    assertThrows(IllegalArgumentException.class,
        () -> new IdealGasPiggingModel(mechanics, methane(), 1.0, 1.0, 1.0, 0.0, 0.7, 0.0, 0.1, 0.6, 0.1));
    IdealGasPiggingModel model = model(0.0, 0.01, 10.0, 5.0, 0.0, 0.0);
    assertThrows(IllegalArgumentException.class, () -> model.initialise(-1.0, 0.0, 1.0e5, 1.0e5, 293.15));
    assertThrows(IllegalArgumentException.class, () -> model.initialise(0.0, 0.0, Double.NaN, 1.0e5, 293.15));
    assertThrows(IllegalArgumentException.class,
        () -> model.advance(model.initialise(0.0, 0.0, 1.0e5, 1.0e5, 293.15), -1.0));
  }

  /**
   * Creates a 62 m by 52 mm public-geometry model with explicit synthetic parameters.
   *
   * @param bypassDiameterM bypass diameter in m
   * @param maximumSubstepSeconds maximum coupling step in s
   * @param staticFrictionN static resistance in N
   * @param kineticFrictionN kinetic resistance in N
   * @param launcherVolumeM3 launcher dead volume in m3
   * @param receiverVolumeM3 receiver dead volume in m3
   * @return configured model
   */
  private static IdealGasPiggingModel model(double bypassDiameterM, double maximumSubstepSeconds,
      double staticFrictionN, double kineticFrictionN, double launcherVolumeM3, double receiverVolumeM3) {
    double area = Math.PI * 0.052 * 0.052 / 4.0;
    PigMotion mechanics = new PigMotion(10.0, area, staticFrictionN, kineticFrictionN);
    return new IdealGasPiggingModel(mechanics, methane(), 62.0, Math.max(launcherVolumeM3, 0.02),
        Math.max(receiverVolumeM3, 0.02), bypassDiameterM, 0.7, 0.0, maximumSubstepSeconds, 0.01, 0.05);
  }

  /** @return initialized dry methane template */
  private static SystemInterface methane() {
    SystemInterface gas = new SystemSrkEos(293.15, 5.0);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule(2);
    gas.init(0);
    return gas;
  }
}
