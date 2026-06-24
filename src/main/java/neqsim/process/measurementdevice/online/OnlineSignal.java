package neqsim.process.measurementdevice.online;

import java.util.Date;

/**
 * OnlineSignal class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class OnlineSignal implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Getter for the field <code>unit</code>.
   *
   * @return the unit
   */
  public String getUnit() {
    return unit;
  }

  /**
   * Setter for the field <code>unit</code>.
   *
   * @param unit the unit to set
   */
  public void setUnit(String unit) {
    this.unit = unit;
  }

  Date dateStamp = new Date();
  String name = "";
  String plantName = "Kaarsto";
  String transmitterName = "21TI1117";
  transient java.sql.ResultSet dataSet = null;
  double value = 1.0;
  private String unit = "C";
  transient neqsim.util.database.AspenIP21Database database = null;

  /**
   * Constructor for OnlineSignal.
   *
   * @param plantName a {@link java.lang.String} object
   * @param transmitterName a {@link java.lang.String} object
   */
  public OnlineSignal(String plantName, String transmitterName) {
    this.plantName = plantName;
    this.transmitterName = transmitterName;

    connect();
  }

  /**
   * connect.
   *
   * @return a boolean
   */
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
    } catch (Exception ex) {
      // dataSet.close();
      return false;
    }
    return true;
  }

  /**
   * getTimeStamp.
   *
   * @return a {@link java.util.Date} object
   */
  public Date getTimeStamp() {
    return dateStamp;
  }

  /**
   * Getter for the field <code>value</code>.
   *
   * @return a double
   */
  public double getValue() {
    try {
      // System.out.println("reading online vale from: " + transmitterName );
      dataSet = database.getResultSet(("SELECT * FROM IP_AnalogDef WHERE NAME='" + transmitterName + "'"));
      dataSet.next();
      value = dataSet.getDouble("IP_VALUE");
      // System.out.println("value + " + value );
    } catch (Exception ex) {
      // dataSet.close();
      return 0;
    } finally {
      try {
	dataSet.close();
      } catch (Exception ex) {
	// dataSet.close();
	return 0;
      }
    }
    dateStamp = new Date(); // read dateStamp
    return value; // read online measurement
  }
}
