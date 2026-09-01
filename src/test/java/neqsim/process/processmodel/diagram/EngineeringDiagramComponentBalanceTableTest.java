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
import neqsim.process.engineering.model.EngineeringDiagramBalanceTable.Boundary;
import neqsim.process.engineering.model.EngineeringDiagramBalanceTable.Direction;
import neqsim.process.engineering.model.EngineeringDiagramBalanceTable.EvidenceState;
import neqsim.process.engineering.model.EngineeringDiagramComponentBalanceTable;
import neqsim.process.engineering.model.EngineeringDiagramComponentBalanceTable.ComponentBalance;
import neqsim.process.engineering.model.EngineeringDiagramComponentBalanceTable.ComponentFlow;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.ContentProfile;
import neqsim.process.engineering.model.EngineeringDiagramStreamTable;
import neqsim.process.engineering.model.EngineeringDiagramStreamTable.Quantity;
import neqsim.process.engineering.model.EngineeringDiagramStreamTable.Row;
import neqsim.process.equipment.stream.StreamInterface;
import org.junit.jupiter.api.Test;

/** Regression tests for explicit, deterministic component-resolved boundary balances. */
class EngineeringDiagramComponentBalanceTableTest {

  @Test
  void aggregatesExplicitComponentMassFlowsWithProvenance() {
    Fixture fixture = fixture();
    List<ComponentFlow> flows = completeFlows(fixture.boundaries);

    EngineeringDiagramComponentBalanceTable table = EngineeringDiagramComponentBalanceTable
        .fromBalanceTable(fixture.balanceTable, flows);

    assertTrue(table.isValid());
    assertEquals(2, table.getComponentBalances().size());
    ComponentBalance ethane = component(table, "ethane");
    ComponentBalance methane = component(table, "methane");
    assertEquals(3, ethane.getBoundaryCount());
    assertEquals(3, ethane.getSuppliedBoundaryCount());
    assertTrue(ethane.isComplete());
    assertEquals(4.0, ethane.getInletMassFlow(), 1.0e-12);
    assertEquals(4.0, ethane.getOutletMassFlow(), 1.0e-12);
    assertEquals(0.0, ethane.getMassResidual(), 1.0e-12);
    assertEquals(6.0, methane.getInletMassFlow(), 1.0e-12);
    assertEquals(6.0, methane.getOutletMassFlow(), 1.0e-12);
    assertEquals(0.0, methane.getRelativeMassResidual(), 1.0e-12);
    assertFalse(table.getSourceBalanceTableFingerprint().isEmpty());
    assertNotSame(table.getComponentFlows(), table.getComponentFlows());
    assertNotSame(table.getComponentBalances(), table.getComponentBalances());
    assertNotSame(table.getDiagnostics(), table.getDiagnostics());
    assertThrows(UnsupportedOperationException.class, () -> table.getComponentFlows().clear());
    assertTrue(table.toJson().contains("\"massFlowUnit\": \"kg/s\""));
    assertTrue(table.toJson().contains("\"provenance\": \"simulation-case:NORMAL-01\""));
    assertTrue(hasDiagnostic(table, "COMPONENT_TOTAL_MASS_MISMATCH"));
  }

  @Test
  void isDeterministicForFreshSystemsAndInputOrder() {
    Fixture firstFixture = fixture();
    Fixture secondFixture = fixture();
    List<ComponentFlow> firstFlows = completeFlows(firstFixture.boundaries);
    List<ComponentFlow> secondFlows = completeFlows(secondFixture.boundaries);
    Collections.reverse(secondFlows);

    EngineeringDiagramComponentBalanceTable first = EngineeringDiagramComponentBalanceTable
        .fromBalanceTable(firstFixture.balanceTable, firstFlows);
    EngineeringDiagramComponentBalanceTable second = EngineeringDiagramComponentBalanceTable
        .fromBalanceTable(secondFixture.balanceTable, secondFlows);

    assertEquals(first.toJson(), second.toJson());
  }

  @Test
  void isDeterministicForConflictingDuplicateInputOrder() {
    Fixture fixture = fixture();
    List<ComponentFlow> forward = completeFlows(fixture.boundaries);
    ComponentFlow original = forward.get(0);
    forward.add(new ComponentFlow(original.getBalanceId(), original.getStreamSemanticObjectId(),
        original.getComponentId(), original.getComponentName(), original.getResultValue() + 0.5,
        original.getResultUnit(), original.getQuantityBasis(), original.getSourceReference(),
        "simulation-case:NORMAL-02", original.getEvidenceState()));
    List<ComponentFlow> reverse = new ArrayList<ComponentFlow>(forward);
    Collections.reverse(reverse);

    EngineeringDiagramComponentBalanceTable first = EngineeringDiagramComponentBalanceTable
        .fromBalanceTable(fixture.balanceTable, forward);
    EngineeringDiagramComponentBalanceTable second = EngineeringDiagramComponentBalanceTable
        .fromBalanceTable(fixture.balanceTable, reverse);

    assertFalse(first.isValid());
    assertTrue(hasDiagnostic(first, "COMPONENT_FLOW_DUPLICATE"));
    assertEquals(first.toJson(), second.toJson());
  }

  @Test
  void diagnosesMissingDuplicateUnknownAndWrongBasisValues() {
    Fixture fixture = fixture();
    List<ComponentFlow> flows = completeFlows(fixture.boundaries);
    ComponentFlow first = flows.get(0);
    flows.add(first);
    flows.remove(1);
    flows.add(new ComponentFlow("BAL-SIMPLE-01", "line:missing", "methane", "methane", 1.0, "kg/s", "COMPONENT_MASS",
        "project-component-register:test", "simulation-case:NORMAL-01", EvidenceState.PROPOSED));
    flows.add(new ComponentFlow("BAL-SIMPLE-01", fixture.boundaries.get(0).getStreamSemanticObjectId(), "water",
        "water", 0.0, "kg/hr", "COMPONENT_MASS", "project-component-register:test", "simulation-case:NORMAL-01",
        EvidenceState.PROPOSED));
    flows.add(new ComponentFlow("BAL-SIMPLE-01", fixture.boundaries.get(1).getStreamSemanticObjectId(), "nitrogen",
        "nitrogen", Double.NaN, "kg/s", "COMPONENT_MASS", "project-component-register:test",
        "simulation-case:NORMAL-01", EvidenceState.PROPOSED));

    EngineeringDiagramComponentBalanceTable table = EngineeringDiagramComponentBalanceTable
        .fromBalanceTable(fixture.balanceTable, flows);

    assertFalse(table.isValid());
    assertTrue(hasDiagnostic(table, "COMPONENT_FLOW_DUPLICATE"));
    assertTrue(hasDiagnostic(table, "COMPONENT_FLOW_UNKNOWN_BOUNDARY"));
    assertTrue(hasDiagnostic(table, "COMPONENT_FLOW_VALUE_INVALID"));
    assertTrue(hasDiagnostic(table, "COMPONENT_FLOW_MISSING"));
    assertTrue(table.toJson().contains("\"nonFiniteResult\": \"NaN\""));
  }

  @Test
  void requiresExplicitComponentValuesAndArguments() {
    Fixture fixture = fixture();

    EngineeringDiagramComponentBalanceTable table = EngineeringDiagramComponentBalanceTable
        .fromBalanceTable(fixture.balanceTable, Collections.<ComponentFlow>emptyList());

    assertFalse(table.isValid());
    assertTrue(hasDiagnostic(table, "COMPONENT_FLOW_NOT_DECLARED"));
    assertTrue(hasDiagnostic(table, "COMPONENT_BALANCE_COMPONENTS_MISSING"));
    assertThrows(IllegalArgumentException.class,
        () -> EngineeringDiagramComponentBalanceTable.fromBalanceTable(null, Collections.<ComponentFlow>emptyList()));
    assertThrows(IllegalArgumentException.class,
        () -> EngineeringDiagramComponentBalanceTable.fromBalanceTable(fixture.balanceTable, null));
    assertThrows(IllegalArgumentException.class, () -> new ComponentFlow("BAL-SIMPLE-01", "line:feed", "methane",
        "methane", 1.0, "kg/s", "COMPONENT_MASS", "source", "provenance", null));
  }

  private static Fixture fixture() {
    EngineeringDiagramReferenceFixtures.SystemCase reference = EngineeringDiagramReferenceFixtures.simpleTrain();
    for (StreamInterface product : reference.getProducts()) {
      reference.getProcessSystem().add(product);
    }
    reference.getProcessSystem().run();
    EngineeringDiagramDocumentSet documents = ProcessDiagramDocumentSetAdapter.fromProcessSystem(
        reference.getProcessSystem(), reference.getCaseId(), "A", "PFD-HMB-006", "Component balance",
        ContentProfile.PFD, "NORMAL-01");
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
    return new Fixture(balanceTable, boundaries);
  }

  private static List<ComponentFlow> completeFlows(List<Boundary> boundaries) {
    Boundary inlet = direction(boundaries, Direction.INLET, 0);
    Boundary firstOutlet = direction(boundaries, Direction.OUTLET, 0);
    Boundary secondOutlet = direction(boundaries, Direction.OUTLET, 1);
    return new ArrayList<ComponentFlow>(
        Arrays.asList(flow(inlet, "methane", 6.0), flow(inlet, "ethane", 4.0), flow(firstOutlet, "methane", 4.0),
            flow(firstOutlet, "ethane", 1.0), flow(secondOutlet, "methane", 2.0), flow(secondOutlet, "ethane", 3.0)));
  }

  private static ComponentFlow flow(Boundary boundary, String component, double massFlow) {
    return new ComponentFlow(boundary.getBalanceId(), boundary.getStreamSemanticObjectId(), component, component,
        massFlow, "kg/s", "COMPONENT_MASS", "project-component-register:test", "simulation-case:NORMAL-01",
        EvidenceState.PROPOSED);
  }

  private static Boundary direction(List<Boundary> boundaries, Direction direction, int index) {
    int match = 0;
    for (Boundary boundary : boundaries) {
      if (boundary.getDirection() == direction) {
        if (match == index) {
          return boundary;
        }
        match++;
      }
    }
    throw new AssertionError("No boundary " + direction + " at index " + index);
  }

  private static Row completeRow(EngineeringDiagramStreamTable table, String sourceLabel) {
    for (Row row : table.getRows()) {
      if (sourceLabel.equals(row.getSourceLabel()) && row.getValues().size() == Quantity.values().length) {
        return row;
      }
    }
    throw new AssertionError("No complete stream-table row for " + sourceLabel);
  }

  private static ComponentBalance component(EngineeringDiagramComponentBalanceTable table, String componentId) {
    for (ComponentBalance balance : table.getComponentBalances()) {
      if (componentId.equals(balance.getComponentId())) {
        return balance;
      }
    }
    throw new AssertionError("No component balance for " + componentId);
  }

  private static boolean hasDiagnostic(EngineeringDiagramComponentBalanceTable table, String code) {
    for (EngineeringDiagramComponentBalanceTable.Diagnostic diagnostic : table.getDiagnostics()) {
      if (code.equals(diagnostic.getCode())) {
        return true;
      }
    }
    return false;
  }

  private static final class Fixture {
    private final EngineeringDiagramBalanceTable balanceTable;
    private final List<Boundary> boundaries;

    private Fixture(EngineeringDiagramBalanceTable balanceTable, List<Boundary> boundaries) {
      this.balanceTable = balanceTable;
      this.boundaries = boundaries;
    }
  }
}
