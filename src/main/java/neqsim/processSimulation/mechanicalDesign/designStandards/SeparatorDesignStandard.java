/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.processSimulation.mechanicalDesign.designStandards;

import neqsim.processSimulation.mechanicalDesign.MechanicalDesign;
import neqsim.processSimulation.processEquipment.separator.SeparatorInterface;

/**
 *
 * @author esol
 */
public class SeparatorDesignStandard extends DesignStandard {

    private static final long serialVersionUID = 1000;

    /**
     * @return the Fg
     */
    public double getFg() {
        return Fg;
    }

    /**
     * @param Fg the Fg to set
     */
    public void setFg(double Fg) {
        this.Fg = Fg;
    }

    double gasLoadFactor = 0.11;
    private double Fg = 0.8;
    private double volumetricDesignFactor = 1.0;

    public SeparatorDesignStandard(String name, MechanicalDesign equipmentInn) {
        super(name, equipmentInn);
        neqsim.util.database.NeqSimTechnicalDesignDatabase database = new neqsim.util.database.NeqSimTechnicalDesignDatabase();
        java.sql.ResultSet dataSet = null;
        try {
            try {
                dataSet = database.getResultSet(
                        ("SELECT * FROM technicalrequirements_process WHERE EQUIPMENTTYPE='Separator' AND Company='"
                                + standardName + "'"));
                while (dataSet.next()) {
                    String specName = dataSet.getString("SPECIFICATION");
                    if (specName.equals("GasLoadFactor")) {
                        gasLoadFactor = (Double.parseDouble(dataSet.getString("MAXVALUE"))
                                + Double.parseDouble(dataSet.getString("MINVALUE"))) / 2.0;
                    }
                    if (specName.equals("Fg")) {
                        Fg = (Double.parseDouble(dataSet.getString("MAXVALUE"))
                                + Double.parseDouble(dataSet.getString("MINVALUE"))) / 2.0;
                    }
                    if (specName.equals("VolumetricDesignFactor")) {
                        volumetricDesignFactor = (Double.parseDouble(dataSet.getString("MAXVALUE"))
                                + Double.parseDouble(dataSet.getString("MINVALUE"))) / 2.0;
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
        if (standardName.equals("StatoilTR")) {
            return gasLoadFactor;
        } else {
            return gasLoadFactor;
        }
    }

    public double getVolumetricDesignFactor() {
        if (standardName.equals("StatoilTR")) {
            return volumetricDesignFactor;
        } else {
            return volumetricDesignFactor;
        }
    }

    /**
     * @param volumetricDesignFactor the volumetricDesignFactor to set
     */
    public void setVolumetricDesignFactor(double volumetricDesignFactor) {
        this.volumetricDesignFactor = volumetricDesignFactor;
    }

    public double getLiquidRetentionTime(String name, MechanicalDesign equipmentInn) {
        double retTime = 90.0;
        double dens = ((SeparatorInterface) equipmentInn.getProcessEquipment()).getThermoSystem().getPhase(1)
                .getPhysicalProperties().getDensity() / 1000.0;

        // select correct residensetime from database
        // to be implmented
        if (name.equals("API12J")) {
            if (dens < 0.85)
                retTime = 60.0;
            else if (dens > 0.93)
                retTime = 180.0;
        }
        return retTime;
    }
}
