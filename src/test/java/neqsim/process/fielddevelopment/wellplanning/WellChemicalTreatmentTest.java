package neqsim.process.fielddevelopment.wellplanning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.process.fielddevelopment.wellplanning.WellChemicalTreatment.HydrateInhibitor;
import neqsim.process.fielddevelopment.wellplanning.WellChemicalTreatment.Result;
import neqsim.process.fielddevelopment.wellplanning.WellChemicalTreatment.StationResult;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/**
 * Tests of {@link WellChemicalTreatment}: CPA hydrate inhibition versus Hammerschmidt, dose solution, partitioning,
 * mass balance and pump/line basis.
 *
 * @author NeqSim
 * @version 1.0
 */
public class WellChemicalTreatmentTest {

  /**
   * Builds a wet natural gas with CPA.
   *
   * @return fluid
   */
  private static SystemInterface wetGas() {
    SystemInterface f = new SystemSrkCPAstatoil(273.15 + 20.0, 100.0);
    f.addComponent("methane", 0.85);
    f.addComponent("ethane", 0.07);
    f.addComponent("propane", 0.03);
    f.addComponent("n-butane", 0.01);
    f.addComponent("water", 0.05);
    f.setMixingRule(10);
    return f;
  }

  /**
   * MEG lowers the hydrate temperature monotonically and by an amount close to the Hammerschmidt correlation.
   */
  @Test
  public void megInhibitionMatchesHammerschmidtMagnitude() {
    WellChemicalTreatment t = new WellChemicalTreatment(wetGas()).setInhibitor(HydrateInhibitor.MEG, 0.9);
    double t0 = t.hydrateTemperatureC(100.0, 0.0);
    double t20 = t.hydrateTemperatureC(100.0, 0.2);
    double t40 = t.hydrateTemperatureC(100.0, 0.4);
    assertTrue(t0 > t20 && t20 > t40);
    assertTrue(t0 > 10.0 && t0 < 25.0, "uninhibited hydrate T=" + t0);
    double hammer20 = 2335.0 * 20.0 / (62.07 * 80.0) / 1.8;
    double hammer40 = 2335.0 * 40.0 / (62.07 * 60.0) / 1.8;
    assertEquals(hammer20, t0 - t20, 0.35 * hammer20);
    assertEquals(hammer40, t0 - t40, 0.35 * hammer40);
  }

  /**
   * The solved dose puts the hydrate temperature at the target and a smaller dose does not.
   */
  @Test
  public void requiredDoseHitsTarget() {
    WellChemicalTreatment t = new WellChemicalTreatment(wetGas()).setInhibitor(HydrateInhibitor.MEG, 0.9);
    double target = 7.0;
    double w = t.requiredWeightFraction(100.0, target);
    assertTrue(w > 0.2 && w < 0.6, "w=" + w);
    assertTrue(t.hydrateTemperatureC(100.0, w) <= target + 1.0e-6);
    assertTrue(t.hydrateTemperatureC(100.0, w - 0.02) > target);
    assertEquals(0.0, t.requiredWeightFraction(100.0, 40.0), 0.0);
  }

  /**
   * Methanol needs a smaller mass fraction than MEG for the same depression and partitions into the gas.
   */
  @Test
  public void methanolNeedsLessMassAndPartitionsToGas() {
    WellChemicalTreatment meg = new WellChemicalTreatment(wetGas()).setInhibitor(HydrateInhibitor.MEG, 0.9)
        .setSubcoolingMargin(3.0).addStation("tree", 100.0, 10.0);
    WellChemicalTreatment meoh = new WellChemicalTreatment(wetGas()).setInhibitor(HydrateInhibitor.METHANOL, 1.0)
        .setSubcoolingMargin(3.0).addStation("tree", 100.0, 10.0);
    Result rm = meg.evaluate();
    Result ro = meoh.evaluate();
    StationResult sm = rm.getStations().get(0);
    StationResult so = ro.getStations().get(0);
    assertTrue(so.getRequiredWeightFraction() < sm.getRequiredWeightFraction());
    assertTrue(so.getFractionInGas() > 0.005, "MeOH in gas=" + so.getFractionInGas());
    assertTrue(so.getFractionInGas() > 10.0 * sm.getFractionInGas());
    assertTrue(sm.getFractionInAqueous() > 0.9);
    assertTrue(sm.getInhibitedHydrateTemperatureC() <= 10.0 - 3.0 + 1.0e-6);
    assertTrue(sm.getAqueousInhibitorWeightFraction() > 0.9 * sm.getRequiredWeightFraction());
  }

  /**
   * Governing station is the coldest/highest-pressure one and the lean-solution rate satisfies the mass balance w = x
   * m_lean / (m_water + m_lean).
   */
  @Test
  public void governingStationAndMassBalance() {
    WellChemicalTreatment t = new WellChemicalTreatment(wetGas()).setInhibitor(HydrateInhibitor.MEG, 0.9)
        .setSubcoolingMargin(3.0).setWaterRate(2000.0).addStation("wellhead flowing", 60.0, 35.0)
        .addStation("tree shut-in", 120.0, 8.0);
    Result r = t.evaluate();
    assertEquals("tree shut-in", r.getGoverningStation());
    double w = r.getGoverningWeightFraction();
    double lean = r.getLeanSolutionKgPerHour();
    assertEquals(w, 0.9 * lean / (2000.0 + lean), 1.0e-9);
    assertEquals(0.9 * lean, r.getActiveInhibitorKgPerHour(), 1.0e-9);
    assertEquals(0.0, r.getStations().get(0).getRequiredWeightFraction(), 0.0);
    assertEquals(lean / r.getLeanDensityKgPerM3(), r.getLeanSolutionM3PerHour(), 1.0e-9);
    assertTrue(r.getLeanDensityKgPerM3() > 1000.0 && r.getLeanDensityKgPerM3() < 1113.0);
  }

  /**
   * Chemical rate, pump power and line friction follow their closed forms.
   */
  @Test
  public void injectionBasisClosedForms() {
    double rate = WellChemicalTreatment.chemicalInjectionRateLitresPerHour(50.0, 10.0, 0.5, 1.1);
    assertEquals(50.0 * 10.0 * 1000.0 * 1.0e-6 / (0.5 * 1.1), rate, 1.0e-12);
    double kw = WellChemicalTreatment.pumpShaftPowerKw(0.5, 250.0, 1.0, 0.5);
    assertEquals(0.5 / 3600.0 * 249.0e5 / 0.5 / 1000.0, kw, 1.0e-12);
    double q = 0.01;
    double d = 0.006;
    double mu = 0.02;
    double dp = WellChemicalTreatment.injectionLineFrictionBar(q, 3000.0, d, mu, 1100.0);
    double hp = 128.0 * mu * 3000.0 * (q / 3600.0) / (Math.PI * Math.pow(d, 4.0)) / 1.0e5;
    assertEquals(hp, dp, 1.0e-9 * hp);
    assertThrows(IllegalArgumentException.class, () -> new WellChemicalTreatment(new SystemSrkCPAstatoil(300.0, 10.0)));
  }
}
