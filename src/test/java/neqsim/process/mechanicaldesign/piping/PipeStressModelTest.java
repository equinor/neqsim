package neqsim.process.mechanicaldesign.piping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.mechanicaldesign.piping.PipeStressModel.RestraintType;
import neqsim.process.mechanicaldesign.piping.PipeStressModel.Results;

/**
 * Tests of {@link PipeStressModel} against closed-form beam and B31.3 results.
 *
 * @author esol
 * @version 1.0
 */
public class PipeStressModelTest {
  private static final double OD = 0.2191;
  private static final double T = 0.00818;
  private static final double E = 203.0e9;
  private static final double ALPHA = 12.0e-6;

  /**
   * Second moment of area of the test pipe.
   *
   * @return I in m4
   */
  private static double inertia() {
    double di = OD - 2 * T;
    return Math.PI / 64.0 * (Math.pow(OD, 4) - Math.pow(di, 4));
  }

  /**
   * Steel area of the test pipe.
   *
   * @return area in m2
   */
  private static double area() {
    double di = OD - 2 * T;
    return Math.PI / 4.0 * (OD * OD - di * di);
  }

  /**
   * Straight model along x with nodes every 3 m.
   *
   * @param length total length in m
   * @return model with nodes N0..Nn and elements
   */
  private static PipeStressModel straight(double length) {
    PipeStressModel m = new PipeStressModel("test");
    m.setPipe(OD, T, 0.0);
    m.setContents(0.0, 0.0);
    int n = (int) Math.round(length / 3.0);
    for (int k = 0; k <= n; k++) {
      m.addNode("N" + k, 3.0 * k, 0.0, 0.0);
      if (k > 0) {
        m.addElement("N" + (k - 1), "N" + k);
      }
    }
    return m;
  }

  /** Fixed-fixed beam under self weight: mid-span deflection wL^4/384EI and end moment wL^2/12. */
  @Test
  void fixedFixedBeamMatchesClosedForm() {
    PipeStressModel m = straight(6.0);
    m.addRestraint("A", "N0", RestraintType.ANCHOR, 0.0);
    m.addRestraint("B", "N2", RestraintType.ANCHOR, 0.0);
    Results r = m.run();
    double w = m.lineWeight();
    double expectedDeflection = w * Math.pow(6.0, 4) / (384.0 * E * inertia());
    assertEquals(-expectedDeflection, r.getSustainedDisplacement("N1")[2], 1.0e-3 * expectedDeflection);
    double[] reaction = r.getRestraint("A").getSustained();
    assertEquals(w * 3.0, reaction[2], 1.0e-3 * w * 3.0);
    assertEquals(w * 36.0 / 12.0, Math.abs(reaction[4]), 1.0e-3 * w * 3.0);
  }

  /** Heated pipe between two anchors carries the full thermal force E A alpha dT. */
  @Test
  void anchoredHeatedPipeCarriesThermalForce() {
    PipeStressModel m = straight(12.0);
    m.addRestraint("A", "N0", RestraintType.ANCHOR, 0.0);
    m.addRestraint("B", "N4", RestraintType.ANCHOR, 0.0);
    m.setConditions(0.0, 20.0, 120.0);
    Results r = m.run();
    double expected = E * area() * ALPHA * 100.0;
    assertEquals(expected, Math.abs(r.getRestraint("A").getOperating()[0]), 1.0e-3 * expected);
    assertEquals(0.0, r.getOperatingDisplacement("N2")[0], 1.0e-6);
  }

  /** One anchor and a free end: the free end grows alpha dT L. */
  @Test
  void freeEndGrowsByThermalStrain() {
    PipeStressModel m = straight(12.0);
    m.addRestraint("A", "N0", RestraintType.ANCHOR, 0.0);
    m.setConditions(0.0, 20.0, 120.0);
    Results r = m.run();
    assertEquals(ALPHA * 100.0 * 12.0, r.getOperatingDisplacement("N4")[0], 1.0e-6);
  }

  /** Sliding supports: the anchor takes the sum of mu times the support normal loads. */
  @Test
  void slidingFrictionLoadsTheAnchor() {
    PipeStressModel m = straight(30.0);
    m.setContents(1000.0, 0.0);
    m.addRestraint("A", "N0", RestraintType.ANCHOR, 0.0);
    for (int k = 2; k <= 10; k += 2) {
      m.addRestraint("S" + k, "N" + k, RestraintType.REST, 0.3);
    }
    m.setConditions(0.0, 20.0, 120.0);
    Results r = m.run();
    assertTrue(r.isConverged());
    assertEquals(5, r.getSliding().size());
    double sumNormal = 0.0;
    for (int k = 2; k <= 10; k += 2) {
      sumNormal += r.getRestraint("S" + k).getOperating()[2];
    }
    assertEquals(0.3 * sumNormal, Math.abs(r.getRestraint("A").getOperating()[0]), 0.01 * 0.3 * sumNormal);
    assertTrue(r.getOperatingDisplacement("N10")[0] > 0.0);
    assertTrue(r.getOperatingDisplacement("N10")[0] < ALPHA * 100.0 * 30.0);
  }

  /** A hot riser lifts a short horizontal leg off its rest support; SUS uses the lifted-off state. */
  @Test
  void riserGrowthLiftsSupportOff() {
    PipeStressModel m = new PipeStressModel("riser");
    m.setPipe(OD, T, 0.0);
    m.addNode("A", 0, 0, 0);
    m.addNode("B", 0, 0, 10);
    m.addNode("C", 3, 0, 10);
    m.addElement("A", "B");
    m.addElement("B", "C");
    m.addRestraint("ANC", "A", RestraintType.ANCHOR, 0.0);
    m.addRestraint("S1", "C", RestraintType.REST, 0.0);
    m.setConditions(0.0, 20.0, 270.0);
    Results r = m.run();
    assertEquals(1, r.getLiftOff().size());
    assertEquals("lifted_off", r.getRestraint("S1").getState());
    assertEquals(0.0, r.getRestraint("S1").getSustained()[2], 1.0e-6);
    assertTrue(r.getOperatingDisplacement("C")[2] > 0.0);
  }

  /** B31.3 bend factors for an 8 in standard-wall long-radius elbow and their use at the bend. */
  @Test
  void bendFactorsFollowB313() {
    PipeStressModel m = new PipeStressModel("L");
    m.setPipe(OD, T, 0.0);
    double r = (OD - T) / 2.0;
    double h = T * 1.5 * OD / (r * r);
    assertEquals(1.65 / h, m.bendFlexibilityFactor(), 1.0e-9);
    assertEquals(0.9 / Math.pow(h, 2.0 / 3.0), m.bendSif(), 1.0e-9);
    assertEquals(2.32, m.bendSif(), 0.01);
    assertEquals(6.83, m.bendFlexibilityFactor(), 0.01);
    m.addNode("A", 0, 0, 0);
    m.addNode("B", 10, 0, 0);
    m.addNode("C", 10, 10, 0);
    m.addElement("A", "B");
    m.addElement("B", "C");
    m.addRestraint("A", "A", RestraintType.ANCHOR, 0.0);
    m.addRestraint("C", "C", RestraintType.ANCHOR, 0.0);
    m.setConditions(0.0, 20.0, 150.0);
    Results res = m.run();
    boolean bendFound = false;
    for (PipeStressModel.StressPoint p : res.getStresses()) {
      if (p.getNode().equals("B")) {
        bendFound = true;
        assertEquals(m.bendSif(), p.getSif(), 1.0e-9);
      }
    }
    assertTrue(bendFound);
    assertTrue(res.getMaxExpansionRatio() > 0.0);
    assertTrue(res.toJson().contains("\"status\""));
  }

  /** Pressure design and nozzle allowables decide the overall status. */
  @Test
  void pressureAndNozzleChecks() {
    PipeStressModel m = straight(6.0);
    PipeStressModel.Restraint nozzle = m.addRestraint("N", "N0", RestraintType.ANCHOR, 0.0);
    m.addRestraint("B", "N2", RestraintType.ANCHOR, 0.0);
    m.setConditions(100.0e5, 20.0, 60.0);
    m.setNozzleAllowables(nozzle, 1.0e9, 1.0e9);
    Results ok = m.run();
    assertEquals("pass", ok.getPressureDesignStatus());
    assertEquals("pass", ok.getRestraint("N").getNozzleStatus());
    assertEquals("no_allowable", ok.getRestraint("B").getNozzleStatus());
    double required = 100.0e5 * OD / (2.0 * (138.0e6 + 0.4 * 100.0e5));
    assertEquals(required, ok.getRequiredWallThicknessM(), 1.0e-9);
    m.setNozzleAllowables(nozzle, 1.0, 1.0);
    assertEquals("fail", m.run().getStatus());
    m.setConditions(400.0e5, 20.0, 60.0);
    m.setNozzleAllowables(nozzle, 1.0e9, 1.0e9);
    assertEquals("fail", m.run().getPressureDesignStatus());
  }

  /** A model without an anchor is rejected. */
  @Test
  void unanchoredModelIsRejected() {
    PipeStressModel m = straight(6.0);
    m.addRestraint("S", "N1", RestraintType.REST, 0.3);
    assertThrows(IllegalStateException.class, m::run);
    assertThrows(IllegalArgumentException.class, () -> m.setPipe(0.1, 0.06, 0.0));
  }

  /** Same code as the example in docs/process/mechanical_design/pipe_stress_model.md. */
  @Test
  void documentationExample() {
    PipeStressModel model = new PipeStressModel("20-PG-0101");
    model.setPipe(0.2731, 0.0151, 0.003);
    model.setMaterial(203.0e9, 0.3, 7850.0, 12.0e-6);
    model.setAllowables(138.0e6, 138.0e6, 1.0);
    model.setContents(60.0, 0.0);
    model.setConditions(95.0e5, 20.0, 140.0);
    model.setOccasionalLoads(0.1, 0.0, 0.7);
    model.addNode("N1", 0.0, 0.0, 0.0);
    model.addNode("N2", 6.0, 0.0, 0.0);
    model.addNode("N3", 12.0, 0.0, 0.0);
    model.addNode("N4", 12.0, 8.0, 0.0);
    model.addElement("N1", "N2");
    model.addElement("N2", "N3");
    model.addElement("N3", "N4");
    PipeStressModel.Restraint nozzle = model.addRestraint("A1", "N1", PipeStressModel.RestraintType.ANCHOR, 0.0);
    model.setNozzleAllowables(nozzle, 15000.0, 12000.0);
    model.addRestraint("A2", "N4", PipeStressModel.RestraintType.ANCHOR, 0.0);
    model.addRestraint("S1", "N2", PipeStressModel.RestraintType.REST, 0.3);
    model.addRestraint("S2", "N3", PipeStressModel.RestraintType.REST, 0.3);
    PipeStressModel.Results res = model.run();

    assertTrue(res.isConverged());
    assertEquals("pass", res.getPressureDesignStatus());
    assertTrue(res.getMaxSustainedRatio() > 0.0 && res.getMaxSustainedRatio() < 1.0);
    assertTrue(res.getMaxExpansionRatio() > 0.0 && res.getMaxOccasionalRatio() > 0.0);
    assertTrue(res.getRestraint("A1").getResultantForceN() > 0.0);
    assertTrue(res.toJson().contains("\"standard\""));
  }
}
