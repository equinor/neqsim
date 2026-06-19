package neqsim.physicalproperties.methods.diffusivity;

import org.junit.jupiter.api.Test;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Diagnostic test to verify database parameter values and units.
 */
public class DiffusivityDiagnosticTest {
  private static final Logger logger = LogManager.getLogger(DiffusivityDiagnosticTest.class);

  @Test
  void printComponentProperties() {
    SystemInterface sys = new SystemSrkEos(298.15, 1.01325);
    sys.addComponent("methane", 0.5);
    sys.addComponent("nitrogen", 0.3);
    sys.addComponent("CO2", 0.1);
    sys.addComponent("water", 0.1);
    sys.createDatabase(true);
    sys.setMixingRule(2);
    ThermodynamicOperations ops = new ThermodynamicOperations(sys);
    ops.TPflash();
    sys.initPhysicalProperties();

    logger.info("\n=== Component Database Properties ===");
    for (int i = 0; i < sys.getNumberOfComponents(); i++) {
      logger.info("\nComponent: " + sys.getComponent(i).getComponentName());
      logger.info("  MolarMass (kg/mol): " + sys.getComponent(i).getMolarMass());
      logger.info("  MolarMass (g/mol):  " + sys.getComponent(i).getMolarMass() * 1000);
      logger.info("  CriticalVolume:     " + sys.getComponent(i).getCriticalVolume());
      logger.info("  CriticalTemp (K):   " + sys.getComponent(i).getTC());
      logger.info("  CriticalPres (bara):" + sys.getComponent(i).getPC());
      logger.info("  NormLiqDens (g/cm3):" + sys.getComponent(i).getNormalLiquidDensity());
      logger.info("  LJ diameter (A):    " + sys.getComponent(i).getLennardJonesMolecularDiameter());
      System.out.println("  LJ epsilon/k (K):   " + sys.getComponent(i).getLennardJonesEnergyParameter());
      logger.info("  NormBoilPt (K):     " + sys.getComponent(i).getNormalBoilingPoint());
      logger.info("  AcentricFactor:     " + sys.getComponent(i).getAcentricFactor());
    }

    // Verify Zc calculation for methane
    double Pc = sys.getComponent(0).getPC();
    double Vc = sys.getComponent(0).getCriticalVolume();
    double Tc = sys.getComponent(0).getTC();
    double R = 8.314472;
    double Zc = Pc * Vc / (R * Tc * 10.0);
    logger.info("\n=== Methane Zc verification ===");
    logger.info("Pc=" + Pc + " Vc=" + Vc + " Tc=" + Tc);
    logger.info("Zc = Pc*Vc/(R*Tc*10) = " + Zc);
    logger.info("Expected Zc ~ 0.286");

    // Test gas phase diffusion coefficients
    if (sys.hasPhaseType("gas")) {
      logger.info("\n=== Gas Diffusion Coefficients (default Chapman-Enskog) ===");
      for (int i = 0; i < sys.getPhase("gas").getNumberOfComponents(); i++) {
	for (int j = i + 1; j < sys.getPhase("gas").getNumberOfComponents(); j++) {
	  double D = sys.getPhase("gas").getPhysicalProperties().diffusivityCalc.calcBinaryDiffusionCoefficient(i, j,
	      0);
	  logger.info("  D(" + sys.getComponent(i).getComponentName() + "-" + sys.getComponent(j).getComponentName()
	      + ") = " + D + " m2/s = " + (D * 1e4) + " cm2/s");
	}
      }

      // Switch to Fuller
      sys.getPhase("gas").getPhysicalProperties().setDiffusionCoefficientModel("Fuller-Schettler-Giddings");
      logger.info("\n=== Gas Diffusion Coefficients (Fuller-Schettler-Giddings) ===");
      for (int i = 0; i < sys.getPhase("gas").getNumberOfComponents(); i++) {
	for (int j = i + 1; j < sys.getPhase("gas").getNumberOfComponents(); j++) {
	  double D = sys.getPhase("gas").getPhysicalProperties().diffusivityCalc.calcBinaryDiffusionCoefficient(i, j,
	      0);
	  logger.info("  D(" + sys.getComponent(i).getComponentName() + "-" + sys.getComponent(j).getComponentName()
	      + ") = " + D + " m2/s = " + (D * 1e4) + " cm2/s");
	}
      }

      // Wilke-Lee
      sys.getPhase("gas").getPhysicalProperties().setDiffusionCoefficientModel("Wilke Lee");
      logger.info("\n=== Gas Diffusion Coefficients (Wilke-Lee) ===");
      for (int i = 0; i < sys.getPhase("gas").getNumberOfComponents(); i++) {
	for (int j = i + 1; j < sys.getPhase("gas").getNumberOfComponents(); j++) {
	  double D = sys.getPhase("gas").getPhysicalProperties().diffusivityCalc.calcBinaryDiffusionCoefficient(i, j,
	      0);
	  logger.info("  D(" + sys.getComponent(i).getComponentName() + "-" + sys.getComponent(j).getComponentName()
	      + ") = " + D + " m2/s = " + (D * 1e4) + " cm2/s");
	}
      }
    }
  }
}
