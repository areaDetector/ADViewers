areaDetector Viewers
====================
:author: Mark Rivers, Tim Madden, Marty Kraimer
:affiliation: University of Chicago, Advanced Photon Source

.. contents:: Contents

.. toctree::
   :hidden:
   
   IDL_Viewer.rst
   ImageJ_EPICS_AD_Viewer.rst
   ImageJ_EPICS_NTNDA_Viewer.rst
   ImageJ_EPICS_AD_Controller.rst
   PY_NTNDA_Viewer.rst

Overview
--------

One of the advantages of areaDetector is that it enables the use of
generic image display clients that obtain their data via EPICS Channel
Access or pvAccess and work with any detector. There are currently four
such generic clients provided with the areaDetector distribution.

The first two are plugins for the popular ImageJ Java-based image processing
program. ``EPICS_AD_Viewer.java`` uses EPICS Channel Access, while
``EPICS_NTNDA_Viewer.java`` uses EPICS pvAccess. 

The third is a Python program that uses Qt widgets and uses EPICS pvAccess.

The fourth is an IDL-based viewer which can be run without an IDL license under the IDL
Virtual Machine. 

Because ImageJ and Python are free and more widely available and
used than IDL, future enhancements are more likely to be done on the
ImageJ plugins and Python viewer, rather than the IDL viewer. 

These viewers are contained
in the areaDetector distribution in the `ADViewers
repository <https://github.com/areaDetector/ADViewers>`__

In addition to the ImageJ viewer plugins, there is an ImageJ plugin to
graphically define the detector/camera readout region, ROIs, and
overlays (``EPICS_AD_Controller.java``). Another ImageJ plugin provided with
areaDetector does realtime line profiles with Gaussian peak fitting
(``GaussianProfile.java``).

ImageJ Viewers
--------------

These are plugins for the popular `ImageJ <http://rsbweb.nih.gov/ij/>`__
program that can be used to display 2-dimensional array data. Both of
these plugins were originally written by Tim Madden from APS. Both
support all NDArray data types and color modes, i.e. Mono, RGB1 (pixel
interleave), RGB2 (row interleave) and RGB3 (plane interleave). The
plugin directory also includes a plugin written elsewhere for reading
and writing netCDF files, so ImageJ can be used to display images and
image sequences (movies) saved with the NDFileNetCDF plugin.

EPICS_AD_Viewer.java
~~~~~~~~~~~~~~~~~~~~

This plugin uses EPICS Channel Access to display images via waveform
records that the :doc:`../ADCore/NDPluginStdArrays` sends
to EPICS.

More information can be found in the :doc:`documentation<ImageJ_EPICS_AD_Viewer>`.

EPICS_NTNDA_Viewer.java
~~~~~~~~~~~~~~~~~~~~~~~

This plugin uses EPICS V4 pvAccess to display NTNDArrays that the
:doc:`../ADCore/NDPluginPva` sends to EPICS. 


The EPICS_NTNDA_Viewer has a number of significant advantages compared to
the EPICS_AD_Viewer:

-  The NTNDArray data is transmitted "atomically" over the network,
   rather than using separate PVs for the image data and the metadata
   (image dimensions, color mode, etc.)
-  NTNDArrays and pvAccess support sending compressed arrays over the
   network. Beginning with ADViewers R1-3 the EPICS_NTNDA_Viewer can
   decompress the NTNDArrays and display them. This can significantly
   reduce the required network bandwith when the IOC and the viewer are
   running on different machines.
-  When using Channel Access the data type of the waveform record is
   fixed at iocInit, and cannot be changed at runtime. This means, for
   example, that if the user might want to view both 8-bit images,
   16-bit images, and 64-bit double FFT images then the waveform record
   would need to be 64-bit double, which adds a factor of 8 network
   overhead when viewing 8-bit images. pvAccess changes the data type of
   the NTNDArrays dynamically at run-time, removing this restriction.
-  Channel Access requires setting ``EPICS_CA_MAX_ARRAY_BYTES``, which is a
   source of considerable confusion and frustration for users. pvAccess
   does not use ``EPICS_CA_MAX_ARRAY_BYTES`` and there is no restriction on
   the size of the NTNDArrays.
-  The performance using pvAccess is significantly better than using
   Channel Access. NDPluginPva is 5-10 times faster than
   NDPluginStdArrays, and ImageJ can display 1.5-2 times more images/s
   with pvAccess than with Channel Access.

More information can be found in the :doc:`documentation<ImageJ_EPICS_NTNDA_Viewer>`.

ImageJ Controller (EPICS_AD_Controller.java)
--------------------------------------------

This is an ImageJ plugin which can be used to graphically control the
following:

-  The readout region of the detector or camera.
-  The size and position of an ROI (NDPluginROI).
-  The size and position of an overlay (NDPluginOverlay).

More information can be found in the :doc:`documentation<ImageJ_EPICS_AD_Controller>`.


.. _ImageJGaussianProfiler:

ImageJ Gaussian Profiler (GaussianProfiler.java)
------------------------------------------------

This is an ImageJ plugin which can be used to dynamically plot a line
profile, fit the profile to a Gaussian peak, and print the fit
parameters (centroid, amplitude, full-width half-maximum (FWHM), and
background. It should be compiled in the same manner as :doc:`EPICS_AD_Viewer<ImageJ_EPICS_AD_Viewer>`.
It is used by drawing a line or rectangle in ImageJ and
then starting Plugins/EPICS_areaDetector/Gaussian Profiler.

The following is the GaussianProfiler window plotting the profile of the
peak shown above in the EPICS_AD_Controller image.

.. figure:: ImageJ_GaussianProfiler.png
    :align: center

    ImageJ GaussianProfiler plotting a line through the peak
    shown above in the EPICS_AD_Controller image

Python Viewer
-------------

There is a Python viewer in the ADViewers/Python directory. This viewer has the following features:

- Supports compressed NDArrays using any of the codecs in NDPluginCodec.
- Supports all NDArray data types and color modes.
- Allows zooming by defining the subregion with a mouse.
- Allows changing the window size.
- Allows changing the lower and upper display intensities with sliders.
- Supports pvAccess only, does not support Channel Access.
- Works with both the p4p and pvapy Python bindings for pvAccess.
  - p4p works on Windows, Linux, and Mac.
  - pvapy works only on Linux and Mac.

More information can be found in the :doc:`documentation<PY_NTNDA_Viewer>`.

IDL Viewer
----------

There is an IDL viewer that uses Channel Access.  It is obsolete and not currently
being developed, but can be used as an example for those wanting to use IDL.

More information can be found in the :doc:`documentation<IDL_Viewer>`.

Troubleshooting
---------------

If a viewer is not displaying new images as the detector
collects them check the following:

-  If other EPICS channel access clients (e.g. medm, caget) running on
   the same machine as the viewer **cannot** connect to the IOC then
   check the following:

   -  There may be a firewall blocking EPICS Channel Access or pvAccess either on
      the server (IOC) machine or the client (viewer) machine.
   -  For Channel Access viewers the environment variable EPICS_CA_ADDR_LIST may need to be set to
      allow the client to find the IOC if the IOC is not on the same
      subnet as the viewer or if other EPICS channel access settings do
      not have their default values.

-  If other EPICS channel access clients (e.g. medm, caget) running on
   the same machine as the viewer **can** connect to the IOC then check
   the following:

   -  The detector is actually collecting images, and the ArrayCallbacks
      PV is set to Enable.
   -  For Channel Access viewers the NDPluginStdArrays plugin (normally
      called image1:) has the EnableCallbacks PV set to Yes, and that
      the MinCallbackTime PV is not set too large.
   -  For pvAccess viewers the NDPluginPva plugin (normally
      called Pva1:) has the EnableCallbacks PV set to Yes, and that
      the MinCallbackTime PV is not set too large.
   -  For Channel Access viewers the environment variable
      EPICS_CA_MAX_ARRAY_BYTES is set to a value at least as large as
      the size of the arrays to be sent to the viewer. This environment
      variable must be set on the machine that the IOC is running on
      before the IOC is started. It must also be set on the machine that
      the Channel Access viewer is running on before ImageJ or IDL is
      started.
