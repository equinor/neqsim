package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.process.equipment.pipeline.TwoFluidComponentConservationReport;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Conservative source-stage phase appearance may supply same-step component face transport.
 *
 * @author ESOL
 * @version 1.0
 */
class TwoFluidPhaseAppearanceAdvectionTest {
  /** Prove that transported newly formed liquid uses actual source components without adding inventory. */
  @Test
  void newlyCondensedOilCanAdvectWithinItsCreatedMassBudget() {
    Fixture fixture = new Fixture();
    fixture.advance(0.0004, 0.001, 0.001);

    double[][][] actual = fixture.transport.getInventoryKg();
    assertEquals(0.0006, actual[0][1][fixture.ethane], 1e-14);
    assertEquals(0.0014, actual[1][1][fixture.ethane], 1e-14);
    assertEquals(0.0, actual[0][1][fixture.methane], 0.0);
    assertEquals(0.0, actual[1][1][fixture.methane], 0.0);
    for (int component = 0; component < 2; component++) {
      double before = fixture.initial[0][0][component] + fixture.initial[1][0][component];
      double after = 0.0;
      for (int cell = 0; cell < 2; cell++) {
        after += actual[cell][0][component] + actual[cell][1][component];
      }
      assertEquals(before, after, 1e-14, "Closed domain must conserve every component");
    }
    TwoFluidComponentConservationReport report = fixture.transport.createReport(1.0, 1, 1e-8);
    assertTrue(report.isConverged(), report.getMessage());
    assertEquals(0.0, report.getMaximumInterphaseTransferResidualKg(), 1e-14);
    assertEquals(0.0, report.getMaximumPhaseMassSynchronizationErrorKg(), 1e-14);
  }

  /** Newly formed liquid uses the source composition for internal flow in either direction. */
  @Test
  void newlyCondensedOilCanAdvectInTheNegativeDirection() {
    Fixture fixture = new Fixture();
    fixture.advance(-0.0004, 0.001, 0.001);
    double[][][] actual = fixture.transport.getInventoryKg();
    assertEquals(0.0014, actual[0][1][fixture.ethane], 1e-14);
    assertEquals(0.0006, actual[1][1][fixture.ethane], 1e-14);
    assertEquals(0.0, actual[0][1][fixture.methane], 0.0);
    assertTrue(fixture.transport.createReport(1.0, 1, 1e-8).isConverged());
  }

  /**
   * Every nonzero signed face transfer carries the source-stage composition, including trace transfers.
   *
   * @param signedFaceOilKgS signed internal oil transfer in kg/s
   */
  @ParameterizedTest
  @ValueSource(doubles = {-4.0e-14, 4.0e-14})
  void traceOilFaceTransferKeepsSourceStageComposition(double signedFaceOilKgS) {
    Fixture fixture = new Fixture();
    int donor = signedFaceOilKgS > 0.0 ? 0 : 1;
    int receiver = 1 - donor;
    double createdOilKg = 1.0e-6;
    double transportedOilKg = Math.abs(signedFaceOilKgS);
    double[][] flux = new double[3][3];
    flux[1][1] = signedFaceOilKgS;
    double[][] sources = new double[2][3];
    sources[donor][0] = -createdOilKg;
    sources[donor][1] = createdOilKg;
    double[][][] componentSources = new double[2][3][2];
    componentSources[donor][0][fixture.ethane] = -createdOilKg;
    componentSources[donor][1][fixture.ethane] = createdOilKg;
    fixture.sections[donor].setGasMassPerLength(1.0 - createdOilKg);
    fixture.sections[donor].setOilMassPerLength(createdOilKg - transportedOilKg);
    fixture.sections[receiver].setOilMassPerLength(transportedOilKg);

    fixture.transport.advance(1.0, flux, sources, componentSources, new double[2], fixture.sections, fixture.fluid,
        fixture.fluid, 1.0e-8);
    double[][][] actual = fixture.transport.getInventoryKg();
    assertEquals(createdOilKg - transportedOilKg, actual[donor][1][fixture.ethane], 1.0e-20);
    assertEquals(transportedOilKg, actual[receiver][1][fixture.ethane], 1.0e-26);
    assertEquals(0.0, actual[receiver][1][fixture.methane], 0.0);
    assertTrue(fixture.transport.createReport(1.0, 1, 1.0e-8).isConverged());
  }

  /** An empty donor without a conservative source must retain its existing fail-closed behavior. */
  @Test
  void emptyOilDonorWithoutSourceIsRejectedWithoutMutation() {
    Fixture fixture = new Fixture();
    assertThrows(IllegalStateException.class, () -> fixture.advance(0.0004, 0.0, 0.0));
    fixture.assertUnchanged();
  }

  /** Never fund a face transfer with more liquid than the accepted source created. */
  @Test
  void outgoingOilCannotExceedTheSameStageCreatedMass() {
    Fixture fixture = new Fixture();
    assertThrows(IllegalStateException.class, () -> fixture.advance(0.0011, 0.001, 0.001));
    fixture.assertUnchanged();
  }

  /** Source component allocation must still reproduce the hydrodynamic source. */
  @Test
  void conflictingComponentAndPhaseSourcesAreRejectedWithoutMutation() {
    Fixture fixture = new Fixture();
    assertThrows(IllegalStateException.class, () -> fixture.advance(0.0004, 0.001, 0.002));
    fixture.assertUnchanged();
  }

  /** Equal phase totals cannot authorize conversion of one chemical component into another. */
  @Test
  void chemicallyInconsistentSourceAllocationsAreRejectedWithoutMutation() {
    Fixture fixture = new Fixture();
    double[][] flux = new double[3][3];
    flux[1][1] = 0.0004;
    double[][] sources = {{-0.001, 0.001, 0.0}, {-0.001, 0.001, 0.0}};
    double[][][] componentSources = new double[2][3][2];
    for (int cell = 0; cell < 2; cell++) {
      componentSources[cell][0][fixture.methane] = -0.001;
      componentSources[cell][1][fixture.ethane] = 0.001;
      fixture.sections[cell].setGasMassPerLength(0.999);
      fixture.sections[cell].setOilMassPerLength(cell == 0 ? 0.0006 : 0.0014);
    }

    assertThrows(IllegalStateException.class, () -> fixture.transport.advance(1.0, flux, sources, componentSources,
        new double[2], fixture.sections, fixture.fluid, fixture.fluid, 1e-8));
    fixture.assertUnchanged();
  }

  /** Incoming newly formed oil can fund a conservative onward departure through an initially empty cell. */
  @Test
  void incomingOilCanFundSameStepOnwardDeparture() {
    Fixture fixture = new Fixture();
    double[][] flux = new double[3][3];
    flux[1][1] = 0.0008;
    flux[2][1] = 0.0015;
    double[][] sources = {{-0.001, 0.001, 0.0}, {-0.001, 0.001, 0.0}};
    double[][][] componentSources = new double[2][3][2];
    for (int cell = 0; cell < 2; cell++) {
      componentSources[cell][0][fixture.ethane] = -0.001;
      componentSources[cell][1][fixture.ethane] = 0.001;
      fixture.sections[cell].setGasMassPerLength(0.999);
      fixture.sections[cell].setOilMassPerLength(cell == 0 ? 0.0002 : 0.0003);
    }

    fixture.transport.advance(1.0, flux, sources, componentSources, new double[2], fixture.sections, fixture.fluid,
        fixture.fluid, 1e-8);
    double[][][] actual = fixture.transport.getInventoryKg();
    assertEquals(0.0002, actual[0][1][fixture.ethane], 1e-14);
    assertEquals(0.0003, actual[1][1][fixture.ethane], 1e-14);
    assertEquals(0.0, actual[0][1][fixture.methane], 0.0);
    assertEquals(0.0, actual[1][1][fixture.methane], 0.0);
    TwoFluidComponentConservationReport report = fixture.transport.createReport(1.0, 1, 1e-8);
    assertEquals(0.0015, report.getOutletBoundaryMassKg()[fixture.ethane], 1e-14);
    assertTrue(report.isConverged(), report.getMessage());
  }

  /** Newly appearing oil can leave the outlet while its actual components close the boundary ledger. */
  @Test
  void newlyCondensedOilCanLeaveThroughTheOutletConservatively() {
    Fixture fixture = new Fixture();
    double[][] flux = new double[3][3];
    flux[1][1] = 0.0004;
    flux[2][1] = 0.0007;
    double[][] sources = {{-0.001, 0.001, 0.0}, {-0.001, 0.001, 0.0}};
    double[][][] componentSources = new double[2][3][2];
    for (int cell = 0; cell < 2; cell++) {
      componentSources[cell][0][fixture.ethane] = -0.001;
      componentSources[cell][1][fixture.ethane] = 0.001;
      fixture.sections[cell].setGasMassPerLength(0.999);
      fixture.sections[cell].setOilMassPerLength(cell == 0 ? 0.0006 : 0.0007);
    }

    fixture.transport.advance(1.0, flux, sources, componentSources, new double[2], fixture.sections, fixture.fluid,
        fixture.fluid, 1e-8);
    double[][][] actual = fixture.transport.getInventoryKg();
    assertEquals(0.0006, actual[0][1][fixture.ethane], 1e-14);
    assertEquals(0.0007, actual[1][1][fixture.ethane], 1e-14);
    assertEquals(0.0, actual[0][1][fixture.methane], 0.0);
    assertEquals(0.0, actual[1][1][fixture.methane], 0.0);
    TwoFluidComponentConservationReport report = fixture.transport.createReport(1.0, 1, 1e-8);
    assertEquals(0.0007, report.getOutletBoundaryMassKg()[fixture.ethane], 1e-14);
    assertEquals(0.0, report.getOutletBoundaryMassKg()[fixture.methane], 0.0);
    assertArrayEquals(new double[2], report.getInletBoundaryMassKg(), 0.0);
    for (int component = 0; component < 2; component++) {
      assertEquals(report.getInitialInventoryKg()[component],
          report.getFinalInventoryKg()[component] + report.getOutletBoundaryMassKg()[component], 1e-14);
    }
    assertTrue(report.isConverged(), report.getMessage());
    assertEquals(0.0, report.getMaximumInterphaseTransferResidualKg(), 1e-14);
    assertEquals(0.0, report.getMaximumPhaseMassSynchronizationErrorKg(), 1e-14);
  }

  /** Non-finite frozen source entries cannot establish a phase composition. */
  @Test
  void nonFiniteAppearanceSourcesAreRejectedWithoutMutation() {
    for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
      Fixture fixture = new Fixture();
      assertThrows(IllegalStateException.class, () -> fixture.advance(0.0004, 0.001, invalid));
      fixture.assertUnchanged();
    }
  }

  /** An existing oil phase must keep its old upwind composition rather than adopt the source composition. */
  @Test
  void existingPhaseKeepsItsOriginalUpwindComposition() {
    SystemInterface fluid = new SystemSrkEos(300.0, 100.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("n-heptane", 0.2);
    fluid.setMixingRule("classic");
    TwoFluidSection[] cells = new TwoFluidSection[2];
    for (int cell = 0; cell < cells.length; cell++) {
      cells[cell] = new TwoFluidSection(cell + 0.5, 1.0, 0.3, 0.0);
      cells[cell].setPressure(100e5);
      cells[cell].setTemperature(300.0);
      cells[cell].setGasMassPerLength(1.0);
      cells[cell].setOilMassPerLength(0.1);
    }
    TwoFluidComponentTransport transport = new TwoFluidComponentTransport(fluid, cells);
    String[] names = transport.getComponentNames();
    int heavy = "n-heptane".equals(names[0]) ? 0 : 1;
    double[][][] before = transport.getInventoryKg();
    double additionKg = 1e-6;
    double departureKg = 5e-5;
    double[][] flux = new double[3][3];
    flux[1][1] = departureKg;
    double[][] sources = {{-additionKg, additionKg, 0.0}, {-additionKg, additionKg, 0.0}};
    double[][][] componentSources = new double[2][3][2];
    for (int cell = 0; cell < cells.length; cell++) {
      componentSources[cell][0][heavy] = -additionKg;
      componentSources[cell][1][heavy] = additionKg;
      cells[cell].setGasMassPerLength(1.0 - additionKg);
      cells[cell].setOilMassPerLength(0.1 + additionKg + (cell == 0 ? -departureKg : departureKg));
    }
    transport.advance(1.0, flux, sources, componentSources, new double[2], cells, fluid, fluid, 1e-8);
    double[][][] after = transport.getInventoryKg();
    for (int component = 0; component < 2; component++) {
      double oldFraction = before[0][1][component] / 0.1;
      double addition = component == heavy ? additionKg : 0.0;
      assertEquals(before[0][1][component] - departureKg * oldFraction + addition, after[0][1][component], 1e-14);
      assertEquals(before[1][1][component] + departureKg * oldFraction + addition, after[1][1][component], 1e-14);
    }
    assertTrue(transport.createReport(1.0, 1, 1e-8).isConverged());
  }

  /** Both outgoing faces share one finite source budget rather than each receiving the full allowance. */
  @Test
  void combinedOutgoingFacesCannotDoubleSpendOneAppearanceSource() {
    Fixture fixture = new Fixture();
    TwoFluidSection[] cells = new TwoFluidSection[3];
    for (int cell = 0; cell < cells.length; cell++) {
      cells[cell] = new TwoFluidSection(cell + 0.5, 1.0, 0.3, 0.0);
      cells[cell].setPressure(100e5);
      cells[cell].setTemperature(300.0);
      cells[cell].setGasMassPerLength(1.0);
    }
    TwoFluidComponentTransport transport = new TwoFluidComponentTransport(fixture.fluid, cells);
    String before = transport.createReport(0.0, 0, 1e-8).toJson();
    double[][] flux = new double[4][3];
    flux[1][1] = -0.0006;
    flux[2][1] = 0.0006;
    double[][] sources = new double[3][3];
    double[][][] componentSources = new double[3][3][2];
    for (int cell = 0; cell < cells.length; cell++) {
      sources[cell][0] = -0.001;
      sources[cell][1] = 0.001;
      componentSources[cell][0][fixture.ethane] = -0.001;
      componentSources[cell][1][fixture.ethane] = 0.001;
      cells[cell].setGasMassPerLength(0.999);
      cells[cell].setOilMassPerLength(cell == 1 ? 0.0 : 0.0016);
    }
    IllegalStateException failure = assertThrows(IllegalStateException.class, () -> transport.advance(1.0, flux,
        sources, componentSources, new double[3], cells, fixture.fluid, fixture.fluid, 1e-8));
    assertTrue(failure.getMessage().contains("Insufficient oil source/incoming donor inventory"));
    assertEquals(before, transport.createReport(0.0, 0, 1e-8).toJson());
  }

  /** A two-cell source/flux fixture isolates component transport from EOS/source-model accuracy. */
  private static final class Fixture {
    private final SystemInterface fluid;
    private final TwoFluidSection[] sections;
    private final TwoFluidComponentTransport transport;
    private final int ethane;
    private final int methane;
    private final double[][][] initial;
    private final String initialReport;

    /** Initialize exactly zero oil in two gas-only cells. */
    private Fixture() {
      fluid = new SystemSrkEos(300.0, 100.0);
      fluid.addComponent("methane", 0.99);
      fluid.addComponent("ethane", 0.01);
      fluid.setMixingRule("classic");
      sections = new TwoFluidSection[2];
      for (int cell = 0; cell < sections.length; cell++) {
        sections[cell] = new TwoFluidSection(cell + 0.5, 1.0, 0.3, 0.0);
        sections[cell].setPressure(100e5);
        sections[cell].setTemperature(300.0);
        sections[cell].setGasMassPerLength(1.0);
      }
      transport = new TwoFluidComponentTransport(fluid, sections);
      String[] names = transport.getComponentNames();
      ethane = "ethane".equals(names[0]) ? 0 : 1;
      methane = 1 - ethane;
      initial = transport.getInventoryKg();
      initialReport = transport.createReport(0.0, 0, 1e-8).toJson();
    }

    /**
     * Advance one closed-boundary, one-second step with a controlled internal oil face flux.
     *
     * @param faceOilKgS positive oil transfer from cell zero to cell one
     * @param phaseCreationKg conservative condensation source per cell
     * @param componentCreationKg ethane allocation of the same source per cell
     */
    private void advance(double faceOilKgS, double phaseCreationKg, double componentCreationKg) {
      double[][] flux = new double[3][3];
      flux[1][1] = faceOilKgS;
      double[][] phaseSource = new double[2][3];
      double[][][] componentSource = new double[2][3][2];
      for (int cell = 0; cell < sections.length; cell++) {
        phaseSource[cell][0] = -phaseCreationKg;
        phaseSource[cell][1] = phaseCreationKg;
        componentSource[cell][0][ethane] = -componentCreationKg;
        componentSource[cell][1][ethane] = componentCreationKg;
        sections[cell].setGasMassPerLength(1.0 - phaseCreationKg);
        sections[cell].setOilMassPerLength(Math.max(0.0, phaseCreationKg + (cell == 0 ? -faceOilKgS : faceOilKgS)));
      }
      transport.advance(1.0, flux, phaseSource, componentSource, new double[2], sections, fluid, fluid, 1e-8);
    }

    /** Check both state and interval ledgers after a rejected candidate. */
    private void assertUnchanged() {
      double[][][] actual = transport.getInventoryKg();
      for (int cell = 0; cell < 2; cell++) {
        for (int phase = 0; phase < 3; phase++) {
          assertArrayEquals(initial[cell][phase], actual[cell][phase], 0.0);
        }
      }
      assertEquals(initialReport, transport.createReport(0.0, 0, 1e-8).toJson());
    }
  }
}
