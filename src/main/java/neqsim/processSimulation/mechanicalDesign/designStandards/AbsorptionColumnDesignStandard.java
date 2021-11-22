package neqsim.processSimulation.mechanicalDesign.designStandards;

import neqsim.processSimulation.mechanicalDesign.MechanicalDesign;

/**
 *
 * @author esol
 */
public class AbsorptionColumnDesignStandard extends DesignStandard {
    private static final long serialVersionUID = 1000;

    private double molecularSieveWaterCapacity = 20;// %

    public AbsorptionColumnDesignStandard(String name, MechanicalDesign equipmentInn) {
        super(name, equipmentInn);

        neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
        java.sql.ResultSet dataSet = null;
        try {
            try {
                dataSet = database.getResultSet(
                        ("SELECT * FROM technicalrequirements WHERE EQUIPMENTTYPE='Absorber' AND Company='"
                                + standardName + "'"));
                while (dataSet.next()) {
                    String specName = dataSet.getString("SPECIFICATION");
                    if (specName.equals("MolecularSieve3AWaterCapacity")) {
                        molecularSieveWaterCapacity =
                                Double.parseDouble(dataSet.getString("MAXVALUE"));
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
