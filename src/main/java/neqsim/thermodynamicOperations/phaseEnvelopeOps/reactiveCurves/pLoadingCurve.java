/*
 * pTphaseEnvelope.java
 *
 * Created on 14. oktober 2000, 21:59
 */

package neqsim.thermodynamicOperations.phaseEnvelopeOps.reactiveCurves;

import java.awt.*;
import java.text.*;
import javax.swing.*;
import neqsim.dataPresentation.JFreeChart.graph2b;
import neqsim.dataPresentation.fileHandeling.createNetCDF.netCDF2D.NetCdf2D;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.OperationInterface;
import org.apache.logging.log4j.*;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public class pLoadingCurve extends Object implements OperationInterface, java.io.Serializable{

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(pLoadingCurve.class);
    
    SystemInterface system;
    int i, j=0, nummer=0, iterations=0,maxNumberOfIterations=10000;
    double gibbsEnergy=0, gibbsEnergyOld=0;
    double Kold, deviation=0, g0 = 0, g1=0;
    double lnOldOldK[], lnK[];
    double lnOldK[];
    double oldDeltalnK[], deltalnK[];
    double tm[] = {1,1};
    double beta = 1e-5;
    int lowestGibbsEnergyPhase=0; // lowestGibbsEnergyPhase
    JProgressBar monitor;
    JFrame mainFrame;
    JPanel mainPanel;
    
    
    double temp=0, pres=0, startPres=0;
    double[][] points = new double[35][];
    
    boolean moreLines=false;
    int np=0;
    //points[2] = new double[1000];
    int speceq=0;
    
    /** Creates new bubblePointFlash */
    public pLoadingCurve() {
    }
    
    public pLoadingCurve(SystemInterface system) {
        this.system = system;
        lnOldOldK = new double[system.getPhases()[0].getNumberOfComponents()];
        lnOldK = new double[system.getPhases()[0].getNumberOfComponents()];
        lnK = new double[system.getPhases()[0].getNumberOfComponents()];
        oldDeltalnK = new double[system.getPhases()[0].getNumberOfComponents()];
        deltalnK = new double[system.getPhases()[0].getNumberOfComponents()];
        mainFrame = new JFrame("Progress Bar");
        mainPanel = new JPanel();
        mainPanel.setSize(200,100);
        mainFrame.getContentPane().setLayout(new FlowLayout());
        mainPanel.setLayout(new FlowLayout());
        mainFrame.setSize(200,100);
        monitor = new JProgressBar(0,1000);
        monitor.setSize(200,100);
        monitor.setStringPainted(true);
        mainPanel.add(monitor);
        mainFrame.getContentPane().add(mainPanel);
        mainFrame.setVisible(true);
    }
    
    
    public void run(){
        int numbPoints=50;
        double inscr = 0.2275;
        points[0] = new double[numbPoints];
        points[1] = new double[numbPoints];
        
        for(int k=0;k<system.getPhases()[1].getNumberOfComponents();k++){
            points[k+2] = new double[numbPoints];
            points[k+2+system.getPhases()[1].getNumberOfComponents()] = new double[numbPoints];
        }
        
        double molMDEA = system.getPhases()[1].getComponents()[2].getNumberOfMolesInPhase();
        system.getChemicalReactionOperations().solveChemEq(0);
        
        for(int i=1;i<points[0].length;i++){
            system.init_x_y();
            system.getChemicalReactionOperations().solveChemEq(1);
            
            points[0][i] = (inscr*(i-1))/molMDEA;
            points[1][i] = (system.getPhases()[1].getComponents()[0].getFugasityCoeffisient()*system.getPhases()[1].getComponents()[0].getx()*system.getPressure());
            
            for(int k=0;k<system.getPhases()[1].getNumberOfComponents();k++){
                points[k+2][i] = system.getPhases()[1].getComponents()[k].getx();
                points[k+2+system.getPhases()[1].getNumberOfComponents()][i] = system.getPhases()[1].getActivityCoefficient(k,1);
            }
            logger.info("point: " + points[0][i] + "  " + points[1][i]);
            system.setPressure(points[1][i]);
            logger.info("ph: " + system.getPhases()[1].getpH());
            system.addComponent(0, inscr, 1);
        }
        mainFrame.setVisible(false);
    }
    
    public void displayResult(){
        DecimalFormat nf = new DecimalFormat();
        nf.setMaximumFractionDigits(1);
        nf.applyPattern("####.#");
        
        double TC = system.getTC();
        double PC = system.getPC();
        logger.info("tc : " + TC + "  PC : " + PC);
        String[] navn= {"CO2 fugacity", "","",""};
        String title2= "";
        String title = "CO2 vapour pressure";
        
        graph2b graph2 = new graph2b(points, navn, title, "loading [-]", "Fugacity CO2 [bar]");
        graph2.setVisible(true);
    }
    
    public void printToFile(String name){
        neqsim.dataPresentation.dataHandeling printDat = new neqsim.dataPresentation.dataHandeling();
        printDat.printToFile(points, name);
    }
    
    public double[][] getPoints(int i){
        return points;
    }
    
    public void createNetCdfFile(String name){
        NetCdf2D file = new NetCdf2D();
        file.setOutputFileName(name);
        file.setXvalues(points[0],"loading","");
        file.setYvalues(points[1],"pressure","");
        for(int k=0;k<system.getPhases()[1].getNumberOfComponents();k++){
            file.setYvalues(points[k+2],"mol frac " + system.getPhases()[1].getComponents()[k].getComponentName(),"");
            file.setYvalues(points[k+2+system.getPhases()[1].getNumberOfComponents()], ("activity " + system.getPhases()[1].getComponents()[k].getComponentName()),"");
        }
        file.createFile();
    }
    
    public double[] get(String name){
        return null;
    }
    
     public org.jfree.chart.JFreeChart getJFreeChart(String name){
        return null;
    }
     
     public String[][] getResultTable(){
        return null;
    }

     public SystemInterface getThermoSystem(){
       return system;
   }
     
      public void addData(String name, double[][] data){
        
    }
}
