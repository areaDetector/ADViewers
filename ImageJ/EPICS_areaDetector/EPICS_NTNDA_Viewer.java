// EPICS_NTNDA_Viewer.java
// Original authors
//      Adapted for EPICS V4 from EPICS_CA_Viewer by Tim Madden
//      Tim Madden, APS
//      Mark Rivers, University of Chicago
//      Marty Kraimer
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Arc2D;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

import ij.gui.PlotWindow;
import ij.plugin.frame.ContrastAdjuster;
import org.epics.nt.NTNDArray;
import org.epics.pvaClient.PvaClient;
import org.epics.pvaClient.PvaClientChannel;
import org.epics.pvaClient.PvaClientChannelStateChangeRequester;
import org.epics.pvaClient.PvaClientMonitor;
import org.epics.pvaClient.PvaClientMonitorData;
import org.epics.pvdata.factory.ConvertFactory;
import org.epics.pvdata.pv.Convert;
import org.epics.pvdata.pv.PVInt;
import org.epics.pvdata.pv.PVScalar;
import org.epics.pvdata.pv.PVScalarArray;
import org.epics.pvdata.pv.PVString;
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdata.pv.PVStructureArray;
import org.epics.pvdata.pv.PVUnion;
import org.epics.pvdata.pv.ScalarType;
import org.epics.pvdata.pv.StructureArrayData;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.ImageWindow;
import ij.gui.ImageCanvas;
import ij.plugin.ContrastEnhancer;
import ij.plugin.PlugIn;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import ij.Undo;
import ij.WindowManager;

/**
 * ImageJ viewer for NTNDArray data.
 *
 */
public class EPICS_NTNDA_Viewer
        implements PvaClientChannelStateChangeRequester,PlugIn {
    // may want to change these
    private String channelName = "13SIM1:Pva1:Image";
    private boolean isDebugMessages = false;
    private boolean isDebugFile = false;
    private String propertyFile = "EPICS_NTNDA_Viewer.properties";

    private static final int QUEUE_SIZE = 1;
    private static final int MS_WAIT = 100;
    private static PvaClient pva = PvaClient.get("pva");
    private static Convert convert = ConvertFactory.getConvert();
    private PvaClientChannel pvaClientChannel = null;
    private PvaClientMonitor pvaClientMonitor = null;

    private ImagePlus img = null;
    private Object snapBackup = null;
    private Object altSnapBackup = null;
    private ImageStack imageStack = null;
    private int imageSizeX = 0;
    private int imageSizeY = 0;
    private int imageSizeZ = 0;
    private int colorMode = 0;
    private byte[] colorLUT = new byte[256];
    private double prevDispMin = 0.;
    private double prevDispMax = 255.;
    private ScalarType dataType = ScalarType.pvBoolean;
    private FileOutputStream debugFile = null;
    private PrintStream debugPrintStream = null;
    private Properties properties = new Properties();

    private volatile boolean isChannelConnected = false;
    private volatile boolean startIsTrue = false;
    private volatile boolean isStarted = false;
    private volatile boolean isPluginRunning = false;
    private volatile boolean isSaveToStack = false;
    private volatile boolean isNewStack = false;
    private volatile boolean isLogOn = false;
    // These are used for the frames/second calculation
    private long prevTime = 0;
    private volatile int numImageUpdates = 0;

    private NTNDCodec ntndCodec = null;

    private JFrame frame = null;
    private JTextField channelNameText = null;
    private JTextField nxText = null;
    private JTextField nyText = null;
    private JTextField nzText = null;
    private JTextField fpsText = null;
    private JTextField statusText = null;
    private JButton startButton = null;
    private JButton stopButton = null;
    private JButton snapButton = null;

    private javax.swing.Timer timer = null;

    /**
     * Constructor
     */
    public EPICS_NTNDA_Viewer() {
        String temp = null;
        readProperties();
        temp = System.getenv("EPICS_NTNDA_VIEWER_CHANNELNAME");
        if (temp != null) {
            channelName = temp;
        }
        createAndShowGUI();
    }

    /* (non-Javadoc)
     * @see org.epics.pvaClient.PvaClientChannelStateChangeRequester#channelStateChange(org.epics.pvaClient.PvaClientChannel, boolean)
     */
    public void channelStateChange(PvaClientChannel channel, boolean connected) {
        isChannelConnected = connected;
        if (isChannelConnected) {
            channelNameText.setBackground(Color.green);
            logMessage("State changed to connected for " + channelName, true, true);
            if (pvaClientMonitor == null) {
                pvaClientMonitor = pvaClientChannel.createMonitor("record[queueSize=" + QUEUE_SIZE + "]field()");
                pvaClientMonitor.issueConnect();
            }
            if (startIsTrue) startMonitor();
        } else if (pvaClientMonitor != null) {
            channelNameText.setBackground(Color.red);
            logMessage("State changed to disconnected for " + channelName, true, true);
            numImageUpdates = 0;
        }
    }

    private void connectPV() {
        try {
            if (pvaClientChannel != null) {
                throw new RuntimeException("Channel already connected");
            }
            channelName = channelNameText.getText();
            logMessage("Trying to connect to : " + channelName, true, true);
            pvaClientChannel = pva.createChannel(channelName, "pva");
            pvaClientChannel.setStateChangeRequester(this);
            isChannelConnected = false;
            channelNameText.setBackground(Color.red);
            pvaClientChannel.issueConnect();
        } catch (Exception ex) {
            logMessage("Could not connect to : " + channelName + " " + ex.getMessage(), true, false);
        }
    }

    private void disconnectPV() {
        try {
            if (pvaClientChannel == null) {
                throw new RuntimeException("Channel already disconnected");
            }
            isChannelConnected = false;
            if (isStarted) stopMonitor();
            pvaClientChannel.destroy();
            if (pvaClientMonitor != null) pvaClientMonitor.destroy();
            pvaClientChannel = null;
            pvaClientMonitor = null;
            logMessage("Disconnected from EPICS PV:" + channelName, true, true);
        } catch (Exception ex) {
            logMessage("Cannot disconnect from EPICS PV:" + channelName + ex.getMessage(), true, true);
        }
    }

    private void startMonitor() {
        isStarted = true;
        pvaClientMonitor.start();
    }

    private void stopMonitor() {
        synchronized (this) {
            pvaClientMonitor.stop();
            isStarted = false;
        }
    }

    private void startDisplay() {
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        snapButton.setEnabled(true);
        startIsTrue = true;
        if (isChannelConnected) startMonitor();
        logMessage("Display started", true, false);
    }

    private void stopDisplay() {
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        snapButton.setEnabled(false);
        startIsTrue = false;
        if (isChannelConnected) stopMonitor();
        logMessage("Display stopped", true, false);
    }

    private void handleEvents() {
        boolean gotEvent = pvaClientMonitor.poll();
        if (!gotEvent) {
            try {
                Thread.sleep(1);
            } catch (Exception ex) {
                logMessage("Thread.sleep exception " + ex, true, true);
            }
            return;
        }
        while (gotEvent) {
            if (isDebugMessages) logMessage("calling updateImage", true, true);
            try {
                boolean result = updateImage(pvaClientMonitor.getData());
                if (!result) {
                    logMessage("updateImage failed", true, true);
                    Thread.sleep(MS_WAIT);
                }
            } catch (Exception ex) {
                logMessage("handleEvents caught exception " + ex, true, true);
            }
            pvaClientMonitor.releaseEvent();
            // Break out of the loop if the display is stopped
            if (!startIsTrue) break;
            gotEvent = pvaClientMonitor.poll();
        }
    }

    /* (non-Javadoc)
     * @see ij.plugin.PlugIn#run(java.lang.String)
     */
    public void run(String arg) {
        IJ.showStatus("epics running");
        try {
            isPluginRunning = true;
            Date date = new Date();
            prevTime = date.getTime();
            if (isDebugFile) {
                debugFile = new FileOutputStream(System.getProperty("user.home") +
                        System.getProperty("file.separator") + "IJEPICS_debug.txt");
                debugPrintStream = new PrintStream(debugFile);
            }
            stopDisplay();
            connectPV();
            while (isPluginRunning) {
                // A very short wait here lets stopMonitor run quickly when needed
                Thread.sleep(1);
                synchronized (this) {
                    if (isStarted && pvaClientMonitor != null) {
                        handleEvents();
                    } else {
                        Thread.sleep(MS_WAIT);
                    }
                }
            } // isPluginRunning

            if (isDebugMessages) logMessage("run: Plugin stopping", true, true);
            if (isDebugFile) {
                debugPrintStream.close();
                debugFile.close();
                logMessage("Closed debug file", true, true);
            }
            disconnectPV();
            timer.stop();
            writeProperties();
            if (img != null) img.close();
            frame.setVisible(false);
            IJ.showStatus("Exiting Server");

        } catch (Exception e) {
            logMessage("run: Got exception: " + e.getMessage(), true, true);
            e.printStackTrace();
            logMessage("Close EPICS_NTNDA_Viewer window, and reopen, try again", true, true);
            IJ.showStatus(e.toString());
            try {
                if (isDebugFile) {
                    debugPrintStream.close();
                    debugFile.close();
                }
            } catch (Exception ee) {
            }
        }
    }

    private void makeImageCopy() {
        ImageProcessor ip = img.getProcessor();
        if (ip == null) return;
        ImagePlus imgcopy = new ImagePlus(channelName + ":" + numImageUpdates, ip.duplicate());
        imgcopy.show();
    }


    private boolean updateImage(PvaClientMonitorData monitorData) {
        Point oldWindowLocation = null;
        boolean madeNewWindow = false;
        PVStructure pvs = monitorData.getPVStructure();
        PVStructureArray dimArray = pvs.getSubField(PVStructureArray.class, "dimension");
        if (dimArray == null) {
            logMessage("dimension not found", true, true);
            return false;
        }
        int ndim = dimArray.getLength();
        if (ndim < 1) {
            logMessage("dimension is empty", true, true);
            return false;
        }
        int dimsint[] = new int[ndim];
        StructureArrayData dimdata = new StructureArrayData();
        dimArray.get(0, ndim, dimdata);
        for (int i = 0; i < ndim; ++i) {
            PVStructure dim = (PVStructure) dimdata.data[i];
            PVInt pvLen = dim.getSubField(PVInt.class, "size");
            if (pvLen == null) {
                logMessage("dimension size not found", true, true);
                return false;
            }
            dimsint[i] = pvLen.get();
        }
        int nx = dimsint[0];
        int ny = dimsint[1];
        int nz = 1;
        if (ndim >= 3)
            nz = dimsint[2];
        int cm = 0;
        PVStructureArray attrArray = pvs.getSubField(PVStructureArray.class, "attribute");
        if (attrArray != null) {
            int nattr = attrArray.getLength();
            StructureArrayData attrdata = new StructureArrayData();
            attrArray.get(0, nattr, attrdata);
            for (int i = 0; i < nattr; i++) {
                PVStructure pvAttr = attrdata.data[i];
                PVString pvName = pvAttr.getSubField(PVString.class, "name");
                if (pvName == null) continue;
                String name = pvName.get();
                if (!name.equals("ColorMode")) continue;
                PVUnion pvUnion = pvAttr.getSubField(PVUnion.class, "value");
                if (pvUnion == null) continue;
                PVScalar pvcm = pvUnion.get(PVScalar.class);
                if (pvcm == null) {
                    logMessage("color mode is not a PVScalar", true, true);
                    continue;
                }
                cm = ConvertFactory.getConvert().toInt(pvcm);
                break;
            }
        }
        PVUnion pvUnionValue = pvs.getSubField(PVUnion.class, "value");
        if (pvUnionValue == null) {
            logMessage("value not found", true, true);
            return false;
        }
        PVScalarArray imagedata = pvUnionValue.get(PVScalarArray.class);
        if (imagedata == null) {
            logMessage("value is not a scalar array", true, true);
            return false;
        }
        PVStructure pvCodecStruct = pvs.getSubField(PVStructure.class, "codec");
        PVString pvCodec = pvCodecStruct.getSubField(PVString.class, "name");
        String codec = pvCodec.get();
        if (!codec.isEmpty()) {
            if (ntndCodec == null) {
                ntndCodec = new NTNDCodec();
            }
            NTNDArray ntndArray = NTNDArray.wrapUnsafe(pvs);
            if (ntndArray == null) {
                logMessage("value is not a valid NTNDArray", true, true);
                return false;
            }
            ntndCodec.decompress(ntndArray);
        }
        imagedata = pvUnionValue.get(PVScalarArray.class);
        ScalarType scalarType = imagedata.getScalarArray().getElementType();
        if (nz == 0) nz = 1;  // 2-D images without color
        if (ny == 0) ny = 1;  // 1-D images which are OK, useful with dynamic profiler

        if (isDebugMessages)
            logMessage("UpdateImage: got image, sizes: " + nx + " " + ny + " " + nz, true, true);

        int numElements = nx * ny * nz;
        if (numElements == 0) {
            logMessage("array size = 0", true, true);
            return false;
        }

        if (isDebugMessages)
            logMessage("UpdateImage dt,dataType" + scalarType, true, true);

        if (isDebugMessages)
            logMessage("UpdateImage cm,colorMode" + cm + " " + colorMode, true, true);


        // if image size changes we must close window and make a new one.
        boolean makeNewWindow = false;
        if (nx != imageSizeX || ny != imageSizeY || nz != imageSizeZ || cm != colorMode || scalarType != dataType) {
            makeNewWindow = true;
            imageSizeX = nx;
            imageSizeY = ny;
            imageSizeZ = nz;
            colorMode = cm;
            dataType = scalarType;
            nxText.setText("" + imageSizeX);
            nyText.setText("" + imageSizeY);
            nzText.setText("" + imageSizeZ);
        }

        // If we are making a new stack close the window
        if (isNewStack) makeNewWindow = true;
        if (img == null) makeNewWindow = true;

        // If we need to make a new window then close the current one if it exists
        if (img != null && makeNewWindow) {
            try {
                if (img.getWindow() == null || !img.getWindow().isClosed()) {
                    ImageWindow win = img.getWindow();
                    if (win != null) {
                        oldWindowLocation = win.getLocationOnScreen();
                    }
                    img.close();
                }
            } catch (Exception ex) {
                logMessage("Exception closing window " + ex.getMessage(), true, true);
            }
            makeNewWindow = false;
        }
        // If the window does not exist or is closed make a new one
        if (img == null || img.getWindow() == null || img.getWindow().isClosed()) {
            switch (colorMode) {
                case 0:
                case 1:
                    if (dataType == ScalarType.pvUByte) {
                        img = new ImagePlus(channelName, new ByteProcessor(imageSizeX, imageSizeY));
                    } else if (dataType == ScalarType.pvUShort) {
                        img = new ImagePlus(channelName, new ShortProcessor(imageSizeX, imageSizeY));
                    } else if (dataType.isNumeric()) {
                        img = new ImagePlus(channelName, new FloatProcessor(imageSizeX, imageSizeY));
                    } else {
                        throw new RuntimeException("illegal array type " + dataType);
                    }
                    break;
                case 2:
                    img = new ImagePlus(channelName, new ColorProcessor(imageSizeY, imageSizeZ));
                    break;
                case 3:
                    img = new ImagePlus(channelName, new ColorProcessor(imageSizeX, imageSizeZ));
                    break;
                case 4:
                    img = new ImagePlus(channelName, new ColorProcessor(imageSizeX, imageSizeY));
                    break;
            }
            img.show();
            if (oldWindowLocation != null) img.getWindow().setLocation(oldWindowLocation);
            madeNewWindow = true;
        }

        if (isNewStack) {
            imageStack = new ImageStack(img.getWidth(), img.getHeight());
            imageStack.addSlice(channelName + numImageUpdates, img.getProcessor());
            // Note: we need to add this first slice twice in order to get the slider bar 
            // on the window - ImageJ won't put it there if there is only 1 slice.
            imageStack.addSlice(channelName + numImageUpdates, img.getProcessor());
            img.close();
            img = new ImagePlus(channelName, imageStack);
            img.show();
            logMessage("img.show() run", true, true);
            isNewStack = false;
        }
        imagedata = pvUnionValue.get(PVScalarArray.class);
        if (imagedata == null) {
            logMessage("value is not a scalar array", true, true);
            return false;
        }

        if (colorMode == 0 || colorMode == 1) {
            if (dataType == ScalarType.pvUByte) {
                byte[] pixels = new byte[numElements];
                convert.toByteArray(imagedata, 0, numElements, pixels, 0);
                img.getProcessor().setPixels(pixels);
            } else if (dataType == ScalarType.pvUShort) {
                short[] pixels = new short[numElements];
                convert.toShortArray(imagedata, 0, numElements, pixels, 0);
                img.getProcessor().setPixels(pixels);
            } else if (dataType.isNumeric()) {
                float[] pixels = new float[numElements];
                convert.toFloatArray(imagedata, 0, numElements, pixels, 0);
                img.getProcessor().setPixels(pixels);
            } else {
                throw new RuntimeException("illegal array type " + dataType);
            }
        } else if (colorMode >= 2 && colorMode <= 4) {
            int[] pixels = (int[]) img.getProcessor().getPixels();

            byte inpixels[] = new byte[numElements];

            convert.toByteArray(imagedata, 0, numElements, inpixels, 0);
            double dispMin = img.getDisplayRangeMin();
            double dispMax = img.getDisplayRangeMax();
            if ((dispMin != 0) || (dispMax != 255)) {
                int i;
                if ((dispMin != prevDispMin) || (dispMax != prevDispMax)) {
                    // Recompute LUT
                    prevDispMin = dispMin;
                    prevDispMax = dispMax;
                    double slope = 255 / (dispMax - dispMin);
                    for (i = 0; i < 256; i++) {
                        if (i < dispMin)
                            colorLUT[i] = 0;
                        else if (i > dispMax)
                            colorLUT[i] = (byte) 255;
                        else
                            colorLUT[i] = (byte) ((i - dispMin) * slope + 0.5);
                    }
                }
                for (i = 0; i < numElements; i++) {
                    inpixels[i] = colorLUT[inpixels[i] & 0xff];
                }
            }

            switch (colorMode) {
                case 2: {
                    int in = 0, out = 0;
                    while (in < numElements) {
                        pixels[out++] = (inpixels[in++] & 0xFF) << 16 | (inpixels[in++] & 0xFF) << 8 | (inpixels[in++] & 0xFF);
                    }
                }
                break;
                case 3: {
                    int nCols = imageSizeX, nRows = imageSizeZ, row, col;
                    int redIn, greenIn, blueIn, out = 0;
                    for (row = 0; row < nRows; row++) {
                        redIn = row * nCols * 3;
                        greenIn = redIn + nCols;
                        blueIn = greenIn + nCols;
                        for (col = 0; col < nCols; col++) {
                            pixels[out++] = (inpixels[redIn++] & 0xFF) << 16 | (inpixels[greenIn++] & 0xFF) << 8 | (inpixels[blueIn++] & 0xFF);
                        }
                    }
                }
                break;
                case 4: {
                    int imageSize = imageSizeX * imageSizeY;
                    int redIn = 0, greenIn = imageSize, blueIn = 2 * imageSize, out = 0;
                    while (redIn < imageSize) {
                        pixels[out++] = (inpixels[redIn++] & 0xFF) << 16 | (inpixels[greenIn++] & 0xFF) << 8 | (inpixels[blueIn++] & 0xFF);
                    }
                }
                break;
            }
            img.getProcessor().setPixels(pixels);
        }

        /*Takes log of image, stores snapshot for Undo if plugin is stopped.
        */
        if (isLogOn) {
            snapBackup = takeLog(img);
            if(dataType!= ScalarType.pvUShort && dataType!=ScalarType.pvUByte) resetContrast(img);
        }

        if (isSaveToStack) {
            img.getStack().addSlice(channelName + numImageUpdates, img.getProcessor().duplicate());
        }
        img.setSlice(img.getNSlices());
        img.show();
        img.updateAndDraw();
        ImageCanvas ic = img.getCanvas();
        Point loc = ic != null ? ic.getCursorLoc() : null;
        if (loc != null)
            img.mouseMoved(loc.x, loc.y);
        img.updateStatusbarValue();
        numImageUpdates++;
        // Automatically set brightness and contrast if we made a new window
        if (madeNewWindow) new ContrastEnhancer().stretchHistogram(img, 0.5);
        return true;
    }

    /**
     * Create the GUI and show it.  For thread safety,
     * this method should be invoked from the
     * event-dispatching thread.
     */
    private void createAndShowGUI() {
        //Create and set up the window.
        nxText = new JTextField(6);
        nxText.setEditable(false);
        nxText.setHorizontalAlignment(JTextField.CENTER);
        nyText = new JTextField(6);
        nyText.setEditable(false);
        nyText.setHorizontalAlignment(JTextField.CENTER);
        nzText = new JTextField(6);
        nzText.setEditable(false);
        nzText.setHorizontalAlignment(JTextField.CENTER);
        fpsText = new JTextField(6);
        fpsText.setEditable(false);
        fpsText.setHorizontalAlignment(JTextField.CENTER);
        statusText = new JTextField(57);
        statusText.setEditable(false);

        channelNameText = new JTextField(channelName, 15);
        startButton = new JButton("Start");
        stopButton = new JButton("Stop");
        snapButton = new JButton("Snap");
        JCheckBox captureCheckBox = new JCheckBox("");
        JCheckBox logCheckBox = new JCheckBox("");

        frame = new JFrame("Image J EPICS_NTNDA_Viewer Plugin");
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
        panel.add(new JLabel("channelName"), c);
        c.gridx = 3;
        panel.add(new JLabel("NX"), c);
        c.gridx = 4;
        panel.add(new JLabel("NY"), c);
        c.gridx = 5;
        panel.add(new JLabel("NZ"), c);
        c.gridx = 6;
        panel.add(new JLabel("Frames/s"), c);
        c.gridx = 7;
        panel.add(new JLabel("Capture to Stack"), c);
        c.gridx = 8;
        panel.add(new JLabel("Log"), c);

        // Middle row
        // These widgets should be centered
        c.anchor = GridBagConstraints.CENTER;
        c.gridy = 1;
        c.gridx = 0;
        panel.add(channelNameText, c);
        c.gridx = 1;
        panel.add(startButton, c);
        c.gridx = 2;
        panel.add(stopButton, c);
        c.gridx = 3;
        panel.add(nxText, c);
        c.gridx = 4;
        panel.add(nyText, c);
        c.gridx = 5;
        panel.add(nzText, c);
        c.gridx = 6;
        panel.add(fpsText, c);
        c.gridx = 7;
        panel.add(captureCheckBox, c);
        c.gridx = 8;
        panel.add(logCheckBox, c);
        c.gridx = 9;
        panel.add(snapButton, c);

        // Bottom row
        c.gridy = 2;
        c.gridx = 0;
        c.anchor = GridBagConstraints.EAST;
        panel.add(new JLabel("Status: "), c);
        c.gridx = 1;
        c.gridwidth = 8;
        c.anchor = GridBagConstraints.WEST;
        panel.add(statusText, c);

        //Display the window.
        frame.pack();
        frame.addWindowListener(new FrameExitListener());
        frame.setVisible(true);

        int timerDelay = 2000;  // 2 seconds 
        timer = new javax.swing.Timer(timerDelay, new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                long time = new Date().getTime();
                double elapsedTime = (double) (time - prevTime) / 1000.;
                double fps = numImageUpdates / elapsedTime;
                NumberFormat form = DecimalFormat.getInstance();
                ((DecimalFormat) form).applyPattern("0.0");
                fpsText.setText("" + form.format(fps));
                if (isPluginRunning && isStarted && numImageUpdates > 0)
                    logMessage(String.format("Received %d images in %.2f sec", numImageUpdates, elapsedTime), true, false);
                prevTime = time;
                numImageUpdates = 0;
            }
        });
        timer.start();

        channelNameText.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                disconnectPV();
                connectPV();
            }
        });

        startButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                startDisplay();
            }
        });

        stopButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                stopDisplay();
            }
        });

        //Turns log on and off. If plugin is stopped, log checkbox takes log or undoes log on static image or other image depending on user selection
        logCheckBox.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    isLogOn = true;
                    logMessage("Log display on", true, true);
                    if (!stopButton.isEnabled()) {
                        if (WindowManager.getCurrentWindow().equals(img.getWindow())) {
                            snapBackup = takeLog(img);
                            img.updateAndDraw();
                            resetContrast(img);
                        }
                        else{
                            ImagePlus imgC = WindowManager.getCurrentImage();
                            altSnapBackup = takeLog(imgC);
                            imgC.updateAndDraw();
                            resetContrast(imgC);
                        }
                    }
                    else if (!WindowManager.getCurrentWindow().equals(img.getWindow())){
                        ImagePlus imgC = WindowManager.getCurrentImage();
                        altSnapBackup = takeLog(imgC);
                        imgC.updateAndDraw();
                        resetContrast(imgC);
                        isLogOn = false;
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
                }

            }
        });

        snapButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                makeImageCopy();
            }
        });

        captureCheckBox.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    isSaveToStack = true;
                    isNewStack = true;
                    logMessage("Capture on", true, true);
                } else {
                    isSaveToStack = false;
                    logMessage("Capture off", true, true);
                }

            }
        });
    }

    private Object takeLog(ImagePlus image){
        image.getProcessor().snapshot();
        image.getProcessor().resetMinAndMax();
        image.getProcessor().log();
        return image.getProcessor().getSnapshotPixels();
    }
    private void resetContrast(ImagePlus image){
        image.getProcessor().resetMinAndMax();
        if (image.getProcessor().getMin()<0) image.getProcessor().setMinAndMax(0, image.getProcessor().getMax());
        new ContrastEnhancer().stretchHistogram(img, 0.5);
    }
    private class FrameExitListener extends WindowAdapter {
        public void windowClosing(WindowEvent event) {
            isPluginRunning = false;
            // We need to wake up the main thread so it shuts down cleanly
            synchronized (this) {
                notify();
            }
        }
    }

    private void logMessage(String message, boolean logDisplay, boolean logFile) {
        Date date = new Date();
        SimpleDateFormat simpleDate = new SimpleDateFormat("dd-MMM-yyyy kk:mm:ss.SSS");
        String completeMessage;

        completeMessage = simpleDate.format(date) + ": " + message;
        if (logDisplay) statusText.setText(completeMessage);
        if (logFile) IJ.log(completeMessage);
    }

    private void readProperties() {
        String temp, path = null;
        try {
            String fileSep = System.getProperty("file.separator");
            path = System.getProperty("user.home") + fileSep + propertyFile;
            FileInputStream file = new FileInputStream(path);
            properties.load(file);
            file.close();
            temp = properties.getProperty("channelName");
            if (temp != null) channelName = temp;
            IJ.log("Read properties file: " + path + "  channelName= " + channelName);
        } catch (Exception ex) {
            IJ.log("readProperties:exception: " + ex.getMessage());
        }
    }

    private void writeProperties() {
        String path;
        try {
            String fileSep = System.getProperty("file.separator");
            path = System.getProperty("user.home") + fileSep + propertyFile;
            properties.setProperty("channelName", channelName);
            FileOutputStream file = new FileOutputStream(path);
            properties.store(file, "EPICS_NTNDA_Viewer Properties");
            file.close();
            logMessage("Wrote properties file: " + path, true, true);
        } catch (Exception ex) {
            logMessage("writeProperties:exception: " + ex.getMessage(), true, true);
        }
    }
}
