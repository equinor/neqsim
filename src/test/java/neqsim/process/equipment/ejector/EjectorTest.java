package neqsim.process.equipment.ejector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.ejector.EjectorMechanicalDesign;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

class EjectorTest {
  @Test
  void boostLowPressureGas() {
    SystemInterface motiveFluid = new SystemSrkEos(273.15 + 20.0, 10.0);
    motiveFluid.addComponent("methane", 1.0);
    motiveFluid.createDatabase(true);
    motiveFluid.setMixingRule(2);

    Stream motiveStream = new Stream("motive", motiveFluid);
    motiveStream.setPressure(10.0, "bara");
    motiveStream.setTemperature(20.0, "C");
    motiveStream.setFlowRate(0.5, "kg/sec");
    motiveStream.run();

    SystemInterface suctionFluid = new SystemSrkEos(273.15 + 20.0, 1.0);
    suctionFluid.addComponent("methane", 1.0);
    suctionFluid.createDatabase(true);
    suctionFluid.setMixingRule(2);

    Stream suctionStream = new Stream("suction", suctionFluid);
    suctionStream.setPressure(1.0, "bara");
    suctionStream.setTemperature(20.0, "C");
    suctionStream.setFlowRate(0.15, "kg/sec");
    suctionStream.run();

    Ejector ejector = new Ejector("gas booster", motiveStream, suctionStream);
    ejector.setDischargePressure(3.0);
    ejector.run();

    StreamInterface outStream = ejector.getOutStream();
    outStream.run();

    assertEquals(3.0, outStream.getPressure("bara"), 1.0e-4);
    assertEquals(motiveStream.getFlowRate("kg/sec") + suctionStream.getFlowRate("kg/sec"),
        outStream.getFlowRate("kg/sec"), 1.0e-6);

    EjectorMechanicalDesign design = ejector.getMechanicalDesign();
    double suctionPressure = suctionStream.getPressure("bara");
    assertTrue(design.getMixingPressure() <= suctionPressure + 1.0e-8);
    assertTrue(design.getMixingPressure() > 0.0);
    assertTrue(design.getMotiveNozzleThroatArea() > 0.0);
    assertTrue(design.getMixingChamberArea() > 0.0);
    assertTrue(design.getDiffuserOutletArea() > 0.0);
    assertEquals(design.getEntrainmentRatio(),
        suctionStream.getFlowRate("kg/sec") / motiveStream.getFlowRate("kg/sec"), 1.0e-8);
    assertTrue(design.getSuctionInletVelocity() > 0.0);
    assertTrue(design.getSuctionInletVelocity() < 500.0,
        "suction velocity should be below 500 m/s for practical ejector design");
    assertTrue(design.getDiffuserOutletVelocity() >= 10.0);
    assertTrue(design.getDiffuserOutletVelocity() <= 60.0);
    assertTrue(design.getBodyVolume() > 0.0);
    assertTrue(design.getConnectedPipingVolume() > 0.0);
    assertEquals(design.getTotalVolume(),
        design.getBodyVolume() + design.getConnectedPipingVolume(), 1.0e-12);
    assertTrue(design.getSuctionConnectionLength() > 0.0);
    assertTrue(design.getDischargeConnectionLength() > 0.0);
  }

  @Test
  void manualDesignOverridesAreHonored() {
    SystemInterface motiveFluid = new SystemSrkEos(273.15 + 20.0, 10.0);
    motiveFluid.addComponent("methane", 1.0);
    motiveFluid.createDatabase(true);
    motiveFluid.setMixingRule(2);

    Stream motiveStream = new Stream("motive", motiveFluid);
    motiveStream.setPressure(10.0, "bara");
    motiveStream.setTemperature(20.0, "C");
    motiveStream.setFlowRate(0.5, "kg/sec");
    motiveStream.run();

    SystemInterface suctionFluid = new SystemSrkEos(273.15 + 20.0, 1.0);
    suctionFluid.addComponent("methane", 1.0);
    suctionFluid.createDatabase(true);
    suctionFluid.setMixingRule(2);

    Stream suctionStream = new Stream("suction", suctionFluid);
    suctionStream.setPressure(1.0, "bara");
    suctionStream.setTemperature(20.0, "C");
    suctionStream.setFlowRate(0.15, "kg/sec");
    suctionStream.run();

    Ejector ejector = new Ejector("manual gas booster", motiveStream, suctionStream);
    ejector.setMixingPressure(0.8);
    ejector.setDischargePressure(3.0);
    ejector.setDesignSuctionVelocity(75.0);
    ejector.setDesignDiffuserOutletVelocity(25.0);
    ejector.setSuctionConnectionLength(1.0);
    ejector.setDischargeConnectionLength(2.0);
    ejector.run();

    EjectorMechanicalDesign design = ejector.getMechanicalDesign();
    assertEquals(0.8, design.getMixingPressure(), 1.0e-8);
    assertEquals(75.0, design.getSuctionInletVelocity(), 1.0e-6);
    assertEquals(25.0, design.getDiffuserOutletVelocity(), 1.0e-6);
    assertEquals(1.0, design.getSuctionConnectionLength(), 1.0e-9);
    assertEquals(2.0, design.getDischargeConnectionLength(), 1.0e-9);

    double expectedConnectedVolume =
        design.getSuctionInletArea() * 1.0 + design.getDiffuserOutletArea() * 2.0;
    assertEquals(expectedConnectedVolume, design.getConnectedPipingVolume(), 1.0e-9);
  }

  @Test
  void mixingPressureCannotExceedSuctionPressure() {
    SystemInterface motiveFluid = new SystemSrkEos(273.15 + 30.0, 12.0);
    motiveFluid.addComponent("methane", 1.0);
    motiveFluid.createDatabase(true);
    motiveFluid.setMixingRule(2);

    Stream motiveStream = new Stream("motive", motiveFluid);
    motiveStream.setPressure(12.0, "bara");
    motiveStream.setTemperature(30.0, "C");
    motiveStream.setFlowRate(0.4, "kg/sec");
    motiveStream.run();

    SystemInterface suctionFluid = new SystemSrkEos(273.15 + 20.0, 2.0);
    suctionFluid.addComponent("methane", 1.0);
    suctionFluid.createDatabase(true);
    suctionFluid.setMixingRule(2);

    Stream suctionStream = new Stream("suction", suctionFluid);
    suctionStream.setPressure(2.0, "bara");
    suctionStream.setTemperature(20.0, "C");
    suctionStream.setFlowRate(0.1, "kg/sec");
    suctionStream.run();

    Ejector ejector = new Ejector("mixing pressure clamp", motiveStream, suctionStream);
    double suctionPressure = suctionStream.getPressure("bara");
    ejector.setMixingPressure(suctionPressure + 3.0);
    ejector.setDischargePressure(4.0);
    ejector.run();

    EjectorMechanicalDesign design = ejector.getMechanicalDesign();
    assertEquals(suctionPressure, design.getMixingPressure(), 1.0e-8,
        "mixing pressure should be limited to suction pressure");
  }

  @Test
  void compressionAndExpansionRatiosAreConsistent() {
    SystemInterface motiveFluid = new SystemSrkEos(273.15 + 20.0, 10.0);
    motiveFluid.addComponent("methane", 1.0);
    motiveFluid.createDatabase(true);
    motiveFluid.setMixingRule(2);

    Stream motiveStream = new Stream("motive", motiveFluid);
    motiveStream.setPressure(10.0, "bara");
    motiveStream.setTemperature(20.0, "C");
    motiveStream.setFlowRate(0.5, "kg/sec");
    motiveStream.run();

    SystemInterface suctionFluid = new SystemSrkEos(273.15 + 20.0, 1.0);
    suctionFluid.addComponent("methane", 1.0);
    suctionFluid.createDatabase(true);
    suctionFluid.setMixingRule(2);

    Stream suctionStream = new Stream("suction", suctionFluid);
    suctionStream.setPressure(1.0, "bara");
    suctionStream.setTemperature(20.0, "C");
    suctionStream.setFlowRate(0.15, "kg/sec");
    suctionStream.run();

    Ejector ejector = new Ejector("ratio check", motiveStream, suctionStream);
    ejector.setDischargePressure(3.0);
    ejector.run();

    double cr = ejector.getCompressionRatio();
    double er = ejector.getExpansionRatio();

    // Compression ratio = discharge / suction pressure
    assertEquals(3.0 / 1.0, cr, 0.01, "compression ratio should be discharge/suction");
    // Expansion ratio = motive / discharge pressure
    assertEquals(10.0 / 3.0, er, 0.01, "expansion ratio should be motive/discharge");

    assertTrue(cr > 1.0, "compression ratio must be > 1");
    assertTrue(er > 1.0, "expansion ratio must be > 1");
  }

  @Test
  void areaRatioIsPositive() {
    SystemInterface motiveFluid = new SystemSrkEos(273.15 + 20.0, 10.0);
    motiveFluid.addComponent("methane", 1.0);
    motiveFluid.createDatabase(true);
    motiveFluid.setMixingRule(2);

    Stream motiveStream = new Stream("motive", motiveFluid);
    motiveStream.setPressure(10.0, "bara");
    motiveStream.setTemperature(20.0, "C");
    motiveStream.setFlowRate(0.5, "kg/sec");
    motiveStream.run();

    SystemInterface suctionFluid = new SystemSrkEos(273.15 + 20.0, 1.0);
    suctionFluid.addComponent("methane", 1.0);
    suctionFluid.createDatabase(true);
    suctionFluid.setMixingRule(2);

    Stream suctionStream = new Stream("suction", suctionFluid);
    suctionStream.setPressure(1.0, "bara");
    suctionStream.setTemperature(20.0, "C");
    suctionStream.setFlowRate(0.15, "kg/sec");
    suctionStream.run();

    Ejector ejector = new Ejector("area ratio check", motiveStream, suctionStream);
    ejector.setDischargePressure(3.0);
    ejector.run();

    double areaRatio = ejector.getAreaRatio();
    assertTrue(areaRatio > 1.0, "area ratio (mixing/nozzle throat) should exceed 1");
  }

  @Test
  void criticalBackPressureExceedsDischargePressure() {
    SystemInterface motiveFluid = new SystemSrkEos(273.15 + 20.0, 10.0);
    motiveFluid.addComponent("methane", 1.0);
    motiveFluid.createDatabase(true);
    motiveFluid.setMixingRule(2);

    Stream motiveStream = new Stream("motive", motiveFluid);
    motiveStream.setPressure(10.0, "bara");
    motiveStream.setTemperature(20.0, "C");
    motiveStream.setFlowRate(0.5, "kg/sec");
    motiveStream.run();

    SystemInterface suctionFluid = new SystemSrkEos(273.15 + 20.0, 1.0);
    suctionFluid.addComponent("methane", 1.0);
    suctionFluid.createDatabase(true);
    suctionFluid.setMixingRule(2);

    Stream suctionStream = new Stream("suction", suctionFluid);
    suctionStream.setPressure(1.0, "bara");
    suctionStream.setTemperature(20.0, "C");
    suctionStream.setFlowRate(0.15, "kg/sec");
    suctionStream.run();

    Ejector ejector = new Ejector("CBP test", motiveStream, suctionStream);
    ejector.setDischargePressure(3.0);
    ejector.run();

    double cbp = ejector.getCriticalBackPressure();
    assertTrue(cbp > 3.0,
        "critical back pressure should exceed operating discharge pressure for stable operation");
    assertFalse(ejector.isInBreakdown(),
        "ejector should not be in breakdown at normal operating conditions");
  }

  @Test
  void machNumbersAreReasonable() {
    SystemInterface motiveFluid = new SystemSrkEos(273.15 + 20.0, 10.0);
    motiveFluid.addComponent("methane", 1.0);
    motiveFluid.createDatabase(true);
    motiveFluid.setMixingRule(2);

    Stream motiveStream = new Stream("motive", motiveFluid);
    motiveStream.setPressure(10.0, "bara");
    motiveStream.setTemperature(20.0, "C");
    motiveStream.setFlowRate(0.5, "kg/sec");
    motiveStream.run();

    SystemInterface suctionFluid = new SystemSrkEos(273.15 + 20.0, 1.0);
    suctionFluid.addComponent("methane", 1.0);
    suctionFluid.createDatabase(true);
    suctionFluid.setMixingRule(2);

    Stream suctionStream = new Stream("suction", suctionFluid);
    suctionStream.setPressure(1.0, "bara");
    suctionStream.setTemperature(20.0, "C");
    suctionStream.setFlowRate(0.15, "kg/sec");
    suctionStream.run();

    Ejector ejector = new Ejector("mach test", motiveStream, suctionStream);
    ejector.setDischargePressure(3.0);
    ejector.run();

    double motMach = ejector.getMotiveNozzleMach();
    double sucMach = ejector.getSuctionMach();
    double mixMach = ejector.getMixingMach();

    assertTrue(motMach > 0.0, "motive nozzle Mach should be positive");
    assertTrue(motMach < 10.0, "motive nozzle Mach should be physically reasonable");
    assertTrue(sucMach >= 0.0, "suction Mach should be non-negative");
    assertTrue(mixMach >= 0.0, "mixing Mach should be non-negative");
    assertTrue(mixMach < motMach, "mixing section Mach should be less than motive nozzle Mach");
  }

  @Test
  void suctionNozzleAndMixingEfficienciesAreConfigurable() {
    SystemInterface motiveFluid = new SystemSrkEos(273.15 + 20.0, 10.0);
    motiveFluid.addComponent("methane", 1.0);
    motiveFluid.createDatabase(true);
    motiveFluid.setMixingRule(2);

    Stream motiveStream = new Stream("motive", motiveFluid);
    motiveStream.setPressure(10.0, "bara");
    motiveStream.setTemperature(20.0, "C");
    motiveStream.setFlowRate(0.5, "kg/sec");
    motiveStream.run();

    SystemInterface suctionFluid = new SystemSrkEos(273.15 + 20.0, 1.0);
    suctionFluid.addComponent("methane", 1.0);
    suctionFluid.createDatabase(true);
    suctionFluid.setMixingRule(2);

    Stream suctionStream = new Stream("suction", suctionFluid);
    suctionStream.setPressure(1.0, "bara");
    suctionStream.setTemperature(20.0, "C");
    suctionStream.setFlowRate(0.15, "kg/sec");
    suctionStream.run();

    Ejector ejector = new Ejector("efficiency test", motiveStream, suctionStream);
    ejector.setDischargePressure(3.0);

    ejector.setSuctionNozzleEfficiency(0.85);
    ejector.setMixingEfficiency(0.80);

    assertEquals(0.85, ejector.getSuctionNozzleEfficiency(), 1.0e-10);
    assertEquals(0.80, ejector.getMixingEfficiency(), 1.0e-10);

    ejector.run();

    // After run, results should still be valid
    assertTrue(ejector.getCompressionRatio() > 1.0);
    assertTrue(ejector.getExpansionRatio() > 1.0);
  }

  @Test
  void performanceCurveGeneration() {
    SystemInterface motiveFluid = new SystemSrkEos(273.15 + 20.0, 10.0);
    motiveFluid.addComponent("methane", 1.0);
    motiveFluid.createDatabase(true);
    motiveFluid.setMixingRule(2);

    Stream motiveStream = new Stream("motive", motiveFluid);
    motiveStream.setPressure(10.0, "bara");
    motiveStream.setTemperature(20.0, "C");
    motiveStream.setFlowRate(0.5, "kg/sec");
    motiveStream.run();

    SystemInterface suctionFluid = new SystemSrkEos(273.15 + 20.0, 1.0);
    suctionFluid.addComponent("methane", 1.0);
    suctionFluid.createDatabase(true);
    suctionFluid.setMixingRule(2);

    Stream suctionStream = new Stream("suction", suctionFluid);
    suctionStream.setPressure(1.0, "bara");
    suctionStream.setTemperature(20.0, "C");
    suctionStream.setFlowRate(0.15, "kg/sec");
    suctionStream.run();

    Ejector ejector = new Ejector("curve test", motiveStream, suctionStream);
    ejector.setDischargePressure(3.0);
    ejector.run();

    List<double[]> curve = ejector.generatePerformanceCurve(1.5, 5.0, 5);
    assertEquals(5, curve.size(), "should return requested number of points");

    for (double[] point : curve) {
      assertEquals(3, point.length, "each point should have [dischP, ER, CR]");
      assertTrue(point[0] > 0.0, "discharge pressure should be positive");
      assertTrue(point[2] > 0.0, "compression ratio should be positive");
    }

    // Entrainment ratio should decrease as discharge pressure increases
    double firstER = curve.get(0)[1];
    double lastER = curve.get(curve.size() - 1)[1];
    assertTrue(firstER >= lastER,
        "entrainment ratio should generally decrease with higher discharge pressure");
  }
}
