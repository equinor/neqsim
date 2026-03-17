package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Validate that BWRS EoS produces reasonable compressor results (positive efficiency, outlet
 * temperature comparable to GERG-2008).
 */
public class BWRSCompressorTest {

  @Test
  void testPureMethaneCompressorBWRS() {
    // --- BWRS fluid (pure methane) ---
    SystemInterface bwrsFluid = new SystemBWRSEos(298.15, 10.0);
    bwrsFluid.addComponent("methane", 1.0);
    bwrsFluid.createDatabase(true);
    bwrsFluid.setMixingRule(2);
    bwrsFluid.setNumberOfPhases(1);
    bwrsFluid.setMaxNumberOfPhases(1);
    bwrsFluid.setForcePhaseTypes(true);
    bwrsFluid.setPhaseType(0, "GAS");

    neqsim.process.equipment.stream.Stream bwrsStream =
        new neqsim.process.equipment.stream.Stream("bwrs gas", bwrsFluid);
    bwrsStream.setFlowRate(10.0, "MSm3/day");
    bwrsStream.run();

    neqsim.process.equipment.compressor.Compressor bwrsComp =
        new neqsim.process.equipment.compressor.Compressor("bwrs compressor", bwrsStream);
    bwrsComp.setOutletPressure(30.0, "bara");
    bwrsComp.setPolytropicEfficiency(0.75);
    bwrsComp.run();

    double bwrsTout = bwrsComp.getOutletStream().getTemperature("C");
    double bwrsPower = bwrsComp.getPower("MW");
    double bwrsHead = bwrsComp.getPolytropicHead("kJ/kg");

    // --- GERG-2008 fluid (reference for pure methane) ---
    SystemInterface gergFluid = new SystemGERG2008Eos(298.15, 10.0);
    gergFluid.addComponent("methane", 1.0);
    gergFluid.createDatabase(true);
    gergFluid.setMixingRule(2);
    gergFluid.setNumberOfPhases(1);
    gergFluid.setMaxNumberOfPhases(1);
    gergFluid.setForcePhaseTypes(true);
    gergFluid.setPhaseType(0, "GAS");

    neqsim.process.equipment.stream.Stream gergStream =
        new neqsim.process.equipment.stream.Stream("gerg gas", gergFluid);
    gergStream.setFlowRate(10.0, "MSm3/day");
    gergStream.run();

    neqsim.process.equipment.compressor.Compressor gergComp =
        new neqsim.process.equipment.compressor.Compressor("gerg compressor", gergStream);
    gergComp.setOutletPressure(30.0, "bara");
    gergComp.setPolytropicEfficiency(0.75);
    gergComp.run();

    double gergTout = gergComp.getOutletStream().getTemperature("C");
    double gergPower = gergComp.getPower("MW");
    double gergHead = gergComp.getPolytropicHead("kJ/kg");

    System.out.println("=== Pure Methane Compressor (10->30 bar, eta=0.75) ===");
    System.out.println(
        "BWRS  Tout=" + bwrsTout + " C, Power=" + bwrsPower + " MW, Head=" + bwrsHead + " kJ/kg");
    System.out.println(
        "GERG  Tout=" + gergTout + " C, Power=" + gergPower + " MW, Head=" + gergHead + " kJ/kg");

    // BWRS must give positive outlet temperature rise (compression heats gas)
    assertTrue(bwrsTout > 25.0 + 20.0, "BWRS outlet temp too low: " + bwrsTout + " C (inlet 25 C)");
    assertTrue(bwrsTout < 25.0 + 200.0, "BWRS outlet temp too high: " + bwrsTout + " C");

    // Power must be positive
    assertTrue(bwrsPower > 0.0, "BWRS power must be positive: " + bwrsPower + " MW");

    // Polytropic head must be positive
    assertTrue(bwrsHead > 0.0, "BWRS polytropic head must be positive: " + bwrsHead + " kJ/kg");

    // Compare with GERG within 15% tolerance for pure methane
    assertEquals(gergTout, bwrsTout, gergTout * 0.15,
        "BWRS outlet temp deviates too much from GERG");
    assertEquals(gergPower, bwrsPower, gergPower * 0.15, "BWRS power deviates too much from GERG");
  }

  @Test
  void testNaturalGasCompressorBWRS() {
    // --- BWRS fluid (methane-ethane only - the 2 components in mbwr32param database) ---
    SystemInterface bwrsFluid = new SystemBWRSEos(298.15, 10.0);
    bwrsFluid.addComponent("methane", 0.90);
    bwrsFluid.addComponent("ethane", 0.10);
    bwrsFluid.createDatabase(true);
    bwrsFluid.setMixingRule(2);
    bwrsFluid.setNumberOfPhases(1);
    bwrsFluid.setMaxNumberOfPhases(1);
    bwrsFluid.setForcePhaseTypes(true);
    bwrsFluid.setPhaseType(0, "GAS");

    neqsim.process.equipment.stream.Stream bwrsStream =
        new neqsim.process.equipment.stream.Stream("bwrs gas", bwrsFluid);
    bwrsStream.setFlowRate(10.0, "MSm3/day");
    bwrsStream.run();

    neqsim.process.equipment.compressor.Compressor bwrsComp =
        new neqsim.process.equipment.compressor.Compressor("bwrs compressor", bwrsStream);
    bwrsComp.setOutletPressure(30.0, "bara");
    bwrsComp.setPolytropicEfficiency(0.75);
    bwrsComp.run();

    double bwrsTout = bwrsComp.getOutletStream().getTemperature("C");
    double bwrsPower = bwrsComp.getPower("MW");
    double bwrsHead = bwrsComp.getPolytropicHead("kJ/kg");

    // --- SRK fluid (reference) ---
    SystemInterface srkFluid = new SystemSrkEos(298.15, 10.0);
    srkFluid.addComponent("methane", 0.90);
    srkFluid.addComponent("ethane", 0.10);
    srkFluid.setMixingRule("classic");
    srkFluid.setNumberOfPhases(1);
    srkFluid.setMaxNumberOfPhases(1);
    srkFluid.setForcePhaseTypes(true);
    srkFluid.setPhaseType(0, "GAS");

    neqsim.process.equipment.stream.Stream srkStream =
        new neqsim.process.equipment.stream.Stream("srk gas", srkFluid);
    srkStream.setFlowRate(10.0, "MSm3/day");
    srkStream.run();

    neqsim.process.equipment.compressor.Compressor srkComp =
        new neqsim.process.equipment.compressor.Compressor("srk compressor", srkStream);
    srkComp.setOutletPressure(30.0, "bara");
    srkComp.setPolytropicEfficiency(0.75);
    srkComp.run();

    double srkTout = srkComp.getOutletStream().getTemperature("C");
    double srkPower = srkComp.getPower("MW");
    double srkHead = srkComp.getPolytropicHead("kJ/kg");

    System.out.println("=== Natural Gas Compressor (10->30 bar, eta=0.75) ===");
    System.out.println(
        "BWRS  Tout=" + bwrsTout + " C, Power=" + bwrsPower + " MW, Head=" + bwrsHead + " kJ/kg");
    System.out.println(
        "SRK   Tout=" + srkTout + " C, Power=" + srkPower + " MW, Head=" + srkHead + " kJ/kg");

    // BWRS must give positive outlet temperature rise (compression heats gas)
    assertTrue(bwrsTout > 25.0 + 20.0, "BWRS outlet temp too low: " + bwrsTout + " C (inlet 25 C)");
    assertTrue(bwrsTout < 25.0 + 200.0, "BWRS outlet temp too high: " + bwrsTout + " C");

    // Power must be positive
    assertTrue(bwrsPower > 0.0, "BWRS power must be positive: " + bwrsPower + " MW");

    // Polytropic head must be positive
    assertTrue(bwrsHead > 0.0, "BWRS polytropic head must be positive: " + bwrsHead + " kJ/kg");
  }
}
