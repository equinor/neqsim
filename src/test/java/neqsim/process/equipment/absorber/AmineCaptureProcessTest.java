package neqsim.process.equipment.absorber;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Integration test for the full amine CO2 capture process: absorber + regenerator closed loop.
 *
 * <p>
 * Builds a complete amine treating process with a sour gas feed, a {@link SimpleAmineAbsorber}, a rich amine let-down
 * valve, a {@link SimpleAmineRegenerator}, a lean amine cooler, a circulation pump, and a {@link Recycle} that closes
 * the lean/rich amine loop. The test verifies that the process converges and produces physically sensible results: CO2
 * is removed from the gas in the absorber and released as acid gas overhead in the regenerator.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class AmineCaptureProcessTest {

  /**
   * Builds and runs the closed-loop amine capture process and asserts physical consistency.
   */
  @Test
  void testClosedLoopAmineCaptureProcess() {
    // --- Sour gas feed: methane with 5 mol% CO2 ---
    SystemInterface gasFluid = new SystemSrkEos(313.15, 50.0);
    gasFluid.addComponent("methane", 0.95);
    gasFluid.addComponent("CO2", 0.05);
    gasFluid.setMixingRule("classic");

    Stream sourGas = new Stream("sour gas", gasFluid);
    sourGas.setFlowRate(10000.0, "kg/hr");
    sourGas.setTemperature(40.0, "C");
    sourGas.setPressure(50.0, "bara");

    // --- Lean amine solvent: 50 wt% MDEA in water with a low residual CO2 loading ---
    SystemInterface amineFluid = new SystemSrkEos(318.15, 50.0);
    amineFluid.addComponent("CO2", 0.005);
    amineFluid.addComponent("water", 0.80);
    amineFluid.addComponent("MDEA", 0.195);
    amineFluid.setMixingRule("classic");

    Stream leanAmineFeed = new Stream("lean amine feed", amineFluid);
    leanAmineFeed.setFlowRate(60000.0, "kg/hr");
    leanAmineFeed.setTemperature(45.0, "C");
    leanAmineFeed.setPressure(50.0, "bara");

    // --- Absorber ---
    SimpleAmineAbsorber absorber = new SimpleAmineAbsorber("amine absorber", sourGas);
    absorber.setLeanAmineInStream(leanAmineFeed);
    absorber.setAmineType("MDEA");
    absorber.setCO2RemovalEfficiency(0.90);

    // --- Rich amine let-down valve to regeneration pressure ---
    ThrottlingValve richValve = new ThrottlingValve("rich valve", absorber.getRichAmineOutStream());
    richValve.setOutletPressure(2.0);

    // --- Regenerator (stripper) ---
    SimpleAmineRegenerator regenerator = new SimpleAmineRegenerator("amine regenerator", richValve.getOutletStream());
    regenerator.setAmineType("MDEA");
    regenerator.setAmineConcentrationWtPct(50.0);
    regenerator.setReboilerTemperatureC(120.0);
    regenerator.setLeanLoadingTarget(0.01);
    regenerator.setRegenerationEfficiency(0.95);

    // --- Lean amine cooler back to absorber temperature ---
    Cooler leanCooler = new Cooler("lean cooler", regenerator.getLeanAmineOutStream());
    leanCooler.setOutTemperature(45.0 + 273.15);

    // --- Circulation pump back to absorber pressure ---
    Pump leanPump = new Pump("lean pump", leanCooler.getOutletStream());
    leanPump.setOutletPressure(50.0);

    // --- Recycle: close the lean amine loop ---
    Recycle recycle = new Recycle("lean recycle");
    recycle.addStream(leanPump.getOutletStream());
    recycle.setOutletStream(leanAmineFeed);
    recycle.setTolerance(1.0e-3);

    // --- Assemble and run the process ---
    ProcessSystem process = new ProcessSystem();
    process.add(sourGas);
    process.add(leanAmineFeed);
    process.add(absorber);
    process.add(richValve);
    process.add(regenerator);
    process.add(leanCooler);
    process.add(leanPump);
    process.add(recycle);
    process.run();

    // --- Verify outputs exist ---
    assertNotNull(absorber.getSweetGasOutStream(), "Sweet gas outlet should be created");
    assertNotNull(regenerator.getLeanAmineOutStream(), "Lean amine outlet should be created");
    assertNotNull(regenerator.getAcidGasOutStream(), "Acid gas outlet should be created");

    // --- CO2 should be removed from the gas in the absorber ---
    double sourGasCO2x = sourGas.getFluid().getPhase(0).getComponent("CO2").getx();
    double sweetGasCO2x = absorber.getSweetGasOutStream().getFluid().getPhase(0).getComponent("CO2").getx();
    assertTrue(sweetGasCO2x < sourGasCO2x,
        "Sweet gas CO2 fraction (" + sweetGasCO2x + ") should be below sour gas CO2 fraction (" + sourGasCO2x + ")");

    // --- Acid gas overhead from the regenerator must contain stripped CO2 ---
    SystemInterface acidGas = regenerator.getAcidGasOutStream().getFluid();
    double acidGasCO2Moles = acidGas.getComponent("CO2").getNumberOfmoles();
    assertTrue(acidGasCO2Moles > 0.0, "Acid gas overhead should contain stripped CO2, got " + acidGasCO2Moles + " mol");

    // --- Reboiler duty must be positive and in a physically sensible range ---
    assertTrue(regenerator.getReboilerDutyKW() > 0.0,
        "Reboiler duty should be positive, got " + regenerator.getReboilerDutyKW() + " kW");

    double specificDuty = regenerator.getSpecificReboilerDutyMJperKgCO2();
    assertTrue(specificDuty > 0.0 && specificDuty < 20.0,
        "Specific reboiler duty should be in a sensible range (0-20 MJ/kg CO2), got " + specificDuty);

    // --- Loadings should be physical: rich loading above lean loading ---
    assertTrue(regenerator.getRichLoading() >= regenerator.getLeanLoading(), "Rich loading ("
        + regenerator.getRichLoading() + ") should be >= lean loading (" + regenerator.getLeanLoading() + ")");
  }

  /**
   * Verifies the standalone regenerator strips acid gas and computes a reboiler duty when run directly.
   */
  @Test
  void testStandaloneRegenerator() {
    SystemInterface richAmine = new SystemSrkEos(333.15, 2.0);
    richAmine.addComponent("CO2", 0.04);
    richAmine.addComponent("water", 0.78);
    richAmine.addComponent("MDEA", 0.18);
    richAmine.setMixingRule("classic");

    Stream richStream = new Stream("rich amine", richAmine);
    richStream.setFlowRate(50000.0, "kg/hr");
    richStream.setTemperature(60.0, "C");
    richStream.setPressure(2.0, "bara");
    richStream.run();

    SimpleAmineRegenerator regenerator = new SimpleAmineRegenerator("regen", richStream);
    regenerator.setAmineType("MDEA");
    regenerator.setReboilerTemperatureC(120.0);
    regenerator.setLeanLoadingTarget(0.02);
    regenerator.setRegenerationEfficiency(0.95);
    regenerator.run(UUID.randomUUID());

    assertTrue(regenerator.getRichLoading() > 0.0, "Rich loading should be positive");
    assertTrue(regenerator.getLeanLoading() < regenerator.getRichLoading(),
        "Lean loading should be below rich loading after regeneration");
    assertTrue(regenerator.getReboilerDutyKW() > 0.0, "Reboiler duty should be positive");

    SystemInterface acidGas = regenerator.getAcidGasOutStream().getFluid();
    assertTrue(acidGas.getComponent("CO2").getNumberOfmoles() > 0.0, "Acid gas should contain stripped CO2");
  }
}
