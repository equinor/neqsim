package neqsim.thermo.util.example;

import neqsim.thermo.system.SystemElectrolyteCPA;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * HenryConstantCalc class.
 * </p>
 *
 * @author esol
 * @since 2.2.3
 * @version $Id: $Id
 */
public class HenryConstantCalc {
    /**
     * <p>
     * main.
     * </p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String args[]) {
        SystemInterface testSystem = new SystemElectrolyteCPA(273.15 + 40.0, 10.0);
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        testSystem.addComponent("CO2", 0.5);
        testSystem.addComponent("water", 10.0);
        testSystem.addComponent("MDEA", 1.0);
        testSystem.chemicalReactionInit();

        testSystem.createDatabase(true);
        testSystem.setMixingRule(7);
        testSystem.init(0);

        try {
            testOps.bubblePointPressureFlash(false);
            testSystem.display();
            // testOps.hydrateFormationTemperature(0);
        } catch (Exception e) {
        }

        // testSystem.getChemicalReactionOperations().solveChemEq(1);
        // System.out.println("Henrys Constant " +
        // testSystem.getPhase(0).getComponent("CO2").getx()/testSystem.getPhase(1).getComponent("CO2").getx()*testSystem.getPressure());
        // System.out.println("Henrys Constant2 " +
        // testSystem.calcHenrysConstant("CO2"));//
        // System.out.println("activity MDEA " +
        // testSystem.getPhase(1).getActivityCoefficient(0));
        // double meanact2 = testSystem.getPhase(1).getMeanIonicActivity(0,1);
        // System.out.println("mean ionic-activity: " + meanact2);
        // double osm = testSystem.getPhase(1).getOsmoticCoefficientOfWater();
        // System.out.println("osm: " + osm);
        testSystem.display();
    }
}
