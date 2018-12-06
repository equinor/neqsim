/*
 * visAdContourPlot.java
 *
 * Created on 7. november 2000, 17:51
 */

package neqsim.dataPresentation.visAD.visAd3D.statistical3DPlot;

import java.rmi.RemoteException;
import javax.swing.*;
import visad.*;
import visad.java3d.DisplayImplJ3D;
import visad.util.*;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public class surfaceFitPlot extends java.lang.Object {

    private static final long serialVersionUID = 1000;
    
    private RealType longitude, latitude, temperature, isotemperature;
    
    private RealTupleType domain_tuple;
    private FunctionType func_domain_range, func_domain_iso_range;
    private Set domain_set;
    private FlatField vals_ff, iso_vals_ff;
    private DataReferenceImpl data_ref,  iso_data_ref;
    private DisplayImpl display;
    private ScalarMap latMap, lonMap;
    private ScalarMap tempIsoMap, tempRGBMap,tempIsoMapIso, tempRGBMap2, tempIsoMap2;
    int NCOLS=0, NROWS = 0;
    float[][] set_samples;
    double[][] z_samples;
    private ContourWidget contourWid;
    
    
    /** Creates new visAdContourPlot */
    public surfaceFitPlot(String firstax, String secax, String zax) throws RemoteException, VisADException{
        
        latitude = new RealType(firstax);
        longitude = new RealType(secax);
        domain_tuple = new RealTupleType(latitude, longitude);
        temperature = new RealType(zax);
        isotemperature = new RealType("isoTemperature");
        func_domain_range = new FunctionType(domain_tuple, temperature);
        func_domain_iso_range = new FunctionType(domain_tuple, isotemperature);
        
    }
    
    public void setXYvals(double xMin, double xMax, int Nrows, double yMin, double yMax, int NCols)throws RemoteException, VisADException{
        NCOLS = NCols;
        NROWS = Nrows;
        
        domain_set = new Linear2DSet(domain_tuple, xMin, xMax, NROWS, yMin, yMax, NCOLS);
        
        set_samples = domain_set.getSamples( true );
        
    }
    
  /*   public void setXYals(double[] xvals, double[] yvals) throws RemoteException, VisADException{
        domain_set = new Linear2DSet(domain_tuple, xMin, xMax, NROWS, yMin, yMax, NCOLS);
        set_samples = domain_set.getSamples( true );
     }*/
    
    public void setZvals(double[][] vals)throws RemoteException, VisADException{
   
      z_samples = vals;
    }
    
    public void init() throws RemoteException, VisADException{
        
        float[][] flat_samples = new float[1][NCOLS*NROWS];
        
        for(int c = 0; c < NCOLS; c++){
            
            for(int r = 0; r < NROWS; r++){
                
                flat_samples[0][ c * NROWS + r ] = (float) z_samples[c][r];
            }
        }
        
        vals_ff = new FlatField(func_domain_range, domain_set);
        vals_ff.setSamples(flat_samples, false);
        iso_vals_ff = new FlatField(func_domain_iso_range, domain_set);
        display = new DisplayImplJ3D("display1");
        GraphicsModeControl dispGMC = (GraphicsModeControl)  display.getGraphicsModeControl();
        dispGMC.setScaleEnable(true);
        
        float[][] flat_isoVals = vals_ff.getFloats(false);
        iso_vals_ff.setSamples(flat_isoVals, false);
        
        latMap = new ScalarMap( latitude,    Display.YAxis );
        lonMap = new ScalarMap( longitude, Display.XAxis );
        
        // This is new!
        
        tempIsoMap = new ScalarMap( temperature,  Display.ZAxis );
        tempRGBMap = new ScalarMap( temperature,  Display.RGB );
        
        tempIsoMap2 = new ScalarMap( isotemperature,  Display.ZAxis );
        tempIsoMapIso = new ScalarMap( isotemperature,  Display.IsoContour );
    //    tempIsoMapIso = new ScalarMap( isotemperature,  Display.IsoContour );
        
        
        
        // Add maps to display
        
        display.addMap( latMap );
        display.addMap( lonMap );
        
        
        display.addMap( tempIsoMap );
        display.addMap( tempRGBMap );
        
        display.addMap( tempIsoMapIso );
        display.addMap( tempIsoMap2 );
        
        
   //     tempIsoMapIso = new ScalarMap( temperature,  Display.IsoContour );
       // tempRGBMap2 = new ScalarMap( temperature,  Display.RGB );
        
    //    display.addMap( tempIsoMapIso );
       // display.addMap( tempRGBMap2 );
  //      display.addMap( tempIsoMapIso );
        //display.addMap( tempRGBMap );
        
      //  ContourWidget contourWid = new ContourWidget(tempIsoMapIso);
        
        // Create a data reference and set the FlatField as our data
        
        data_ref = new DataReferenceImpl("data_ref");
        iso_data_ref = new DataReferenceImpl("data_ref2");
         
        data_ref.setData( vals_ff );
       iso_data_ref.setData( iso_vals_ff );
        
        // Add reference to display
        
        display.addReference( data_ref );
        display.addReference( iso_data_ref );
        
        
        // Create application window and add display to window
        
        JFrame jframe = new JFrame("NeqSim 3D-plot");
        jframe.getContentPane().add(display.getComponent());
        
        
        // Set window size and make it visible
        
        jframe.setSize(700, 700);
        jframe.setVisible(true);
    }
    
    public static void main(String[] args) throws RemoteException, VisADException {
     
      /*visAd3DPlot test = new visAd3DPlot("long", "alt", "height");
        test.setXYvals(0, 10, 4, 0, 10, 4);
        
        double[][] z = {
            {3,2,1,3,}, {2,6,4,1,},{1,3,2,1,}, {3,2,1,3,}
        };
        
        test.setZvals(z);
        test.init();
        
        */
        
    }
    
    
    
}
