package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemDesmukhMather;
import neqsim.thermo.system.SystemKentEisenberg;
import neqsim.thermo.system.SystemGEWilson;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPitzer;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Standard-state checks independent of the reaction solver's concentration variable.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class AqueousActivityScaleTest {
  /**
   * Creates an isothermal aqueous electrolyte without invoking reaction equilibrium.
   *
   * @param salt sodium chloride amount in mol per approximately kg water
   * @return initialized aqueous phase
   */
  private PhaseInterface brine(double salt) {
    SystemInterface system = new SystemElectrolyteCPAstatoil(298.15, 1.01325);
    system.addComponent("water", 1.0 / 0.018015);
    system.addComponent("Na+", salt);
    system.addComponent("Cl-", salt);
    system.setMixingRule(10);
    system.init(0);
    system.setNumberOfPhases(1);
    system.setPhaseType(0, PhaseType.AQUEOUS);
    system.init(1);
    return system.getPhase(0);
  }

  /** Checks eos molality coefficients have unit infinite dilution limit. */
  @Test
  void eosMolalityCoefficientsHaveUnitInfiniteDilutionLimit() {
    PhaseInterface phase = brine(1e-10);
    assertEquals(1.0, phase.getActivityCoefficient(1, "molality"), 2e-5);
    assertEquals(1.0, phase.getActivityCoefficient(2, "molality"), 2e-5);
  }

  /** Checks ion scale conversion matches mean coefficient and chemical potential. */
  @Test
  void ionScaleConversionMatchesMeanCoefficientAndChemicalPotential() {
    PhaseInterface phase = brine(0.1);
    double waterMass = phase.getComponent(0).getNumberOfMolesInPhase() * phase.getComponent(0).getMolarMass();
    for (int i = 1; i <= 2; i++) {
      double moleFractionActivity = phase.getComponent(i).getx() * phase.getActivityCoefficient(i);
      double molalActivity = phase.getComponent(i).getNumberOfMolesInPhase() / waterMass
          * phase.getActivityCoefficient(i, "molality");
      assertEquals(Math.log(phase.getComponent(0).getMolarMass()), Math.log(moleFractionActivity / molalActivity),
          1e-12);
    }
    assertEquals(phase.getMolalMeanIonicActivity(1, 2),
        Math.sqrt(phase.getActivityCoefficient(1, "molality") * phase.getActivityCoefficient(2, "molality")), 1e-12);
    assertEquals(phase.getActivityCoefficient(0), phase.getActivityCoefficient(0, "molality"), 0.0);
  }

  /** Checks symmetric ge coefficients use water infinite dilution reference. */
  @Test
  void symmetricGeCoefficientsUseWaterInfiniteDilutionReference() {
    SystemGEWilson system = new SystemGEWilson(298.15, 1.01325);
    system.addComponent("methanol", 1e-9);
    system.addComponent("water", 1.0);
    system.setMixingRule("classic");
    system.init(0);
    system.init(1);
    PhaseGE phase = (PhaseGE) system.getPhase(1);
    double nativeGamma = phase.getActivityCoefficient(0);
    assertEquals(1.0, phase.getActivityCoefficient(0, "molality"), 1e-7);
    assertEquals(nativeGamma, phase.getActivityCoefficient(0), 0.0,
        "Calculating the reference must not mutate the source phase");
  }

  /** Checks pitzer molality coefficient is already on requested basis. */
  @Test
  void pitzerMolalityCoefficientIsAlreadyOnRequestedBasis() {
    SystemPitzer system = new SystemPitzer(298.15, 1.01325);
    system.addComponent("water", 55.508);
    system.addComponent("Na+", 0.5);
    system.addComponent("Cl-", 0.5);
    system.init(0);
    system.init(1);
    PhasePitzer phase = (PhasePitzer) system.getPhase(1);
    assertTrue(phase.getActivityCoefficient(1) > 0.0);
    assertEquals(phase.getActivityCoefficient(1), phase.getActivityCoefficient(1, "molality"), 0.0);
    assertEquals(phase.getActivityCoefficient(2), phase.getActivityCoefficient(2, "molality"), 0.0);
    assertEquals(phase.getActivityCoefficient(1),
        phase.getActivityCoefficient(1, "molefraction") * phase.getComponent(0).getx(), 1e-12);
    assertEquals(Math.sqrt(phase.getActivityCoefficient(1) * phase.getActivityCoefficient(2)),
        phase.getMolalMeanIonicActivity(1, 2), 1e-12);
  }

  /** Checks neutral water reports iupac molal ph. */
  @Test
  void neutralWaterReportsIupacMolalPH() {
    SystemInterface system = new SystemElectrolyteCPAstatoil(298.15, 1.01325);
    system.addComponent("water", 55.508);
    system.chemicalReactionInit();
    system.setMixingRule(10);
    new ThermodynamicOperations(system).TPflash();
    PhaseInterface aqueous = system.getPhase("aqueous");
    assertEquals(7.0, aqueous.getpH("molality"), 0.02);
    assertEquals(-Math.log10(aqueous.getComponent("water").getMolarMass()), aqueous.getpH() - aqueous.getpH("molality"),
        1e-12, "Documented legacy no-argument behavior remains available");
  }

  /** Checks carbonate buffer preserves inventory and reports molal ph. */
  @Test
  void carbonateBufferPreservesInventoryAndReportsMolalPH() {
    SystemInterface system = new SystemElectrolyteCPAstatoil(298.15, 1.01325);
    system.addComponent("methane", 5.0);
    system.addComponent("CO2", 0.01);
    system.addComponent("water", 55.5);
    system.addComponent("Ca++", 0.0001);
    system.addComponent("Cl-", 0.0002);
    system.addComponent("Na+", 0.001);
    system.addComponent("OH-", 0.001);
    system.chemicalReactionInit();
    system.setMixingRule(10);
    system.setMultiPhaseCheck(true);
    new ThermodynamicOperations(system).TPflash();
    int aqueousIndex = system.getPhaseNumberOfPhase("aqueous");
    PhaseInterface aqueous = system.getPhase(aqueousIndex);
    double inorganicCarbon = 0.0;
    double charge = 0.0;
    for (int p = 0; p < system.getNumberOfPhases(); p++) {
      PhaseInterface phase = system.getPhase(p);
      for (String name : new String[] {"CO2", "HCO3-", "CO3--"}) {
        inorganicCarbon += phase.getComponent(name).getNumberOfMolesInPhase();
      }
      for (int i = 0; i < phase.getNumberOfComponents(); i++) {
        charge += phase.getComponent(i).getNumberOfMolesInPhase() * phase.getComponent(i).getIonicCharge();
      }
    }
    assertEquals(0.01, inorganicCarbon, 1e-10);
    assertEquals(0.0, charge, 1e-10);
    assertTrue(aqueous.getpH("molality") > 7.5 && aqueous.getpH("molality") < 7.8,
        "Dilute carbonate-buffer pH on the IUPAC molality scale");
    for (String name : new String[] {"CO2water", "carbonate", "waterreac"}) {
      assertEquals(0.0, system.getChemicalReactionOperations().getReactionList().getReaction(name)
          .calcLogReactionResidual(system, aqueousIndex), 1e-7, name);
    }
  }

  /** Checks desmukh mather recovers its original molality correlation. */
  @Test
  void desmukhMatherRecoversItsOriginalMolalityCorrelation() {
    SystemDesmukhMather system = new SystemDesmukhMather(298.15, 1.01325);
    system.addComponent("water", 55.508);
    system.addComponent("Na+", 0.1);
    system.addComponent("Cl-", 0.1);
    system.setMixingRule("classic");
    system.init(0);
    system.init(1);
    PhaseDesmukhMather phase = (PhaseDesmukhMather) system.getPhase(1);
    double ionMolality = phase.getComponent(1).getMolality(phase);
    double expected = phase.getActivityCoefficient(1) * phase.getComponent(1).getx()
        / (ionMolality * phase.getSolventMolarMass());
    assertEquals(expected, phase.getActivityCoefficient(1, "molality"), 1e-12);
    assertEquals(Math.sqrt(phase.getActivityCoefficient(1, "molality") * phase.getActivityCoefficient(2, "molality")),
        phase.getMolalMeanIonicActivity(1, 2), 1e-12);
  }

  /** Checks kent eisenberg retains ideal mole fraction activities. */
  @Test
  void kentEisenbergRetainsIdealMoleFractionActivities() {
    SystemKentEisenberg system = new SystemKentEisenberg(298.15, 1.01325);
    system.addComponent("water", 55.508);
    system.addComponent("Na+", 0.1);
    system.addComponent("Cl-", 0.1);
    system.setMixingRule("classic");
    system.init(0);
    system.init(1);
    PhaseInterface phase = system.getPhase(1);
    assertEquals(1.0, phase.getActivityCoefficient(1, "molefraction"), 0.0);
    assertEquals(phase.getComponent(0).getx(), phase.getActivityCoefficient(1, "molality"), 0.0);
  }

  /** Checks aqueous reporting preserves native Deshmukh-Mather activity with multiple solvents. */
  @Test
  void desmukhMatherMixedSolventReportingPreservesNativeChemicalActivity() {
    SystemDesmukhMather system = new SystemDesmukhMather(298.15, 1.01325);
    system.addComponent("water", 40.0);
    system.addComponent("MDEA", 10.0);
    system.addComponent("Na+", 0.1);
    system.addComponent("Cl-", 0.1);
    system.addComponent("H3O+", 1e-7);
    system.addComponent("OH-", 1e-7);
    system.setMixingRule("classic");
    system.init(0);
    system.init(1);
    PhaseDesmukhMather phase = (PhaseDesmukhMather) system.getPhase(1);
    phase.setType(PhaseType.AQUEOUS);
    double waterMass = phase.getComponent("water").getNumberOfMolesInPhase()
        * phase.getComponent("water").getMolarMass();
    assertTrue(phase.getSolventWeight() > waterMass, "MDEA must participate in the native solvent basis");
    int hydrogen = phase.getComponent("H3O+").getComponentNumber();
    // ChemicalEquilibrium's native DM activity is gamma_stored*x_i/M_averageSolvent.
    double nativeActivity = phase.getActivityCoefficient(hydrogen) * phase.getComponent(hydrogen).getx()
        / phase.getSolventMolarMass();
    double reportedActivity = phase.getComponent(hydrogen).getNumberOfMolesInPhase() / waterMass
        * phase.getActivityCoefficient(hydrogen, "molality");
    assertEquals(nativeActivity, reportedActivity, nativeActivity * 1e-12);
    assertEquals(-Math.log10(nativeActivity), phase.getpH("molality"), 1e-12);
  }

  /** Checks the native Duan-Sun salting coefficient retains its published unit-molality standard. */
  @Test
  void duanSunRetainsItsNativeMolalSaltingCoefficient() {
    // The public SystemDuanSun supports CO2 only. Exercise the aqueous phase's
    // activity correlation directly without claiming a supported brine flash.
    PhaseDuanSun phase = new PhaseDuanSun();
    phase.setTemperature(298.15);
    phase.setPressure(10.0);
    phase.addComponent("CO2", 0.01, 0.01, 0);
    phase.addComponent("water", 55.508, 55.508, 1);
    phase.addComponent("Na+", 0.1, 0.1, 2);
    phase.addComponent("Cl-", 0.1, 0.1, 3);
    phase.init(55.718, 4, 0, PhaseType.AQUEOUS, 1.0);
    phase.getExcessGibbsEnergy(phase, 4, 298.15, 10.0, PhaseType.AQUEOUS);
    int carbonDioxide = 0;
    assertEquals(phase.getActivityCoefficient(carbonDioxide), phase.getActivityCoefficient(carbonDioxide, "molality"),
        0.0);
    assertEquals(phase.getActivityCoefficient(carbonDioxide),
        phase.getActivityCoefficient(carbonDioxide, "molefraction") * phase.getComponent("water").getx(), 1e-12);
  }

}
