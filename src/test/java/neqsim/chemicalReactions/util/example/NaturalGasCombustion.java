package neqsim.chemicalReactions.util.example;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>NaturalGasCombustion class.</p>
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class NaturalGasCombustion  extends neqsim.NeqSimTest{
    /**
     * <p>main.</p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    @SuppressWarnings("unused")
    public static void main(String[] args) {
        SystemInterface testSystem = new SystemSrkEos(303.3, 2.8);

        testSystem.addComponent("methane", 5.0, "kg/sec");
        // testSystem.addComponent("nitrogen", 100.0, "kg/sec");
        testSystem.addComponent("oxygen", 40.0, "kg/sec");
        testSystem.chemicalReactionInit();

        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);
        testSystem.init(0);
        testSystem.setMaxNumberOfPhases(1);
        testSystem.setNumberOfPhases(1);
        testSystem.getChemicalReactionOperations().solveChemEq(0, 0);
        // testSystem.getChemicalReactionOperations().solveChemEq(0,1);
        testSystem.display();
        ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
        // ops.compustionCalc();
    }
}
