
import ij.*;
import ij.gui.*;
import ij.measure.Calibration;
import ij.measure.CurveFitter;
import ij.measure.Minimizer;
import ij.measure.UserFunction;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij.util.IJMath;
import ij.util.Tools;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import gov.aps.jca.*;
import gov.aps.jca.dbr.*;
import gov.aps.jca.configuration.*;
import gov.aps.jca.event.*;


/** 
 * Plugin allows for live fiting of any built in ImageJ equation and a custom Slit Function
 * Open two instances of the plugin to get two seperate live fits
*/

public class LiveFitter_EPICSUserCalc implements PlugIn, PlotMaker {
    ImagePlus imp;
    private boolean firstTime;
    private boolean plotVertically;
    private Plot plot;
    private PlotWindow win;
    private JFrame frame = null;
    private JTextField channelNameTextA;
    private JTextField statusText = null;
    private JButton fitButton = null;
    private JComboBox ffComboBox = null;
    private JComboBox cComboBox = null;
    private JComboBox scComboBox = null;
    private JTextField lwText = null;
    private JComboBox sComboBox = null;
    private JTextField lText = null;
    private JCheckBox vCheckbox = null;

    private final String slitEquation = "y = a+b*x+e/2*Math.erf((x-c+d/2)/(f*Math.sqrt(2)))-e/2*Math.erf((x-c-d/2)/(f*Math.sqrt(2)))";
    JCALibrary jca;
    DefaultConfiguration conf;
    Context ctxt;

    /** these are EPICS channel objects for params and labels... */
    Channel ch_a;
    Channel ch_b;
    Channel ch_c;
    Channel ch_d;
    Channel ch_e;
    Channel ch_f;
    Channel ch_g;
    Channel ch_h;
    Channel ch_i;
    Channel ch_atxt;
    Channel ch_btxt;
    Channel ch_ctxt;
    Channel ch_dtxt;
    Channel ch_etxt;
    Channel ch_ftxt;
    Channel ch_gtxt;
    Channel ch_htxt;
    Channel ch_itxt;
    Channel ch_eq;

    boolean isDebugMessages;
    boolean isDebugFile;
    boolean isConnected;
    boolean isPluginRunning;
    boolean update;
    boolean clear;
    boolean updateLabels;
    double[] params;
    int numParameters;
    String UCPrefix;

    FileOutputStream debugFile;
    PrintStream debugPrintStream;

    private final static String[] SHAPE_NAMES = new String[] {
            "Circle", "X", "Line", "Box", "Triangle", "+", "Dot", "Connected Circles", "Diamond",
            "Custom", "Filled", "Bar", "Separated Bars"};
    /** Names in nicely sorting order for menus */
    private final static String[] SORTED_SHAPES = new String[] {
            SHAPE_NAMES[2], SHAPE_NAMES[7], SHAPE_NAMES[10], SHAPE_NAMES[11], SHAPE_NAMES[12],
            SHAPE_NAMES[0], SHAPE_NAMES[3], SHAPE_NAMES[4], SHAPE_NAMES[5],
            SHAPE_NAMES[8], SHAPE_NAMES[1], SHAPE_NAMES[6]};

    private final static String[] COLOR_CHOICES = new String[] {"Blue", "Black", "Green", "Yellow", "Orange", "Red", "Pink", "Magenta", "Gray"};
    private final static String[] FILL_COLOR_CHOICES = new String[] {"Blue", "White", "Black", "Green", "Yellow", "Orange", "Red", "Pink", "Magenta", "Gray"};
    private final static String[] SORTED_FIT_CHOICES  = new String[] {"Straight Line","2nd Degree Polynomial",
            "3rd Degree Polynomial", "4th Degree Polynomial", "5th Degree Polynomial","6th Degree Polynomial","7th Degree Polynomial",
            "8th Degree Polynomial","Power", "Power (linear regression)", "Exponential", "Exponential (linear regression)", "Exponential with Offset",
            "Exponential Recovery", "Exponential Recovery (no offset)",
            "Log", "y = a+b*ln(x-c)", "Gaussian", "Gaussian (no offset)", "Error Function", "Rodbard", "Rodbard (NIH Image)",
            "Inverse Rodbard", "Gamma Variate", "Chapman-Richards", "Slit Function"
    };


    public void run(String arg) {
        try {
            isDebugMessages = false;
            isDebugFile = false;
            isConnected = false;
            isPluginRunning = true;
            firstTime = true;
            if (firstTime)
                plotVertically = Prefs.verticalProfile || IJ.altKeyDown();
            createAndShowGUI();
            firstTime = false;
            startEPICSCA();
            if (isDebugFile) {
                debugFile = new FileOutputStream(System.getProperty("user.home") + System.getProperty("file.separator") + "IJEPICS_debug.txt");
                debugPrintStream = new PrintStream(debugFile);
            }
        }
        catch (Exception e) { }
    }

    public void createAndShowGUI(){
        //Create and set up the window.
        lwText = new JTextField("1.0",6);
        lwText.setEditable(true);
        lwText.setHorizontalAlignment(JTextField.LEFT);
        lText = new JTextField("", 6);
        lText.setEditable(true);
        statusText = new JTextField(40);
        statusText.setEditable(false);

        channelNameTextA = new JTextField("", 15);

        fitButton = new JButton("Display Fit");

        ffComboBox = new JComboBox(SORTED_FIT_CHOICES);
        cComboBox = new JComboBox(COLOR_CHOICES);
        scComboBox = new JComboBox(FILL_COLOR_CHOICES);
        sComboBox = new JComboBox(SORTED_SHAPES);
        vCheckbox = new JCheckBox("", true);

        frame = new JFrame("Image J LiveFitter_EPICSUserCalc Plugin");
        JPanel panel = new JPanel(new BorderLayout());
        panel.setLayout(new GridBagLayout());
        panel.setBorder(new EmptyBorder(new Insets(5, 5, 5, 5)));
        frame.getContentPane().add(BorderLayout.CENTER, panel);
        GridBagConstraints c = new GridBagConstraints();
        // Add extra space around each component to avoid clutter
        c.insets = new Insets(2, 2, 2, 2);

        //first column
        c.anchor = GridBagConstraints.EAST;
        c.gridx = 0;
        c.gridy = 0;
        panel.add(new JLabel("Fit Function:"), c);
        c.gridx = 0;
        c.gridy = 1;
        panel.add(new JLabel("Color:"), c);
        c.gridx = 0;
        c.gridy = 2;
        panel.add(new JLabel("Secondary Fill Color:"), c);
        c.gridx = 0;
        c.gridy = 3;
        panel.add(new JLabel("Line Width:"), c);
        c.gridx = 0;
        c.gridy = 4;
        panel.add(new JLabel("Symbol:"), c);
        c.gridx = 0;
        c.gridy = 5;
        panel.add(new JLabel("Label:"), c);
        c.gridx = 0;
        c.gridy = 6;
        panel.add(new JLabel("Visible"), c);
        c.anchor = GridBagConstraints.CENTER;
        c.gridx = 2;
        c.gridy = 0;
        panel.add(new JLabel("userCalc:"), c);

        // Middle column
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 1;
        c.gridy = 0;
        panel.add(ffComboBox, c);
        c.gridx = 1;
        c.gridy = 1;
        panel.add(cComboBox, c);
        c.gridx = 1;
        c.gridy = 2;
        panel.add(scComboBox, c);
        c.gridx = 1;
        c.gridy = 3;
        panel.add(lwText, c);
        c.gridx = 1;
        c.gridy = 4;
        panel.add(sComboBox, c);
        c.gridx = 1;
        c.gridy = 5;
        panel.add(lText, c);
        c.gridx = 1;
        c.gridy = 6;
        panel.add(vCheckbox, c);
        c.anchor = GridBagConstraints.CENTER;
        c.gridx = 2;
        c.gridy = 6;
        panel.add(fitButton, c);
        c.anchor = GridBagConstraints.EAST;
        c.gridx = 2;
        c.gridy = 1;
        panel.add(channelNameTextA, c);

        //bottom (status bar)
        c.gridy = 7;
        c.gridx = 0;
        c.anchor = GridBagConstraints.EAST;
        panel.add(new JLabel("Status: "), c);
        c.gridx = 1;
        c.gridwidth = 3;
        c.anchor = GridBagConstraints.WEST;
        panel.add(statusText, c);

        //Display the window.
        frame.pack();
        frame.addWindowListener(new LiveFitter_EPICSUserCalc.FrameExitListener());
        frame.setVisible(true);


        channelNameTextA.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                disconnectPVs();
                connectPVs();
            }
        });

        //calls newfit to display profile plot
        fitButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                firstTime = true;
                newFit();
            }
        });

    }
    public class FrameExitListener extends WindowAdapter {
        public void windowClosing(WindowEvent event) {
            try {
                isPluginRunning = false;
                disconnectPVs();
                closeEPICSCA();
                frame.setVisible(false);
            }
            catch (Exception ee) {}
        }
    }

    //gets plot/profile and fit to display
    public Plot getPlot() {
        Roi roi = imp.getRoi();
        if (imp ==null || roi==null || !(roi.isLine()||roi.getType()==Roi.RECTANGLE)) {
            return null;
        }
        ImageProcessor ip = imp.getProcessor();
        if (ip == null || roi == null) return null; //these may change asynchronously
        if (roi.getType() == Roi.LINE)
            ip.setInterpolate(PlotWindow.interpolate);
        else
            ip.setInterpolate(false);
        ProfilePlot profileP = new ProfilePlot(imp, Prefs.verticalProfile);//get the profile
        if (profileP == null) return null;
        double[] profile = profileP.getProfile();
        if (profile==null || profile.length<2)
            return null;
        String xUnit = "pixels";                    //the following code is mainly for x calibration
        double xInc = 1;
        double xStart = 0;
        Calibration cal = imp.getCalibration();
        if (roi.getType() == Roi.LINE) {
            Line line = (Line)roi;
            if (cal != null) {
                double dx = cal.pixelWidth*(line.x2 - line.x1);
                double dy = cal.pixelHeight*(line.y2 - line.y1);
                double length = Math.sqrt(dx*dx + dy*dy);
                xInc = length/(profile.length-1);
                xUnit = cal.getUnits();
            }
        } else if (roi.getType() == Roi.RECTANGLE) {
            if (cal != null) {
                xInc = roi.getBounds().getWidth()*cal.pixelWidth/(profile.length-1);
                xUnit = cal.getUnits();
            }
        } else return null;
        String xLabel = "Distance (" + xUnit + ")";
        String yLabel = (cal !=null && cal.getValueUnit()!=null && !cal.getValueUnit().equals("Gray Value")) ? "Value ("+cal.getValueUnit()+")" : "cts";

        int n = profile.length;                 // create the x axis
        double[] x = new double[n];
        for (int i=0; i<n; i++) {
            x[i] = i * xInc;
        }

        String title = imp.getTitle();
        int index = title.lastIndexOf('.');
        if (index>0 && (title.length()-index)<=5)
            title = title.substring(0, index);
        String label = lText.getText();
        if(label.equals("")) label = " Fit: " + ffComboBox.getSelectedItem();
        else label = " " + label;
        Plot plot = new Plot("Profile Plot: "+title + label , xLabel, yLabel);
        this.plot = plot;

        plot.add("Line", x, profile);
        plot.setColor(Color.BLACK);
        double fixedMin = ProfilePlot.getFixedMin();
        double fixedMax = ProfilePlot.getFixedMax();
        if (fixedMin!=0 || fixedMax!=0) {
            double[] a = Tools.getMinMax(x);
            plot.setLimits(a[0],a[1], fixedMin, fixedMax);
        }

        plot.addPoints(x, profile,2);

        CurveFitter cv = new CurveFitter(x, profile);

        String fitName = (String) ffComboBox.getSelectedItem();
        if (fitName.equals("Slit Function")) {
            fitSlitFunction(cv, x, profile);
        }
        else {
            cv.doFit(CurveFitter.sortedTypes[ffComboBox.getSelectedIndex()]);
        }
        double[] fitParams = cv.getParams();
        double[] xfit = cv.getXPoints();
        double[] yfit = new double[n];
        for (int i = 0; i < profile.length; i++) {
            yfit[i] = cv.f(fitParams, xfit[i]);
        }
        plot.addPoints(xfit, yfit, 2);

        String color = (String)cComboBox.getSelectedItem();
        String color2 = (String)scComboBox.getSelectedItem();
        String symbol = (String)sComboBox.getSelectedItem();
        Boolean visible = vCheckbox.isSelected();
        boolean numWidth = isNumeric(lwText.getText());
        double width;
        if(numWidth) width = Tools.parseDouble(lwText.getText(),1.0);
        else width = 1.0;
        if(width<0) width = -width;
        String style = color.trim() + "," + color2.trim() + "," + (float) width + "," + symbol + (visible ? "" : "hidden");
        int currentObjectIndex = plot.getNumPlotObjects() - 1;
        plot.setPlotObjectStyle(currentObjectIndex, style);
        if(isPluginRunning && isConnected) updatePvValues(cv);
        String legend = "";
        char pChar = 'a';
        double[] pVal = cv.getParams();
        for (int i = 0; i < cv.getNumParams(); i++) {
            legend += pChar + "=" + IJ.d2s(pVal[i], 3) + " ";
            pChar++;
        }
        legend = legend + "  " + cv.getFormula();
        plot.setColor(Color.MAGENTA);
        plot.addLabel(0,0,legend);
        plot.setLineWidth(1);
        if(!firstTime) {
            win.drawPlot(plot);
            win.getPlot().getProcessor().setColor(Color.MAGENTA);
            win.getPlot().getProcessor().drawString(legend,75,17);
        }
        return plot;
    }
    private boolean isNumeric(String str){
        return str!=null && str.matches("\\d*\\.?\\d+");
    }
    public ImagePlus getSourceImage() {
        return imp;
    }

    //    "y = a+b*x+e/2*Math.erf((x-c+d/2)/(f*2^(1/2)))-e/2*Math.erf((x-c+d/2)/(f*2^(1/2)))" custom fitting
    private double[] fitSlitFunction(CurveFitter cf, double[] x, double[] profile){
        double yMax = profile[0];
        double yMin = profile[0];
        for(int i = 0; i<profile.length; i++){
            if(profile[i]>yMax) yMax = profile[i];
            if(profile[i]<yMin) yMin = profile[i];
        }
        plot.setLimits(x[0], x[x.length-1], yMin, yMax);
        double cEst1 = 0;
        double cEst2 = 0;
        double eEst = 0;
        for(int i = 0; i<profile.length; i++) {
            cEst1 += (profile[i]-yMin)*x[i];
            cEst2 += profile[i]-yMin;
            eEst += profile[i];
        }
        double[] initialParams = new double[6];
        initialParams[0] = yMin;
        if(initialParams[0]<0 || initialParams[0]>20000) initialParams[0] = 1;
        initialParams[1] = 0;
        initialParams[2] = cEst1/cEst2;
        if(initialParams[2]<0 || initialParams[2]<x[x.length-1]) initialParams[0] = x.length/2+x[0];
        initialParams[4] = (eEst - x.length*yMin)/x.length;
        initialParams[3] = (eEst - x.length*yMin)/initialParams[4];
        initialParams[5] = 5;
        cf.doCustomFit(new UserFunction() {
            @Override
            public double userFunction(double[] params, double x) {
//                    xMin, xMax, yMin, yMax
                double[] limits = plot.getLimits();
                double xMin = limits[0];
                double xMax = limits[1];
                double yMin = limits[2];
                double yMax = limits[3];
                if(params[0] > yMax) return Double.NaN;
                if(params[0] < 0) return Double.NaN;
                if(params[1] > 0.05) return Double.NaN;
                if(params[1] < -0.05) return Double.NaN;
                if(params[2] > xMax) return Double.NaN;
                if(params[2] < 0) return Double.NaN;
                if(params[3] > xMax-xMin) return Double.NaN;
                if(params[3] < 0) return Double.NaN;
                if(params[4] >= yMax) return Double.NaN;
                if(params[4] < yMin) return Double.NaN;
                if(params[5] > 30) return Double.NaN;
                if(params[5] < 0) return Double.NaN;
                return params[0]+params[1]*x+params[4]/2* IJMath.erf((x-params[2]+params[3]/2)/(params[5]*Math.pow(2,0.5)))-params[4]/2*IJMath.erf((x-params[2]-params[3]/2)/(params[5]*Math.pow(2,0.5)));
            }}, 6, slitEquation, initialParams, null, false);
        return (cf.getStatus() == Minimizer.SUCCESS ? cf.getParams() : initialParams);
    }
    
// displays fit plot for the first time, checks to make sure a proper roi and image are selected
    public void newFit(){
        ImagePlus holderImg = imp;
        imp = WindowManager.getCurrentImage();
        Roi roi = imp.getRoi();
        if (imp ==null || roi==null || !(roi.isLine()||roi.getType()==Roi.RECTANGLE)) {
            IJ.error("Live Fit", "Line or rectangular selection required");
            imp = holderImg;
        }
        else {
            plot = getPlot();
            firstTime = false;
            plot.setPlotMaker(this);
            win = plot.show();
            if (roi != null && roi.getType() == Roi.RECTANGLE)
                plot.getImagePlus().setProperty("Label", plotVertically ? "vertical" : "horizontal");
        }
    }

    // sends values to userCalc
    public void updatePvValues(CurveFitter cf){
        try {
            params = cf.getParams();
            numParameters = cf.getNumParams();
            boolean connected = isConnected;
            if (connected) checkConnections();
            else return;
            if (!isConnected) return;
            if(firstTime){
                clear = true;
                clearPVS();
                updateLabelPVs(numParameters, cf.getFormula());
            }
            update = true;
            if(numParameters>=2){
                ch_a.put(params[0]);
                ctxt.pendIO(2.0);
                ch_b.put(params[1]);
                ctxt.pendIO(2.0);
            }
            if(numParameters>=3){
                ch_c.put(params[2]);
                ctxt.pendIO(2.0);
            }
            if(numParameters>=4){
                ch_d.put(params[3]);
                ctxt.pendIO(2.0);
            }
            if(numParameters>=5){
                ch_e.put(params[4]);
                ctxt.pendIO(2.0);
            }
            if(numParameters>=6){
                ch_f.put(params[5]);
                ctxt.pendIO(2.0);
            }
            if(numParameters>=7){
                ch_g.put(params[6]);
                ctxt.pendIO(2.0);
            }
            if(numParameters>=8){
                ch_h.put(params[7]);
                ctxt.pendIO(2.0);
            }
            if(numParameters>=9){
                ch_i.put(params[8]);
                ctxt.pendIO(2.0);
            }
            update=false;
        }
        catch (Exception e) {}
    }

    // updates parameter label values in userCalc
    public void updateLabelPVs(int num, String formula){
        checkConnections();
        if (!isConnected) return;
        updateLabels = true;
        try{
            if(num>=2) {
                ch_atxt.put("a");
                ctxt.pendIO(2.0);
                ch_btxt.put("b");
                ctxt.pendIO(2.0);
            }
            if(num>=3) {
                ch_ctxt.put("c");
                ctxt.pendIO(2.0);
            }
            if(num>=4) {
                ch_dtxt.put("d");
                ctxt.pendIO(2.0);
            }
            if(num>=5) {
                ch_etxt.put("e");
                ctxt.pendIO(2.0);
            }
            if(num>=6) {
                ch_ftxt.put("f");
                ctxt.pendIO(2.0);
            }
            if(num>=7) {
                ch_gtxt.put("g");
                ctxt.pendIO(2.0);
            }
            if(num>=8) {
                ch_htxt.put("h");
                ctxt.pendIO(2.0);
            }
            if(num==9){
                ch_itxt.put("i");
                ctxt.pendIO(2.0);
            }
            ch_eq.put(formula);
            ctxt.pendIO(2.0);
            updateLabels = false;
        }
        catch (Exception e) {}
    }
    
    //clears userCalc - set values to 0 or ""
    public void clearPVS(){
        checkConnections();
        if (!isConnected) return;
        try{
            ch_a.put(0);
            ctxt.pendIO(2.0);
            ch_b.put(0);
            ctxt.pendIO(2.0);
            ch_c.put(0);
            ctxt.pendIO(2.0);
            ch_d.put(0);
            ctxt.pendIO(2.0);
            ch_e.put(0);
            ctxt.pendIO(2.0);
            ch_f.put(0);
            ctxt.pendIO(2.0);
            ch_g.put(0);
            ctxt.pendIO(2.0);
            ch_h.put(0);
            ctxt.pendIO(2.0);
            ch_i.put(0);
            ctxt.pendIO(2.0);
            ch_atxt.put("");
            ctxt.pendIO(2.0);
            ch_btxt.put("");
            ctxt.pendIO(2.0);
            ch_ctxt.put("");
            ctxt.pendIO(2.0);
            ch_dtxt.put("");
            ctxt.pendIO(2.0);
            ch_etxt.put("");
            ctxt.pendIO(2.0);
            ch_ftxt.put("");
            ctxt.pendIO(2.0);
            ch_gtxt.put("");
            ctxt.pendIO(2.0);
            ch_htxt.put("");
            ctxt.pendIO(2.0);
            ch_itxt.put("");
            ctxt.pendIO(2.0);
        }
        catch (Exception ee) {}
    }
    public void connectPVs()
    {
        try
        {
            UCPrefix = channelNameTextA.getText();
            if(UCPrefix.equals("") || UCPrefix.charAt(0)=='_') {
                channelNameTextA.setBackground(Color.RED);
                return;
            }

            logMessage("Trying to connect to EPICS PVs: " + UCPrefix, true, true);
            if (isDebugFile)
            {
                debugPrintStream.println("Trying to connect to EPICS PVs: " + UCPrefix);
                debugPrintStream.println("context.printfInfo  ****************************");
                debugPrintStream.println();
                ctxt.printInfo(debugPrintStream);

                debugPrintStream.print("jca.printInfo  ****************************");
                debugPrintStream.println();
                jca.printInfo(debugPrintStream);
                debugPrintStream.print("jca.listProperties  ****************************");
                debugPrintStream.println();
                jca.listProperties(debugPrintStream);
            }
            ch_a = createEPICSChannel(UCPrefix +".A");
            ch_b = createEPICSChannel(UCPrefix + ".B");
            ch_c = createEPICSChannel(UCPrefix + ".C");
            ch_d = createEPICSChannel(UCPrefix + ".D");
            ch_e = createEPICSChannel(UCPrefix + ".E");
            ch_f = createEPICSChannel(UCPrefix + ".F");
            ch_g = createEPICSChannel(UCPrefix + ".G");
            ch_h = createEPICSChannel(UCPrefix + ".H");
            ch_i = createEPICSChannel(UCPrefix + ".I");
            ch_atxt = createEPICSChannel(UCPrefix +".INAN");
            ch_btxt = createEPICSChannel(UCPrefix +".INBN");
            ch_ctxt = createEPICSChannel(UCPrefix +".INCN");
            ch_dtxt = createEPICSChannel(UCPrefix +".INDN");
            ch_etxt = createEPICSChannel(UCPrefix +".INEN");
            ch_ftxt = createEPICSChannel(UCPrefix +".INFN");
            ch_gtxt = createEPICSChannel(UCPrefix +".INGN");
            ch_htxt = createEPICSChannel(UCPrefix +".INHN");
            ch_itxt = createEPICSChannel(UCPrefix +".ININ");
            ch_eq = createEPICSChannel(UCPrefix+".INLN");
            ctxt.flushIO();
            checkConnections();
        }
        catch (Exception ex)
        {
            checkConnections();
        }
    }

    public void disconnectPVs()
    {
        try
        {
            ch_a.destroy();
            ch_b.destroy();
            ch_c.destroy();
            ch_d.destroy();
            ch_e.destroy();
            ch_f.destroy();
            ch_g.destroy();
            ch_h.destroy();
            ch_i.destroy();
            ch_atxt.destroy();
            ch_btxt.destroy();
            ch_ctxt.destroy();
            ch_dtxt.destroy();
            ch_etxt.destroy();
            ch_ftxt.destroy();
            ch_gtxt.destroy();
            ch_htxt.destroy();
            ch_itxt.destroy();
            ch_eq.destroy();
            isConnected = false;
            logMessage("Disconnected from EPICS PVs OK", true, true);
        }
        catch (Exception ex)
        {
            logMessage("Cannot disconnect from EPICS PV:" + ex.getMessage(), true, true);
        }
    }


    public void startEPICSCA()
    {
        logMessage("Initializing EPICS", true, true);

        try
        {
            System.setProperty("jca.use_env", "true");
            // Get the JCALibrary instance.
            jca = JCALibrary.getInstance();
            ctxt = jca.createContext(JCALibrary.CHANNEL_ACCESS_JAVA);
            ctxt.initialize();
        }
        catch (Exception ex)
        {
            logMessage("startEPICSCA exception: " + ex.getMessage(), true, true);
        }

    }

    public void closeEPICSCA() throws Exception
    {
        logMessage("Closing EPICS", true, true);
        ctxt.destroy();
    }

    public Channel createEPICSChannel(String chname) throws Exception
    {
        // Create the Channel to connect to the PV.
        Channel ch = ctxt.createChannel(chname);

        // send the request and wait for the channel to connect to the PV.
        ctxt.pendIO(2.0);
        if (isDebugFile)
        {
            debugPrintStream.print("\n\n  Channel info****************************\n");
            ch.printInfo(debugPrintStream);
        }
        if (isDebugMessages)
        {
            IJ.log("Host is " + ch.getHostName());
            IJ.log("can read = " + ch.getReadAccess());
            IJ.log("can write " + ch.getWriteAccess());
            IJ.log("type " + ch.getFieldType());
            IJ.log("name = " + ch.getName());
            IJ.log("element count = " + ch.getElementCount());
        }
        return (ch);
    }

    public void checkConnections()
    {
        boolean connected;
        try
        {
            if(UCPrefix.equals("")) {
                isConnected = false;
                channelNameTextA.setBackground(Color.red);
                return;
            }
            connected = (ch_a != null && ch_a.getConnectionState() == Channel.ConnectionState.CONNECTED &&
                    ch_b != null && ch_b.getConnectionState() == Channel.ConnectionState.CONNECTED &&
                    ch_c != null && ch_c.getConnectionState() == Channel.ConnectionState.CONNECTED &&
                    ch_d != null && ch_d.getConnectionState() == Channel.ConnectionState.CONNECTED &&
                    ch_e != null && ch_e.getConnectionState() == Channel.ConnectionState.CONNECTED &&
                    ch_f != null && ch_f.getConnectionState() == Channel.ConnectionState.CONNECTED &&
                    ch_g != null && ch_g.getConnectionState() == Channel.ConnectionState.CONNECTED &&
                    ch_h != null && ch_h.getConnectionState() == Channel.ConnectionState.CONNECTED &&
                    ch_i != null && ch_i.getConnectionState() == Channel.ConnectionState.CONNECTED &&
                    ch_atxt != null && ch_atxt.getConnectionState() == Channel.ConnectionState.CONNECTED &&
                    ch_btxt != null && ch_btxt.getConnectionState() == Channel.ConnectionState.CONNECTED &&
                    ch_ctxt != null && ch_ctxt.getConnectionState() == Channel.ConnectionState.CONNECTED &&
                    ch_dtxt != null && ch_dtxt.getConnectionState() == Channel.ConnectionState.CONNECTED &&
                    ch_etxt != null && ch_etxt.getConnectionState() == Channel.ConnectionState.CONNECTED &&
                    ch_ftxt != null && ch_ftxt.getConnectionState() == Channel.ConnectionState.CONNECTED &&
                    ch_gtxt != null && ch_gtxt.getConnectionState() == Channel.ConnectionState.CONNECTED &&
                    ch_htxt != null && ch_htxt.getConnectionState() == Channel.ConnectionState.CONNECTED &&
                    ch_itxt != null && ch_itxt.getConnectionState() == Channel.ConnectionState.CONNECTED &&
                    ch_eq != null && ch_itxt.getConnectionState() == Channel.ConnectionState.CONNECTED);
            if (connected && !isConnected)
            {
                isConnected = true;
                logMessage("Connected to EPICS PVs OK", true, true);
                channelNameTextA.setBackground(Color.green);
            }
            if (!connected)
            {
                isConnected = false;
                logMessage("Cannot connect to EPICS PVs", true, true);
                channelNameTextA.setBackground(Color.red);
//
            }
        }
        catch (Exception ex)
        {
            IJ.log("checkConnections: got exception= " + ex.getMessage());
            if (isDebugFile) ex.printStackTrace(debugPrintStream);
        }
    }
    public void logMessage(String message, boolean logDisplay, boolean logFile)
    {
        Date date = new Date();
        SimpleDateFormat simpleDate = new SimpleDateFormat("d/M/y k:m:s.S");
        String completeMessage;

        completeMessage = simpleDate.format(date) + ": " + message;
        if (logDisplay) statusText.setText(completeMessage);
        if (logFile) IJ.log(completeMessage);
    }
}
