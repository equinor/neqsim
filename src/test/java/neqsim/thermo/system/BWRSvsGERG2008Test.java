package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Compare BWRS EoS with GERG-2008 model for a methane/ethane mixture.
 */
public class BWRSvsGERG2008Test {
  private void compareAtPressure(double pressure) {
    SystemInterface bwrs = new SystemBWRSEos(298.15, pressure);
    bwrs.addComponent("methane", 0.8);
    bwrs.addComponent("ethane", 0.2);
    bwrs.createDatabase(true);
    bwrs.setMixingRule(2);
    new ThermodynamicOperations(bwrs).TPflash();
    double bwrsDensity = bwrs.getPhase(0).getDensity();
    double bwrsZ = bwrs.getPhase(0).getZ();
    double bwrsPhiCH4 = bwrs.getPhase(0).getComponent("methane").fugcoef(bwrs.getPhase(0));
    double bwrsPhiC2H6 = bwrs.getPhase(0).getComponent("ethane").fugcoef(bwrs.getPhase(0));
    double bwrsCp = bwrs.getPhase(0).getCp() / bwrs.getPhase(0).getNumberOfMolesInPhase();
    double bwrsJT = bwrs.getPhase(0).getJouleThomsonCoefficient();
    double bwrsSpeed = bwrs.getPhase(0).getSoundSpeed();

    SystemInterface gerg = new SystemGERG2008Eos(298.15, pressure);
    gerg.addComponent("methane", 0.8);
    gerg.addComponent("ethane", 0.2);
    gerg.createDatabase(true);
    gerg.setMixingRule(2);
    new ThermodynamicOperations(gerg).TPflash();
    double gergDensity = gerg.getPhase(0).getDensity();
    double gergZ = gerg.getPhase(0).getZ();
    double gergPhiCH4 = gerg.getPhase(0).getComponent("methane").fugcoef(gerg.getPhase(0));
    double gergPhiC2H6 = gerg.getPhase(0).getComponent("ethane").fugcoef(gerg.getPhase(0));
    double gergCp = gerg.getPhase(0).getCp() / gerg.getPhase(0).getNumberOfMolesInPhase();
    double gergJT = -1.0 / gerg.getPhase(0).getCp()
        * (gerg.getPhase(0).getMolarVolume() * gerg.getPhase(0).getNumberOfMolesInPhase()
            + gerg.getTemperature() * gerg.getPhase(0).getdPdTVn() / gerg.getPhase(0).getdPdVTn());
    double gergSpeed = gerg.getPhase(0).getSoundSpeed();

    assertEquals(gergDensity, bwrsDensity, gergDensity * 0.2);
    assertEquals(gergZ, bwrsZ, gergZ * 0.15);
    assertEquals(gergPhiCH4, bwrsPhiCH4, Math.abs(gergPhiCH4) * 2.0);
    assertEquals(gergPhiC2H6, bwrsPhiC2H6, Math.abs(gergPhiC2H6) * 2.0);
    assertEquals(gergCp, bwrsCp, gergCp * 0.2);
    assertEquals(gergJT, bwrsJT, Math.abs(gergJT) * 3.0);
    assertEquals(gergSpeed, bwrsSpeed, gergSpeed * 0.2);
  }

  @Test
  public void testAt10bar() {
    compareAtPressure(10.0);
  }

  @Test
  public void testAt100bar() {
    compareAtPressure(100.0);
  }
}
