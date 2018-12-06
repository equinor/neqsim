/*
 * StandardInterface.java
 *
 * Created on 13. juni 2004, 23:29
 */

package neqsim.standards;

import neqsim.standards.salesContract.ContractInterface;
import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author  ESOL
 */
public interface StandardInterface {
    double getValue(java.lang.String returnParameter, java.lang.String returnUnit);
    double getValue(java.lang.String returnParameter);
    public String getUnit(String returnParameter);
    public void calculate();
    public boolean isOnSpec();
    public ContractInterface getSalesContract();
    public void setSalesContract(String name);
    public String getName();
    public void setSalesContract(ContractInterface salesContract);
    public String getStandardDescription();
    public String[][] createTable(String name);
    public void display(String name);
    public SystemInterface getThermoSystem();
    public void setThermoSystem(SystemInterface thermoSystem);
    public String[][] getResultTable();
    public void setResultTable(String[][] resultTable);
}
