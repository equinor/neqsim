package neqsim.physicalProperties.util.examples;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import org.apache.logging.log4j.*;

/**
 *
 * @author esol //
 */
public class TestDiffusionCoefficient {
    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(TestDiffusionCoefficient.class);

    public static void main(String args[]) {
        SystemInterface testSystem = new SystemSrkEos(273.15 + 28.66, 12.2);
        testSystem.addComponent("nitrogen", 0.037);
        testSystem.addComponent("n-heptane", 0.475);
        testSystem.addComponent("water", 0.475);

        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);
        testSystem.setMultiPhaseCheck(true);

        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        try {
            testOps.TPflash();
            testSystem.display();
        } catch (Exception e) {
            logger.error(e.toString());
        }

        System.out.println("binary diffusion coefficient water in nitrogen gas "
                + testSystem.getPhase("gas").getPhysicalProperties()
                        .getDiffusionCoefficient("water", "nitrogen")
                + " m2/sec");
        System.out
                .println(
                        "binary diffusion coefficient nitrogen in liquid n-heptane "
                                + testSystem.getPhase("oil").getPhysicalProperties()
                                        .getDiffusionCoefficient("nitrogen", "n-heptane")
                                + " m2/sec");
        System.out.println("binary diffusion coefficient nitrogen in water "
                + testSystem.getPhase("aqueous").getPhysicalProperties()
                        .getDiffusionCoefficient("nitrogen", "water")
                + " m2/sec");

        System.out.println("effective diffusion coefficient water in gas " + testSystem
                .getPhase("gas").getPhysicalProperties().getEffectiveDiffusionCoefficient("water")
                + " m2/sec");
        System.out.println("effective diffusion coefficient nitrogen in liquid n-heptane "
                + testSystem.getPhase("oil").getPhysicalProperties()
                        .getEffectiveDiffusionCoefficient("nitrogen")
                + " m2/sec");
        System.out.println("effective diffusion coefficient nitrogen in water "
                + testSystem.getPhase("aqueous").getPhysicalProperties()
                        .getEffectiveDiffusionCoefficient("nitrogen")
                + " m2/sec");
    }
}
