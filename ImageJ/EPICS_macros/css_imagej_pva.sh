# EPICS_AD_Viewer macro
# Authors
#      Kaz Gofron, NSLS2
#
# Place 4 files in /usr/share/imagej/macros folder
# Channel Access:
#   CSS_epics.ijm
#   css_imagej.sh
# PV Access:
#   CSS_epics_pva.ijm
#   css_imagej_pva.sh
#
# From CSS/Phoebus/... call .sh script
# Channel Access:
#   /usr/share/imagej/macros/css_imagej.sh $(Sys)$(Dev)image1:
# PV Access:
#   /usr/share/imagej/macros/css_imagej_pva.sh $(Sys)$(Dev)Pva1:Image
# which will start imagej viewer with populated PVs

cd ~
rm EPICS_NTNDA_Viewer.properties
echo "channelName=$1" > EPICS_NTNDA_Viewer.properties
imagej -m ~/.imagej/macros/CSS_epics_pva.ijm

