// EPICS_AD_Viewer.java
// Original authors
//      Tim Madden, APS
// Current author
//      Mark Rivers, University of Chicago
import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.*;
import java.io.*;
import java.text.*;
import java.awt.event.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;

import gov.aps.jca.*;
import gov.aps.jca.dbr.*;
import gov.aps.jca.configuration.*;
import gov.aps.jca.event.*;

public class EPICS_AD_Viewer implements PlugIn
{
    ImagePlus img;
    ImageStack imageStack;
    Object snapBackup = null;
    Object altSnapBackup = null;
    int imageSizeX = 0;
    int imageSizeY = 0;
    int imageSizeZ = 0;
    int colorMode;
    DBRType dataType;
    int ADDataType;
    byte[] colorLUT = new byte[256];
    double prevDispMin = 0.;
    double prevDispMax = 255.;

    FileOutputStream debugFile;
    PrintStream debugPrintStream;
    Properties properties = new Properties();
    String propertyFile = "EPICS_AD_Viewer.properties";

    // These are used for the frames/second calculation
    long prevTime;
    int numImageUpdates;

    JCALibrary jca;
    DefaultConfiguration conf;
    Context ctxt;

    /** these are EPICS channel objects to get images... */
    Channel ch_nx;
    Channel ch_ny;
    Channel ch_nz;
    Channel ch_colorMode;
    Channel ch_dataType;
    Channel ch_image;
    Channel ch_image_id;
    volatile int ArrayCounter;

    JFrame frame;

    String PVPrefix;
    JTextField PVPrefixText;
    JTextField NXText;
    JTextField NYText;
    JTextField NZText;
    JTextField FPSText;
    JTextField StatusText;
    JButton startButton;
    JButton stopButton;
    JButton snapButton;
    JCheckBox logCheckBox;

    boolean isDebugMessages;
    boolean isDebugFile;
    boolean isDisplayImages;
    boolean isPluginRunning;
    boolean isSaveToStack;
    boolean isNewStack;
    boolean isConnected;
    boolean isLogOn;
    boolean firstLog;
    volatile boolean isNewImageAvailable;

    javax.swing.Timer timer;

    public void run(String arg)
    {
        IJ.showStatus("epics running");
        try
        {
            isDebugFile = false;
            isDebugMessages = false;
            isDisplayImages = false;
            isPluginRunning = true;
            isNewImageAvailable = false;
            isSaveToStack = false;
            isNewStack = false;
            isLogOn = false;
            firstLog=false;
            Date date = new Date();
            prevTime = date.getTime();
            numImageUpdates = 0;
            PVPrefix = "13SIM1:image1:";
            readProperties();

            if (isDebugFile)
            {
                debugFile = new FileOutputStream(System.getProperty("user.home") + System.getProperty("file.separator") + "IJEPICS_debug.txt");
                debugPrintStream = new PrintStream(debugFile);
            }
            javax.swing.SwingUtilities.invokeLater(
                    new Runnable()
                    {
                        public void run()
                        {
                            createAndShowGUI();
                        }
                    }
            );

            img = new ImagePlus(PVPrefix, new ByteProcessor(100, 100));
            img.show();
            img.close();
            startEPICSCA();
            connectPVs();

            while (isPluginRunning)
            {
                synchronized (this)
                {
                    wait(1000);
                }
                if (isDisplayImages && isNewImageAvailable)
                {
                    if (isDebugMessages) IJ.log("calling updateImage");
                    updateImage();
                    isNewImageAvailable = false;
                }
            }

            if (isDebugFile)
            {
                debugPrintStream.close();
                debugFile.close();
                logMessage("Closed debug file", true, true);
            }

            timer.stop();
            writeProperties();
            disconnectPVs();
            closeEPICSCA();
            img.close();

            frame.setVisible(false);

            IJ.showStatus("Exiting Server");

        }
        catch (Exception e)
        {
            IJ.log("Got exception: " + e.getMessage());
            e.printStackTrace();
            IJ.log("Close epics CA window, and reopen, try again");

            IJ.showStatus(e.toString());

            try
            {
                if (isDebugFile)
                {
                    debugPrintStream.close();
                    debugFile.close();
                }
            }
            catch (Exception ee) { }
        }
    }

    public void makeImageCopy()
    {
        ImageProcessor ip = img.getProcessor();
        if (ip == null) return;
        if(isLogOn) {
            ImageProcessor ipcopy = ip.duplicate();
            ipcopy.setPixels(snapBackup);
            ImagePlus imgcopy = new ImagePlus(PVPrefix + ":" + ArrayCounter, ipcopy);
            resetContrast(imgcopy);
            imgcopy.show();
        }
        else {
            ImagePlus imgcopy = new ImagePlus(PVPrefix + ":" + ArrayCounter, ip.duplicate());
            imgcopy.show();
        }
    }

    public void connectPVs()
    {
        try
        {
            PVPrefix = PVPrefixText.getText();
            logMessage("Trying to connect to EPICS PVs: " + PVPrefix, true, true);
            if (isDebugFile)
            {
                debugPrintStream.println("Trying to connect to EPICS PVs: " + PVPrefix);
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
            ch_nx = createEPICSChannel(PVPrefix + "ArraySize0_RBV");
            ch_ny = createEPICSChannel(PVPrefix + "ArraySize1_RBV");
            ch_nz = createEPICSChannel(PVPrefix + "ArraySize2_RBV");
            ch_colorMode = createEPICSChannel(PVPrefix + "ColorMode_RBV");
            ch_dataType = createEPICSChannel(PVPrefix + "DataType_RBV");
            ch_image = createEPICSChannel(PVPrefix + "ArrayData");
            ch_image_id = createEPICSChannel(PVPrefix + "ArrayCounter_RBV");
            ch_image_id.addMonitor(
                    Monitor.VALUE,
                    new newArrayCounterCallback()
            );
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
            ch_nx.destroy();
            ch_ny.destroy();
            ch_nz.destroy();
            ch_colorMode.destroy();
            ch_dataType.destroy();
            ch_image.destroy();
            ch_image_id.destroy();
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

    public class newArrayCounterCallback implements MonitorListener
    {
        public void monitorChanged(MonitorEvent ev)
        {
            if (isDebugMessages)
                IJ.log("Monitor callback");
            isNewImageAvailable = true;
            DBR_Int x = (DBR_Int)ev.getDBR();
            ArrayCounter = (x.getIntValue())[0];
            synchronized (EPICS_AD_Viewer.this) {
                EPICS_AD_Viewer.this.notify();
            }
        }
    }

    public void checkConnections()
    {
        boolean connected;
        try
        {
            connected = (ch_nx != null && ch_nx.getConnectionState() == Channel.ConnectionState.CONNECTED &&
                    ch_ny != null && ch_ny.getConnectionState() == Channel.ConnectionState.CONNECTED &&
                    ch_nz != null && ch_nz.getConnectionState() == Channel.ConnectionState.CONNECTED &&
                    ch_colorMode != null && ch_colorMode.getConnectionState() == Channel.ConnectionState.CONNECTED &&
                    ch_dataType != null && ch_dataType.getConnectionState() == Channel.ConnectionState.CONNECTED &&
                    ch_image != null && ch_image.getConnectionState() == Channel.ConnectionState.CONNECTED &&
                    ch_image_id != null && ch_image_id.getConnectionState() == Channel.ConnectionState.CONNECTED);
            if (connected && !isConnected)
            {
                isConnected = true;
                logMessage("Connected to EPICS PVs OK", true, true);
                PVPrefixText.setBackground(Color.green);
                startButton.setEnabled(!isDisplayImages);
                stopButton.setEnabled(isDisplayImages);
                snapButton.setEnabled(isDisplayImages);
            }
            if (!connected)
            {
                isConnected = false;
                logMessage("Cannot connect to EPICS PVs", true, true);
                PVPrefixText.setBackground(Color.red);
                startButton.setEnabled(false);
                stopButton.setEnabled(false);
                snapButton.setEnabled(false);
            }
        }
        catch (Exception ex)
        {
            IJ.log("checkConnections: got exception= " + ex.getMessage());
            if (isDebugFile) ex.printStackTrace(debugPrintStream);
        }
    }

    public void updateImage()
    {
        try
        {
            checkConnections();
            if (!isConnected) return;
            int nx = epicsGetInt(ch_nx);
            int ny = epicsGetInt(ch_ny);
            int nz = epicsGetInt(ch_nz);
            int cm = epicsGetInt(ch_colorMode);
            int ad_dt = epicsGetInt(ch_dataType);
            Point oldWindowLocation=null;
            boolean madeNewWindow = false;
            DBRType dt = ch_image.getFieldType();

            if (nz == 0) nz = 1;  // 2-D images without color
            if (ny == 0) ny = 1;  // 1-D images which are OK, useful with dynamic profiler
            if (isDebugMessages)
                IJ.log("got image, sizes: " + nx + " " + ny + " " + nz);
            int getsize = nx * ny * nz;
            if (getsize == 0) return;  // Not valid dimensions


            // if image size changes we must close window and make a new one.
            boolean makeNewWindow = false;
            if (nx != imageSizeX || ny != imageSizeY || nz != imageSizeZ || cm != colorMode || dt != dataType || ad_dt != ADDataType)
            {
                makeNewWindow = true;
                imageSizeX = nx;
                imageSizeY = ny;
                imageSizeZ = nz;
                colorMode = cm;
                dataType = dt;
                ADDataType = ad_dt;
                NXText.setText("" + imageSizeX);
                NYText.setText("" + imageSizeY);
                NZText.setText("" + imageSizeZ);
            }

            // If we are making a new stack close the window
            if (isNewStack) makeNewWindow = true;

            // If we need to make a new window then close the current one if it exists
            if (makeNewWindow)
            {
                try
                {
                    if (img.getWindow() == null || !img.getWindow().isClosed())
                    {
                        ImageWindow win = img.getWindow();
                        if (win != null) {
                            oldWindowLocation = win.getLocationOnScreen();
                        }
                        img.close();
                    }
                }
                catch (Exception ex) {
                    IJ.log("updateImage got exception: " + ex.getMessage());
                }
                makeNewWindow = false;
            }
            // If the window does not exist or is closed make a new one
            if (img.getWindow() == null || img.getWindow().isClosed())
            {
                switch (colorMode)
                {
                    case 0:
                    case 1:
                        if (ADDataType == 1)
                        {
                            img = new ImagePlus(PVPrefix, new ByteProcessor(imageSizeX, imageSizeY));
                        }
                        else if (ADDataType == 3)
                        {
                            img = new ImagePlus(PVPrefix, new ShortProcessor(imageSizeX, imageSizeY));
                        }
                        else
                        {
                            img = new ImagePlus(PVPrefix, new FloatProcessor(imageSizeX, imageSizeY));
                        }
                        break;
                    case 2:
                        img = new ImagePlus(PVPrefix, new ColorProcessor(imageSizeY, imageSizeZ));
                        break;
                    case 3:
                        img = new ImagePlus(PVPrefix, new ColorProcessor(imageSizeX, imageSizeZ));
                        break;
                    case 4:
                        img = new ImagePlus(PVPrefix, new ColorProcessor(imageSizeX, imageSizeY));
                        break;
                }
                img.show();
                if (oldWindowLocation != null) img.getWindow().setLocation(oldWindowLocation);
                madeNewWindow = true;
            }

            if (isNewStack)
            {
                imageStack = new ImageStack(img.getWidth(), img.getHeight());
                imageStack.addSlice(PVPrefix + ArrayCounter, img.getProcessor());
                // Note: we need to add this first slice twice in order to get the slider bar
                // on the window - ImageJ won't put it there if there is only 1 slice.
                imageStack.addSlice(PVPrefix + ArrayCounter, img.getProcessor());
                img.close();
                img = new ImagePlus(PVPrefix, imageStack);
                img.show();
                isNewStack = false;
            }

            if (isDebugMessages) IJ.log("about to get pixels");
            if (colorMode == 0 || colorMode == 1)
            {
                if (ADDataType == 1)
                {
                    byte[] pixels = epicsGetByteArray(ch_image, getsize);
                    img.getProcessor().setPixels(pixels);
                }
                else if (ADDataType == 3)
                {
                    short[] pixels = epicsGetShortArray(ch_image, getsize);
                    img.getProcessor().setPixels(pixels);
                }
                else
                {
                    float[] pixels = epicsGetFloatArray(ch_image, getsize);
                    img.getProcessor().setPixels(pixels);
                }
            }
            else if (colorMode >= 2 && colorMode <= 4)
            {
                int[] pixels = (int[])img.getProcessor().getPixels();
                byte inpixels[] = epicsGetByteArray(ch_image, getsize);
                double dispMin = img.getDisplayRangeMin();
                double dispMax = img.getDisplayRangeMax();
                if ((dispMin != 0) || (dispMax != 255)) {
                    int i;
                    if ((dispMin != prevDispMin) || (dispMax != prevDispMax)) {
                        // Recompute LUT
                        prevDispMin = dispMin;
                        prevDispMax = dispMax;
                        double slope = 255/(dispMax - dispMin);
                        for (i=0; i<256; i++) {
                            if (i<dispMin)
                                colorLUT[i] = 0;
                            else if (i>dispMax)
                                colorLUT[i] = (byte)255;
                            else
                                colorLUT[i] = (byte)((i-dispMin)*slope + 0.5);
                        }
                    }
                    for (i=0; i<getsize; i++) {
                        inpixels[i] = colorLUT[inpixels[i] & 0xff];
                    }
                }
                switch (colorMode)
                {
                    case 2:
                    {
                        int in = 0, out = 0;
                        while (in < getsize)
                        {
                            pixels[out++] = (inpixels[in++] & 0xFF) << 16 | (inpixels[in++] & 0xFF) << 8 | (inpixels[in++] & 0xFF);
                        }
                    }
                    break;
                    case 3:
                    {
                        int nCols = imageSizeX, nRows = imageSizeZ, row, col;
                        int redIn, greenIn, blueIn, out = 0;
                        for (row = 0; row < nRows; row++)
                        {
                            redIn = row * nCols * 3;
                            greenIn = redIn + nCols;
                            blueIn = greenIn + nCols;
                            for (col = 0; col < nCols; col++)
                            {
                                pixels[out++] = (inpixels[redIn++] & 0xFF) << 16 | (inpixels[greenIn++] & 0xFF) << 8 | (inpixels[blueIn++] & 0xFF);
                            }
                        }
                    }
                    break;
                    case 4:
                    {
                        int imageSize = imageSizeX * imageSizeY;
                        int redIn = 0, greenIn = imageSize, blueIn = 2 * imageSize, out = 0;
                        while (redIn < imageSize)
                        {
                            pixels[out++] = (inpixels[redIn++] & 0xFF) << 16 | (inpixels[greenIn++] & 0xFF) << 8 | (inpixels[blueIn++] & 0xFF);
                        }
                    }
                    break;
                }
                img.getProcessor().setPixels(pixels);
            }
            if (isLogOn)
            {
                img.getProcessor().snapshot();
                snapBackup=img.getProcessor().getSnapshotPixels();
                if(ADDataType!=3 && ADDataType!=1)
                    log(img);
                else {
                    img.getProcessor().log();
                }
                if (firstLog){
                    resetContrast(img);
                    firstLog = false;
                }
            }
            if (isSaveToStack)
            {
                img.getStack().addSlice(PVPrefix + ArrayCounter, img.getProcessor().duplicate());
            }
            img.setSlice(img.getNSlices());
            img.show();
            img.updateAndDraw();
            ImageCanvas ic = img.getCanvas();
            Point loc = ic!=null ? ic.getCursorLoc() : null;
            if (loc!=null)
                img.mouseMoved(loc.x,loc.y);
            img.updateStatusbarValue();
            numImageUpdates++;
            // Automatically set brightness and contrast if we made a new window
            if (madeNewWindow) new ContrastEnhancer().stretchHistogram(img, 0.5);
        }
        catch (Exception ex)
        {
            logMessage("UpdateImage got exception: " + ex.getMessage(), true, true);
            if (isDebugFile) ex.printStackTrace(debugPrintStream);
        }
    }


    public int epicsGetInt(Channel ch) throws Exception
    {
        if (isDebugMessages)
            IJ.log("Channel Get: " + ch.getName());
        DBR_Int x = (DBR_Int)ch.get(DBRType.INT, 1);
        ctxt.pendIO(5.0);
        return (x.getIntValue()[0]);
    }

    public byte[] epicsGetByteArray(Channel ch, int num) throws Exception
    {
        DBR x = ch.get(DBRType.BYTE, num);
        ctxt.pendIO(10.0);
        DBR_Byte xi = (DBR_Byte)x;
        byte zz[] = xi.getByteValue();
        return (zz);
    }


    public short[] epicsGetShortArray(Channel ch, int num) throws Exception
    {
        DBR x = ch.get(DBRType.SHORT, num);
        ctxt.pendIO(10.0);
        DBR_Short xi = (DBR_Short)x;
        short zz[] = xi.getShortValue();
        return (zz);
    }

    public float[] epicsGetFloatArray(Channel ch, int num) throws Exception
    {
        DBR x = ch.get(DBRType.FLOAT, num);
        ctxt.pendIO(10.0);
        DBR_Float xi = (DBR_Float)x;
        float zz[] = xi.getFloatValue();
        int i;
        if (ADDataType == 0) {
            for (i=0; i<num; i++) {
                if (zz[i] > 127.) zz[i] = -256 + zz[i];
            }
        } else if (ADDataType == 2) {
            for (i=0; i<num; i++) {
                if (zz[i] > 65535.) zz[i] = -65536 + zz[i];
            }
        }
        return (zz);
    }

    /**
     * Create the GUI and show it.  For thread safety,
     * this method should be invoked from the
     * event-dispatching thread.
     */
    public void createAndShowGUI()
    {
        //Create and set up the window.
        NXText = new JTextField(6);
        NXText.setEditable(false);
        NXText.setHorizontalAlignment(JTextField.CENTER);
        NYText = new JTextField(6);
        NYText.setEditable(false);
        NYText.setHorizontalAlignment(JTextField.CENTER);
        NZText = new JTextField(6);
        NZText.setEditable(false);
        NZText.setHorizontalAlignment(JTextField.CENTER);
        FPSText = new JTextField(6);
        FPSText.setEditable(false);
        FPSText.setHorizontalAlignment(JTextField.CENTER);
        StatusText = new JTextField(40);
        StatusText.setEditable(false);

        PVPrefixText = new JTextField(PVPrefix, 15);
        startButton = new JButton("Start");
        stopButton = new JButton("Stop");
        stopButton.setEnabled(false);
        snapButton = new JButton("Snap");
        JCheckBox captureCheckBox = new JCheckBox("");
        logCheckBox = new JCheckBox("");

        frame = new JFrame("Image J EPICS_AD_Viewer Plugin");
        JPanel panel = new JPanel(new BorderLayout());
        panel.setLayout(new GridBagLayout());
        panel.setBorder(new EmptyBorder(new Insets(5, 5, 5, 5)));
        frame.getContentPane().add(BorderLayout.CENTER, panel);
        GridBagConstraints c = new GridBagConstraints();
        // Add extra space around each component to avoid clutter
        c.insets = new Insets(2, 2, 2, 2);

        // Top row
        // Anchor all components CENTER
        c.anchor = GridBagConstraints.CENTER;
        c.gridx = 0;
        c.gridy = 0;
        panel.add(new JLabel("PVPrefix"), c);
        c.gridx = 1;
        panel.add(new JLabel("NX"), c);
        c.gridx = 2;
        panel.add(new JLabel("NY"), c);
        c.gridx = 3;
        panel.add(new JLabel("NZ"), c);
        c.gridx = 4;
        panel.add(new JLabel("Frames/s"), c);
        c.gridx = 5;
        panel.add(new JLabel("Capture to Stack"), c);
        c.gridx = 9;
        panel.add(new JLabel("Log"), c);

        // Middle row
        // These widgets should be centered
        c.anchor = GridBagConstraints.CENTER;
        c.gridy = 1;
        c.gridx = 0;
        panel.add(PVPrefixText, c);
        c.gridx = 1;
        panel.add(NXText, c);
        c.gridx = 2;
        panel.add(NYText, c);
        c.gridx = 3;
        panel.add(NZText, c);
        c.gridx = 4;
        panel.add(FPSText, c);
        c.gridx = 5;
        panel.add(captureCheckBox, c);
        c.gridx = 6;
        panel.add(snapButton, c);
        c.gridx = 7;
        panel.add(startButton, c);
        c.gridx = 8;
        panel.add(stopButton, c);
        c.gridx = 9;
        panel.add(logCheckBox, c);

        // Bottom row
        c.gridy = 2;
        c.gridx = 0;
        c.anchor = GridBagConstraints.EAST;
        panel.add(new JLabel("Status: "), c);
        c.gridx = 1;
        c.gridwidth = 7;
        c.anchor = GridBagConstraints.WEST;
        panel.add(StatusText, c);

        //Display the window.
        frame.pack();
        frame.addWindowListener(new FrameExitListener());
        frame.setVisible(true);


        int timerDelay = 2000;  // 2 seconds
        timer = new javax.swing.Timer(timerDelay, new ActionListener()
        {
            public void actionPerformed(ActionEvent event)
            {
                checkConnections();
                long time = new Date().getTime();
                double fps = 1000. * numImageUpdates / (double)(time - prevTime);
                NumberFormat form = DecimalFormat.getInstance();
                ((DecimalFormat)form).applyPattern("0.0");
                FPSText.setText("" + form.format(fps));
                if (isDisplayImages && numImageUpdates > 0) logMessage("New images=" + numImageUpdates, true, false);
                prevTime = time;
                numImageUpdates = 0;
            }
        });
        timer.start();

        PVPrefixText.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent event)
            {
                disconnectPVs();
                connectPVs();
            }
        });

        startButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent event)
            {
                startButton.setEnabled(false);
                stopButton.setEnabled(true);
                snapButton.setEnabled(true);
                isDisplayImages = true;
                logMessage("Image display started", true, true);
            }
        });

        stopButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent event)
            {
                startButton.setEnabled(true);
                stopButton.setEnabled(false);
                snapButton.setEnabled(false);
                isDisplayImages = false;
                logMessage("Image display stopped", true, true);
            }
        });
        logCheckBox.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    if (colorMode >= 2 && colorMode <= 4) {
                        logMessage("Log not intended for color images", true, true);
                        logCheckBox.setSelected(false);
                    } else {
                        isLogOn = true;
                        logMessage("Log display on", true, true);
                        firstLog = true;
                        if (!stopButton.isEnabled()) {
                            if (WindowManager.getCurrentWindow().equals(img.getWindow())) {
                                snapBackup = takeLog(img);
                                img.updateAndDraw();
                                resetContrast(img);
                            } else {
                                ImagePlus imgC = WindowManager.getCurrentImage();
                                altSnapBackup = takeLog(imgC);
                                imgC.updateAndDraw();
                                resetContrast(imgC);
                            }
                        } else if (!WindowManager.getCurrentWindow().equals(img.getWindow())) {
                            ImagePlus imgC = WindowManager.getCurrentImage();
                            altSnapBackup = takeLog(imgC);
                            imgC.updateAndDraw();
                            resetContrast(imgC);
                            isLogOn = false;
                        }
                    }
                } else {
                    logMessage("Log display off", true, true);
                    isLogOn = false;
                    if (!stopButton.isEnabled()) {
                        if (WindowManager.getCurrentWindow().equals(img.getWindow())) {
                            WindowManager.setCurrentWindow(img.getWindow());
                            img.getProcessor().setSnapshotPixels(snapBackup);
                            Undo.undo();
                            resetContrast(img);
                        }
                        else{
                            ImagePlus imgC = WindowManager.getCurrentImage();
                            imgC.getProcessor().setSnapshotPixels(altSnapBackup);
                            Undo.undo();
                            resetContrast(imgC);
                        }
                    }
                    else if (!WindowManager.getCurrentWindow().equals(img.getWindow())){
                        ImagePlus imgC = WindowManager.getCurrentImage();
                        imgC.getProcessor().setSnapshotPixels(altSnapBackup);
                        Undo.undo();
                        resetContrast(imgC);
                    }
                    else{
                        resetContrast(img);
                    }
                }
            }
        });

        snapButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent event)
            {
                makeImageCopy();
            }
        });

        captureCheckBox.addItemListener(new ItemListener()
                                        {
                                            public void itemStateChanged(ItemEvent e) {
                                                if (e.getStateChange() == ItemEvent.SELECTED) {
                                                    isSaveToStack = true;
                                                    isNewStack = true;
                                                    IJ.log("record on");
                                                } else {
                                                    isSaveToStack = false;
                                                    IJ.log("record off");
                                                }

                                            }
                                        }
        );

    }
    private Object takeLog(ImagePlus image){
        image.getProcessor().snapshot();
        image.getProcessor().resetMinAndMax();
        image.getProcessor().log();
        return image.getProcessor().getSnapshotPixels();
    }
    private void log(ImagePlus image){
        float[] pixelArr = (float[]) image.getProcessor().getPixels();
        int i = 0;
        for(float x : pixelArr){
            if(Math.abs(x - 0) < 1e-9)
                pixelArr[i++]=Float.NEGATIVE_INFINITY;
            else if(x<0)
                pixelArr[i++]=Float.NaN;
            else
                pixelArr[i++]=(float)Math.log(x);
        }
        image.getProcessor().setPixels(pixelArr);
    }
    private void resetContrast(ImagePlus image){
        image.getProcessor().resetMinAndMax();
        new ContrastEnhancer().stretchHistogram(image, 0.51);
    }
    public class FrameExitListener extends WindowAdapter
    {
        public void windowClosing(WindowEvent event)
        {
            isPluginRunning = false;
            isNewImageAvailable = false;
            // We need to wake up the main thread so it shuts down cleanly
            synchronized (this)
            {
                notify();
            }
        }
    }

    public void logMessage(String message, boolean logDisplay, boolean logFile)
    {
        Date date = new Date();
        SimpleDateFormat simpleDate = new SimpleDateFormat("d/M/y k:m:s.S");
        String completeMessage;

        completeMessage = simpleDate.format(date) + ": " + message;
        if (logDisplay) StatusText.setText(completeMessage);
        if (logFile) IJ.log(completeMessage);
    }

    public void readProperties()
    {
        String temp, path = null;

        try
        {
            String fileSep = System.getProperty("file.separator");
            path = System.getProperty("user.home") + fileSep + propertyFile;
            FileInputStream file = new FileInputStream(path);
            properties.load(file);
            file.close();
            temp = properties.getProperty("PVPrefix");
            if (temp != null) PVPrefix = temp;
            IJ.log("Read properties file: " + path + "  PVPrefix= " + PVPrefix);
        }
        catch (Exception ex)
        {
            IJ.log("readProperties:exception: " + ex.getMessage());
        }
    }

    public void writeProperties()
    {
        String path;
        try
        {
            String fileSep = System.getProperty("file.separator");
            path = System.getProperty("user.home") + fileSep + propertyFile;
            properties.setProperty("PVPrefix", PVPrefix);
            FileOutputStream file = new FileOutputStream(path);
            properties.store(file, "EPICS_AD_Viewer Properties");
            file.close();
            IJ.log("Wrote properties file: " + path);
        }
        catch (Exception ex)
        {
            IJ.log("writeProperties:exception: " + ex.getMessage());
        }
    }
}


