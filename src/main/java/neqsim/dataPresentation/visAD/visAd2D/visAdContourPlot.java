/*
 * visAdContourPlot.java
 *
 * Created on 7. november 2000, 17:51
 */
package neqsim.dataPresentation.visAD.visAd2D;

import java.rmi.RemoteException;
import javax.swing.JFrame;
import visad.DataReferenceImpl;
import visad.Display;
import visad.DisplayImpl;
import visad.FlatField;
import visad.FunctionType;
import visad.GraphicsModeControl;
import visad.Linear2DSet;
import visad.RealTupleType;
import visad.RealType;
import visad.ScalarMap;
import visad.Set;
import visad.VisADException;
import visad.java2d.DisplayImplJ2D;

/**
 * <p>
 * visAdContourPlot class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class visAdContourPlot {
    private RealType longitude, latitude, temperature;

    private RealTupleType domain_tuple;
    private FunctionType func_domain_range;
    private Set domain_set;
    private FlatField vals_ff;
    private DataReferenceImpl data_ref;
    private DisplayImpl display;
    private ScalarMap latMap, lonMap;
    private ScalarMap tempIsoMap, tempRGBMap;
    int NCOLS = 0, NROWS = 0;
    float[][] set_samples;
    double[][] z_samples;

    /**
     * <p>
     * Constructor for visAdContourPlot.
     * </p>
     *
     * @param firstax a {@link java.lang.String} object
     * @param secax a {@link java.lang.String} object
     * @param zax a {@link java.lang.String} object
     * @throws java.rmi.RemoteException if any.
     * @throws visad.VisADException if any.
     */
    public visAdContourPlot(String firstax, String secax, String zax)
            throws RemoteException, VisADException {
        latitude = RealType.getRealType(firstax);
        longitude = RealType.getRealType(secax);
        domain_tuple = new RealTupleType(latitude, longitude);
        temperature = RealType.getRealType(zax);
        func_domain_range = new FunctionType(domain_tuple, temperature);
    }

    /**
     * <p>
     * setXYvals.
     * </p>
     *
     * @param xMin a double
     * @param xMax a double
     * @param Nrows a int
     * @param yMin a double
     * @param yMax a double
     * @param NCols a int
     * @throws java.rmi.RemoteException if any.
     * @throws visad.VisADException if any.
     */
    public void setXYvals(double xMin, double xMax, int Nrows, double yMin, double yMax, int NCols)
            throws RemoteException, VisADException {
        NCOLS = NCols;
        NROWS = Nrows;

        domain_set = new Linear2DSet(domain_tuple, xMin, xMax, NROWS, yMin, yMax, NCOLS);

        set_samples = domain_set.getSamples(true);
    }

    /**
     * <p>
     * setZvals.
     * </p>
     *
     * @param vals an array of {@link double} objects
     * @throws java.rmi.RemoteException if any.
     * @throws visad.VisADException if any.
     */
    public void setZvals(double[][] vals) throws RemoteException, VisADException {
        z_samples = vals;
    }

    /**
     * <p>
     * init.
     * </p>
     *
     * @throws java.rmi.RemoteException if any.
     * @throws visad.VisADException if any.
     */
    public void init() throws RemoteException, VisADException {
        float[][] flat_samples = new float[1][NCOLS * NROWS];

        for (int c = 0; c < NCOLS; c++) {
            for (int r = 0; r < NROWS; r++) {
                flat_samples[0][c * NROWS + r] = (float) z_samples[c][r];
            }
        }

        vals_ff = new FlatField(func_domain_range, domain_set);
        vals_ff.setSamples(flat_samples, false);
        display = new DisplayImplJ2D("display1");
        GraphicsModeControl dispGMC = display.getGraphicsModeControl();
        dispGMC.setScaleEnable(true);

        latMap = new ScalarMap(latitude, Display.YAxis);
        lonMap = new ScalarMap(longitude, Display.XAxis);

        // This is new!
        tempIsoMap = new ScalarMap(temperature, Display.IsoContour);

        // this ScalarMap will color the isolines
        // don't foget to add it to the display
        tempRGBMap = new ScalarMap(temperature, Display.RGB);

        // Add maps to display
        display.addMap(latMap);
        display.addMap(lonMap);

        display.addMap(tempIsoMap);
        // display.addMap( tempRGBMap );

        // Create a data reference and set the FlatField as our data
        data_ref = new DataReferenceImpl("data_ref");

        data_ref.setData(vals_ff);

        // Add reference to display
        display.addReference(data_ref);

        // Create application window and add display to window
        JFrame jframe = new JFrame("VisAD Tutorial example 3_05");
        jframe.getContentPane().add(display.getComponent());

        // Set window size and make it visible
        jframe.setSize(500, 500);
        jframe.setVisible(true);
    }

    /**
     * <p>
     * main.
     * </p>
     *
     * @param args an array of {@link java.lang.String} objects
     * @throws java.rmi.RemoteException if any.
     * @throws visad.VisADException if any.
     */
    public static void main(String[] args) throws RemoteException, VisADException {
        visAdContourPlot test = new visAdContourPlot("long", "alt", "height");
        test.setXYvals(0, 10, 4, 0, 10, 4);

        double[][] z = {{3, 2, 1, 3,}, {2, 6, 4, 1,}, {1, 3, 2, 1,}, {3, 2, 1, 3,}};

        test.setZvals(z);
        test.init();
    }
}
