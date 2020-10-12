# channelToImageAD.py

import numpy as np
import math


class ChannelToImageAD:
    """
    channelToImageAD provides python access to the data provided by areaDetector/ADSupport
    It is meant for use by a callback from an NTNDArray record.
    NTNDArray is implemented in areaDetector/ADCore.
    NTNDArray has the following fields of interest to ChannelToImageAD:
        value            This contains a numpy array with a scalar dtype
        dimension        2d or 3d array description

    Normal use is:
    ...
    from channelToImageAD import ChannelToImageAD
    ...
        self.channelToImageAD = ChannelToImageAD()
        self.channelDict = self.channelToImage.channelDictCreate()

    ...
        try:
            self.channelToImage.channelToImage(data,dimArray,self.imageSize,...)
            channelDict = self.channelToImage.getImageDict()
            self.channelDict["image"] = channelDict["image"]
            ... other methods
    ...

    Copyright - See the COPYRIGHT that is included with this distribution.
        NTNDA_Viewer is distributed subject to a Software License Agreement found
        in file LICENSE that is included with this distribution.

    authors
        Marty Kraimer
        Mark Rivers
    latest date 2020.07.30
    """

    def __init__(self, parent=None):
        self.__channelDict = self.channelDictCreate()
        self.__manualLimits = (0, 255)

    def channelDictCreate(self):
        """
        Returns
        -------
        channelDict : dict
            channelDict["channel"]      None
            channelDict["dtypeChannel"] None
            channelDict["nx"]           0
            channelDict["ny"]           0
            channelDict["nz"]           0
            channelDict["image"]        None
            channelDict["dtypeImage"]   np.uint8
        """
        return {
            "channel": None,
            "dtypeChannel": None,
            "dtypeImage": np.uint8,
            "nx": 0,
            "ny": 0,
            "nz": 0,
            "image": None,
            "dtypeImage": np.uint8,
        }

    def setManualLimits(self, manualLimits):
        """
         Parameters
        -----------
            manualLimits : tuple
                 manualLimits[0] : lowManualLimit
                 manualLimits[1] : highManualLimit
        """
        self.__manualLimits = manualLimits

    def getChannelDict(self):
        """
        Returns
        -------
        channelDict : dict
            channelDict["channel"]      numpy 2d or 3d array for the channel
            channelDict["dtypeChannel"] dtype for data from the callback
            channelDict["nx"]           nx for data from the callback
            channelDict["ny"]           ny for data from the callback
            channelDict["nz"]           nz (1,3) for (2d,3d) image
            channelDict["imagel"]       numpy 2d or 3d array for the image
            channelDict["dtypeImage"]   dtype for image

        """
        return self.__channelDict

    def getManualLimits(self):
        """
        Returns
        -------
            manualLimits : tuple
                 manualLimits[0] : lowManualLimit
                 manualLimits[1] : highManualLimit
        """
        return self.__manualLimits

    def __reshapeChannel(self, data, dimArray):
        nz = 1
        ndim = len(dimArray)
        if ndim == 2:
            nx = dimArray[0]["size"]
            ny = dimArray[1]["size"]
            image = np.reshape(data, (ny, nx))
        elif ndim == 3:
            if dimArray[0]["size"] == 3:
                nz = dimArray[0]["size"]
                nx = dimArray[1]["size"]
                ny = dimArray[2]["size"]
                image = np.reshape(data, (ny, nx, nz))
            elif dimArray[1]["size"] == 3:
                nz = dimArray[1]["size"]
                nx = dimArray[0]["size"]
                ny = dimArray[2]["size"]
                image = np.reshape(data, (ny, nz, nx))
                image = np.swapaxes(image, 2, 1)
            elif dimArray[2]["size"] == 3:
                nz = dimArray[2]["size"]
                nx = dimArray[0]["size"]
                ny = dimArray[1]["size"]
                image = np.reshape(data, (nz, ny, nx))
                image = np.swapaxes(image, 0, 2)
                image = np.swapaxes(image, 0, 1)
            else:
                raise Exception("no axis has dim = 3")
                return
        else:
            raise Exception("ndim not 2 or 3")
        return (image, nx, ny, nz)

    def channelToImage(self, data, dimArray, imageSize, manualLimits=False):
        """
         Parameters
        -----------
            data               : data from the callback
            dimArray           : dimension from callback
            imageSize          : width and height for the generated image
            manualLimits       : (False,True) means client (does not,does) set limits
        """
        dtype = data.dtype
        reshape = self.__reshapeChannel(data, dimArray)
        image = reshape[0]
        self.__channelDict["channel"] = image
        nx = reshape[1]
        ny = reshape[2]
        nz = reshape[3]
        self.__channelDict["nx"] = nx
        self.__channelDict["ny"] = ny
        self.__channelDict["nz"] = nz
        self.__channelDict["dtypeChannel"] = dtype
        if manualLimits:
            displayMin = self.__manualLimits[0]
            displayMax = self.__manualLimits[1]
        else:
            displayMin = np.min(data)
            displayMax = np.max(data)
        interp = True
        if dtype == np.uint8:
            if displayMin <= 2 and displayMax >= 250:
                interp = False
        if interp:
            xp = (displayMin, displayMax)
            fp = (0.0, 255.0)
            image = (np.interp(image, xp, fp)).astype(np.uint8)

        nmax = 0
        if nx > nmax:
            nmax = nx
        if ny > nmax:
            nmax = ny
        self.__channelDict["image"] = image
