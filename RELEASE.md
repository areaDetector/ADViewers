ADViewers Releases
===============

The latest untagged master branch can be obtained at
https://github.com/areaDetector/ADViewers.

Prior to the release of [ADCore](https://github.com/areaDetector/ADCore) R3-0 the code in ADViewers
was in the Viewers subdirectory of ADCore.

Tagged source code releases can be obtained at 
https://github.com/areaDetector/ADViewers/releases.

Release Notes
=============
R1-0 (April XXX, 2017)
======================

### ImageJ/EPICS_areaDetector/EPICS_NTNDA_Viewer.java
* This is a new plugin written by Tim Madden.  It is essentially identical to EPICS_AD_Viewer.java except
  that it displays NTNDArrays from the NDPluginPva plugin, i.e. using pvAccess to transport the images rather
  than NDPluginStdArrays which uses Channel Access.  
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

ADCore R2-6 and earlier
==================
Release notes are part of the
[ADCore Release Notes](https://github.com/areaDetector/ADCore/blob/master/RELEASE.md).
