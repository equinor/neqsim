package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.TwoFluidComponentConservationReport;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Component-resolved downstream publication and rejected-step isolation. */
class TwoFluidComponentOutletTest {
  @Test
  void unequalThreePhaseTransportPublishesEachComponentByNameAndSurvivesDownstreamFlash() {
    SystemInterface fluid = fluid(false);
    assertEquals(3, fluid.getNumberOfPhases());
    TwoFluidSection[] cells = cells();
    TwoFluidComponentTransport transport = new TwoFluidComponentTransport(fluid, cells);
    double[][] faces = { { 2.0, 0.05, 0.01 }, { 2.0, 0.05, 0.01 } };
    transport.advance(0.01, faces, new double[1][3], cells, fluid, fluid, 1.0e-8);
    TwoFluidComponentConservationReport report = transport.createReport(0.01, 1, 1.0e-8);
    assertTrue(report.isConverged(), report.getMessage());
    String before = report.toJson();
    SystemInterface reordered = fluid(true);
    double[] composition = reordered.getMolarComposition().clone();
    SystemInterface outlet = transport.createOutletFluid(reordered, 65.0e5, 290.15, 0.01);
    assertNotSame(reordered, outlet);
    assertArrayEquals(composition, reordered.getMolarComposition(), 0.0);
    assertEquals(70.0e5, reordered.getPressure("Pa"), 1.0e-8);
    assertEquals(65.0e5, outlet.getPressure("Pa"), 1.0e-8);
    assertEquals(290.15, outlet.getTemperature("K"), 0.0);
    assertEquals(2.06, outlet.getFlowRate("kg/sec"), 1.0e-12);
    assertNotEquals(fluid.getPhase(0).getComponent("methane").getz(), outlet.getPhase(0).getComponent("methane").getz(),
        0.01, "Unequal phase transports must change the total exported composition");
    assertComponentFlows(report, outlet);
    Stream downstream = new Stream("component-outlet", outlet);
    downstream.run();
    assertComponentFlows(report, downstream.getFluid());
    TwoFluidComponentTransport copied = SerializationUtils.clone(transport);
    assertComponentFlows(report, copied.createOutletFluid(fluid, 65.0e5, 290.15, 0.01));
    SystemInterface emptyTemplate = reordered.clone();
    emptyTemplate.setTotalFlowRate(0.0, "kg/sec");
    assertComponentFlows(report, transport.createOutletFluid(emptyTemplate, 65.0e5, 290.15, 0.01));
    assertEquals(0.0, emptyTemplate.getFlowRate("kg/sec"), 0.0);
    assertArrayEquals(composition, emptyTemplate.getMolarComposition(), 0.0);
    assertEquals(before, transport.createReport(0.01, 1, 1.0e-8).toJson());
  }

  @Test
  void closedOutletPublishesZeroMassWithoutAnEmptyFlashOrTemplateMutation() {
    SystemInterface fluid = fluid(false);
    TwoFluidSection[] cells = cells();
    TwoFluidComponentTransport transport = new TwoFluidComponentTransport(fluid, cells);
    transport.advance(0.1, new double[2][3], new double[1][3], cells, fluid, fluid, 1.0e-8);
    fluid.setTotalFlowRate(0.0, "kg/sec");
    double[] composition = fluid.getMolarComposition().clone();
    SystemInterface outlet = transport.createOutletFluid(fluid, 65.0e5, 290.15, 0.1);
    assertEquals(0.0, outlet.getFlowRate("kg/sec"), 0.0);
    assertEquals(0.0, fluid.getFlowRate("kg/sec"), 0.0);
    assertEquals(70.0e5, fluid.getPressure("Pa"), 1.0e-8);
    assertArrayEquals(composition, outlet.getMolarComposition(), 1.0e-12);
    assertArrayEquals(new double[3], transport.createReport(0.1, 1, 1.0e-8).getOutletBoundaryMassKg(), 0.0);
  }

  @Test
  void outletRejectsChangedMolarMassAndInvalidConditionsWithoutChangingTheAcceptedLedger() {
    SystemInterface fluid = fluid(false);
    TwoFluidComponentTransport transport = new TwoFluidComponentTransport(fluid, cells());
    String before = transport.createReport(0.0, 0, 1.0e-8).toJson();
    SystemInterface changed = fluid.clone();
    changed.getPhase(0).getComponent("methane").setMolarMass(0.020);
    assertThrows(IllegalArgumentException.class, () -> transport.createOutletFluid(changed, 65.0e5, 290.15, 0.1));
    assertThrows(IllegalArgumentException.class, () -> transport.createOutletFluid(null, 65.0e5, 290.15, 0.1));
    for (double invalid : new double[] { 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY }) {
      assertThrows(IllegalArgumentException.class, () -> transport.createOutletFluid(fluid, invalid, 290.15, 0.1));
      assertThrows(IllegalArgumentException.class, () -> transport.createOutletFluid(fluid, 65.0e5, invalid, 0.1));
      assertThrows(IllegalArgumentException.class, () -> transport.createOutletFluid(fluid, 65.0e5, 290.15, invalid));
    }
    assertEquals(before, transport.createReport(0.0, 0, 1.0e-8).toJson());
  }

  @Test
  void overflowAndInvalidEndpointDiscardTheComponentCandidate() {
    SystemInterface fluid = fluid(false);
    TwoFluidSection[] cells = cells();
    TwoFluidComponentTransport transport = new TwoFluidComponentTransport(fluid, cells);
    String before = transport.createReport(0.0, 0, 1.0e-8).toJson();
    assertThrows(IllegalStateException.class, () -> transport.advance(Double.MAX_VALUE,
        new double[][] { { 2.0, 0.0, 0.0 }, { 2.0, 0.0, 0.0 } }, new double[1][3], cells, fluid, fluid, 1.0e-8));
    assertEquals(before, transport.createReport(0.0, 0, 1.0e-8).toJson());
    for (double invalid : new double[] { -1.0, Double.NaN, Double.POSITIVE_INFINITY }) {
      TwoFluidSection[] invalidCells = cells();
      invalidCells[0].setGasMassPerLength(invalid);
      assertThrows(IllegalArgumentException.class,
          () -> transport.advance(0.1, new double[2][3], new double[1][3], invalidCells, fluid, fluid, 1.0e-8));
      assertEquals(before, transport.createReport(0.0, 0, 1.0e-8).toJson());
    }
  }

  private static void assertComponentFlows(TwoFluidComponentConservationReport report, SystemInterface outlet) {
    String[] names = report.getComponentNames();
    double[] masses = report.getOutletBoundaryMassKg();
    for (int component = 0; component < names.length; component++) {
      double flow = outlet.getTotalNumberOfMoles() * outlet.getPhase(0).getComponent(names[component]).getz()
          * outlet.getPhase(0).getComponent(names[component]).getMolarMass();
      assertEquals(masses[component] / report.getElapsedTimeSeconds(), flow, 1.0e-11, names[component]);
    }
  }

  private static TwoFluidSection[] cells() {
    TwoFluidSection section = new TwoFluidSection(0.0, 1.0, 0.20, 0.0);
    section.setPressure(70.0e5);
    section.setTemperature(298.15);
    section.setGasMassPerLength(2.0);
    section.setOilMassPerLength(3.0);
    section.setWaterMassPerLength(4.0);
    return new TwoFluidSection[] { section };
  }

  private static SystemInterface fluid(boolean reversed) {
    SystemInterface fluid = new SystemSrkEos(298.15, 70.0);
    if (reversed) {
      fluid.addComponent("water", 0.1);
      fluid.addComponent("n-heptane", 0.2);
      fluid.addComponent("methane", 0.7);
    } else {
      fluid.addComponent("methane", 0.7);
      fluid.addComponent("n-heptane", 0.2);
      fluid.addComponent("water", 0.1);
    }
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    fluid.setTotalFlowRate(1.0, "kg/sec");
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    return fluid;
  }
}
