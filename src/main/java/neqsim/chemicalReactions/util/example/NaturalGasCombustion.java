package neqsim.chemicalReactions.util.example;

import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

public class NaturalGasCombustion {

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
