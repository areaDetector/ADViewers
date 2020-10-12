# numpyImage.py

from PyQt5.QtWidgets import QWidget, QRubberBand
from PyQt5.QtWidgets import QHBoxLayout, QLabel
from PyQt5.QtCore import QPoint, QRect, QSize
from PyQt5.QtCore import QThread
from PyQt5.QtGui import QPainter, QImage
from PyQt5.QtCore import *

import numpy as np
import math
import time
import copy


class FollowMouse:
    """
    Normal use is:
    ...
    from numpyImage import FollowMouse
    ...

    self.followMouse = FollowMouse(self.exceptionEvent)
    box = QHBoxLayout()
    box.addWidget(self.followMouse.createHbox())
    wid = QWidget()
    wid.setLayout(box)
    self.forthRow = wid
    ...
    layout.addWidget(self.forthRow, 3, 0, alignment=Qt.AlignLeft)

    ...

    ...

    Copyright - See the COPYRIGHT that is included with this distribution.
        NTNDA_Viewer is distributed subject to a Software License Agreement found
        in file LICENSE that is included with this distribution.

    authors
        Marty Kraimer
    latest date 2020.09.25
    """

    def __init__(self, exceptionCallback):
        self.__exceptionCallback = exceptionCallback
        self.__zoomDict = None
        self.__mouseDict = None
        self.__nx = 0
        self.__ny = 0
        self.__nz = 0
        self.__dtype = ""

    def createHbox(self):
        """ create a horizontal widget for nx,ny,nx,dtype,x.x.value"""
        box = QHBoxLayout()
        box.setContentsMargins(0, 0, 0, 0)

        nxLabel = QLabel("nx:")
        box.addWidget(nxLabel)
        self.nxText = QLabel()
        self.nxText.setFixedWidth(40)
        box.addWidget(self.nxText)

        nyLabel = QLabel("ny:")
        box.addWidget(nyLabel)
        self.nyText = QLabel()
        self.nyText.setFixedWidth(40)
        box.addWidget(self.nyText)

        nzLabel = QLabel("nz:")
        box.addWidget(nzLabel)
        self.nzText = QLabel()
        self.nzText.setFixedWidth(40)
        box.addWidget(self.nzText)

        dtypeLabel = QLabel("dtype:")
        box.addWidget(dtypeLabel)
        self.dtypeText = QLabel()
        self.dtypeText.setFixedWidth(60)
        box.addWidget(self.dtypeText)

        xLabel = QLabel("x:")
        box.addWidget(xLabel)
        self.xText = QLabel()
        self.xText.setFixedWidth(40)
        box.addWidget(self.xText)

        yLabel = QLabel("y:")
        box.addWidget(yLabel)
        self.yText = QLabel()
        self.yText.setFixedWidth(40)
        box.addWidget(self.yText)

        valueLabel = QLabel("value:")
        box.addWidget(valueLabel)
        self.valueText = QLabel()
        self.valueText.setFixedWidth(500)
        box.addWidget(self.valueText)

        wid = QWidget()
        wid.setLayout(box)
        return wid

    def setChannelInfo(self, channelDict):
        """ provide data for nx,nt,nz,dtype"""
        self.channel = channelDict["channel"]
        self.image = channelDict["image"]
        change = False
        if self.__nx != channelDict["nx"]:
            change = True
        if self.__ny != channelDict["ny"]:
            change = True
        if self.__nz != channelDict["nz"]:
            change = True
        if self.__dtype != str(channelDict["dtypeChannel"]):
            change = True
        if not change:
            if self.__zoomDict != None and self.__mouseDict != None:
                self.setZoomInfo(self.__zoomDict, self.__mouseDict)
            return
        self.__nx = channelDict["nx"]
        self.__ny = channelDict["ny"]
        self.__nz = channelDict["nz"]
        self.__dtype = str(channelDict["dtypeChannel"])
        self.nxText.setText(str(self.__nx))
        self.nyText.setText(str(self.__ny))
        self.nzText.setText(str(self.__nz))
        self.dtypeText.setText(self.__dtype)

    def setZoomInfo(self, zoomDict, mouseDict):
        """ provide data for x.y,value"""
        self.__zoomDict = zoomDict
        self.__mouseDict = mouseDict
        mouseX = int(mouseDict["mouseX"])
        mouseY = int(mouseDict["mouseY"])
        mouseXchannel = int(mouseX)
        mouseYchannel = int(mouseY)
        if mouseXchannel >= self.__nx:
            self.__exceptionCallback("mouseX out of bounds")
            return
        if mouseYchannel >= self.__ny:
            self.__exceptionCallback("mouseY out of bounds")
            return
        self.xText.setText(str(mouseXchannel))
        self.yText.setText(str(mouseYchannel))
        value = str()
        if self.__nz == 1:
            value = str(self.channel[mouseYchannel, mouseXchannel])
        elif self.__nz == 3:
            value1 = self.channel[mouseYchannel, mouseXchannel, 0]
            value2 = self.channel[mouseYchannel, mouseXchannel, 1]
            value3 = self.channel[mouseYchannel, mouseXchannel, 2]
            value = str("[" + str(value1) + "," + str(value2) + "," + str(value3) + "]")
        self.valueText.setText(value)


class NumpyImage(QWidget):
    """
    ___
    Normal use is:
    ...
    from numpyImage import NumpyImage
    ...
       self.imageSize = 600
       self.numpyImage = NumpyImage(imageSize=self.imageSize,exceptionCallback=self.exceptionEvent)
    ___
    numpyImage privides two main services:
    1) Converts a numpy array to a QImage and displays the resulting image.
       See method display for details.
    2) Provides support for mouse action in the QImage.
       See methods setZoomCallback and resetZoom for details.
    ___
    Copyright - See the COPYRIGHT that is included with this distribution.
        NTNDA_Viewer is distributed subject to a Software License Agreement found
        in file LICENSE that is included with this distribution.

    authors
        Marty Kraimer
    latest date 2020.09.25
    """

    def __init__(self, imageSize=800, flipy=False, exceptionCallback=None):
        """
         Parameters
        ----------
            imageSize : int
                 image width and height
            flipy : True or False
                 should y axis (height) be flipped
            exceptionCallback : method that accepts a string argument
                 called when numpy has an exception message.
        """
        super(QWidget, self).__init__()
        self.__imageSize = int(imageSize)
        self.__flipy = flipy
        self.__thread = self.__Worker(self.__imageSize, self.__ImageToQImage())
        self.__imageZoom = False
        self.__rubberBand = QRubberBand(QRubberBand.Rectangle, self)
        self.setAttribute(Qt.WA_NoSystemBackground)
        self.__mousePressPosition = QPoint(0, 0)
        self.__mouseReleasePosition = QPoint(0, 0)
        self.__clientZoomCallback = None
        self.__clientMouseMoveCallback = None
        self.__clientExceptionCallback = exceptionCallback
        self.__mousePressed = False
        self.__okToClose = False
        self.__isHidden = True
        self.__xoffsetZoom = 10
        self.__yoffsetZoom = 300
        self.__bytesPerLine = None
        self.__Format = None
        self.__colorTable = None
        self.__imageDict = {
            "image": None,
            "nx": 0,
            "ny": 0,
            "nz": 0,
        }

        self.__mouseDict = {"mouseX": 0, "mouseY": 0}
        self.__zoomList = list()
        self.__resetZoom = True
        self.setMouseTracking(True)
        self.__width = -1
        self.__height = -1

    def __createZoomDict(self):
        return {
            "isZoom": False,
            "width": 0,
            "height": 0,
            "nx": 0,
            "ny": 0,
            "xoffset": 0,
            "yoffset": 0,
        }

    def setOkToClose(self):
        """ allow image window to be closed"""
        self.__okToClose = True

    def setZoomCallback(self, clientCallback, clientZoom=False):
        """
        Parameters
        ----------
            clientCallback : client method
                 client mouse zoom allowed
            clientZoom : True of False
                 should client handle mouse zoom?
                 if False numpyImage handles mouse zoom
        """
        self.__clientZoomCallback = clientCallback
        if not clientZoom:
            self.__imageZoom = True

    def setMouseMoveCallback(self, clientCallback):
        """
        Parameters
        ----------
            clientCallback : client method
                 client called when mouse is released
        """
        self.__clientMouseMoveCallback = clientCallback

    def setExceptionCallback(self, clientCallback):
        """
        Parameters
        ----------
            clientCallback : client method
                 client called exceptiion occurs
        """
        self.__clientExceptionCallback = clientCallback

    def resetZoom(self):
        """ reset to unzoomed image"""
        self.__resetZoom = True

    def zoomIn(self, zoomScale):
        """
        Parameters
        ----------
            zoomScale : int
                 zoom in by zoomScale/255
        """
        nx = self.__zoomDict["nx"]
        ny = self.__zoomDict["ny"]
        xoffset = self.__zoomDict["xoffset"]
        yoffset = self.__zoomDict["yoffset"]

        zoomDict = self.__createZoomDict()
        zoomDict["nx"] = nx
        zoomDict["ny"] = ny
        zoomDict["xoffset"] = xoffset
        zoomDict["yoffset"] = yoffset
        zoomDict["width"] = self.__zoomDict["width"]
        zoomDict["height"] = self.__zoomDict["height"]

        ratio = ny / nx
        if ratio > 1.0:
            nxold = nx
            nx = nx - (2.0 * zoomScale)
            if nx < 4.0:
                if self.__clientExceptionCallback != None:
                    self.__clientExceptionCallback(
                        "mouseZoom selected to small a subimage"
                    )
                return
            xoffset = xoffset + zoomScale
            ny = ny * nx / nxold
            yoffset = yoffset + ratio * zoomScale
        else:
            nyold = ny
            ny = ny - (2.0 * zoomScale)
            if ny < 4.0:
                if self.__clientExceptionCallback != None:
                    self.__clientExceptionCallback(
                        "mouseZoom selected to small a subimage"
                    )
                return
            yoffset = yoffset + zoomScale
            nx = nx * ny / nyold
            xoffset = xoffset + zoomScale / ratio

        self.__zoomDict["nx"] = nx
        self.__zoomDict["ny"] = ny
        self.__zoomDict["xoffset"] = xoffset
        self.__zoomDict["yoffset"] = yoffset
        self.__zoomDict["isZoom"] = True
        self.__zoomList.append(zoomDict)
        return True

    def zoomBack(self):
        """ revert to previous zoom"""
        num = len(self.__zoomList)
        if num == 0:
            if self.__clientExceptionCallback != None:
                self.__clientExceptionCallback("zoomBack failed")
                return
            else:
                raise Exception("zoomBack failed")
        self.__zoomDict = self.__zoomList[num - 1]
        self.__zoomDict["isZoom"] = True
        self.__zoomList.pop()
        if num == 1:
            self.resetZoom()

    def display(self, pixarray, bytesPerLine=None, Format=0, colorTable=None):
        """
        Parameters
        ----------
            pixarray : numpy array
                 pixarray that is converted to QImage and displayed.
                 It must have shape of length 2 or 3 where:
                     shape[0]=ny which is image height
                     shape[1]=nx which is iaage width
                 In shape has length 3 then
                     shape[1]=nz which must be 2 or 3
            bytesPerLine : int
                 If specified must be total bytes in second dimension of image
            Format: int
                 If this is >0 the QImage is created as follows:
                     if bytesPerLine==None :
                        qimage = QImage(data,image.shape[1], image.shape[0],Format)
                     else :
                         qimage = QImage(data,image.shape[1], image.shape[0],bytesPerLine,Format)
                     if colorTable!=None :
                         qimage.setColorTable(colorTable)
                     return qimage
                Otherwise the QImage is created as follows:
                     if pixarray has dtype uint8:
                          if 2d array :
                              if colorTable==None :
                                  create a QImage with format QImage.Format_Grayscale8
                              else :
                                  create a QImage with format QImage.Format_Indexed8
                          elif 3d array (ny,nx,nz) and nz is 3 or 4:
                              if nz==3 :
                                  create a QImage with format QImage.Format_RGB888
                              else :
                                  create a QImage with format QImage.Format_RGBA8888
                          else :
                              an exception is raised
                else:
                    an exception is raised

            colorTable: qRgb color table
                 Default is to let numpyImage decide
        """
        if self.__flipy:
            image = np.flip(pixarray, 0)
            self.__imageDict["image"] = np.flip(pixarray, 0)
        else:
            image = pixarray
        nx = image.shape[1]
        ny = image.shape[0]
        nz = 1
        if len(image.shape) == 3:
            nz = image.shape[2]
        if (
            nx != self.__imageDict["nx"]
            or ny != self.__imageDict["ny"]
            or nz != self.__imageDict["nz"]
        ):
            self.__resetZoom = True
            self.__imageDict["nx"] = nx
            self.__imageDict["ny"] = ny
            self.__imageDict["nz"] = nz

        if self.__resetZoom:
            self.close()
            self.__zoomList = list()
            self.__zoomDict = self.__createZoomDict()
            self.__resetZoom = False

        if self.__zoomDict["isZoom"]:
            nx = self.__zoomDict["nx"]
            ny = self.__zoomDict["ny"]
            xoffset = int(self.__zoomDict["xoffset"])
            endx = int(xoffset + nx)
            yoffset = int(self.__zoomDict["yoffset"])
            endy = int(yoffset + ny)
            image = image[yoffset:endy, xoffset:endx]
        else:
            self.__zoomDict["nx"] = nx
            self.__zoomDict["ny"] = ny
            width = self.__imageSize
            height = self.__imageSize
            ratio = nx / ny
            if ratio < 1.0:
                width = ratio * width
            elif ratio > 1.0:
                height = height / ratio
            self.__zoomDict["width"] = int(width)
            self.__zoomDict["height"] = int(height)
        self.__imageDict["image"] = image
        self.__bytesPerLine = bytesPerLine
        self.__Format = Format
        self.__colorTable = colorTable
        width = self.__zoomDict["width"]
        height = self.__zoomDict["height"]
        if width != self.width or height != self.height:
            self.close()
            self.width = width
            self.height = height
            point = self.geometry().topLeft()
            self.__xoffsetZoom = point.x()
            self.__yoffsetZoom = point.y()
            self.setGeometry(
                QRect(
                    self.__xoffsetZoom,
                    self.__yoffsetZoom,
                    self.__zoomDict["width"],
                    self.__zoomDict["height"],
                )
            )
            self.setFixedSize(self.__zoomDict["width"], self.__zoomDict["height"])
        self.update()
        if self.__isHidden:
            self.__isHidden = False
            self.show()

    def closeEvent(self, event):
        """
        This is a QWidget method.
        It is only present to override until it is okToClose
        """
        if not self.__okToClose:
            point = self.geometry().topLeft()
            self.__xoffsetZoom = point.x()
            self.__yoffsetZoom = point.y()
            self.hide()
            self.__isHidden = True
            self.__firstDisplay = True
            return

    def mousePressEvent(self, event):
        """
        This is a QWidget method.
        It is one of the methods for implemention zoom
        """
        if self.__clientZoomCallback == None:
            return
        self.__mousePressed = True
        self.__mousePressPosition = QPoint(event.pos())
        self.__rubberBand.setGeometry(QRect(self.__mousePressPosition, QSize()))
        self.__rubberBand.show()

    def mouseMoveEvent(self, event):
        """
        This is a QWidget method.
        It is one of the methods for implemention zoom
        """
        if not self.__mousePressed:
            if self.__clientMouseMoveCallback != None:
                pos = QPoint(event.pos())
                xmin = pos.x()
                ymin = pos.y()
                geometry = self.geometry()
                width = geometry.width()
                if width != self.__imageSize:
                    xmin = int(xmin * self.__imageSize / width)
                height = geometry.height()
                if height != self.__imageSize:
                    ymin = int(ymin * self.__imageSize / height)
                delx = self.__imageDict["nx"] / self.__imageSize
                dely = self.__imageDict["ny"] / self.__imageSize
                mouseX = int(xmin * delx)
                mouseY = int(ymin * dely)
                if self.__zoomDict["isZoom"]:
                    nximage = self.__imageDict["nx"]
                    nyimage = self.__imageDict["ny"]
                    nx = self.__zoomDict["nx"]
                    ny = self.__zoomDict["ny"]
                    xoffset = self.__zoomDict["xoffset"]
                    yoffset = self.__zoomDict["yoffset"]
                    ratio = nx / nximage
                    mouseX = mouseX * ratio + xoffset
                    ratio = ny / nyimage
                    mouseY = mouseY * ratio + yoffset
                self.__mouseDict["mouseX"] = mouseX
                self.__mouseDict["mouseY"] = mouseY
                self.__clientMouseMoveCallback(self.__zoomDict, self.__mouseDict)
            return
        self.__rubberBand.setGeometry(
            QRect(self.__mousePressPosition, event.pos()).normalized()
        )

    def mouseReleaseEvent(self, event):
        """
        This is a QWidget method.
        It is one of the methods for implemention zoom
        """
        if not self.__mousePressed:
            return
        self.__mousePressed = False
        self.__mouseReleasePosition = QPoint(event.pos())
        self.__rubberBand.hide()
        self.__mousePressed = False
        imageGeometry = self.geometry().getRect()
        xsize = imageGeometry[2]
        ysize = imageGeometry[3]
        xmin = self.__mousePressPosition.x()
        xmax = self.__mouseReleasePosition.x()
        if xmin > xmax:
            xmax, xmin = xmin, xmax
        if xmin < 0:
            xmin = 0
        if xmax > xsize:
            xmax = xsize
        ymin = self.__mousePressPosition.y()
        ymax = self.__mouseReleasePosition.y()
        if ymin > ymax:
            ymax, ymin = ymin, ymax
        if ymin < 0:
            ymin = 0
        if ymax > ysize:
            ymax = ysize
        sizey = ymax - ymin
        sizex = xmax - xmin
        if sizey <= 3 or sizex <= 3:
            return
        if not self.__imageZoom:
            self.__clientZoomCallback((xsize, ysize), (xmin, xmax, ymin, ymax))
            return
        self.__newZoom(xmin, xmax, ymin, ymax)
        self.__clientZoomCallback()

    def paintEvent(self, ev):
        """
        This is the method that displays the QImage
        """
        image = self.__imageDict["image"]
        self.__thread.render(
            self, image, self.__bytesPerLine, self.__Format, self.__colorTable
        )
        self.__thread.wait()

    def __newZoom(self, xminMouse, xmaxMouse, yminMouse, ymaxMouse):
        nximage = self.__imageDict["nx"]
        nyimage = self.__imageDict["ny"]
        nx = self.__zoomDict["nx"]
        ny = self.__zoomDict["ny"]
        xoffset = self.__zoomDict["xoffset"]
        yoffset = self.__zoomDict["yoffset"]
        width = self.__zoomDict["width"]
        height = self.__zoomDict["height"]

        zoomDict = self.__createZoomDict()
        zoomDict["nx"] = nx
        zoomDict["ny"] = ny
        zoomDict["xoffset"] = xoffset
        zoomDict["yoffset"] = yoffset
        zoomDict["width"] = width
        zoomDict["height"] = height

        ratiox = nx / nximage
        mouseRatiox = (xmaxMouse - xminMouse) / width
        ratioy = ny / nyimage
        mouseRatioy = (ymaxMouse - yminMouse) / height
        nx = nximage * ratiox * mouseRatiox
        offsetmouse = nximage * (xminMouse / width) * ratiox
        xoffset = xoffset + offsetmouse
        ny = nyimage * ratioy * mouseRatioy
        offsetmouse = nyimage * (yminMouse / height) * ratioy
        yoffset = yoffset + offsetmouse
        if nx < 4 or ny < 4:
            if self.__clientExceptionCallback != None:
                self.__clientExceptionCallback("mouseZoom selected to small a subimage")
            return
        width = self.__imageSize
        height = self.__imageSize
        ratio = nx / ny
        if ratio > 1.0:
            height = int(height / ratio)
        else:
            width = int(width * ratio)

        self.__zoomDict["nx"] = nx
        self.__zoomDict["ny"] = ny
        self.__zoomDict["xoffset"] = xoffset
        self.__zoomDict["yoffset"] = yoffset
        self.__zoomDict["isZoom"] = True
        self.__zoomDict["width"] = width
        self.__zoomDict["height"] = height
        self.__zoomList.append(zoomDict)

    class __ImageToQImage:
        def __init__(self):
            self.error = str()

        def toQImage(self, image, bytesPerLine=None, Format=0, colorTable=None):
            try:
                self.error = str("")
                if image is None:
                    self.error = "no image"
                    return None
                mv = memoryview(image.data)
                data = mv.tobytes()
                if Format > 0:
                    if bytesPerLine == None:
                        qimage = QImage(data, image.shape[1], image.shape[0], Format)
                    else:
                        qimage = QImage(
                            data, image.shape[1], image.shape[0], bytesPerLine, Format
                        )
                    if colorTable != None:
                        qimage.setColorTable(colorTable)
                    return qimage
                if image.dtype == np.uint8:
                    if len(image.shape) == 2:
                        nx = image.shape[1]
                        if colorTable == None:
                            qimage = QImage(
                                data,
                                image.shape[1],
                                image.shape[0],
                                nx,
                                QImage.Format_Grayscale8,
                            )
                        else:
                            qimage = QImage(
                                data,
                                image.shape[1],
                                image.shape[0],
                                nx,
                                QImage.Format_Indexed8,
                            )
                            qimage.setColorTable(colorTable)
                        return qimage
                    elif len(image.shape) == 3:
                        if image.shape[2] == 3:
                            nx = image.shape[1] * 3
                            qimage = QImage(
                                data,
                                image.shape[1],
                                image.shape[0],
                                nx,
                                QImage.Format_RGB888,
                            )
                            return qimage
                        elif image.shape[2] == 4:
                            nx = image.shape[1] * 4
                            qimage = QImage(
                                data,
                                image.shape[1],
                                image.shape[0],
                                nx,
                                QImage.Format_RGBA8888,
                            )
                            return qimage
                    self.error = "nz must have length 3 or 4"
                    return None
                self.error = "unsupported dtype=" + str(image.dtype)
                return None
            except Exception as error:
                self.error = str(error)
                return None

    class __Worker(QThread):
        def __init__(self, imageSize, imageToQimage):
            QThread.__init__(self)
            self.error = str("")
            self.imageSize = imageSize
            self.imageToQImage = imageToQimage
            self.bytesPerLine = None

        def setImageSize(self, imageSize):
            self.imageSize = imageSize

        def render(self, caller, image, bytesPerLine=None, Format=0, colorTable=None):
            self.error = str("")
            self.image = image
            self.caller = caller
            self.Format = Format
            self.colorTable = colorTable
            self.bytesPerLine = bytesPerLine
            self.start()

        def run(self):
            self.setPriority(QThread.HighPriority)
            qimage = self.imageToQImage.toQImage(
                self.image,
                bytesPerLine=self.bytesPerLine,
                Format=self.Format,
                colorTable=self.colorTable,
            )
            if qimage == None:
                self.error = self.imageToQImage.error
                return
            numx = self.image.shape[1]
            numy = self.image.shape[0]
            scalex = self.imageSize
            scaley = self.imageSize
            ratio = numx / numy
            if ratio < 1.0:
                scalex = ratio * scalex
            elif ratio > 1.0:
                scaley = scaley / ratio
            qimage = qimage.scaled(scalex, scaley)
            painter = QPainter(self.caller)
            painter.drawImage(0, 0, qimage)
            while True:
                if painter.end():
                    break
            self.image = None
