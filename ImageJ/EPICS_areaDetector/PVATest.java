// PVATest.java
// Mark Rivers, University of Chicago

import org.epics.pvaClient.PvaClient;
import org.epics.pvaClient.PvaClientChannel;
import java.util.Date;

public class PVATest
{
    // may want to change these
    private String channelName = "13SIM1:Pva1:Image";
    private boolean isConnected = false;
    
    private static PvaClient pva=PvaClient.get();
    private PvaClientChannel mychannel = null;

    public static void main(String[] args) {
      System.out.println("PVATest starting");
      try {
        PVATest pvaTest = new PVATest();
        pvaTest.run();
      }
      catch (Exception ex) {
        System.out.println("Error in main: " + ex.getMessage());
      }
    }
    
    public void run() {
      try {
        while (true) {
          if (!isConnected) {
            connectPV();
          }
          Thread.sleep(1000);
        }
      }
      catch (Exception ex) {
        System.out.println("Error in run: " + ex.getMessage());
      }
    }

    public void connectPV() {
      disconnectPV();
      try {
        System.out.println("Trying to connect to : " + channelName);
        mychannel = pva.createChannel(channelName,"pva");
        mychannel.connect(2.0); 
        isConnected = true;
        System.out.println("connected to " + channelName);
      }
      catch (Exception ex) {
          System.out.println("Could not connect to : " + channelName + " " + ex.getMessage());
          isConnected = false;
          disconnectPV();
      }
    }

    public void disconnectPV() {
      try {
        if (mychannel!=null) {
          mychannel.destroy();
          System.out.println("Destroyed channel for EPICS PV:" + channelName);
          mychannel = null;
        }
      }
      catch (Exception ex) {
        System.out.println("Error in disconnectPV for EPICS PV:" + channelName + ex.getMessage());
      }
      isConnected = false;
    }
}