# NTNDA_Viewer.py
"""
Copyright - See the COPYRIGHT that is included with this distribution.
    NTNDA_Viewer is distributed subject to a Software License Agreement found
    in file LICENSE that is included with this distribution.

authors
    Marty Kraimer
    Mark Rivers
latest date 2020.11.09
    original development started 2019.12
"""

import time
import os
import numpy as np
from PyQt5.QtWidgets import QWidget, QLabel, QLineEdit
from PyQt5.QtWidgets import QPushButton, QHBoxLayout, QGridLayout
from PyQt5.QtWidgets import QRadioButton
from PyQt5.QtCore import *
from PyQt5.QtGui import qRgb

from numpyImage import NumpyImage, FollowMouse
from codecAD import CodecAD
from channelToImageAD import ChannelToImageAD
from colorTable import ColorTable


class NTNDA_Viewer(QWidget):
    def __init__(self, ntnda_Channel_Provider, providerName, qapplication, parent=None):
        super(QWidget, self).__init__(parent)
        self.imageSize = 800
        self.isClosed = False
        self.isStarted = False
        self.provider = ntnda_Channel_Provider
        self.qapplication = qapplication
        self.provider.NTNDA_Viewer = self
        self.setWindowTitle(providerName + "_NTNDA_Viewer")
        self.codecAD = CodecAD()
        self.channelToImage = ChannelToImageAD()
        self.colorTable = ColorTable()
        self.colorTable.setColorChangeCallback(self.colorChangeEvent)
        self.colorTable.setExceptionCallback(self.colorExceptionEvent)

        self.channelDict = None
        self.numpyImage = None
        self.manualLimits = False
        self.nImages = 0
        self.zoomScale = 1
        self.codecIsNone = True
        # first row
        box = QHBoxLayout()
        box.setContentsMargins(0, 0, 0, 0)
        self.startButton = QPushButton("start")
        self.startButton.setEnabled(True)
        self.startButton.clicked.connect(self.startEvent)
        box.addWidget(self.startButton)
        self.stopButton = QPushButton("stop")
        self.stopButton.setEnabled(False)
        self.stopButton.clicked.connect(self.stopEvent)
        box.addWidget(self.stopButton)

        self.showColorTableButton = QPushButton("showColorTable")
        self.showColorTableButton.setEnabled(True)
        self.showColorTableButton.clicked.connect(self.showColorTableEvent)
        box.addWidget(self.showColorTableButton)

        self.plot3dButton = QPushButton("plot3d")
        self.plot3dButton.setEnabled(True)
        self.plot3dButton.clicked.connect(self.plot3dEvent)
        box.addWidget(self.plot3dButton)

        self.channelNameLabel = QLabel("channelName:")
        box.addWidget(self.channelNameLabel)
        self.channelNameText = QLineEdit()
        self.channelNameText.setFixedWidth(450)
        self.channelNameText.setEnabled(True)
        self.channelNameText.setText(self.provider.getChannelName())
        self.channelNameText.editingFinished.connect(self.channelNameEvent)
        box.addWidget(self.channelNameText)
        wid = QWidget()
        wid.setLayout(box)
        self.firstRow = wid
        # second row
        box = QHBoxLayout()
        box.setContentsMargins(0, 0, 0, 0)

        imageRateLabel = QLabel("imageRate:")
        box.addWidget(imageRateLabel)
        self.imageRateText = QLabel()
        self.imageRateText.setFixedWidth(40)
        box.addWidget(self.imageRateText)
        if len(self.provider.getChannelName()) < 1:
            name = os.getenv("EPICS_NTNDA_VIEWER_CHANNELNAME")
            if name is not None:
                self.provider.setChannelName(name)

        self.imageSizeLabel = QLabel("imageSize:")
        box.addWidget(self.imageSizeLabel)
        self.imageSizeText = QLineEdit()
        self.imageSizeText.setFixedWidth(60)
        self.imageSizeText.setEnabled(True)
        self.imageSizeText.setText(str(self.imageSize))
        self.imageSizeText.returnPressed.connect(self.imageSizeEvent)
        box.addWidget(self.imageSizeText)

        compressRatioLabel = QLabel("compressRatio:")
        box.addWidget(compressRatioLabel)
        self.compressRatioText = QLabel("1    ")
        box.addWidget(self.compressRatioText)
        codecNameLabel = QLabel("codec:")
        box.addWidget(codecNameLabel)
        self.codecNameText = QLabel("none   ")
        box.addWidget(self.codecNameText)
        self.clearButton = QPushButton("clear")
        self.clearButton.setEnabled(True)
        self.clearButton.clicked.connect(self.clearEvent)
        box.addWidget(self.clearButton)
        self.statusText = QLineEdit()
        self.statusText.setText("nothing done so far                    ")
        self.statusText.setFixedWidth(450)
        box.addWidget(self.statusText)
        wid = QWidget()
        wid.setLayout(box)
        self.secondRow = wid
        # third row
        box = QHBoxLayout()
        box.setContentsMargins(0, 0, 0, 0)

        hbox = QHBoxLayout()
        self.autoScaleButton = QRadioButton("autoScale")
        self.autoScaleButton.toggled.connect(self.scaleEvent)
        self.autoScaleButton.setChecked(True)
        self.manualScaleButton = QRadioButton("manualScale")
        self.manualScaleButton.toggled.connect(self.scaleEvent)
        hbox.addWidget(self.autoScaleButton)
        hbox.addWidget(self.manualScaleButton)
        wid = QWidget()
        wid.setLayout(hbox)
        box.addWidget(wid)

        box.addWidget(QLabel("manualMin"))
        self.minLimitText = QLineEdit()
        self.minLimitText.setFixedWidth(50)
        self.minLimitText.setEnabled(True)
        self.minLimitText.setText("0")
        self.minLimitText.returnPressed.connect(self.manualLimitsEvent)
        box.addWidget(self.minLimitText)
        box.addWidget(QLabel("manualMax"))
        self.maxLimitText = QLineEdit()
        self.maxLimitText.setFixedWidth(50)
        self.maxLimitText.setEnabled(True)
        self.maxLimitText.setText("255")
        self.maxLimitText.returnPressed.connect(self.manualLimitsEvent)
        box.addWidget(self.maxLimitText)

        self.resetButton = QPushButton("resetZoom")
        box.addWidget(self.resetButton)
        self.resetButton.setEnabled(True)
        self.resetButton.clicked.connect(self.resetEvent)
        self.zoomInButton = QPushButton("zoomIn")
        box.addWidget(self.zoomInButton)
        self.zoomInButton.setEnabled(True)
        self.zoomInButton.clicked.connect(self.zoomInEvent)

        hbox = QHBoxLayout()
        self.x1Button = QRadioButton("x1")
        self.x1Button.toggled.connect(self.zoomScaleEvent)
        self.x1Button.setChecked(True)
        hbox.addWidget(self.x1Button)
        self.x2Button = QRadioButton("x2")
        self.x2Button.toggled.connect(self.zoomScaleEvent)
        hbox.addWidget(self.x2Button)
        self.x4Button = QRadioButton("x4")
        self.x4Button.toggled.connect(self.zoomScaleEvent)
        hbox.addWidget(self.x4Button)
        self.x8Button = QRadioButton("x8")
        self.x8Button.toggled.connect(self.zoomScaleEvent)
        hbox.addWidget(self.x8Button)
        self.x16Button = QRadioButton("x16")
        self.x16Button.toggled.connect(self.zoomScaleEvent)
        hbox.addWidget(self.x16Button)
        wid = QWidget()
        wid.setLayout(hbox)
        box.addWidget(wid)

        self.zoomBackButton = QPushButton("zoomBack")
        box.addWidget(self.zoomBackButton)
        self.zoomBackButton.setEnabled(True)
        self.zoomBackButton.clicked.connect(self.zoomBackEvent)

        wid = QWidget()
        wid.setLayout(box)
        self.thirdRow = wid
        # forth row
        self.followMouse = FollowMouse(self.exceptionEvent)
        box = QHBoxLayout()
        box.addWidget(self.followMouse.createHbox())
        wid = QWidget()
        wid.setLayout(box)
        self.forthRow = wid
        # initialize
        layout = QGridLayout()
        layout.setVerticalSpacing(0)
        layout.addWidget(self.firstRow, 0, 0, alignment=Qt.AlignLeft)
        layout.addWidget(self.secondRow, 1, 0, alignment=Qt.AlignLeft)
        layout.addWidget(self.thirdRow, 2, 0, alignment=Qt.AlignLeft)
        layout.addWidget(self.forthRow, 3, 0, alignment=Qt.AlignLeft)
        self.setLayout(layout)
        self.subscription = None
        self.lasttime = time.time() - 2
        self.arg = None
        self.show()
        self.setFixedHeight(self.height())
        self.setFixedWidth(self.width())

    def resetEvent(self):
        if self.numpyImage is None:
            return
        if self.channelDict is None:
            return
        self.numpyImage.resetZoom()
        self.display()

    def colorChangeEvent(self):
        self.display()

    def plot3dEvent(self):
        if self.numpyImage is None:
            self.statusText.setText("no image")
            return
        if self.channelDict is None:
            self.statusText.setText("no channel")
            return
        if self.channelDict["channel"] is None:
            self.statusText.setText("no channel")
            return
        self.numpyImage.plot3d(self.channelDict["channel"])

    def showColorTableEvent(self):
        self.colorTable.show()

    def colorExceptionEvent(self, error):
        self.statusText.setText(error)

    def zoomInEvent(self):
        if self.numpyImage is None:
            return
        self.numpyImage.zoomIn(self.zoomScale)
        self.display()

    def zoomBackEvent(self):
        if self.numpyImage is None:
            return
        self.numpyImage.zoomBack()
        self.display()

    def zoomScaleEvent(self):
        if self.x1Button.isChecked():
            self.zoomScale = 1
        elif self.x2Button.isChecked():
            self.zoomScale = 2
        elif self.x4Button.isChecked():
            self.zoomScale = 4
        elif self.x8Button.isChecked():
            self.zoomScale = 8
        elif self.x16Button.isChecked():
            self.zoomScale = 16
        else:
            self.statusText.setText("why is no zoomScale enabled?")

    def zoomEvent(self):
        self.display()

    def scaleEvent(self):
        if self.autoScaleButton.isChecked():
            self.manualLimits = False
        elif self.manualScaleButton.isChecked():
            self.manualLimits = True
        else:
            self.statusText.setText("why is no scaleButton enabled?")
        self.display()

    def manualLimitsEvent(self):
        try:
            low = int(self.minLimitText.text())
            high = int(self.maxLimitText.text())
            self.channelToImage.setManualLimits((low, high))
            self.display()
        except Exception as error:
            self.statusText.setText(str(error))

    def imageSizeEvent(self):
        size = self.imageSizeText.text()
        try:
            value = int(size)
        except Exception as error:
            self.statusText.setText("value is not an integer")
            self.imageSizeText.setText(str(self.imageSize))
            return
        isStarted = self.isStarted
        if value < 128:
            value = 128
        if isStarted:
            self.stop()
        if self.numpyImage is not None:
            self.numpyImage.setOkToClose()
            self.numpyImage.close()
            self.numpyImage = None
        self.imageSizeText.setText(str(value))
        self.imageSize = value
        if isStarted:
            self.start()

    def numpyMouseMoveEvent(self, zoomDict, mouseDict):
        self.followMouse.setZoomInfo(zoomDict, mouseDict)

    def exceptionEvent(self, message):
        self.statusText.setText(message)

    def display(self):
        if self.isClosed:
            return
        if self.channelDict is None:
            return
        try:
            if self.channelDict["nz"] == 3:
                self.numpyImage.display(self.channelDict["image"])
            else:
                self.numpyImage.display(
                    self.channelDict["image"],
                    colorTable=self.colorTable.getColorTable(),
                )
        except Exception as error:
            self.statusText.setText(str(error))

    def closeEvent(self, event):
        if self.isStarted: self.stop()
        self.isClosed = True
        if self.numpyImage is not None:
            self.numpyImage.setOkToClose()
            self.numpyImage.close()
        self.colorTable.setOkToClose()
        self.colorTable.close()
        self.qapplication.closeAllWindows()

    def startEvent(self):
        self.start()
        self.isStarted = True

    def stopEvent(self):
        self.stop()
        self.isStarted = False

    def clearEvent(self):
        self.statusText.setText("")
        self.statusText.setStyleSheet("background-color:white")

    def channelNameEvent(self):
        try:
            self.provider.setChannelName(self.channelNameText.text())
        except Exception as error:
            self.statusText.setText(str(error))

    def start(self):
        if self.numpyImage is None:
            self.numpyImage = NumpyImage(
                flipy=False,
                imageSize=self.imageSize,
                exceptionCallback=self.exceptionEvent,
            )
            self.numpyImage.setZoomCallback(self.zoomEvent)
            self.numpyImage.setMouseMoveCallback(self.numpyMouseMoveEvent)
        self.provider.start()
        self.channelNameText.setEnabled(False)
        self.startButton.setEnabled(False)
        self.stopButton.setEnabled(True)
        self.channelNameText.setEnabled(False)

    def stop(self):
        self.provider.stop()
        self.startButton.setEnabled(True)
        self.stopButton.setEnabled(False)
        self.channelNameLabel.setStyleSheet("background-color:gray")
        self.channelNameText.setEnabled(True)
        self.channel = None
        self.imageRateText.setText("0")

    def callback(self, arg):
        if arg is None:
            return
        if self.isClosed:
            return
        if len(arg) == 1:
            value = arg.get("exception")
            if value is not None:
                self.statusText.setText(str(value))
                return
            value = arg.get("status")
            if value is not None:
                if value == "disconnected":
                    self.channelNameLabel.setStyleSheet("background-color:red")
                    self.statusText.setText("disconnected")
                    return
                elif value == "connected":
                    self.channelNameLabel.setStyleSheet("background-color:green")
                    self.statusText.setText("connected")
                    return
                else:
                    self.statusText.setText("unknown callback error")
                    return
        try:
            data = arg["value"]
            dimArray = arg["dimension"]
            ndim = len(dimArray)
            if ndim != 2 and ndim != 3:
                self.statusText.setText("ndim not 2 or 3")
                return
            compressed = arg["compressedSize"]
            uncompressed = arg["uncompressedSize"]
            codec = arg["codec"]
            codecName = codec["name"]
            codecNameLength = len(codecName)
            if self.codecAD.decompress(data, codec, compressed, uncompressed):
                self.codecIsNone = False
                self.codecNameText.setText(self.codecAD.getCodecName())
                data = self.codecAD.getData()
                self.compressRatioText.setText(str(self.codecAD.getCompressRatio()))
            else:
                if not self.codecIsNone:
                    self.codecIsNone = True
                    self.codecNameText.setText(self.codecAD.getCodecName())
                    self.compressRatioText.setText(str(self.codecAD.getCompressRatio()))
        except Exception as error:
            self.statusText.setText(str(error))
            return
        try:
            self.channelToImage.channelToImage(
                data, dimArray, self.imageSize, manualLimits=self.manualLimits
            )
            self.channelDict = self.channelToImage.getChannelDict()
            self.followMouse.setChannelInfo(self.channelDict)
            self.display()
        except Exception as error:
            self.statusText.setText(str(error))
        self.nImages = self.nImages + 1
        self.timenow = time.time()
        timediff = self.timenow - self.lasttime
        if timediff > 1:
            self.imageRateText.setText(str(round(self.nImages / timediff)))
            self.lasttime = self.timenow
            self.nImages = 0
