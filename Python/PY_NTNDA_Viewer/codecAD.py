# codecAD.py
"""
codecAD provides python access to the codec support provided by areaDetector/ADSupport
It is meant for use by a callback from an NTNDArray record.
NTNDArray is implemented in areaDetector/ADCore.
NTNDArray has the following fields of interest to a callback:
    value            This contains a numpy array with a scalar dtype
    codec            This describes the codec information
    compressedSize   The compressed size if a codec was used
    uncompressedSize The uncompressed size of the data
    dimension        2d or 3d array description
    
In order to use this code environment variable LD_LIBRARY_PATH must be defined.
For example:
   export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:/home/epics7/areaDetector/ADSupport/lib/linux-x86_64
      
Normal use is:
...
from codecAD import CodecAD
...
    self.codecAD = CodecAD()
...
    if self.codecAD.decompress(data,codec,compressed,uncompressed) :
        codecName = self.codecAD.getCodecName()
        data = self.codecAD.getData()
        compressRatio = self.codecAD.getCompressRatio()
    else :
        pass
        " note that data is not changed"
...   
     
Copyright - See the COPYRIGHT that is included with this distribution.
    NTNDA_Viewer is distributed subject to a Software License Agreement found
    in file LICENSE that is included with this distribution.

authors
    Marty Kraimer
    Mark Rivers
latest date 2020.07.30
"""

import sys
import ctypes
import ctypes.util
import os
import numpy as np


class CodecAD:
    """
    codecAD provides python access to the codec support provided by areaDetector/ADSupport
    It is meant for use by a callback from an NTNDArray record.
    NTNDArray is implemented in areaDetector/ADCore.
    NTNDArray has the following fields of interest to a callback:
        value            This contains a numpy array with a scalar dtype
        codec            This describes the codec information
        compressedSize   The compressed size if a codec was used
        uncompressedSize The uncompressed size of the data
        dimension        2d or 3d array description

    In order to use this code environment variable LD_LIBRARY_PATH must be defined.
    For example:
       export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:/home/epics7/areaDetector/ADSupport/lib/linux-x86_64

    Normal use is:
    ...
    from codecAD import CodecAD
    ...
        self.codecAD = CodecAD()
    ...
        if self.codecAD.decompress(data,codec,compressed,uncompressed) :
            codecName = self.codecAD.getCodecName()
            data = self.codecAD.getData()
            compressRatio = self.codecAD.getCompressRatio()
        else :
            pass
            " note that data is not changed"
    ...

    Copyright - See the COPYRIGHT that is included with this distribution.
        NTNDA_Viewer is distributed subject to a Software License Agreement found
        in file LICENSE that is included with this distribution.

    authors
        Marty Kraimer
        Mark Rivers
    latest date 2020.07.30
    """

    def __init__(self):
        self.__codecName = "none"
        self.__data = None
        self.__compressRatio = 1.0
        self.__saveLibrary = dict()

    def __findLibrary(self, name):
        lib = self.__saveLibrary.get(name)
        if lib != None:
            return lib
        result = ctypes.util.find_library(name)
        if result == None:
            return None
        if os.name == "nt":
            lib = ctypes.windll.LoadLibrary(result)
        else:
            lib = ctypes.cdll.LoadLibrary(result)
        if lib != None:
            self.__saveLibrary.update({name: lib})
        return lib

    def getCodecName(self):
        """
        Returns
        -------
        codecName : str
            codecName for the last decompress
        """
        return self.__codecName

    def getData(self):
        """
        Returns
        -------
        data : numpy array
            data for the last decompress
        """
        return self.__data

    def getCompressRatio(self):
        """
        Returns
        -------
        compressRatio : float
            compressRatio for the last decompress
        """
        return self.__compressRatio

    def decompress(self, data, codec, compressed, uncompressed):
        """
        decompress data described by codec.
        The arguments are all provided by a callback from an NTNDArray record.
        areaDetector/ADCore defines the NTNDArray record.

        Parameters
        ----------
            data :        Provided by the NTNDArray record.
            codec:        Provided by the NTNDArray record.
            compressed:   Provided by the NTNDArray record.
            uncompressed: Provided by the NTNDArray record.

        Returns
        -------
            False
                No decompression was done.
                The original data is not modified.
            True
                decompression was done.
                getCodecName, getData, and getCompressRatio provide the results
        """
        self.__codecName = codec["name"]
        if len(self.__codecName) == 0:
            self.__data = None
            self.__codecName = "none"
            self.__compressRatio = 1.0
            return False
        typevalue = codec["parameters"]
        if typevalue == 1:
            dtype = "int8"
            elementsize = int(1)
        elif typevalue == 5:
            dtype = "uint8"
            elementsize = int(1)
        elif typevalue == 2:
            dtype = "int16"
            elementsize = int(2)
        elif typevalue == 6:
            dtype = "uint16"
            elementsize = int(2)
        elif typevalue == 3:
            dtype = "int32"
            elementsize = int(4)
        elif typevalue == 7:
            dtype = "uint32"
            elementsize = int(4)
        elif typevalue == 4:
            dtype = "int64"
            elementsize = int(8)
        elif typevalue == 8:
            dtype = "uint64"
            elementsize = int(8)
        elif typevalue == 9:
            dtype = "float32"
            elementsize = int(4)
        elif typevalue == 10:
            dtype = "float64"
            elementsize = int(8)
        else:
            raise Exception("decompress mapIntToType failed")
        if self.__codecName == "blosc":
            lib = self.__findLibrary(self.__codecName)
        elif self.__codecName == "jpeg":
            lib = self.__findLibrary("decompressJPEG")
        elif self.__codecName == "lz4" or self.__codecName == "bslz4":
            lib = self.__findLibrary("bitshuffle")
        else:
            lib = None
        if lib == None:
            raise Exception("shared library " + self.codecName + " not found")
        inarray = bytearray(data)
        in_char_array = ctypes.c_ubyte * compressed
        out_char_array = ctypes.c_ubyte * uncompressed
        outarray = bytearray(uncompressed)
        if self.__codecName == "blosc":
            lib.blosc_decompress(
                in_char_array.from_buffer(inarray),
                out_char_array.from_buffer(outarray),
                uncompressed,
            )
            data = np.array(outarray)
            data = np.frombuffer(data, dtype=dtype)
        elif self.__codecName == "lz4":
            lib.LZ4_decompress_fast(
                in_char_array.from_buffer(inarray),
                out_char_array.from_buffer(outarray),
                uncompressed,
            )
            data = np.array(outarray)
            data = np.frombuffer(data, dtype=dtype)
        elif self.__codecName == "bslz4":
            lib.bshuf_decompress_lz4(
                in_char_array.from_buffer(inarray),
                out_char_array.from_buffer(outarray),
                int(uncompressed / elementsize),
                elementsize,
                int(0),
            )
            data = np.array(outarray)
            data = np.frombuffer(data, dtype=dtype)
        elif self.__codecName == "jpeg":
            lib.decompressJPEG(
                in_char_array.from_buffer(inarray),
                compressed,
                out_char_array.from_buffer(outarray),
                uncompressed,
            )
            data = np.array(outarray)
            data = data.flatten()
        else:
            raise Exception(self.__codecName + " is unsupported codec")
        self.__compressRatio = round(float(uncompressed / compressed))
        self.__data = data
        return True
