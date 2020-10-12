# colorTable.py

from PyQt5.QtWidgets import QWidget, QRubberBand
from PyQt5.QtWidgets import QLabel, QLineEdit
from PyQt5.QtWidgets import (
    QGroupBox,
    QHBoxLayout,
    QVBoxLayout,
    QGridLayout,
    QPushButton,
)
from PyQt5.QtWidgets import QPushButton
from PyQt5.QtCore import QPoint, QRect, QSize, QPointF
from PyQt5.QtCore import QThread
from PyQt5.QtGui import QPainter, QImage
from PyQt5.QtCore import *
from PyQt5.QtGui import QColor, qRgb
import numpy as np
import math


class ColorTable(QWidget):
    """

    Normal use is:
    ...
    from colorTable import ColorTable
    ...
        self.colorTable = ColorTable()

    ...

    ...

    Copyright - See the COPYRIGHT that is included with this distribution.
        NTNDA_Viewer is distributed subject to a Software License Agreement found
        in file LICENSE that is included with this distribution.

    authors
        Marty Kraimer
    latest date 2020.08.11
    """

    def __init__(self, parent=None):
        super(QWidget, self).__init__(parent)
        self.__okToClose = False
        self.__isHidden = True
        self.__colorTable = [qRgb(i, i, i) for i in range(256)]
        self.__clientColorChangeCallback = None
        self.__clientExceptionCallback = None
        self.__xoffset = None
        self.__yoffset = None

        masterbox = QVBoxLayout()
        self.invertButton = QPushButton("invert color table")
        self.invertButton.setEnabled(True)
        self.invertButton.clicked.connect(self.__invertEvent)
        masterbox.addWidget(self.invertButton)

        linearModeBox = QVBoxLayout()
        linearModeBox.addWidget(QLabel("color mode linear"))
        linearModeBox.addWidget(QLabel("red:"))
        self.redText = QLineEdit()
        self.redText.setFixedWidth(50)
        self.redText.setEnabled(True)
        self.redText.setText("1.0")
        self.redText.returnPressed.connect(self.__colorLimitEvent)
        linearModeBox.addWidget(self.redText)
        linearModeBox.addWidget(QLabel("green:"))
        self.greenText = QLineEdit()
        self.greenText.setFixedWidth(50)
        self.greenText.setEnabled(True)
        self.greenText.setText("1.0")
        self.greenText.returnPressed.connect(self.__colorLimitEvent)
        linearModeBox.addWidget(self.greenText)
        linearModeBox.addWidget(QLabel("blue:"))
        self.blueText = QLineEdit()
        self.blueText.setFixedWidth(50)
        self.blueText.setEnabled(True)
        self.blueText.setText("1.0")
        self.blueText.returnPressed.connect(self.__colorLimitEvent)
        linearModeBox.addWidget(self.blueText)
        wid = QWidget()
        wid.setLayout(linearModeBox)
        masterbox.addWidget(wid)

        lutModeBox = QVBoxLayout()
        lutModeBox.addWidget(QLabel("color mode lut"))
        self.grayButton = QPushButton("gray")
        self.grayButton.setEnabled(True)
        self.grayButton.clicked.connect(self.__grayButtonEvent)
        lutModeBox.addWidget(self.grayButton)
        self.redButton = QPushButton("red")
        self.redButton.setEnabled(True)
        self.redButton.clicked.connect(self.__redButtonEvent)
        lutModeBox.addWidget(self.redButton)
        self.greenButton = QPushButton("green")
        self.greenButton.setEnabled(True)
        self.greenButton.clicked.connect(self.__greenButtonEvent)
        lutModeBox.addWidget(self.greenButton)
        self.blueButton = QPushButton("blue")
        self.blueButton.setEnabled(True)
        self.blueButton.clicked.connect(self.__blueButtonEvent)
        lutModeBox.addWidget(self.blueButton)
        self.juliaButton = QPushButton("julia")
        self.juliaButton.setEnabled(True)
        self.juliaButton.clicked.connect(self.__juliaButtonEvent)
        lutModeBox.addWidget(self.juliaButton)

        wid = QWidget()
        wid.setLayout(lutModeBox)
        masterbox.addWidget(wid)

        wid = QWidget()
        wid.setLayout(masterbox)
        self.firstRow = wid
        # initialize
        layout = QGridLayout()
        layout.setVerticalSpacing(0)
        layout.addWidget(self.firstRow, 0, 0, alignment=Qt.AlignLeft)
        self.setLayout(layout)
        self.setFixedHeight(self.height())
        self.setFixedWidth(180)

    def getColorTable(self):
        """ get current colorTable """
        return self.__colorTable

    def setColorChangeCallback(self, callback):
        """ set a callback for when colorTable changes """
        self.__clientColorChangeCallback = callback

    def setExceptionCallback(self, callback):
        """ set a callback for exception """
        self.__clientExceptionCallback = callback

    def setOkToClose(self):
        """ allow image window to be closed"""
        self.__okToClose = True

    def closeEvent(self, event):
        """
        This is a QWidget method.
        It is only present to override until it is okToClose
        """
        if not self.__okToClose:
            point = self.geometry().topLeft()
            self.__xoffset = point.x()
            self.__yoffset = point.y()
            self.setGeometry(
                self.__xoffset, self.__yoffset, self.width(), self.height()
            )
            self.hide()
            self.__isHidden = True
            return

    def __userColorChangeEvent(self):
        if self.__clientColorChangeCallback == None:
            return
        self.__clientColorChangeCallback()

    def __invertEvent(self):
        for i in range(256):
            rgb = self.__colorTable[i]
            blue = 255 - (rgb & 0x000000FF)
            green = 255 - ((rgb >> 8) & 0x000000FF) << 8
            red = 255 - ((rgb >> 16) & 0x000000FF) << 16
            self.__colorTable[i] = red | green | blue | 0xFF000000
        self.__userColorChangeEvent()

    def __colorLimitEvent(self):
        try:
            red = float(self.redText.text())
            if red < 0.0:
                raise Exception("red is less than zero")
            green = float(self.greenText.text())
            if green < 0.0:
                raise Exception("green is less than zero")
            blue = float(self.blueText.text())
            if blue < 0.0:
                raise Exception("blue is less than zero")
            maxvalue = red
            if green > maxvalue:
                maxvalue = green
            if blue > maxvalue:
                maxvalue = blue
            if maxvalue <= 0:
                raise Exception("at least one of red,green,blue must be > 0")
            red = red / maxvalue
            green = green / maxvalue
            blue = blue / maxvalue
            colorTable = []
            for ind in range(256):
                r = int(ind * red)
                g = int(ind * green)
                b = int(ind * blue)
                colorTable.append(qRgb(r, g, b))
            self.__colorTable = colorTable
            self.__userColorChangeEvent()
        except Exception as error:
            if self.__clientExceptionCallback == None:
                print("Exception error=", str(error))
            else:
                self.__clientExceptionCallback("colorTable exception " + str(error))

    def __grayButtonEvent(self):
        self.__colorTable = [qRgb(i, i, i) for i in range(256)]
        self.__userColorChangeEvent()

    def __redButtonEvent(self):
        self.__colorTable = [qRgb(i, 0, 0) for i in range(256)]
        self.__userColorChangeEvent()

    def __greenButtonEvent(self):
        self.__colorTable = [qRgb(0, i, 0) for i in range(256)]
        self.__userColorChangeEvent()

    def __blueButtonEvent(self):
        self.__colorTable = [qRgb(0, 0, i) for i in range(256)]
        self.__userColorChangeEvent()

    def __juliaButtonEvent(self):
        self.__colorTable = [
            qRgb(i % 8 * 32, i % 16 * 16, i % 32 * 8) for i in range(256)
        ]
        self.__userColorChangeEvent()
