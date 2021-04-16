/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.processSimulation.mechanicalDesign.designStandards;

import neqsim.processSimulation.mechanicalDesign.MechanicalDesign;

/**
 *
 * @author esol
 */
public class MaterialPipeDesignStandard extends DesignStandard {

    private static final long serialVersionUID = 1000;

    public MaterialPipeDesignStandard() {
    }

    public MaterialPipeDesignStandard(String name, MechanicalDesign equipmentInn) {
        super(name, equipmentInn);
        readMaterialDesignStandard("Carbon Steel Pipe", "A25");
    }

    /**
     * @return the designFactor
     */
    public double getDesignFactor() {
        return designFactor;
    }

    /**
     * @param designFactor the designFactor to set
     */
    public void setDesignFactor(double designFactor) {
        this.designFactor = designFactor;
    }

    /**
     * @return the Efactor
     */
    public double getEfactor() {
        return Efactor;
    }

    /**
     * @param Efactor the Efactor to set
     */
    public void setEfactor(double Efactor) {
        this.Efactor = Efactor;
    }

    /**
     * @return the temperatureDeratingFactor
     */
    public double getTemperatureDeratingFactor() {
        return temperatureDeratingFactor;
    }

    /**
     * @param temperatureDeratingFactor the temperatureDeratingFactor to set
     */
    public void setTemperatureDeratingFactor(double temperatureDeratingFactor) {
        this.temperatureDeratingFactor = temperatureDeratingFactor;
    }

    /**
     * @return the minimumYeildStrength
     */
    public double getMinimumYeildStrength() {
        return minimumYeildStrength;
    }

    /**
     * @param minimumYeildStrength the minimumYeildStrength to set
     */
    public void setMinimumYeildStrength(double minimumYeildStrength) {
        this.minimumYeildStrength = minimumYeildStrength;
    }

    String grade = "";
    String specName = "";
    String specificationNumber = "";
    private double minimumYeildStrength = 35000 * 0.00689475729;
    private double designFactor = 0.8;
    private double Efactor = 1.0;
    private double temperatureDeratingFactor = 1.0;

    public void readMaterialDesignStandard(String specNo, String grade) {
        grade = grade;
        specificationNumber = specNo;

        neqsim.util.database.NeqSimTechnicalDesignDatabase database = new neqsim.util.database.NeqSimTechnicalDesignDatabase();
        java.sql.ResultSet dataSet = null;
        try {
            try {
                dataSet = database.getResultSet(("SELECT * FROM materialpipeproperties WHERE specificationNumber='"
                        + specificationNumber + "' AND grade='" + grade + "'"));
                while (dataSet.next()) {

                    minimumYeildStrength = (Double.parseDouble(dataSet.getString("minimumYeildStrength")))
                            * 0.00689475729;

                    // design factor table has to be developed
                    // Efactor table has to be implemented
                    // temperatureDeratingFactor has to be implemented
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
