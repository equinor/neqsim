package neqsim.chemicalreactions.chemicalreaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;

/**
 * Checks the default mole-fraction constants against independent molality references.
 *
 * <p>
 * The CO2water correlation is published on the molality scale: its constant term must be shifted by ln(M_water) for the
 * solute stoichiometric sum +1. The carbonate row uses A=216.049 from Huttenhuis et al. (2008), Table 2, DOI
 * 10.1016/j.fluid.2007.10.020, which explicitly defines mole-fraction constants and an infinite-dilution water
 * reference. Water autodissociation already includes the required squared conversion and must not be converted again.
 * The standard-state transformation is also given in Smith et al. (2026), Eq. 36, DOI 10.1002/cjce.70438.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class CarbonateReactionStandardStateTest {
  /** Checks carbonic dissociation constants use mole fraction standard state. */
  @Test
  void carbonicDissociationConstantsUseMoleFractionStandardState() {
    // Plummer and Busenberg (1982), DOI 10.1016/0016-7037(82)90056-4,
    // public USGS/PHREEQC analytical expressions. The default correlations are
    // separate approximations; the tolerance is 0.015 log10 units (3.5 percent).
    for (double t : new double[] {278.15, 298.15, 318.15, 338.15, 358.15}) {
      SystemElectrolyteCPAstatoil system = new SystemElectrolyteCPAstatoil(t, 1.01325);
      system.addComponent("water", 1.0);
      PhaseInterface phase = system.getPhase(0);
      double logAssociation2 = 107.8871 + 0.03252849 * t - 5151.79 / t - 38.92561 * Math.log10(t) + 563713.9 / (t * t);
      double logAssociation12 = 464.1965 + 0.09344813 * t - 26986.16 / t - 165.75951 * Math.log10(t)
          + 2248628.9 / (t * t);
      double logWaterMass = Math.log10(phase.getComponent(0).getMolarMass());
      assertEquals(logAssociation2 - logAssociation12 + logWaterMass,
          Math.log10(ChemicalReactionFactory.getChemicalReaction("CO2water").getK(phase)), 0.015, "K1 at " + t);
      assertEquals(-logAssociation2 + logWaterMass,
          Math.log10(ChemicalReactionFactory.getChemicalReaction("carbonate").getK(phase)), 0.015, "K2 at " + t);
    }
  }

  /** Checks water dissociation retains its already converted standard state. */
  @Test
  void waterDissociationRetainsItsAlreadyConvertedStandardState() {
    SystemElectrolyteCPAstatoil system = new SystemElectrolyteCPAstatoil(298.15, 1.01325);
    system.addComponent("water", 1.0);
    PhaseInterface phase = system.getPhase(0);
    double mw = phase.getComponent(0).getMolarMass();
    assertEquals(-14.0 + 2.0 * Math.log10(mw),
        Math.log10(ChemicalReactionFactory.getChemicalReaction("waterreac").getK(phase)), 0.01);
  }
}
