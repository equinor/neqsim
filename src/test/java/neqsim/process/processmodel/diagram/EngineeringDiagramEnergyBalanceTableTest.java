package neqsim.process.processmodel.diagram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import neqsim.process.engineering.model.EngineeringDiagramBalanceTable;
import neqsim.process.engineering.model.EngineeringDiagramBalanceTable.Balance;
import neqsim.process.engineering.model.EngineeringDiagramBalanceTable.Boundary;
import neqsim.process.engineering.model.EngineeringDiagramBalanceTable.Direction;
import neqsim.process.engineering.model.EngineeringDiagramBalanceTable.EvidenceState;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.ContentProfile;
import neqsim.process.engineering.model.EngineeringDiagramEnergyBalanceTable;
import neqsim.process.engineering.model.EngineeringDiagramEnergyBalanceTable.EnergyBalance;
import neqsim.process.engineering.model.EngineeringDiagramEnergyBalanceTable.EnergyDirection;
import neqsim.process.engineering.model.EngineeringDiagramEnergyBalanceTable.EnergyFlow;
import neqsim.process.engineering.model.EngineeringDiagramEnergyBalanceTable.EnergyKind;
import neqsim.process.engineering.model.EngineeringDiagramEnergyBalanceTable.EnergyPort;
import neqsim.process.engineering.model.EngineeringDiagramStreamTable;
import neqsim.process.engineering.model.EngineeringDiagramStreamTable.Quantity;
import neqsim.process.engineering.model.EngineeringDiagramStreamTable.Row;
import neqsim.process.equipment.stream.StreamInterface;
import org.junit.jupiter.api.Test;

/** Regression tests for explicit, deterministic heat/work energy-balance closure. */
class EngineeringDiagramEnergyBalanceTableTest {

  @Test
  void closesExplicitParallelHeatAndShaftWorkPorts() {
    Fixture fixture = fixture();
    List<EnergyPort> ports = completePorts();
    List<EnergyFlow> flows = completeFlows();

    EngineeringDiagramEnergyBalanceTable table = EngineeringDiagramEnergyBalanceTable
        .fromBalanceTable(fixture.balanceTable, ports, flows);

    assertTrue(table.isValid());
    assertEquals(1, table.getEnergyBalances().size());
    EnergyBalance energy = table.getEnergyBalances().get(0);
    Balance source = fixture.balanceTable.getBalances().get(0);
    assertEquals("BAL-SIMPLE-01", energy.getBalanceId());
    assertEquals(4, energy.getDeclaredPortCount());
    assertEquals(4, energy.getSuppliedFlowCount());
    assertEquals(source.getInletStreamEnthalpyFlow(), energy.getInletStreamEnthalpyFlow(), 1.0e-12);
    assertEquals(source.getOutletStreamEnthalpyFlow(), energy.getOutletStreamEnthalpyFlow(), 1.0e-12);
    assertEquals(1000.0, energy.getHeatTransferIntoControlVolume(), 1.0e-12);
    assertEquals(0.0, energy.getHeatTransferOutOfControlVolume(), 1.0e-12);
    assertEquals(0.0, energy.getShaftWorkIntoControlVolume(), 1.0e-12);
    assertEquals(500.0, energy.getShaftWorkOutOfControlVolume(), 1.0e-12);
    assertEquals(source.getStreamEnthalpyResidual() + 500.0, energy.getEnergyResidual(), 1.0e-8);
    assertTrue(Double.isFinite(energy.getRelativeEnergyResidual()));
    assertTrue(energy.isComplete());
    assertFalse(table.getSourceBalanceTableFingerprint().isEmpty());
    assertNotSame(table.getEnergyPorts(), table.getEnergyPorts());
    assertNotSame(table.getEnergyFlows(), table.getEnergyFlows());
    assertNotSame(table.getEnergyBalances(), table.getEnergyBalances());
    assertNotSame(table.getDiagnostics(), table.getDiagnostics());
    assertThrows(UnsupportedOperationException.class, () -> table.getEnergyPorts().clear());
    assertTrue(table.toJson().contains("\"energyFlowUnit\": \"W\""));
    assertTrue(table.toJson().contains("\"quantityBasis\": \"ENERGY_RATE\""));
    assertTrue(table.toJson().contains("\"provenance\": \"simulation-case:NORMAL-01\""));
  }

  @Test
  void isDeterministicForFreshSystemsAndInputOrder() {
    Fixture firstFixture = fixture();
    Fixture secondFixture = fixture();
    List<EnergyPort> firstPorts = completePorts();
    List<EnergyFlow> firstFlows = completeFlows();
    List<EnergyPort> secondPorts = completePorts();
    List<EnergyFlow> secondFlows = completeFlows();
    Collections.reverse(secondPorts);
    Collections.reverse(secondFlows);

    EngineeringDiagramEnergyBalanceTable first = EngineeringDiagramEnergyBalanceTable
        .fromBalanceTable(firstFixture.balanceTable, firstPorts, firstFlows);
    EngineeringDiagramEnergyBalanceTable second = EngineeringDiagramEnergyBalanceTable
        .fromBalanceTable(secondFixture.balanceTable, secondPorts, secondFlows);

    assertEquals(first.toJson(), second.toJson());
  }

  @Test
  void diagnosesDuplicateUnknownInvalidAndMissingEnergyEvidence() {
    Fixture fixture = fixture();
    EnergyPort heat = port("equipment:heater", "energy:heat", EnergyKind.HEAT_TRANSFER,
        EnergyDirection.INTO_CONTROL_VOLUME);
    EnergyPort work = port("equipment:compressor", "energy:work", EnergyKind.SHAFT_WORK,
        EnergyDirection.INTO_CONTROL_VOLUME);
    EnergyFlow heatFlow = flow("energy:heat", 1000.0);
    List<EnergyPort> ports = new ArrayList<EnergyPort>(Arrays.asList(heat, heat, work,
        new EnergyPort("BAL-MISSING", "equipment:unknown", "energy:unknown", EnergyKind.HEAT_TRANSFER,
            EnergyDirection.OUT_OF_CONTROL_VOLUME, "project-energy-register:test", EvidenceState.PROPOSED)));
    List<EnergyFlow> flows = new ArrayList<EnergyFlow>(Arrays.asList(
        heatFlow, heatFlow, new EnergyFlow("BAL-SIMPLE-01", "energy:work", Double.NaN, "kW", "ENERGY_RATE",
            "project-energy-register:test", "simulation-case:NORMAL-01", EvidenceState.PROPOSED),
        flow("energy:missing", 5.0)));

    EngineeringDiagramEnergyBalanceTable table = EngineeringDiagramEnergyBalanceTable
        .fromBalanceTable(fixture.balanceTable, ports, flows);

    assertFalse(table.isValid());
    assertFalse(table.getEnergyBalances().get(0).isComplete());
    assertTrue(hasDiagnostic(table, "ENERGY_PORT_DUPLICATE"));
    assertTrue(hasDiagnostic(table, "ENERGY_PORT_UNKNOWN_BALANCE"));
    assertTrue(hasDiagnostic(table, "ENERGY_FLOW_DUPLICATE"));
    assertTrue(hasDiagnostic(table, "ENERGY_FLOW_UNKNOWN_PORT"));
    assertTrue(hasDiagnostic(table, "ENERGY_FLOW_VALUE_INVALID"));
    assertTrue(hasDiagnostic(table, "ENERGY_FLOW_MISSING"));
    assertTrue(table.toJson().contains("\"nonFiniteResult\": \"NaN\""));
  }

  @Test
  void requiresExplicitPortsFlowsAndValidArguments() {
    Fixture fixture = fixture();

    EngineeringDiagramEnergyBalanceTable empty = EngineeringDiagramEnergyBalanceTable.fromBalanceTable(
        fixture.balanceTable, Collections.<EnergyPort>emptyList(), Collections.<EnergyFlow>emptyList());

    assertFalse(empty.isValid());
    assertFalse(empty.getEnergyBalances().get(0).isComplete());
    assertTrue(hasDiagnostic(empty, "ENERGY_PORT_NOT_DECLARED"));
    assertTrue(hasDiagnostic(empty, "ENERGY_FLOW_NOT_DECLARED"));
    assertTrue(hasDiagnostic(empty, "ENERGY_PORT_MISSING_FOR_BALANCE"));
    assertThrows(IllegalArgumentException.class, () -> EngineeringDiagramEnergyBalanceTable.fromBalanceTable(null,
        Collections.<EnergyPort>emptyList(), Collections.<EnergyFlow>emptyList()));
    assertThrows(IllegalArgumentException.class, () -> EngineeringDiagramEnergyBalanceTable
        .fromBalanceTable(fixture.balanceTable, null, Collections.<EnergyFlow>emptyList()));
    assertThrows(IllegalArgumentException.class, () -> EngineeringDiagramEnergyBalanceTable
        .fromBalanceTable(fixture.balanceTable, Collections.<EnergyPort>emptyList(), null));
    assertThrows(IllegalArgumentException.class, () -> new EnergyPort("BAL-SIMPLE-01", "equipment:heater",
        "energy:heat", null, EnergyDirection.INTO_CONTROL_VOLUME, "source", EvidenceState.PROPOSED));
    assertThrows(IllegalArgumentException.class,
        () -> new EnergyFlow("BAL-SIMPLE-01", "energy:heat", 0.0, "W", "ENERGY_RATE", "source", "provenance", null));
  }

  @Test
  void propagatesInvalidSourceBalanceState() {
    Fixture fixture = fixture();
    EngineeringDiagramBalanceTable invalidSource = EngineeringDiagramBalanceTable.fromStreamTable(fixture.streamTable,
        Collections.<Boundary>emptyList());

    EngineeringDiagramEnergyBalanceTable table = EngineeringDiagramEnergyBalanceTable.fromBalanceTable(invalidSource,
        completePorts(), completeFlows());

    assertFalse(table.isValid());
    assertTrue(hasDiagnostic(table, "ENERGY_BALANCE_SOURCE_INVALID"));
    assertTrue(hasDiagnostic(table, "ENERGY_PORT_UNKNOWN_BALANCE"));
  }

  private static Fixture fixture() {
    EngineeringDiagramReferenceFixtures.SystemCase reference = EngineeringDiagramReferenceFixtures.simpleTrain();
    for (StreamInterface product : reference.getProducts()) {
      reference.getProcessSystem().add(product);
    }
    reference.getProcessSystem().run();
    EngineeringDiagramDocumentSet documents = ProcessDiagramDocumentSetAdapter.fromProcessSystem(
        reference.getProcessSystem(), reference.getCaseId(), "A", "PFD-HMB-007", "Energy balance", ContentProfile.PFD,
        "NORMAL-01");
    EngineeringDiagramStreamTable streamTable = EngineeringDiagramStreamTable.fromDocumentSet(documents, "NORMAL-01");
    List<Boundary> boundaries = new ArrayList<Boundary>();
    boundaries.add(
        new Boundary("BAL-SIMPLE-01", completeRow(streamTable, reference.getFeed().getName()).getSemanticObjectId(),
            Direction.INLET, "project-balance-register:test", EvidenceState.PROPOSED));
    for (StreamInterface product : reference.getProducts()) {
      boundaries.add(new Boundary("BAL-SIMPLE-01", completeRow(streamTable, product.getName()).getSemanticObjectId(),
          Direction.OUTLET, "project-balance-register:test", EvidenceState.PROPOSED));
    }
    EngineeringDiagramBalanceTable balanceTable = EngineeringDiagramBalanceTable.fromStreamTable(streamTable,
        boundaries);
    return new Fixture(streamTable, balanceTable);
  }

  private static List<EnergyPort> completePorts() {
    return new ArrayList<EnergyPort>(Arrays.asList(
        port("equipment:heater", "energy:heat-a", EnergyKind.HEAT_TRANSFER, EnergyDirection.INTO_CONTROL_VOLUME),
        port("equipment:heater", "energy:heat-b", EnergyKind.HEAT_TRANSFER, EnergyDirection.INTO_CONTROL_VOLUME),
        port("equipment:cooler", "energy:heat-out", EnergyKind.HEAT_TRANSFER, EnergyDirection.OUT_OF_CONTROL_VOLUME),
        port("equipment:turbine", "energy:shaft-out", EnergyKind.SHAFT_WORK, EnergyDirection.OUT_OF_CONTROL_VOLUME)));
  }

  private static List<EnergyFlow> completeFlows() {
    return new ArrayList<EnergyFlow>(Arrays.asList(flow("energy:heat-a", 700.0), flow("energy:heat-b", 300.0),
        flow("energy:heat-out", 0.0), flow("energy:shaft-out", 500.0)));
  }

  private static EnergyPort port(String equipmentId, String portId, EnergyKind kind, EnergyDirection direction) {
    return new EnergyPort("BAL-SIMPLE-01", equipmentId, portId, kind, direction, "project-energy-register:test",
        EvidenceState.PROPOSED);
  }

  private static EnergyFlow flow(String portId, double value) {
    return new EnergyFlow("BAL-SIMPLE-01", portId, value, "W", "ENERGY_RATE", "project-energy-register:test",
        "simulation-case:NORMAL-01", EvidenceState.PROPOSED);
  }

  private static Row completeRow(EngineeringDiagramStreamTable table, String sourceLabel) {
    for (Row row : table.getRows()) {
      if (sourceLabel.equals(row.getSourceLabel()) && row.getValues().size() == Quantity.values().length) {
        return row;
      }
    }
    throw new AssertionError("No complete stream-table row for " + sourceLabel);
  }

  private static boolean hasDiagnostic(EngineeringDiagramEnergyBalanceTable table, String code) {
    for (EngineeringDiagramEnergyBalanceTable.Diagnostic diagnostic : table.getDiagnostics()) {
      if (code.equals(diagnostic.getCode())) {
        return true;
      }
    }
    return false;
  }

  private static final class Fixture {
    private final EngineeringDiagramStreamTable streamTable;
    private final EngineeringDiagramBalanceTable balanceTable;

    private Fixture(EngineeringDiagramStreamTable streamTable, EngineeringDiagramBalanceTable balanceTable) {
      this.streamTable = streamTable;
      this.balanceTable = balanceTable;
    }
  }
}
