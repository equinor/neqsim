package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Public/synthetic regressions for condensation evidence and conditioning conservation.
 *
 * @author NeqSim
 * @version 1.0
 */
class DryGasSealEvidenceTest {
  /**
   * Invokes a private kernel to test one physical sub-analysis without repeating full scans.
   *
   * @param target analyzer
   * @param name kernel name
   * @return kernel evidence
   * @throws Exception on reflection failure
   */
  @SuppressWarnings("unchecked")
  private Map<String, Object> kernel(DryGasSealAnalyzer target, String name) throws Exception {
    Method method = DryGasSealAnalyzer.class.getDeclaredMethod(name);
    method.setAccessible(true);
    return (Map<String, Object>) method.invoke(target);
  }

  /**
   * Creates a public synthetic gas.
   *
   * @return configured fluid
   */
  private SystemInterface gas() {
    SystemInterface fluid = new SystemPrEos(313.15, 80.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("propane", 0.15);
    fluid.addComponent("n-hexane", 0.05);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    fluid.init(0);
    return fluid;
  }

  /**
   * Checks that absent, failed and nonfinite flashes cannot pass screening.
   *
   * @throws Exception on reflection or serialization failure
   */
  @Test
  void incompleteEvidenceFailsClosed() throws Exception {
    Method check = DryGasSealAnalyzer.class.getDeclaredMethod("condensationEvidencePasses", Object.class);
    check.setAccessible(true);
    Map<String, Object> evidence = new LinkedHashMap<>();
    evidence.put("max_liquid_vol_pct", 0.0);
    assertFalse((Boolean) check.invoke(null, evidence));
    evidence.put("calculation_complete", true);
    assertTrue((Boolean) check.invoke(null, evidence));
    evidence.put("max_liquid_vol_pct", Double.NaN);
    assertFalse((Boolean) check.invoke(null, evidence));
    evidence.put("max_liquid_vol_pct", 0.0);
    evidence.put("error", "failed flash");
    assertFalse((Boolean) check.invoke(null, evidence));
    assertFalse((Boolean) check.invoke(null, new Object[] {null}));
  }

  /**
   * Checks pure aqueous liquid and mixed gas/water phases are counted.
   *
   * @throws Exception on reflection or serialization failure
   */
  @Test
  void waterAndSingleLiquidAreCounted() throws Exception {
    Method check = DryGasSealAnalyzer.class.getDeclaredMethod("liquidVolumePercent", SystemInterface.class);
    check.setAccessible(true);
    SystemInterface water = new SystemPrEos(298.15, 5.0);
    water.addComponent("water", 1.0);
    water.setMixingRule("classic");
    new ThermodynamicOperations(water).TPflash();
    assertEquals(100.0, (Double) check.invoke(null, water), 1e-8);
    water = new SystemPrEos(298.15, 5.0);
    water.addComponent("water", 1.0);
    water.addComponent("methane", 1.0);
    water.setMixingRule("classic");
    water.setMultiPhaseCheck(true);
    new ThermodynamicOperations(water).TPflash();
    assertTrue((Double) check.invoke(null, water) > 0.0);
  }

  /**
   * Checks kg/hr leakage conversion works even when flow is configured before composition.
   *
   * @throws Exception on reflection or serialization failure
   */
  @Test
  void massLeakageUsesMolarMassAndNormalConditions() throws Exception {
    DryGasSealAnalyzer analyzer = new DryGasSealAnalyzer("synthetic");
    analyzer.setSealLeakageRate(1.0, "kg/hr");
    SystemInterface fluid = gas();
    analyzer.setSealGas(fluid);
    kernelVoid(analyzer, "resolveLeakageBasis");
    Map<String, Object> result = kernel(analyzer, "runCondensateAccumulation");
    double expected = 1.0 / fluid.getMolarMass("kg/mol") / 3600.0;
    assertEquals(expected, (Double) result.get("seal_leakage_mol_per_sec"), 1e-10);
    assertThrows(IllegalArgumentException.class, () -> analyzer.setGCUMargins(-1, 17));
    assertThrows(IllegalArgumentException.class, () -> analyzer.setGCUSupplyFlowNLmin(Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> analyzer.setSealLeakageRate(1, "scfm"));
    assertThrows(IllegalArgumentException.class, () -> analyzer.setSealLeakageRate(Double.NaN, "kg/hr"));
  }

  /**
   * Invokes a void internal conversion kernel.
   *
   * @param analyzer target
   * @param name method name
   * @throws Exception on reflection failure
   */
  private void kernelVoid(DryGasSealAnalyzer analyzer, String name) throws Exception {
    Method method = DryGasSealAnalyzer.class.getDeclaredMethod(name);
    method.setAccessible(true);
    method.invoke(analyzer);
  }

  /**
   * Checks conditioned composition and exact retained-gas reheating energy balance.
   *
   * @throws Exception on reflection or serialization failure
   */
  @Test
  @SuppressWarnings("unchecked")
  void gcuReheatsOnlySeparatedGas() throws Exception {
    SystemInterface feed = gas();
    DryGasSealAnalyzer analyzer = new DryGasSealAnalyzer("conditioning");
    analyzer.setSealGas(feed);
    analyzer.setSealCavityPressure(80, "bara");
    analyzer.setSealCavityTemperature(40, "C");
    analyzer.setPrimaryVentPressure(2, "bara");
    analyzer.setGCUSupplyFlowNLmin(1000);
    Map<String, Object> result = kernel(analyzer, "runGCUSizing");
    assertFalse(result.containsKey("error"), result.toString());
    Map<String, Double> dry = (Map<String, Double>) result.get("dry_gas_composition");
    assertTrue(dry.get("n-hexane") < 0.05);
    assertEquals(1.0, dry.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9);
    double flow = (Double) result.get("dry_gas_molar_flow_mol_s");
    double dh = (Double) result.get("reheated_gas_enthalpy_J_mol")
        - (Double) result.get("separated_gas_enthalpy_J_mol");
    assertEquals(flow * dh, (Double) result.get("reheat_duty_W"), 1e-8);
    assertTrue(Double.isNaN((Double) result.get("total_electrical_kW")));
    assertEquals("explicit_supply", result.get("flow_basis"));
    // Recompute the separated composition independently from the cold TP flash.
    SystemInterface cold = feed.clone();
    cold.setTemperature((Double) result.get("gcu_cooling_target_C"), "C");
    cold.setPressure((Double) result.get("cricondentherm_pressure_bara"));
    new ThermodynamicOperations(cold).TPflash();
    assertEquals(cold.getPhase("gas").getComponent("n-hexane").getx(), dry.get("n-hexane"), 1e-8);
    analyzer.getResults().put("old_evidence", result);
    analyzer.setAmbientTemperature(10, "C");
    assertTrue(analyzer.getResults().isEmpty());
    assertFalse(analyzer.isSafeToOperate());
    assertEquals(313.15, feed.getTemperature(), 1e-8);
  }
}
