// EPICS_NTNDA_Viewer.java
// Original authors
//      Adapted for EPICS V4 from EPICS_CA_Viewer by Tim Madden
//      Tim Madden, APS
//      Mark Rivers, University of Chicago
//      Marty Kraimer
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
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

import org.epics.pvaClient.PvaClient;
import org.epics.pvaClient.PvaClientChannel;
import org.epics.pvaClient.PvaClientMonitor;
import org.epics.pvaClient.PvaClientMonitorData;
import org.epics.pvdata.factory.ConvertFactory;
import org.epics.pvdata.pv.Convert;
import org.epics.pvdata.pv.PVInt;
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
import ij.plugin.ContrastEnhancer;
import ij.plugin.PlugIn;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

/**
 * ImageJ viewer for NTNDArray data.
 *
 */
public class EPICS_NTNDA_Viewer implements PlugIn
{
    // may want to change these
    private String channelName = "13SIM1:Pva1:Image";
    private boolean isDebugMessages = false;
    private boolean isDebugFile = false;
    private String propertyFile = "EPICS_NTNDA_Viewer.properties";
    
    private ImagePlus img = null;
    private ImageStack imageStack = null;
    private int imageSizeX = 0;
    private int imageSizeY = 0;
    private int imageSizeZ = 0;
    private int colorMode = 0;
    private ScalarType dataType = ScalarType.pvBoolean;
    private FileOutputStream debugFile = null;
    private PrintStream debugPrintStream = null;
    private Properties properties = new Properties();
    
    private enum State {
        idle,
        connect,
        connected,
        quit
    }
    private volatile State state = State.idle;
    // These are used for the frames/second calculation
    private long prevTime = 0;
    private volatile int numImageUpdates = 0;
    private JFrame frame = null;

    private JTextField channelNameText = null;
    private JTextField nxTest = null;
    private JTextField nyTest = null;
    private JTextField nzTest = null;
    private JTextField fpsText = null;
    private JTextField statusText = null;
    private JButton startButton = null;
    private JButton stopButton = null;
    private JButton snapButton = null;

    private boolean isPluginRunning = false;
    private boolean isSaveToStack = false;
    private boolean isNewStack = false;

    private javax.swing.Timer timer = null;

    private static PvaClient pva=PvaClient.get();
    private PvaClientChannel mychannel = null;
    private PvaClientMonitor pvamon = null;
    private Convert converter = ConvertFactory.getConvert();
    /**
     * Constructor
     */
    public EPICS_NTNDA_Viewer()
    {
        createAndShowGUI();
    }
    /* (non-Javadoc)
     * @see ij.plugin.PlugIn#run(java.lang.String)
     */
    public void run(String arg)
    {
        IJ.showStatus("epics running");
        try
        {
            isPluginRunning = true;
            Date date = new Date();
            prevTime = date.getTime();
            readProperties();
            if (isDebugFile)
            {
                debugFile = new FileOutputStream(System.getProperty("user.home") +
                        System.getProperty("file.separator") + "IJEPICS_debug.txt");
                debugPrintStream = new PrintStream(debugFile);
            }
            while (isPluginRunning)
            {
                if(state==State.idle) {
                    Thread.sleep(1000);
                    continue;
                }
                if(state==State.quit) {
                    if(mychannel!=null) mychannel.destroy();
                    if(img!=null) img.close();
                    img = null;
                    mychannel = null;
                    pvamon = null;
                    state = State.idle;
                    channelNameText.setBackground(Color.white);
                    continue;
                }
                if(state==State.connect) {
                    try
                    {
                        channelName = channelNameText.getText();
                        logMessage("Trying to connect to : " + channelName, true, true);
                        mychannel = pva.createChannel(channelName,"pva");
                        mychannel.connect(5.0); 
                        pvamon=mychannel.createMonitor("field()");
                        pvamon.start();        
                        logMessage("connected to " + channelName, true,true);
                        state = State.connected;
                        channelNameText.setBackground(Color.green);
                    }
                    catch (Exception ex)
                    {
                        logMessage("Could not connect to : " + channelName + ex.getMessage(), true, true);
                        mychannel = null;
                        pvamon = null;
                        channelNameText.setBackground(Color.red);
                    }
                }
                if(state==State.connected) {
                    boolean is_image;
                    if(!mychannel.getChannel().isConnected()) {
                        mychannel.destroy();
                        if(img!=null) img.close();
                        img = null;
                        mychannel = null;
                        pvamon = null;
                        state = State.connect;
                        channelNameText.setBackground(Color.white);
                        continue;
                    }
                    channelNameText.setBackground(Color.green);
                    try{
                        is_image = pvamon.waitEvent(1);
                    }
                    catch(Exception ex)
                    {
                        if (isDebugMessages)
                            IJ.log("run: waitEvent throws ");
                        is_image=false;

                    }
                    if (is_image)
                    {
                        if (isDebugMessages) IJ.log("calling updateImage");
                        try {
                            updateImage(pvamon.getData());
                            if (isDebugMessages) IJ.log("run:to call releaseEvent ");
                            pvamon.releaseEvent();
                            if (isDebugMessages) IJ.log("run: called releaseEvent ");
                        }
                        catch(Exception ex)
                        {
                            startButton.setEnabled(true);
                            stopButton.setEnabled(false);
                            snapButton.setEnabled(false);
                            state = State.quit;
                        }
                        
                    }// if (is_image)
                }
            }
            if (isDebugMessages) logMessage("run: Plugin stopping", true, true);
            if (isDebugFile)
            {
                debugPrintStream.close();
                debugFile.close();
                logMessage("Closed debug file", true, true);
            }

            timer.stop();
            writeProperties();

            if(img!=null) img.close();

            frame.setVisible(false);

            IJ.showStatus("Exiting Server");

        }
        catch (Exception e)
        {
            IJ.log("run: Got exception: " + e.getMessage());
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
    
    private void makeImageCopy()
    {
        ImageProcessor ip = img.getProcessor();
        if (ip == null) return;
        ImagePlus imgcopy = new ImagePlus(channelName + ":" + numImageUpdates, ip.duplicate());
        imgcopy.show();
    }

    private void updateImage(PvaClientMonitorData monitorData)
    {
        monitorData = pvamon.getData();                 
        Point oldWindowLocation =null;
        boolean madeNewWindow = false;
        PVStructure pvs = monitorData.getPVStructure();
        PVStructureArray dimArray = pvs.getSubField(PVStructureArray.class,"dimension");
        if(dimArray==null) {
            logMessage("dimension not found",false,true);
            throw new RuntimeException("dimension not found");
        }
        int ndim = dimArray.getLength();
        if(ndim<1) {
            logMessage("dimension is empty",false,true);
            throw new RuntimeException("dimension is empty");
        }
        int dimsint[] = new int[ndim];
        StructureArrayData dimdata=new StructureArrayData();
        dimArray.get(0,ndim,dimdata);
        for(int i=0; i<ndim; ++i) {
            PVStructure dim = (PVStructure)dimdata.data[i];
            PVInt pvLen = dim.getSubField(PVInt.class,"size");
            if(pvLen==null) {
                logMessage("dimension size not found",false,true);
                throw new RuntimeException("dimension size not found");
            }
            dimsint[i] = pvLen.get();
        }
        int nx = dimsint[0];
        int ny = dimsint[1];
        int nz = 1;
        if (ndim>=3)
            nz = dimsint[2];
        int cm = -1;
        PVStructureArray attrArray = pvs.getSubField(PVStructureArray.class,"attribute");
        if(attrArray==null) {
            logMessage("attribute array not found",false,true);
            throw new RuntimeException("attribute array not found");
        }
        int nattr = attrArray.getLength();
        StructureArrayData attrdata=new StructureArrayData();
        attrArray.get(0,nattr,attrdata);
        for (int i = 0; i<nattr; i++)
        {
            PVStructure pvAttr = attrdata.data[i];
            PVString pvName = pvAttr.getSubField(PVString.class,"name");
            if(pvName==null) {
                logMessage("attribute name not found",false,true);
                throw new RuntimeException("attribute name not found");
            }
            String name = pvName.get();
            if(!name.equals("ColorMode")) continue;
            PVUnion pvUnion = pvAttr.getSubField(PVUnion.class,"value");
            if(pvUnion==null) {
                logMessage("attribute value not found",false,true);
                throw new RuntimeException("attribute value not found");
            }
            PVInt pvcm = pvUnion.get(PVInt.class);
            if(pvcm==null) {
                logMessage("color mode is not an int",false,true);
                throw new RuntimeException("color mode is not an int");
            }
            cm = pvcm.get();
            break;
        }
        if(cm<0) {
            logMessage("ColorMode not foud",false,true);
            throw new RuntimeException("ColorMode not found");
        }
        PVUnion pvUnion = pvs.getSubField(PVUnion.class,"value");
        if(pvUnion==null) {
            logMessage("value not found",false,true);
            throw new RuntimeException("value not found");
        }
        PVScalarArray imagedata = pvUnion.get(PVScalarArray.class);
        if(imagedata==null) {
            logMessage("value is not a scalar array",false,true);
            throw new RuntimeException("value is not a scalar array");
        }
        int arraylen = imagedata.getLength();
        ScalarType scalarType = imagedata.getScalarArray().getElementType();
        if (nz == 0) nz = 1;  // 2-D images without color
        if (ny == 0) ny = 1;  // 1-D images which are OK, useful with dynamic profiler

        if (isDebugMessages)
            logMessage("UpdateImage: got image, sizes: " + nx + " " + ny + " " + nz,true,true);

        int getsize = nx * ny * nz;
        if (getsize == 0) return;  // Not valid dimensions

        if (isDebugMessages)
            logMessage("UpdateImage dt,dataType" + scalarType, true, true);

        if (isDebugMessages)
            logMessage("UpdateImage cm,colorMode" + cm+ " "+colorMode, true, true);

        // if image size changes we must close window and make a new one.
        boolean makeNewWindow = false;
        if (nx != imageSizeX || ny != imageSizeY || nz != imageSizeZ || cm != colorMode || scalarType !=dataType)
        {
            makeNewWindow = true;
            imageSizeX = nx;
            imageSizeY = ny;
            imageSizeZ = nz;
            colorMode = cm;
            dataType = scalarType;
            nxTest.setText("" + imageSizeX);
            nyTest.setText("" + imageSizeY);
            nzTest.setText("" + imageSizeZ);
        }

        // If we are making a new stack close the window
        if (isNewStack) makeNewWindow = true;
        if(img==null) makeNewWindow = true;

        // If we need to make a new window then close the current one if it exists
        if (img!=null && makeNewWindow)
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
                IJ.log("updateImage for exception: " + ex.getMessage());
            }
            makeNewWindow = false;
        }
        // If the window does not exist or is closed make a new one
        if (img==null || img.getWindow() == null || img.getWindow().isClosed())
        {
            switch (colorMode)
            {
            case 0:
            case 1:
                if(dataType==ScalarType.pvByte||dataType==ScalarType.pvUByte)
                {
                    img = new ImagePlus(channelName, new ByteProcessor(imageSizeX, imageSizeY));
                }
                else if(dataType==ScalarType.pvShort||dataType==ScalarType.pvUShort)
                {
                    img = new ImagePlus(channelName, new ShortProcessor(imageSizeX, imageSizeY));
                }
                else if (dataType.isNumeric()) 
                {
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

        if (isNewStack)
        {
            imageStack = new ImageStack(img.getWidth(), img.getHeight());
            imageStack.addSlice(channelName + numImageUpdates, img.getProcessor());
            // Note: we need to add this first slice twice in order to get the slider bar 
            // on the window - ImageJ won't put it there if there is only 1 slice.
            imageStack.addSlice(channelName + numImageUpdates, img.getProcessor());
            img.close();
            img = new ImagePlus(channelName, imageStack);
            img.show();
            isNewStack = false;
        }

        if (isDebugMessages) IJ.log("about to get pixels");
        if (colorMode == 0 || colorMode == 1)
        {
            if(dataType==ScalarType.pvByte||dataType==ScalarType.pvUByte)
            {            
                byte[] pixels= new byte[arraylen];
                converter.toByteArray(imagedata, 0, arraylen, pixels, 0);
                img.getProcessor().setPixels(pixels);
            }
            else if(dataType==ScalarType.pvShort||dataType==ScalarType.pvUShort)
            {
                short[] pixels = new short[arraylen];
                converter.toShortArray(imagedata, 0, arraylen, pixels, 0);
                img.getProcessor().setPixels(pixels);
            }
            else if (dataType.isNumeric()) 
            {
                float[] pixels =new float[arraylen];
                converter.toFloatArray(imagedata, 0, arraylen, pixels, 0);
                img.getProcessor().setPixels(pixels);
            } else {
                throw new RuntimeException("illegal array type " + dataType);
            }
        }
        else if (colorMode >= 2 && colorMode <= 4)
        {
            int[] pixels = (int[])img.getProcessor().getPixels();

            //byte inpixels[] = epicsGetByteArray(ch_image, getsize);
            byte inpixels[]=new byte[getsize];

            converter.toByteArray(imagedata, 0, getsize, inpixels, 0);
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

        if (isSaveToStack)
        {
            img.getStack().addSlice(channelName + numImageUpdates, img.getProcessor().duplicate());
        }
        img.setSlice(img.getNSlices());
        img.show();
        img.updateAndDraw();
        img.updateStatusbarValue();
        numImageUpdates++;
        // Automatically set brightness and contrast if we made a new window
        if (madeNewWindow) new ContrastEnhancer().stretchHistogram(img, 0.5);
    }
    /**
     * Create the GUI and show it.  For thread safety,
     * this method should be invoked from the
     * event-dispatching thread.
     */
    private void createAndShowGUI()
    {
        //Create and set up the window.
        nxTest = new JTextField(6);
        nxTest.setEditable(false);
        nxTest.setHorizontalAlignment(JTextField.CENTER);
        nyTest = new JTextField(6);
        nyTest.setEditable(false);
        nyTest.setHorizontalAlignment(JTextField.CENTER);
        nzTest = new JTextField(6);
        nzTest.setEditable(false);
        nzTest.setHorizontalAlignment(JTextField.CENTER);
        fpsText = new JTextField(6);
        fpsText.setEditable(false);
        fpsText.setHorizontalAlignment(JTextField.CENTER);
        statusText = new JTextField(40);
        statusText.setEditable(false);

        channelNameText = new JTextField(channelName, 15);
        startButton = new JButton("Start");
        stopButton = new JButton("Stop");
        stopButton.setEnabled(false);
        snapButton = new JButton("Snap");
        snapButton.setEnabled(false);
        JCheckBox captureCheckBox = new JCheckBox("");

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

        // Middle row
        // These widgets should be centered
        c.anchor = GridBagConstraints.CENTER;
        c.gridy = 1;
        c.gridx = 0;
        panel.add(channelNameText, c);
        c.gridx = 1;
        panel.add(nxTest, c);
        c.gridx = 2;
        panel.add(nyTest, c);
        c.gridx = 3;
        panel.add(nzTest, c);
        c.gridx = 4;
        panel.add(fpsText, c);
        c.gridx = 5;
        panel.add(captureCheckBox, c);
        c.gridx = 6;
        panel.add(snapButton, c);
        c.gridx = 7;
        panel.add(startButton, c);
        c.gridx = 8;
        panel.add(stopButton, c);

        // Bottom row
        c.gridy = 2;
        c.gridx = 0;
        c.anchor = GridBagConstraints.EAST;
        panel.add(new JLabel("Status: "), c);
        c.gridx = 1;
        c.gridwidth = 7;
        c.anchor = GridBagConstraints.WEST;
        panel.add(statusText, c);

        //Display the window.
        frame.pack();
        frame.addWindowListener(new FrameExitListener());
        frame.setVisible(true);

        int timerDelay = 2000;  // 2 seconds 
        timer = new javax.swing.Timer(timerDelay, new ActionListener()
        {
            public void actionPerformed(ActionEvent event)
            {
                long time = new Date().getTime();
                double fps = 1000. * numImageUpdates / (double)(time - prevTime);
                NumberFormat form = DecimalFormat.getInstance();
                ((DecimalFormat)form).applyPattern("0.0");
                fpsText.setText("" + form.format(fps));
                if (isPluginRunning && numImageUpdates > 0) logMessage("New images=" + numImageUpdates, true, false);
                prevTime = time;
                numImageUpdates = 0;
            }
        });
        timer.start();

        startButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent event)
            {
                startButton.setEnabled(false);
                stopButton.setEnabled(true);
                snapButton.setEnabled(true);
                state = State.connect;
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
                state = State.quit;
                logMessage("Image display stopped", true, true);
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
            public void itemStateChanged(ItemEvent e)
            {
                if (e.getStateChange() == ItemEvent.SELECTED)
                {
                    isSaveToStack = true;
                    isNewStack = true;
                    IJ.log("record on");
                }
                else
                {
                    isSaveToStack = false;
                    IJ.log("record off");
                }

            }
        });
    }

    private class FrameExitListener extends WindowAdapter
    {
        public void windowClosing(WindowEvent event)
        {
            isPluginRunning = false;
            // We need to wake up the main thread so it shuts down cleanly
            synchronized (this)
            {
                notify();
            }
        }
    }

    private void logMessage(String message, boolean logDisplay, boolean logFile)
    {
        Date date = new Date();
        SimpleDateFormat simpleDate = new SimpleDateFormat("d/M/y k:m:s.S");
        String completeMessage;

        completeMessage = simpleDate.format(date) + ": " + message;
        if (logDisplay) statusText.setText(completeMessage);
        if (logFile) IJ.log(completeMessage);
    }

    private void readProperties()
    {
        String temp, path = null;
        try
        {
            String fileSep = System.getProperty("file.separator");
            path = System.getProperty("user.home") + fileSep + propertyFile;
            FileInputStream file = new FileInputStream(path);
            properties.load(file);
            file.close();
            temp = properties.getProperty("channelName");
            if (temp != null) channelName = temp;
            IJ.log("Read properties file: " + path + "  channelName= " + channelName);
        }
        catch (Exception ex)
        {
            IJ.log("readProperties:exception: " + ex.getMessage());
        }
    }

    private void writeProperties()
    {
        String path;
        try
        {
            String fileSep = System.getProperty("file.separator");
            path = System.getProperty("user.home") + fileSep + propertyFile;
            properties.setProperty("channelName", channelName);
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
