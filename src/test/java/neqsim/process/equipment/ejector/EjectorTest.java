package neqsim.process.equipment.ejector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    assertTrue(design.getSuctionInletVelocity() >= 25.0);
    assertTrue(design.getSuctionInletVelocity() <= 120.0);
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

    double expectedConnectedVolume = design.getSuctionInletArea() * 1.0
        + design.getDiffuserOutletArea() * 2.0;
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
}
