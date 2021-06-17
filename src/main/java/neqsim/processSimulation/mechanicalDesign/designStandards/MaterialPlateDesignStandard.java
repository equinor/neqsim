package neqsim.processSimulation.mechanicalDesign.designStandards;

import neqsim.processSimulation.mechanicalDesign.MechanicalDesign;

/**
 *
 * @author esol
 */
public class MaterialPlateDesignStandard extends DesignStandard {

    private static final long serialVersionUID = 1000;

    public MaterialPlateDesignStandard() {
    }

    public MaterialPlateDesignStandard(String name, MechanicalDesign equipmentInn) {
        super(name, equipmentInn);
        readMaterialDesignStandard("Carbon Steel Plates and Sheets", "SA-516", "55", 1);
    }

    /**
     * @return the divisionClass
     */
    public double getDivisionClass() {
        return divisionClass;
    }

    /**
     * @param divisionClass the divisionClass to set
     */
    public void setDivisionClass(double divisionClass) {
        this.divisionClass = divisionClass;
    }

    String grade = "";

    String materialName = "";
    String specificationNumber = "";
    int divisionClassNumber = 1;
    private double divisionClass = 425;

    public void readMaterialDesignStandard(String name, String specNo, String grade, int divClassNo) {
        materialName = name;
        specificationNumber = specNo;
        divisionClassNumber = divClassNo;

        neqsim.util.database.NeqSimTechnicalDesignDatabase database = new neqsim.util.database.NeqSimTechnicalDesignDatabase();
        java.sql.ResultSet dataSet = null;
        try {
            try {
                dataSet = database.getResultSet(("SELECT * FROM materialplateproperties WHERE materialName='" + name
                        + "' AND grade='" + grade + "' AND specificationNumber='" + specNo + "'"));
                while (dataSet.next()) {
                    if (divClassNo == 1) {
                        divisionClass = (Double.parseDouble(dataSet.getString("divisionClass1"))) * 0.00689475729; // MPa
                    } else {
                        divisionClass = (Double.parseDouble(dataSet.getString("divisionClass2"))) * 0.00689475729; // MPa
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

            // gasLoadFactor = Double.parseDouble(dataSet.getString("gasloadfactor"));
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

}
