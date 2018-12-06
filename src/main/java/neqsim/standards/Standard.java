/*
 * Standard.java
 *
 * Created on 13. juni 2004, 23:56
 */

package neqsim.standards;

import java.awt.*;
import java.text.*;
import javax.swing.*;
import neqsim.standards.salesContract.BaseContract;
import neqsim.standards.salesContract.ContractInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
/**
 *
 * @author  ESOL
 */
public abstract class Standard implements StandardInterface{

    private static final long serialVersionUID = 1000;
    
    protected String name = "Base Standard";
    protected String standardDescription = "Base Description";
    protected ContractInterface salesContract = new BaseContract();
    protected String[][] resultTable = null;
    transient protected SystemInterface thermoSystem;
    protected ThermodynamicOperations thermoOps;
    private String referenceState = "real";  //"ideal"real
    
    /** Creates a new instance of Standard */
    public Standard() {
    }
    
    public Standard(SystemInterface thermoSyst) {
        thermoSystem = thermoSyst;
    }
    
    public SystemInterface getThermoSystem(){
    return thermoSystem;
    }
    
    public void setThermoSystem(SystemInterface thermoSystem){
     this.thermoSystem = thermoSystem;
    }
    
    public void setSalesContract(String name){
        if(name.equals("baseContract")) {
            salesContract = new BaseContract();
        }
    }
    
    public void setSalesContract(ContractInterface salesContract){
        this.salesContract = salesContract;
    }
    
    public ContractInterface getSalesContract() {
        return salesContract;
    }
    
    /** Getter for property name.
     * @return Value of property name.
     *
     */
    public String getName() {
        return name;
    }
    
    /** Setter for property name.
     * @param name New value of property name.
     *
     */
    public void setName(String name) {
        this.name = name;
    }
    
    /** Getter for property standardDescription.
     * @return Value of property standardDescription.
     *
     */
    public String getStandardDescription() {
        return standardDescription;
    }
    
    /** Setter for property standardDescription.
     * @param standardDescription New value of property standardDescription.
     *
     */
    public void setStandardDescription(String standardDescription) {
        this.standardDescription = standardDescription;
    }
    
    public String[][] createTable(String name){
        thermoSystem.setNumberOfPhases(1);
        
        thermoSystem.createTable(name);
        
        DecimalFormat nf = new DecimalFormat();
        nf.setMaximumFractionDigits(5);
        nf.applyPattern("#.#####E0");
        String[][] table = new String[thermoSystem.getPhases()[0].getNumberOfComponents()+30][6];
        String[] names = {"", "Phase 1", "Phase 2", "Phase 3", "Unit"};
        table[0][0] = "";//getPhases()[0].getPhaseTypeName();//"";
        
        for(int i=0;i<thermoSystem.getPhases()[0].getNumberOfComponents()+30;i++){
            for(int j=0;j<6;j++){
                table[i][j]="";
            }
        }
        for(int i=0;i<thermoSystem.getNumberOfPhases();i++){
            table[0][i+1] = thermoSystem.getPhase(i).getPhaseTypeName();
        }
        
        StringBuffer buf = new StringBuffer();
        FieldPosition test = new FieldPosition(0);
        for(int i=0;i<thermoSystem.getNumberOfPhases();i++){
            for(int j=0;j<thermoSystem.getPhases()[0].getNumberOfComponents();j++){
                table[j+1][0] = thermoSystem.getPhases()[0].getComponents()[j].getName();
                buf = new StringBuffer();
                table[j+1][i+1] = nf.format(thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getComponents()[j].getx(), buf, test).toString();
                table[j+1][4] = "[-]";
            }
        }
        
        resultTable = table;
        return table;
    }
    
    public void display(String name){
        JDialog dialog = new JDialog(new JFrame(), "Standard-Report");
        Container dialogContentPane = dialog.getContentPane();
        dialogContentPane.setLayout(new BorderLayout());
        
        String[] names = {"", "Phase 1", "Phase 2", "Phase 3", "Unit"};
        String[][] table = createTable(name);
        JTable Jtab = new JTable(table,names);
        JScrollPane scrollpane = new JScrollPane(Jtab);
        dialogContentPane.add(scrollpane);
        dialog.pack();
        dialog.setVisible(true);
    }
    
    public String[][] getResultTable() {
        return resultTable;
    }
    
    public void setResultTable(String[][] resultTable) {
        this.resultTable = resultTable;
    }
    
     /**
     * @return the referenceState
     */
    public String getReferenceState() {
        return referenceState;
    }

    /**
     * @param referenceState the referenceState to set
     */
    public void setReferenceState(String referenceState) {
        this.referenceState = referenceState;
    }
    
}
