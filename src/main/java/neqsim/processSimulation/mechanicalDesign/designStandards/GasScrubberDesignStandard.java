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
public class GasScrubberDesignStandard extends DesignStandard {

    private static final long serialVersionUID = 1000;

    double gasLoadFactor = 0.11;
    double designFactorVolumeFlow = 1.1;
    double lengthGasInletToMeshpad = 550.0; // unit: mm
    double lengthGasInetToHHLL = 550.0; // unit: mm
    double lengthMeshPadToDemistingCyclone = 550.0; // unit: mm

    public GasScrubberDesignStandard(String name, MechanicalDesign equipmentInn) {
        super(name, equipmentInn);

        neqsim.util.database.NeqSimTechnicalDesignDatabase database = new neqsim.util.database.NeqSimTechnicalDesignDatabase();
        java.sql.ResultSet dataSet = null;
        try {
            try {
                dataSet = database.getResultSet(
                        ("SELECT * FROM technicalrequirements_process WHERE EQUIPMENTTYPE='Gas scrubber' AND Company='"
                                + standardName + "'"));
                while (dataSet.next()) {
                    String specName = dataSet.getString("SPECIFICATION");
                    if (specName.equals("GasLoadFactor")) {
                        gasLoadFactor = Double.parseDouble(dataSet.getString("MAXVALUE"));
                    }
                    if (specName.equals("FlowDesignFactor")) {
                        designFactorVolumeFlow = Double.parseDouble(dataSet.getString("MAXVALUE"));
                    }
                    if (specName.equals("LengthGasInetToHHLL")) {
                        designFactorVolumeFlow = Double.parseDouble(dataSet.getString("MINVALUE"));
                    }
                    if (specName.equals("LengthMeshPadToDemistingCyclone")) {
                        designFactorVolumeFlow = Double.parseDouble(dataSet.getString("MINVALUE"));
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

    public double getGasLoadFactor() {
        return gasLoadFactor;
    }

    public double getVolumetricDesignFactor() {
        return designFactorVolumeFlow;
    }
}
