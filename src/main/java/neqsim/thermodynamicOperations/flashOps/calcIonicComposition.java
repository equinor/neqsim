/*
 * calcIonicComposition.java
 *
 * Created on 8. mars 2001, 10:56
 */

package neqsim.thermodynamicOperations.flashOps;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * calcIonicComposition class.
 * </p>
 *
 * @author even solbraa
 * @version $Id: $Id
 */
public class calcIonicComposition extends Flash {
    private static final long serialVersionUID = 1000;

    int phaseNumber;
    String[][] resultTable = null;

    /**
     * <p>
     * Constructor for calcIonicComposition.
     * </p>
     */
    public calcIonicComposition() {}

    /**
     * <p>
     * Constructor for calcIonicComposition.
     * </p>
     *
     * @param system a {@link neqsim.thermo.system.SystemInterface} object
     * @param phase a int
     */
    public calcIonicComposition(SystemInterface system, int phase) {
        this.system = system;
        phaseNumber = phase;
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        resultTable = new String[system.getPhase(0).getNumberOfComponents() + 2][4];
        resultTable[0][0] = "Component";
        resultTable[0][1] = "mmol/kgSolvent";
        resultTable[0][2] = "mg/kgSolvent";
        resultTable[0][3] = "Act.Coef";
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        DecimalFormat nf = new DecimalFormat();
        symbols.setDecimalSeparator('.');
        nf.setDecimalFormatSymbols(symbols);
        nf.setMaximumFractionDigits(5);
        nf.applyPattern("#.#####E0");
        int ionNumber = 0;
        for (int i = 0; i < system.getPhase(phaseNumber).getNumberOfComponents(); i++) {
            // if (system.getPhase(phaseNumber).getComponent(i).isIsIon()) {
            resultTable[ionNumber + 1][0] =
                    system.getPhase(phaseNumber).getComponent(i).getComponentName();
            resultTable[ionNumber + 1][1] = Double.valueOf(nf.format(system.getPhase(phaseNumber)
                    .getComponent(i).getNumberOfMolesInPhase()
                    / (system.getPhase(phaseNumber).getComponent("water").getNumberOfMolesInPhase()
                            * system.getPhase(phaseNumber).getComponent("water").getMolarMass())
                    * 1000)).toString();
            resultTable[ionNumber + 1][2] = Double.valueOf(nf.format(system.getPhase(phaseNumber)
                    .getComponent(i).getNumberOfMolesInPhase()
                    * system.getPhase(phaseNumber).getComponent(i).getMolarMass()
                    / (system.getPhase(phaseNumber).getComponent("water").getNumberOfMolesInPhase()
                            * system.getPhase(phaseNumber).getComponent("water").getMolarMass())
                    * 1e6)).toString();
            resultTable[ionNumber + 1][3] = Double
                    .valueOf(
                            nf.format(system.getPhase(phaseNumber)
                                    .getActivityCoefficient(i, system.getPhase(phaseNumber)
                                            .getComponent("water").getComponentNumber())))
                    .toString();

            ionNumber++;
            // }
        }
    }

    /** {@inheritDoc} */
    @Override
    public org.jfree.chart.JFreeChart getJFreeChart(String name) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public String[][] getResultTable() {
        return resultTable;
    }
}
