/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.processSimulation.mechanicalDesign.designStandards;

import neqsim.processSimulation.mechanicalDesign.MechanicalDesign;

/**
 *
 * @author esol
 */
public class AdsorptionDehydrationDesignStandard extends DesignStandard {

    private static final long serialVersionUID = 1000;

    private double molecularSieveWaterCapacity = 20;// %

    public AdsorptionDehydrationDesignStandard(String name, MechanicalDesign equipmentInn) {
        super(name, equipmentInn);

        neqsim.util.database.NeqSimTechnicalDesignDatabase database = new neqsim.util.database.NeqSimTechnicalDesignDatabase();
        java.sql.ResultSet dataSet = null;
        try {
            try {
                dataSet = database.getResultSet(
                        ("SELECT * FROM technicalrequirements_process WHERE EQUIPMENTTYPE='Adsorber Dehydration' AND Company='"
                                + standardName + "'"));
                while (dataSet.next()) {
                    String specName = dataSet.getString("SPECIFICATION");
                    if (specName.equals("MolecularSieve3AWaterCapacity")) {
                        molecularSieveWaterCapacity = Double.parseDouble(dataSet.getString("MAXVALUE"));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (dataSet != null) {
                    dataSet.close();
                }
            } catch (Exception e) {
                System.out.println("error closing database.....GasScrubberDesignStandard");
                e.printStackTrace();
            }
        }
    }

    /**
     * @return the molecularSieveWaterCapacity
     */
    public double getMolecularSieveWaterCapacity() {
        return molecularSieveWaterCapacity;
    }

    /**
     * @param molecularSieveWaterCapacity the molecularSieveWaterCapacity to set
     */
    public void setMolecularSieveWaterCapacity(double molecularSieveWaterCapacity) {
        this.molecularSieveWaterCapacity = molecularSieveWaterCapacity;
    }
}
