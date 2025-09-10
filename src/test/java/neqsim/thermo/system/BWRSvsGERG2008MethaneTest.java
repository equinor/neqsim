package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class BWRSvsGERG2008MethaneTest {

  private void compareAtPressure(double pressure) {
    SystemInterface bwrs = new SystemBWRSEos(298.15, pressure);
    bwrs.addComponent("methane", 1.0);
    bwrs.createDatabase(true);
    bwrs.setMixingRule(2);
    new ThermodynamicOperations(bwrs).TPflash();
    bwrs.initProperties();
    double bwrsDensity = bwrs.getPhase(0).getDensity();
    double bwrsZ = bwrs.getPhase(0).getZ();
    double bwrsCp = bwrs.getPhase(0).getCp() / bwrs.getPhase(0).getNumberOfMolesInPhase();
    double bwrsJT = bwrs.getPhase(0).getJouleThomsonCoefficient();
    double bwrsCo = bwrs.getPhase(0).getIsothermalCompressibility();
    double bwrsSpeed = bwrs.getPhase(0).getSoundSpeed();

    SystemInterface gerg = new SystemGERG2008Eos(298.15, pressure);
    gerg.addComponent("methane", 1.0);
    gerg.createDatabase(true);
    gerg.setMixingRule(2);
    new ThermodynamicOperations(gerg).TPflash();
    gerg.initProperties();
    double gergDensity = gerg.getPhase(0).getDensity();
    double gergZ = gerg.getPhase(0).getZ();
    double gergCp = gerg.getPhase(0).getCp() / gerg.getPhase(0).getNumberOfMolesInPhase();
    double gergJT = gerg.getPhase(0).getJouleThomsonCoefficient();
    double gergCo = gerg.getPhase(0).getIsothermalCompressibility();
    double gergSpeed = gerg.getPhase(0).getSoundSpeed();

    SystemInterface pr = new SystemPrEos(298.15, pressure);
    pr.addComponent("methane", 1.0);
    pr.createDatabase(true);
    pr.setMixingRule(2);
    new ThermodynamicOperations(pr).TPflash();
    pr.initProperties();
    double prDensity = pr.getPhase(0).getDensity();
    double prZ = pr.getPhase(0).getZ();
    double prCp = pr.getPhase(0).getCp() / pr.getPhase(0).getNumberOfMolesInPhase();
    double prJT = pr.getPhase(0).getJouleThomsonCoefficient();
    double prCo = pr.getPhase(0).getIsothermalCompressibility();
    double prSpeed = pr.getPhase(0).getSoundSpeed();

    System.out.println("Pressure: " + pressure + " bar");
    System.out.println("BWRS density: " + bwrsDensity + " kg/m3");
    System.out.println("GERG density: " + gergDensity + " kg/m3");
    System.out.println("PR density: " + prDensity + " kg/m3");
    System.out.println("BWRS Z: " + bwrsZ);
    System.out.println("GERG Z: " + gergZ);
    System.out.println("PR Z: " + prZ);
    System.out.println("BWRS Cp: " + bwrsCp + " J/molK");
    System.out.println("GERG Cp: " + gergCp + " J/molK");
    System.out.println("PR Cp: " + prCp + " J/molK");
    System.out.println("BWRS JT: " + bwrsJT + " K/bar");
    System.out.println("GERG JT: " + gergJT + " K/bar");
    System.out.println("PR JT: " + prJT + " K/bar");
    System.out.println("BWRS Co: " + bwrsCo + " 1/bar");
    System.out.println("GERG Co: " + gergCo + " 1/bar");
    System.out.println("PR Co: " + prCo + " 1/bar");
    System.out.println("BWRS speed: " + bwrsSpeed + " m/s");
    System.out.println("GERG speed: " + gergSpeed + " m/s");
    System.out.println("PR speed: " + prSpeed + " m/s");

    assertEquals(gergDensity, bwrsDensity, gergDensity * 0.05);
    assertEquals(gergZ, bwrsZ, gergZ * 0.05);
    assertEquals(gergCp, bwrsCp, gergCp * 0.05);
    assertEquals(gergJT, bwrsJT, Math.abs(gergJT) * 0.05);
    assertEquals(gergCo, bwrsCo, Math.abs(gergCo) * 0.05);
    assertEquals(gergSpeed, bwrsSpeed, gergSpeed * 0.05);
    assertEquals(prDensity, bwrsDensity, prDensity * 0.05);
    assertEquals(prZ, bwrsZ, prZ * 0.05);
    assertEquals(prCp, bwrsCp, prCp * 0.25);
    assertEquals(prJT, bwrsJT, Math.abs(prJT) * 0.25);
    assertEquals(prCo, bwrsCo, Math.abs(prCo) * 0.05);
    assertEquals(prSpeed, bwrsSpeed, prSpeed * 0.25);
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
