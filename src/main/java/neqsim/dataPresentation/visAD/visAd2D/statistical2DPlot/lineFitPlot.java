package neqsim.dataPresentation.visAD.visAd2D.statistical2DPlot;

import java.rmi.RemoteException;
import javax.swing.JFrame;
import visad.ConstantMap;
import visad.DataReferenceImpl;
import visad.Display;
import visad.DisplayImpl;
import visad.FlatField;
import visad.FunctionType;
import visad.GraphicsModeControl;
import visad.Integer1DSet;
import visad.Irregular1DSet;
import visad.RealTupleType;
import visad.RealType;
import visad.ScalarMap;
import visad.Set;
import visad.VisADException;
import visad.java2d.DisplayImplJ2D;

/**
 * <p>
 * lineFitPlot class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class lineFitPlot {
    private RealType x, y, index;

    private RealTupleType x_y_tuple;

    private FunctionType func_i_tuple, func_line, func_i_tuple2;

    private Set x_set, index_set, index_set2;

    private FlatField line_ff, points_ff;

    private DataReferenceImpl points_ref, line_ref;
    boolean scatter = false;
    private DisplayImpl display;
    float maxX = 0, minX = 0;
    float maxY = 0, minY = 0;

    private ScalarMap xMap, yMap, xRangeMap;
    float[][] set_samples;
    float[][] xy_samples;
    float[][] x_line_samples, y_line_samples;
    float[][] xy_disc_samples;

    /**
     * <p>
     * Constructor for lineFitPlot.
     * </p>
     *
     * @param firstax a {@link java.lang.String} object
     * @param yax a {@link java.lang.String} object
     * @throws java.rmi.RemoteException if any.
     * @throws visad.VisADException if any.
     */
    public lineFitPlot(String firstax, String yax) throws RemoteException, VisADException {
        x = RealType.getRealType("test1");
        y = RealType.getRealType("test");

        x_y_tuple = new RealTupleType(x, y);
        index = RealType.getRealType("index");

        func_i_tuple = new FunctionType(index, x_y_tuple);
        func_i_tuple2 = new FunctionType(index, x_y_tuple);
    }

    // public void setXYals(double[][] vals)throws RemoteException, VisADException{
    // xy_samples = vals;
    // }

    /**
     * <p>
     * setXYVals.
     * </p>
     *
     * @param xvals an array of {@link double} objects
     * @param yvals an array of {@link double} objects
     * @throws java.rmi.RemoteException if any.
     * @throws visad.VisADException if any.
     */
    public void setXYVals(double[] xvals, double[] yvals) throws RemoteException, VisADException {
        xy_samples = new float[2][xvals.length];
        minX = (float) xvals[0];
        maxX = (float) xvals[0];
        minY = (float) yvals[0];
        maxY = (float) yvals[0];

        for (int i = 0; i < xvals.length; i++) {
            xy_samples[0][i] = (float) xvals[i];
            xy_samples[1][i] = (float) yvals[i];

            minX = xy_samples[0][i] < minX ? xy_samples[0][i] : minX;
            maxX = xy_samples[0][i] > maxX ? xy_samples[0][i] : maxX;
            minY = xy_samples[1][i] < minY ? xy_samples[1][i] : minY;
            maxY = xy_samples[1][i] > maxY ? xy_samples[1][i] : maxY;
        }
    }

    /**
     * <p>
     * setXYVals2.
     * </p>
     *
     * @param xvals an array of {@link double} objects
     * @param yvals an array of {@link double} objects
     * @throws java.rmi.RemoteException if any.
     * @throws visad.VisADException if any.
     */
    public void setXYVals2(double[] xvals, double[] yvals) throws RemoteException, VisADException {
        scatter = true;
        y_line_samples = new float[1][yvals.length];
        x_line_samples = new float[1][xvals.length];

        for (int i = 0; i < xvals.length; i++) {
            x_line_samples[0][i] = (float) xvals[i];
            y_line_samples[0][i] = (float) yvals[i];

            minX = x_line_samples[0][i] < minX ? x_line_samples[0][i] : minX;
            maxX = x_line_samples[0][i] > maxX ? x_line_samples[0][i] : maxX;
            minY = y_line_samples[0][i] < minY ? y_line_samples[0][i] : minY;
            maxY = y_line_samples[0][i] > maxY ? y_line_samples[0][i] : maxY;
        }
    }

    /**
     * <p>
     * setLineXYVals.
     * </p>
     *
     * @param xvals an array of {@link double} objects
     * @param yvals an array of {@link double} objects
     * @throws java.rmi.RemoteException if any.
     * @throws visad.VisADException if any.
     */
    public void setLineXYVals(double[] xvals, double[] yvals)
            throws RemoteException, VisADException {
        y_line_samples = new float[1][yvals.length];
        x_line_samples = new float[1][xvals.length];

        for (int i = 0; i < xvals.length; i++) {
            x_line_samples[0][i] = (float) xvals[i];
            y_line_samples[0][i] = (float) yvals[i];

            minX = x_line_samples[0][i] < minX ? x_line_samples[0][i] : minX;
            maxX = x_line_samples[0][i] > maxX ? x_line_samples[0][i] : maxX;
            minY = y_line_samples[0][i] < minY ? y_line_samples[0][i] : minY;
            maxY = y_line_samples[0][i] > maxY ? y_line_samples[0][i] : maxY;
        }
    }

    /*
     * public void setContinousXVals(double[] vals)throws RemoteException, VisADException{
     * System.arraycopy(vals,0,xy_samples[0],0,vals.length); }
     * 
     * public void setContinousYVals(double[] vals)throws RemoteException, VisADException{
     * System.arraycopy(vals,0,xy_samples[1],0,vals.length); }
     */

    /**
     * <p>
     * init.
     * </p>
     *
     * @throws java.rmi.RemoteException if any.
     * @throws visad.VisADException if any.
     */
    public void init() throws RemoteException, VisADException {
        index_set = new Integer1DSet(index, xy_samples[0].length);
        points_ff = new FlatField(func_i_tuple, index_set);
        points_ff.setSamples(xy_samples);

        if (!scatter) {
            func_line = new FunctionType(x, y);
            x_set = new Irregular1DSet(x, x_line_samples);
            line_ff = new FlatField(func_line, x_set);
            line_ff.setSamples(y_line_samples);
        } else {
            index_set2 = new Integer1DSet(index, xy_samples[0].length);
            line_ff = new FlatField(func_i_tuple, index_set);
            line_ff.setSamples(y_line_samples);
        }

        display = new DisplayImplJ2D("StatPlot");

        GraphicsModeControl dispGMC = display.getGraphicsModeControl();
        dispGMC.setScaleEnable(true);

        xMap = new ScalarMap(x, Display.XAxis);
        yMap = new ScalarMap(y, Display.YAxis);

        xRangeMap = new ScalarMap(x, Display.SelectRange);

        display.addMap(xMap);
        display.addMap(yMap);
        display.addMap(xRangeMap);

        xMap.setRange(minX - (maxX - minX) / 10.0, maxX + (maxX - minX) / 10.0);
        yMap.setRange(minY - (maxY - minY) / 10.0, maxY + (maxY - minY) / 10.0);

        points_ref = new DataReferenceImpl("points_ref");
        line_ref = new DataReferenceImpl("line_ref");

        points_ref.setData(points_ff);
        line_ref.setData(line_ff);

        ConstantMap[] pointsMap = {new ConstantMap(1.0f, Display.Red),
                new ConstantMap(0.0f, Display.Green), new ConstantMap(0.0f, Display.Blue),
                new ConstantMap(4.5f, Display.PointSize)};

        ConstantMap[] lineMap = {new ConstantMap(0.0f, Display.Red),
                new ConstantMap(0.0f, Display.Green), new ConstantMap(1.0f, Display.Blue),
                new ConstantMap(1.5f, Display.LineWidth)};

        display.addReference(points_ref, pointsMap);
        display.addReference(line_ref, lineMap);

        JFrame jframe = new JFrame("NeqSim 2D-plot");
        jframe.getContentPane().add(display.getComponent());

        // Set window size and make it visible
        jframe.setSize(700, 700);
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
        lineFitPlot plot = new lineFitPlot("long", "alt");

        double[][] z = {{0, 0.5, 1, 3, 1}, {2, 6, 4, 1, 3}, {1, 3, 2, 1, 1}, {3, 2, 1, 3, 2},
                {1, 3, 2, 1, 1}};

        plot.setXYVals(z[0], z[1]);
        plot.setLineXYVals(z[0], z[3]);
        plot.init();
    }
}
