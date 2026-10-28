package neqsim.process.equipment.ejector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests of the fixed-geometry (off-design) mode of the {@link Ejector}. The reference numbers of the Oseberg design
 * case (4th stage gas at 0.6 barg, motive about 67 bara, discharge 2.1 bara) were calculated with the Python
 * EjectorModel of the Oseberg study, which implements the same equations.
 *
 * @author esol
 * @version 1.0
 */
class EjectorFixedGeometryTest {
  private static final double THROAT_AREA = 1.69e-4;
  private static final double DESIGN_AREA_RATIO = 31.7;

  /**
   * Creates an SRK fluid from a composition.
   *
   * @param names component names
   * @param moleFractions mole fractions
   * @param tempC temperature in C
   * @param pressureBara pressure in bara
   * @return fluid
   */
  private static SystemInterface fluid(String[] names, double[] moleFractions, double tempC, double pressureBara) {
    SystemInterface f = new SystemSrkEos(273.15 + tempC, pressureBara);
    for (int i = 0; i < names.length; i++) {
      f.addComponent(names[i], moleFractions[i]);
    }
    f.createDatabase(true);
    f.setMixingRule(2);
    return f;
  }

  /**
   * Creates the Oseberg-like ejector: motive at 67.5 bara and 85 C, suction at 1.51 bara and 40 C.
   *
   * @param areaRatio mixing to throat area ratio
   * @param dischargeBara discharge pressure in bara
   * @return ejector in fixed-geometry mode (not yet run)
   */
  private static Ejector oseberg(double areaRatio, double dischargeBara) {
    SystemInterface motive = fluid(new String[] {"methane", "ethane", "propane", "n-butane", "n-pentane"},
        new double[] {0.80, 0.10, 0.06, 0.03, 0.01}, 85.0, 67.5);
    SystemInterface suction = fluid(new String[] {"methane", "ethane", "propane", "i-butane", "n-butane", "n-pentane"},
        new double[] {0.20, 0.25, 0.30, 0.08, 0.12, 0.05}, 40.0, 1.51);
    Stream m = new Stream("motive", motive);
    m.setFlowRate(1.0, "kg/sec");
    Stream s = new Stream("suction", suction);
    s.setFlowRate(1.0, "kg/sec");
    Ejector e = new Ejector("ejector", m, s);
    e.setDischargePressure(dischargeBara);
    e.setFixedGeometryAreaRatio(THROAT_AREA, areaRatio);
    return e;
  }

  @BeforeAll
  static void warmUp() {
    assertTrue(THROAT_AREA > 0.0);
  }

  @Test
  void designCaseMatchesPythonReferenceWithinThreePercent() {
    Ejector e = oseberg(DESIGN_AREA_RATIO, 2.1);
    e.run();
    EjectorOperatingPoint op = e.getOperatingPoint();
    assertEquals(EjectorOperatingPoint.Regime.CRITICAL, op.getRegime());
    assertFalse(e.isInBreakdown());
    assertEquals(1.7667, op.getMotiveFlow(), 0.03 * 1.7667);
    assertEquals(1.2355, op.getSuctionFlow(), 0.03 * 1.2355);
    assertEquals(0.6993, op.getEntrainmentRatio(), 0.03 * 0.6993);
    assertEquals(2.489, op.getCriticalBackPressure(), 0.03 * 2.489);
    assertEquals(25.915, op.getMixedMolarMass(), 0.03 * 25.915);
    assertEquals(0.185, op.getBreakdownMargin(), 0.03);
    assertEquals(op.getEntrainmentRatio(), e.getEntrainmentRatio(), 1.0e-12);
    assertTrue(op.isMotiveChoked());
    assertEquals(2.1, e.getOutStream().getPressure("bara"), 1.0e-4);
    double expectedTotal = op.getMotiveFlow() + op.getSuctionFlow();
    assertEquals(expectedTotal, e.getOutStream().getFlowRate("kg/sec"), 1.0e-6 * expectedTotal);
    assertEquals(0.0, e.getMassBalance("kg/sec"), 1.0e-6);
  }

  @Test
  void areaRatioSensitivityFollowsPythonModel() {
    Ejector small = oseberg(20.0, 2.1);
    small.run();
    Ejector design = oseberg(DESIGN_AREA_RATIO, 2.1);
    design.run();
    Ejector large = oseberg(45.0, 2.1);
    large.run();
    assertEquals(0.3219, small.getOperatingPoint().getEntrainmentRatio(), 0.03 * 0.3219);
    assertEquals(3.355, small.getOperatingPoint().getCriticalBackPressure(), 0.03 * 3.355);
    assertEquals(1.137, large.getOperatingPoint().getEntrainmentRatio(), 0.03 * 1.137);
    assertEquals(EjectorOperatingPoint.Regime.SUBCRITICAL, large.getOperatingPoint().getRegime());
    assertTrue(large.getOperatingPoint().isBackPressureAboveCritical());
    assertFalse(large.getOperatingPoint().isInBreakdown());
    // larger area ratio: more entrainment but lower critical back pressure, same motive flow
    assertTrue(small.getEntrainmentCapacity() < design.getEntrainmentCapacity());
    assertTrue(design.getEntrainmentCapacity() < large.getEntrainmentCapacity());
    assertTrue(small.getCriticalBackPressure() > design.getCriticalBackPressure());
    assertTrue(design.getCriticalBackPressure() > large.getCriticalBackPressure());
    assertEquals(small.getMotiveFlowCapacity(), large.getMotiveFlowCapacity(), 1.0e-9);
  }

  @Test
  void backPressureAboveCriticalReducesEntrainmentAndBreaksDown() {
    Ejector sub = oseberg(DESIGN_AREA_RATIO, 3.5);
    sub.run();
    assertEquals(EjectorOperatingPoint.Regime.SUBCRITICAL, sub.getOperatingPoint().getRegime());
    assertEquals(0.3716, sub.getOperatingPoint().getEntrainmentRatio(), 0.05 * 0.3716);
    assertTrue(sub.getBreakdownMargin() < 0.0);

    Ejector broken = oseberg(DESIGN_AREA_RATIO, 8.0);
    broken.run();
    assertTrue(broken.isInBreakdown());
    assertEquals(0.0, broken.getEntrainmentRatio(), 1.0e-12);
    assertEquals(0.0, broken.getEntrainmentCapacity(), 1.0e-12);
    assertTrue(broken.getBreakdownMargin() < -0.5);
    assertFalse(broken.getOperatingPoint().getWarnings().isEmpty());
    assertEquals(8.0, broken.getOutStream().getPressure("bara"), 1.0e-4);
  }

  @Test
  void performanceCurveHasVariableEntrainmentRatio() {
    Ejector e = oseberg(DESIGN_AREA_RATIO, 2.1);
    e.run();
    List<double[]> curve = e.generatePerformanceCurve(1.8, 6.0, 6);
    assertEquals(6, curve.size());
    double first = curve.get(0)[1];
    double last = curve.get(curve.size() - 1)[1];
    assertTrue(first > 0.6, "critical entrainment at low back pressure");
    assertEquals(0.0, last, 1.0e-9);
    for (int i = 1; i < curve.size(); i++) {
      assertTrue(curve.get(i)[1] <= curve.get(i - 1)[1] + 1.0e-9, "entrainment must not rise with back pressure");
    }
    // the curve restores the original discharge pressure
    assertEquals(2.1, e.getOutStream().getPressure("bara"), 1.0e-4);

    List<EjectorOperatingPoint> points = e.generateFixedGeometryCurve(1.8, 6.0, 6);
    assertEquals(EjectorOperatingPoint.Regime.CRITICAL, points.get(0).getRegime());
    assertEquals(EjectorOperatingPoint.Regime.BREAKDOWN, points.get(5).getRegime());
    assertEquals(points.get(0).getEntrainmentRatio(), first, 1.0e-6);
  }

  @Test
  void suctionPressureCurveGivesCapacityVersusSuctionPressure() {
    Ejector e = oseberg(DESIGN_AREA_RATIO, 2.1);
    List<EjectorOperatingPoint> pts = e.generateSuctionPressureCurve(1.2, 1.8, 4);
    assertEquals(4, pts.size());
    assertTrue(pts.get(3).getSuctionFlow() > pts.get(0).getSuctionFlow(), "mass capacity rises with suction pressure");
    assertEquals(1.2, pts.get(0).getSuctionPressure(), 1.0e-9);
  }

  @Test
  void operatingWindowReportAndSupplyLimit() {
    Ejector e = oseberg(DESIGN_AREA_RATIO, 2.1);
    e.setMaxDischargePressureLimit(2.4);
    e.run();
    Map<String, Object> r = e.getOperatingWindowReport();
    for (String key : new String[] {"motiveFlow_kg_s", "suctionFlow_kg_s", "entrainmentRatio", "compressionRatio",
        "criticalBackPressure_bara", "breakdownMargin", "suctionSupplyUtilisation", "dischargePressureMargin"}) {
      assertTrue(r.containsKey(key), key);
    }
    assertEquals(2.4 / 2.1 - 1.0, (Double) r.get("dischargePressureMargin"), 1.0e-9);
    assertTrue(e.getOperatingWindowReportJson().contains("breakdownMargin"));
    // 1 kg/s suction supply is below the 1.2 kg/s capacity: starved
    assertTrue(e.isSuctionStarved());
    double capacity = e.getEntrainmentCapacity();
    e.setLimitEntrainmentToSuctionSupply(true);
    e.run();
    assertEquals(1.0, e.getOperatingPoint().getSuctionFlow(), 1.0e-9);
    assertTrue(e.getEntrainmentCapacity() < capacity);
  }

  @Test
  void autoSizeFixedGeometryDeliversDesignFlowWithBackPressureMargin() {
    Ejector e = oseberg(DESIGN_AREA_RATIO, 2.1);
    e.autoSizeFixedGeometry(1.0, 0.10);
    assertTrue(e.isFixedGeometryMode());
    assertEquals(1.1426e-4, e.getFixedThroatArea(), 0.05 * 1.1426e-4);
    assertEquals(35.9, e.getFixedMixingArea() / e.getFixedThroatArea(), 0.05 * 35.9);
    e.run();
    assertEquals(1.0, e.getEntrainmentCapacity(), 0.02);
    assertTrue(e.getCriticalBackPressure() >= 2.1 * 1.10 * 0.97);
    assertTrue(e.getBreakdownMargin() > 0.08);
  }

  @Test
  void defaultModeIsUnchangedAndAreaSetterIsNoOp() {
    Ejector e = oseberg(DESIGN_AREA_RATIO, 2.1);
    e.clearFixedGeometry();
    e.setThroatArea(5.0);
    assertFalse(e.isFixedGeometryMode());
    e.run();
    assertTrue(e.getOperatingPoint() == null);
    assertEquals(2.0, e.getOutStream().getFlowRate("kg/sec"), 1.0e-6);
  }

  @Test
  void invalidGeometryIsRejected() {
    Ejector e = oseberg(DESIGN_AREA_RATIO, 2.1);
    assertThrows(IllegalArgumentException.class, () -> e.setFixedGeometry(1.0e-4, 0.5e-4));
    assertThrows(IllegalArgumentException.class, () -> e.setFixedGeometry(-1.0, 1.0));
    e.clearFixedGeometry();
    assertThrows(IllegalStateException.class, () -> e.generateFixedGeometryCurve(1.0, 2.0, 3));
  }
}
