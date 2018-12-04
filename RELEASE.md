ADViewers Releases
===============

The latest untagged master branch can be obtained at
https://github.com/areaDetector/ADViewers.

Prior to the release of [ADCore](https://github.com/areaDetector/ADCore) R3-0 the code in ADViewers
was in the Viewers subdirectory of ADCore.

Tagged source code releases can be obtained at 
https://github.com/areaDetector/ADViewers/releases .

Release Notes
=============

R1-3 (December 3, 2018)
======================
### EPICS_NTNDA_Viewer
* Added support for reading compressed NTNDArrays.  Blosc and JPEG compression are both supported.
  This can substantially reduce network traffic when the IOC and the viewer are running on 
  different machines.
  * The decompression is done using the C libraries because I could not find any native Java code for blosc decompression,
    and the native Java code for jpeg decompression is signifcantly more complicated (and probably slower) 
    than just using the C library.
    The required libraries on Linux are decompressJPEG.so, libjpeg.so, libblosc.so, and libzlib.so.
    On Windows the libraries are decompressJPEG.dll, jpeg.dll, blosc.dll, and zlib.dll.  
  * ImageJ needs to be able to find these shareable libraries to handle compressed arrays.
    One way to do this is to add areaDetector/ADSupport/lib/linux-x86_64/ to the LD_LIBRARY_PATH environment
    variable on Linux, and areaDetector/ADSupport/lib/windows-x86 to the PATH environment variable on Windows.
    This assumes that ADSupport was built using WITH_BLOSC=YES, WITH_JPEG=YES, BLOSC_EXTERNAL=NO, and JPEG_EXTERNAL=NO.
  * In principle another way to do this is to set the jna.library.path property to point to that directory when starting ImageJ,
    e.g. `java -Djna.library.path=/home/epics/support/areaDetector/ADSupport/lib/linux-x86_64 -jar ij.jar` 
    However, ImageJ is normally started via an executable file rather than a script invoking ij.jar on both Linux and Windows,
    and loading via the above command requires other settings as well to make ImageJ work properly.
  * The ADViewers distribution includes two new jar files, jna-5.1.0.jar and jblosc-1.0.1.dev.jar.
    The jna file provides support for Java Native Access, which is the interface to calling the shareable libraries.
    The jblosc file provides a Java wrapper around the blosc shareable library. These files need to be copied to
    ImageJ/plugins/EPICS_areaDetector along with the other files in the ADViewers/ImageJ/EPICS_areaDetector directory.
  * The ADViewers distribution also includes two new .java files,  decompressJPEGDll.java and myUtil.java. 
    These files need to be compiled once in ImageJ using the `Plugins/Compile and Run ...` menu.  The files
    are actually just compiled and not run, since they are just support files, not plugins.
    decompressJPEGDll.java is a wrapper around the C JPEG library. 
    myUtil.java is a modified version of Util.java that is included in the JBlosc package.  The version in that
    package lacked support for short (16-bit integer) arrays, and lacked the ability to specify the byte order
    for JNA buffers.
* Changes to the user interface.  
  * Removed the Connect/Disconnect button.  Typing Enter in the Channel Name field will do a connect.
  * Typing a new Channel Name followed by Enter will disconnect the existing channel and connect the new one.
  * The Start/Stop button has been replaced by separate Start and Stop buttons.
    This makes it easier to tell the current state at a glance.

R1-2 (November 11, 2018)
======================
### EPICS_NTNDA_Viewer
* Changed connection management to use callbacks rather than polling.  Thanks to Marty Kraimer for this.
* Previously this viewer was incorrectly treating signed 8-bit and 16-bit images as unsigned.
  Changed so that signed 8-bit and 16-bit data are now converted to float, so ImageJ correctly displays
  negative values.
  Conversion to float is required because the ImageJ ByteProcessor and ShortProcessor
  only support unsigned integers.
* Improved the status messages in the display to show when the display is stopped, and when connect and
  disconnect events occur.
### EPICS_AD_Viewer
* Previously this viewer was incorrectly treating signed 8-bit and 16-bit images as unsigned.
  Changed so that signed 8-bit and 16-bit data are now converted to float, so ImageJ correctly displays
  negative values. 
  Conversion to float is required because the ImageJ ByteProcessor and ShortProcessor
  only support unsigned integers.


R1-1 (October 4, 2018)
======================
### EPICS_NTNDAViewer
* Found a serious bug in epics-pvaclient-4.3.1.jar.  A destroy() function was not being called when it should have been.
  The result was that when the NTNDArray was not reachable (i.e. IOC not running) the broadcast search requests added
  additional copies of the same PV with time.  This caused the request to grow in size until it required many
  packets.  The network was thus flooded with broadcast packets if the ImageJ plugin ran for many hours or days without
  being able to connect to the IOC.  This was sufficient to cause VME IOCs on the subnet to be 95% CPU bound just processing 
  these broadcast packets.
* Fixed the logic in connectPV() when a connect attempt failed.  This was also incorrect, and could contribute to the above
  problem.

* Marty Kraimer fixed the problem in epics-pvaclient-4.3.2.jar, which is now included in this release of ADViewer.

### pvAccess jar files
* All of the other pvAccess jar files were updated to the latest versions.


R1-0 (July 1, 2017)
======================
### Initial release.  
* Prior to the release of [ADCore](https://github.com/areaDetector/ADCore) R3-0 the code in ADViewers
  was in the Viewers subdirectory of ADCore.

### ImageJ/EPICS_areaDetector/EPICS_NTNDA_Viewer.java
* This is a new plugin written by Tim Madden and Marty Kraimer.  
  It is essentially identical to EPICS_AD_Viewer.java except that it displays NTNDArrays from the NDPluginPva plugin, 
  i.e. using pvAccess to transport the images rather than NDPluginStdArrays which uses Channel Access.  
  This has a number of advantages:
    - The NTNDArray data is transmitted "atomically" over the network, rather than using separate PVs for the
      image data and the metadata (image dimensions, color mode, etc.)
    - When using Channel Access the data type of the waveform record is fixed at iocInit, and cannot be
      changed at runtime.  This means, for example, that if the user might want to view both 8-bit images, 
      16-bit images, and 64-bit double FFT images then the waveform record would need to be 64-bit double, which
      adds a factor of 8 network overhead when viewing 8-bit images. pvAccess changes the data type of the NTNDArrays
      dynamically at run-time, removing this restriction.
    - Channel Access requires setting EPICS_CA_MAX_ARRAY_BYTES, which is a source of considerable confusion and 
      frustration for users.  pvAccess does not use EPICS_CA_MAX_ARRAY_BYTES and there is no restriction on
      the size of the NTNDArrays.
    - The performance using pvAccess is significantly better than using Channel Access.  NDPluginPva is 5-10 times
      faster than NDPluginStdArrays, and ImageJ can display 1.5-2 times more images/s with pvAccess than with
      Channel Access.
   
   The required EPICS V4 jar files are included in ImageJ/EPICS_areaDetector.  This entire directory 
   should be copied to the ImageJ/plugins folder, and then one time do ImageJ/Compile and run and select the
   file EPICS_NTNDA_Viewer.java.  

   Users are encouraged to switch to using pvAccess with this new plugin. 

### Viewers/ImageJ/EPICS_AD_Viewer.java 
* Previously this ImageJ plugin monitored the UniqueId_RBV PV in the NDPluginStdArrays plugin, 
  and read the new image from this plugin when UniqueId_RBV changed.  
  However, this does not work correctly with the new ProcessPlugin feature in NDPluginDriver, 
  because that does not increment the UniqueId.
  EPICS_AD_Viewer.java was changed so that it now monitors the ArrayCounter_RBV PV in NDPluginStdArrays
  rather than UniqueId_RBV.  ArrayCounter_RBV will increment every time the plugin receives 
  a new NDArray, which fixes the problem.  
  Note that ArrayCounter_RBV will also change if the user manually changes ArrayCounter, for example by
  setting it back to 0.  This will also cause ImageJ to display the image, when it would not have done 
  so previously.  This should not be a problem.


ADCore R2-6 and earlier
==================
Release notes are part of the
[ADCore Release Notes](https://github.com/areaDetector/ADCore/blob/master/RELEASE.md).
