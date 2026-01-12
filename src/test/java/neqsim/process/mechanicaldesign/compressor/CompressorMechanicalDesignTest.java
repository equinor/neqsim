package neqsim.process.mechanicaldesign.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Tests for compressor mechanical design calculations. */
public class CompressorMechanicalDesignTest {

  @Test
  void testCalcDesign() {
    SystemInterface gas = new SystemSrkEos(300.0, 10.0);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule(2);
    ThermodynamicOperations ops = new ThermodynamicOperations(gas);
    ops.TPflash();

    Stream inlet = new Stream("inlet", gas);
    inlet.setFlowRate(10000.0, "kg/hr");

    Compressor comp = new Compressor("comp", inlet);
    comp.setOutletPressure(40.0);
    comp.setPolytropicEfficiency(0.75);
    comp.setSpeed(8000);

    ProcessSystem ps = new ProcessSystem();
    ps.add(inlet);
    ps.add(comp);
    ps.run();

    comp.initMechanicalDesign();
    comp.getMechanicalDesign().calcDesign();

    // Verify basic design outputs are calculated
    assertTrue(comp.getMechanicalDesign().getWeightTotal() > 0.0,
        "Total weight should be positive");
    assertTrue(comp.getMechanicalDesign().getNumberOfStages() >= 1, "Should have at least 1 stage");
    assertTrue(comp.getMechanicalDesign().getImpellerDiameter() > 0,
        "Impeller diameter should be positive");
    assertTrue(comp.getMechanicalDesign().getShaftDiameter() > 0,
        "Shaft diameter should be positive");
    assertTrue(comp.getMechanicalDesign().getDriverPower() > 0, "Driver power should be positive");
    assertTrue(comp.getMechanicalDesign().getDesignPressure() > 40.0,
        "Design pressure should be > discharge pressure");
  }

  @Test
  void testHighPressureBarrelCasing() {
    SystemInterface gas = new SystemSrkEos(300.0, 50.0);
    gas.addComponent("methane", 0.9);
    gas.addComponent("ethane", 0.1);
    gas.setMixingRule(2);
    ThermodynamicOperations ops = new ThermodynamicOperations(gas);
    ops.TPflash();

    Stream inlet = new Stream("inlet", gas);
    inlet.setFlowRate(5000.0, "kg/hr");

    Compressor comp = new Compressor("HP comp", inlet);
    comp.setOutletPressure(150.0); // High pressure -> barrel casing
    comp.setPolytropicEfficiency(0.78);
    comp.setSpeed(10000);

    ProcessSystem ps = new ProcessSystem();
    ps.add(inlet);
    ps.add(comp);
    ps.run();

    comp.getMechanicalDesign().calcDesign();

    // High pressure should result in barrel casing
    assertEquals(CompressorMechanicalDesign.CasingType.BARREL,
        comp.getMechanicalDesign().getCasingType(),
        "High pressure compressor should have barrel casing");
  }

  @Test
  void testMultiStageCompressor() {
    SystemInterface gas = new SystemSrkEos(300.0, 5.0);
    gas.addComponent("nitrogen", 1.0);
    gas.setMixingRule(2);
    ThermodynamicOperations ops = new ThermodynamicOperations(gas);
    ops.TPflash();

    Stream inlet = new Stream("inlet", gas);
    inlet.setFlowRate(20000.0, "kg/hr");

    Compressor comp = new Compressor("multi-stage", inlet);
    comp.setOutletPressure(80.0); // High ratio -> multiple stages
    comp.setPolytropicEfficiency(0.76);
    comp.setSpeed(6000);

    ProcessSystem ps = new ProcessSystem();
    ps.add(inlet);
    ps.add(comp);
    ps.run();

    comp.getMechanicalDesign().calcDesign();

    // High pressure ratio should require multiple stages
    assertTrue(comp.getMechanicalDesign().getNumberOfStages() > 1,
        "High pressure ratio should require multiple stages");
    assertTrue(comp.getMechanicalDesign().getHeadPerStage() > 0,
        "Head per stage should be positive");
    assertTrue(comp.getMechanicalDesign().getHeadPerStage() <= 30.0,
        "Head per stage should be <= 30 kJ/kg");
  }

  @Test
  void testDriverSizingMargins() {
    SystemInterface gas = new SystemSrkEos(300.0, 10.0);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule(2);

    Stream inlet = new Stream("inlet", gas);
    inlet.setFlowRate(500.0, "kg/hr"); // Small compressor

    Compressor comp = new Compressor("small comp", inlet);
    comp.setOutletPressure(20.0);
    comp.setPolytropicEfficiency(0.72);
    comp.setSpeed(12000);

    ProcessSystem ps = new ProcessSystem();
    ps.add(inlet);
    ps.add(comp);
    ps.run();

    comp.getMechanicalDesign().calcDesign();

    double shaftPower = comp.getPower("kW");
    double driverPower = comp.getMechanicalDesign().getDriverPower();

    // Small compressors should have 25% margin
    assertTrue(driverPower >= shaftPower * 1.20,
        "Small compressor should have at least 20% driver margin");
  }
}
