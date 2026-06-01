package neqsim.process.equipment.powergeneration.gasturbine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for the gas turbine fleet right-sizing and retrofit study stack.
 *
 * @author neqsim
 */
public class GasTurbineUnitTest {

  private static StreamInterfaceFuel fuel() {
    return new StreamInterfaceFuel();
  }

  /** Minimal fuel stream wrapper that returns a representative natural gas. */
  private static class StreamInterfaceFuel {
    final Stream stream;

    StreamInterfaceFuel() {
      SystemInterface sys = new SystemSrkEos(288.15, 30.0);
      sys.addComponent("methane", 0.85);
      sys.addComponent("ethane", 0.08);
      sys.addComponent("propane", 0.04);
      sys.addComponent("CO2", 0.02);
      sys.addComponent("nitrogen", 0.01);
      sys.setMixingRule("classic");
      sys.init(0);
      stream = new Stream("fuel", sys);
      stream.setFlowRate(10.0, "kg/hr");
      stream.run();
    }
  }

  @Test
  public void catalogLoads() {
    Map<String, GasTurbineSpec> all = GasTurbineCatalog.all();
    assertFalse(all.isEmpty(), "Catalog should not be empty");
    GasTurbineSpec lm2500 = GasTurbineCatalog.get("LM2500");
    assertNotNull(lm2500);
    assertTrue(lm2500.getRatedPowerMW() > 15.0 && lm2500.getRatedPowerMW() < 40.0,
        "LM2500 power " + lm2500.getRatedPowerMW() + " MW out of expected range");
    assertTrue(lm2500.getHeatRateKJPerKWh() > 8000.0 && lm2500.getHeatRateKJPerKWh() < 12000.0,
        "LM2500 heat rate out of expected range");
  }

  @Test
  public void catalogFindBestFit() {
    GasTurbineSpec spec = GasTurbineCatalog.findBestFit(10.0e6, 1.10);
    assertNotNull(spec);
    assertTrue(spec.getRatedPowerW() >= 11.0e6, "Best fit must cover 10 MW × 1.10 redundancy");
  }

  @Test
  public void performanceMapAtIso() {
    GasTurbineSpec spec = GasTurbineCatalog.get("LM2500");
    GasTurbinePerformanceMap map = GasTurbinePerformanceMap.fromSpec(spec);
    double powerAtIso = map.getAvailablePower(spec.getRatedPowerW(),
        GasTurbinePerformanceMap.T_ISO_K, GasTurbinePerformanceMap.P_ISO_BARA);
    assertEquals(spec.getRatedPowerW(), powerAtIso, 1.0e-3,
        "At ISO conditions available power should equal rated");
    double hrAt100 =
        map.getHeatRate(spec.getHeatRateKJPerKWh(), 1.0, GasTurbinePerformanceMap.T_ISO_K);
    assertEquals(spec.getHeatRateKJPerKWh(), hrAt100, spec.getHeatRateKJPerKWh() * 0.02,
        "Heat rate at 100% load should be near ISO value");
  }

  @Test
  public void performanceMapPartLoadPenalty() {
    GasTurbineSpec spec = GasTurbineCatalog.get("LM6000PF");
    GasTurbinePerformanceMap map = GasTurbinePerformanceMap.fromSpec(spec);
    double iso = spec.getHeatRateKJPerKWh();
    double hr50 = map.getHeatRate(iso, 0.50, GasTurbinePerformanceMap.T_ISO_K);
    double hr30 = map.getHeatRate(iso, 0.30, GasTurbinePerformanceMap.T_ISO_K);
    assertTrue(hr50 > iso * 1.10, "50% load heat rate should be ≥10% above ISO");
    assertTrue(hr30 > hr50, "30% load worse than 50% load");
  }

  @Test
  public void degradationGrowsWithHours() {
    GasTurbineDegradation d = new GasTurbineDegradation();
    d.addFiredHours(2000.0);
    double after2k = d.getTotalHeatRatePenalty();
    assertTrue(after2k > 0.0);
    d.offlineWash();
    assertTrue(d.getTotalHeatRatePenalty() < after2k, "Offline wash should reduce penalty");
  }

  @Test
  public void emissionsCarbonBalance() {
    StreamInterfaceFuel f = fuel();
    GasTurbineEmissions em = new GasTurbineEmissions();
    SystemInterface sys = f.stream.getFluid();
    // 1 mol/s of mixed gas
    double co2 = em.computeCO2KgPerS(sys, 1.0);
    // 85% CH4 (1C) + 8% C2H6 (2C) + 4% C3H8 (3C) + 2% CO2 (1C) = 1.15 C per mol
    // mass CO2 = 1.15 * 44.01 g/s = 50.6 g/s = 0.0506 kg/s
    assertEquals(0.0506, co2, 0.001, "CO2 carbon balance");
  }

  @Test
  public void unitRunsWithCompressorLoad() {
    StreamInterfaceFuel f = fuel();
    GasTurbineSpec spec = GasTurbineCatalog.get("LM2500");
    GasTurbineUnit gt = new GasTurbineUnit("GT-1", f.stream, spec);
    gt.setAmbient(283.15, 1.013);
    gt.setDemandedPower(15.0e6); // 15 MW
    gt.run();
    assertTrue(gt.getLoadFraction() > 0.4 && gt.getLoadFraction() < 1.0,
        "Load fraction " + gt.getLoadFraction() + " out of range");
    assertTrue(gt.getFuelMassFlowKgPerS() > 0.0);
    assertTrue(gt.getCO2EmissionKgPerS() > 0.0);
    assertTrue(gt.getThermalEfficiency() > 0.25 && gt.getThermalEfficiency() < 0.45,
        "Efficiency " + gt.getThermalEfficiency() + " out of plausible range");
  }

  @Test
  public void co2TaxScheduleInterpolates() {
    CO2TaxSchedule sched = CO2TaxSchedule.loadDefault();
    assertFalse(sched.asMap().isEmpty(), "Schedule should load");
    double y2025 = sched.getTotalNOKPerTonne(2025);
    double y2030 = sched.getTotalNOKPerTonne(2030);
    assertTrue(y2025 > 0.0);
    assertTrue(y2030 >= y2025, "CO2 cost should be non-decreasing 2025→2030");
  }

  @Test
  public void dispatchPicksFeasibleFleet() {
    StreamInterfaceFuel f1 = fuel();
    StreamInterfaceFuel f2 = fuel();
    StreamInterfaceFuel f3 = fuel();
    GasTurbineUnit a = new GasTurbineUnit("GT-A", f1.stream, GasTurbineCatalog.get("LM2500"));
    GasTurbineUnit b = new GasTurbineUnit("GT-B", f2.stream, GasTurbineCatalog.get("LM2500"));
    GasTurbineUnit c = new GasTurbineUnit("GT-C", f3.stream, GasTurbineCatalog.get("LM2500"));
    List<GasTurbineUnit> fleet = Arrays.asList(a, b, c);

    TurbineDispatchOptimizer opt = new TurbineDispatchOptimizer(3.0, 1500.0);
    TurbineDispatchOptimizer.DispatchResult dr = opt.dispatch(fleet, 18.0e6);
    assertTrue(dr.feasible, "Should be feasible: " + dr.reason);
    assertTrue(dr.runningUnits.size() >= 1);
    assertTrue(dr.spareUnits.size() >= 1, "N+1 spare expected");
    assertTrue(dr.totalCostNOKPerHr > 0.0);
  }

  @Test
  public void retrofitStudyComputesNpv() {
    // Baseline: 2x large aero turbines running far below rated
    StreamInterfaceFuel fA = fuel();
    StreamInterfaceFuel fB = fuel();
    GasTurbineUnit baseA =
        new GasTurbineUnit("Base-A", fA.stream, GasTurbineCatalog.get("LM6000PF"));
    GasTurbineUnit baseB =
        new GasTurbineUnit("Base-B", fB.stream, GasTurbineCatalog.get("LM6000PF"));
    List<GasTurbineUnit> baseline = new ArrayList<GasTurbineUnit>(Arrays.asList(baseA, baseB));

    // Retrofit: 2x smaller industrial turbines well matched to load
    StreamInterfaceFuel fC = fuel();
    StreamInterfaceFuel fD = fuel();
    GasTurbineUnit retroA =
        new GasTurbineUnit("Retro-A", fC.stream, GasTurbineCatalog.get("SGT_700"));
    GasTurbineUnit retroB =
        new GasTurbineUnit("Retro-B", fD.stream, GasTurbineCatalog.get("SGT_700"));
    List<GasTurbineUnit> retro = new ArrayList<GasTurbineUnit>(Arrays.asList(retroA, retroB));

    // Declining late-life demand, 15 years
    double[] demand = new double[15];
    for (int i = 0; i < demand.length; i++) {
      demand[i] = Math.max(8.0, 20.0 - i * 0.8);
    }

    CO2TaxSchedule sched = CO2TaxSchedule.loadDefault();
    LateLifeRetrofitStudy study =
        new LateLifeRetrofitStudy(baseline, retro, demand, 2026, sched, 3.0);
    study.setRetrofitCapexMNOK(800.0);
    study.setDiscountRate(0.08);
    study.setAnnualOperatingHours(8000.0);

    LateLifeRetrofitStudy.RetrofitResult res = study.run();
    assertEquals(15, res.years.size());
    assertNotNull(res.toSummary());
    // We at least expect the retrofit fleet to emit less CO2 at low loads
    int feasibleYears = 0;
    for (LateLifeRetrofitStudy.YearResult yr : res.years) {
      if (yr.baselineFeasible && yr.retrofitFeasible) {
        feasibleYears++;
      }
    }
    assertTrue(feasibleYears >= 5,
        "Need at least 5 mutually feasible years for the comparison, got " + feasibleYears);
  }
}
