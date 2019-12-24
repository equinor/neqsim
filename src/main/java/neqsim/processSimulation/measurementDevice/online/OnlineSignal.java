/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.processSimulation.measurementDevice.online;

import java.util.Date;
import neqsim.util.database.AspenIP21Database;

/**
 *
 * @author esol
 */
public class OnlineSignal {

    private static final long serialVersionUID = 1000;

    /**
     * @return the unit
     */
    public String getUnit() {
        return unit;
    }

    /**
     * @param unit the unit to set
     */
    public void setUnit(String unit) {
        this.unit = unit;
    }

    Date dateStamp = new Date();
    String name = "";
    String plantName = "Kaarsto";
    String transmitterName = "21TI1117";
    java.sql.ResultSet dataSet = null;
    double value = 1.0;
    private String unit = "C";
    neqsim.util.database.AspenIP21Database database = null;

    public OnlineSignal(String plantName, String transmitterName) {
        this.plantName = plantName;
        this.transmitterName = transmitterName;

        connect();
    }

    public boolean connect() {
        if (plantName.equals("Karsto")) {
            database = new neqsim.util.database.AspenIP21Database();
        } else {
            database = new neqsim.util.database.AspenIP21Database();
        }
        try {
            dataSet = database.getResultSet(("SELECT * FROM IP_AnalogDef WHERE NAME='" + name + "'"));
            dataSet.next();
            value = dataSet.getDouble("IP_VALUE");
        } catch (Exception e) {
            //dataSet.close();
            return false;
        }
        return true;
    }

    public Date getTimeStamp() {
        return dateStamp;
    }

    public double getValue() {
        try {
           // System.out.println("reading online vale from: " + transmitterName );
            dataSet = database.getResultSet(("SELECT * FROM IP_AnalogDef WHERE NAME='" + transmitterName + "'"));
            dataSet.next();
            value = dataSet.getDouble("IP_VALUE");
            //System.out.println("value + " + value );
        } catch (Exception e) {
            //dataSet.close();
            return 0;
        } finally {
            try {
                dataSet.close();
            } catch (Exception e) {
                //dataSet.close();
                return 0;
            }
        }
        dateStamp = new Date(); // read dateStamp
        return value; // read online measurement
    }
}
