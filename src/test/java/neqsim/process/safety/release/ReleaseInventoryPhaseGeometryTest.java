package neqsim.process.safety.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Geometry, entrainment-boundary and process-frame checks for phase-selected inventories. */
class ReleaseInventoryPhaseGeometryTest extends neqsim.NeqSimTest {
  private static final double CAPACITY_M3 = 0.02;
  private static final double VESSEL_DIAMETER_M = 0.20;
  private static final double VESSEL_LENGTH_M = CAPACITY_M3 / (Math.PI * VESSEL_DIAMETER_M * VESSEL_DIAMETER_M / 4.0);
  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T00:00:00Z"), ZoneOffset.UTC);

  private static SystemInterface twoPhaseHydrocarbon() {
    SystemInterface fluid = new SystemSrkEos(250.0, 10.0);
    fluid.addComponent("methane", 0.5);
    fluid.addComponent("n-butane", 0.5);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(3);
    assertTrue(fluid.hasPhaseType(PhaseType.GAS));
    assertTrue(fluid.hasPhaseType(PhaseType.OIL) || fluid.hasPhaseType(PhaseType.LIQUID));
    return fluid;
  }

  private static ReleaseFlowModel stableReleaseModel() {
    return new ReleaseFlowModel() {
      private static final long serialVersionUID = 1L;

      @Override
      public String getModelId() {
        return "geometry-gate-test";
      }

      @Override
      public ReleaseFlowResult calculate(ReleaseFlowRequest request) {
        SystemInterface fluid = request.getFluid();
        fluid.init(3);
        ReleaseState state = ReleaseState.fromFluid(fluid, 0.0);
        Map<ReleaseFlowResult.Station, ReleaseState> stations = new EnumMap<ReleaseFlowResult.Station, ReleaseState>(
            ReleaseFlowResult.Station.class);
        stations.put(ReleaseFlowResult.Station.UPSTREAM_STAGNATION, state);
        stations.put(ReleaseFlowResult.Station.THROAT_CRITICAL, state);
        stations.put(ReleaseFlowResult.Station.ORIFICE_EXIT, state);
        stations.put(ReleaseFlowResult.Station.AMBIENT_EXPANDED, state);
        return ReleaseFlowResult.success(this, 1.0e-4, false, stations,
            Collections.singletonList(new ReleaseFlowResult.Diagnostic("CONTROLLED_TEST_MODEL",
                "Fixed rate isolates live geometry and inventory conservation")),
            null, false);
      }
    };
  }

  private static ReleaseInventory controlInventory() {
    return new ReleaseInventory("control", twoPhaseHydrocarbon(), CAPACITY_M3, 0.001, 0.62, 101325.0,
        stableReleaseModel(), 0.02, PhaseType.GAS);
  }

  private static double liquidVolumeM3(SystemInterface fluid) {
    double volume = 0.0;
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      if (fluid.getPhase(phase).getType() != PhaseType.GAS) {
        volume += fluid.getPhase(phase).getVolume("m3");
      }
    }
    return volume;
  }

  private static double initialVerticalLevelM() {
    return liquidVolumeM3(controlInventory().getThermoSystem())
        / (Math.PI * VESSEL_DIAMETER_M * VESSEL_DIAMETER_M / 4.0);
  }

  @Test
  void verticalGeometryReturnsLiveLevelAndInterfaceAreaWithoutMutatingFluid() {
    SystemInterface fluid = controlInventory().getThermoSystem();
    double level = liquidVolumeM3(fluid) / (Math.PI * VESSEL_DIAMETER_M * VESSEL_DIAMETER_M / 4.0);
    double originalMoles = fluid.getTotalNumberOfMoles();
    ReleaseInventoryPhaseGeometry geometry = new ReleaseInventoryPhaseGeometry(
        ReleaseInventoryPhaseGeometry.Orientation.VERTICAL, VESSEL_DIAMETER_M, VESSEL_LENGTH_M,
        0.5 * (level + VESSEL_LENGTH_M), 0.01);

    ReleaseInventoryPhaseGeometry.Assessment assessment = geometry.assess(fluid);

    assertTrue(assessment.isInterfaceBandClear());
    assertEquals(PhaseType.GAS, assessment.getPhaseAtOpening());
    assertEquals(level, assessment.getLiquidLevelM(), 1.0e-10);
    assertEquals(Math.PI * VESSEL_DIAMETER_M * VESSEL_DIAMETER_M / 4.0, assessment.getInterfacialAreaM2(), 1.0e-12);
    assertEquals(originalMoles, fluid.getTotalNumberOfMoles(), 0.0);

    ReleaseInventoryPhaseGeometry horizontal = new ReleaseInventoryPhaseGeometry(
        ReleaseInventoryPhaseGeometry.Orientation.HORIZONTAL, VESSEL_DIAMETER_M, VESSEL_LENGTH_M, VESSEL_DIAMETER_M,
        0.0);
    ReleaseInventoryPhaseGeometry.Assessment horizontalAssessment = horizontal.assess(fluid);
    assertEquals(PhaseType.GAS, horizontalAssessment.getPhaseAtOpening());
    assertTrue(horizontalAssessment.getLiquidLevelM() > 0.0);
    assertTrue(horizontalAssessment.getLiquidLevelM() < VESSEL_DIAMETER_M);
    assertTrue(horizontalAssessment.getInterfacialAreaM2() > 0.0);
    assertEquals(originalMoles, fluid.getTotalNumberOfMoles(), 0.0);
  }

  @Test
  void interfaceBandAndWrongOpeningPhaseFailClosedAtConstruction() {
    double level = initialVerticalLevelM();
    double halfBand = 0.01;
    SystemInterface assessed = controlInventory().getThermoSystem();
    ReleaseInventoryPhaseGeometry justOutside = new ReleaseInventoryPhaseGeometry(
        ReleaseInventoryPhaseGeometry.Orientation.VERTICAL, VESSEL_DIAMETER_M, VESSEL_LENGTH_M,
        level + halfBand + 1.0e-6, halfBand);
    ReleaseInventoryPhaseGeometry justInside = new ReleaseInventoryPhaseGeometry(
        ReleaseInventoryPhaseGeometry.Orientation.VERTICAL, VESSEL_DIAMETER_M, VESSEL_LENGTH_M,
        level + halfBand - 1.0e-6, halfBand);
    assertTrue(justOutside.assess(assessed).isInterfaceBandClear());
    assertEquals(PhaseType.GAS, justOutside.assess(assessed).getPhaseAtOpening());
    assertFalse(justInside.assess(assessed).isInterfaceBandClear());

    ReleaseInventoryPhaseGeometry unresolved = new ReleaseInventoryPhaseGeometry(
        ReleaseInventoryPhaseGeometry.Orientation.VERTICAL, VESSEL_DIAMETER_M, VESSEL_LENGTH_M, level, halfBand);
    IllegalStateException entrainment = assertThrows(IllegalStateException.class,
        () -> new ReleaseInventory("unresolved", twoPhaseHydrocarbon(), CAPACITY_M3, 0.001, 0.62, 101325.0,
            stableReleaseModel(), 0.02, PhaseType.GAS, unresolved));
    assertTrue(entrainment.getMessage().contains("INVENTORY_ENTRAINMENT_UNRESOLVED"));

    ReleaseInventoryPhaseGeometry liquidOpening = new ReleaseInventoryPhaseGeometry(
        ReleaseInventoryPhaseGeometry.Orientation.VERTICAL, VESSEL_DIAMETER_M, VESSEL_LENGTH_M, 0.5 * level, 0.0);
    IllegalStateException mismatch = assertThrows(IllegalStateException.class,
        () -> new ReleaseInventory("mismatch", twoPhaseHydrocarbon(), CAPACITY_M3, 0.001, 0.62, 101325.0,
            stableReleaseModel(), 0.02, PhaseType.GAS, liquidOpening));
    assertTrue(mismatch.getMessage().contains("INVENTORY_OPENING_PHASE_MISMATCH"));

    ReleaseInventoryPhaseGeometry wrongCapacity = new ReleaseInventoryPhaseGeometry(
        ReleaseInventoryPhaseGeometry.Orientation.VERTICAL, VESSEL_DIAMETER_M, 0.5 * VESSEL_LENGTH_M, 0.1, 0.0);
    assertThrows(IllegalArgumentException.class, () -> new ReleaseInventory("capacity", twoPhaseHydrocarbon(),
        CAPACITY_M3, 0.001, 0.62, 101325.0, stableReleaseModel(), 0.02, PhaseType.GAS, wrongCapacity));
  }

  @Test
  void geometryGatedInventoryExportsSchemaValidFramesFromBothProcessContainers() {
    for (boolean useModel : new boolean[] {false, true}) {
      double level = initialVerticalLevelM();
      ReleaseInventoryPhaseGeometry geometry = new ReleaseInventoryPhaseGeometry(
          ReleaseInventoryPhaseGeometry.Orientation.VERTICAL, VESSEL_DIAMETER_M, VESSEL_LENGTH_M,
          0.5 * (level + VESSEL_LENGTH_M), 0.005);
      ReleaseInventory vessel = new ReleaseInventory("geometry inventory", twoPhaseHydrocarbon(), CAPACITY_M3, 0.001,
          0.62, 101325.0, stableReleaseModel(), 0.01, PhaseType.GAS, geometry);
      assertSame(geometry, vessel.getPhaseGeometry());
      ProcessSystem process = new ProcessSystem();
      process.add(vessel);
      SourceTermSession session;
      if (useModel) {
        ProcessModel model = new ProcessModel();
        model.add("geometry area", process);
        session = new SourceTermSession("geometry session", model, CLOCK);
        session.addInventorySource("opening", "geometry area", "geometry inventory");
      } else {
        session = new SourceTermSession("geometry session", process, CLOCK);
        session.addInventorySource("opening", "geometry inventory");
      }
      assertEquals(SourceTermFrame.Status.VALID, session.runSteadyState().get(0).getStatus());
      SourceTermFrame frame = session.step(0.02).get(0);
      assertEquals(SourceTermFrame.Status.VALID, frame.getStatus(), frame.toJson());
      SourceTermFrame.verifyEnvelope(frame.toJson());
      JsonObject provenance = JsonParser.parseString(frame.toJson()).getAsJsonObject().getAsJsonObject("provenance");
      assertEquals("COUPLED_RIGID_ADIABATIC_GEOMETRY_GATED_PHASE_INVENTORY",
          provenance.get("releaseBasis").getAsString());
      assertEquals("VERTICAL", provenance.get("inventoryGeometryOrientation").getAsString());
      assertEquals("OPENING_PHASE_CONFIRMED_ENTRAINMENT_BAND_CLEAR",
          provenance.get("inventoryGeometryStatus").getAsString());
      assertNotNull(provenance.get("inventoryLiquidLevelM"));
      assertNotNull(provenance.get("inventoryInterfacialAreaM2"));
      assertTrue(vessel.getBalance().getReleasedMassKg() > 0.0);
    }
  }
}
