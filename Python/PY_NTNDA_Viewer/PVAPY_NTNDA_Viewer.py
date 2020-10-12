#!/usr/bin/env python
"""
Copyright - See the COPYRIGHT that is included with this distribution.
    NTNDA_Viewer is distributed subject to a Software License Agreement found
    in file LICENSE that is included with this distribution.

author Marty Kraimer
    latest date 2020.03.02
    original development started 2019.12
"""

from NTNDA_Viewer import NTNDA_Viewer
from pvaccess import *
import sys
from threading import Event
from PyQt5.QtWidgets import QApplication
from PyQt5.QtCore import QObject, pyqtSignal


class PVAPYProvider(QObject):
    monitorCallbacksignal = pyqtSignal()
    connectCallbacksignal = pyqtSignal()

    def __init__(self):
        QObject.__init__(self)
        self.monitordata = None
        self.connectdata = None
        self.isConnected = False
        self.firstStart = True
        self.connectCallbacksignal.connect(self.connectionCallback)
        self.ConnectDoneEvent = Event()
        self.ConnectDoneEvent.clear()
        self.monitorCallbacksignal.connect(self.monitorCallback)
        self.monitorDoneEvent = Event()
        self.monitorDoneEvent.clear()
        self.channelName = "13SIM1:Pva1:Image"
        self.channel = None
        self.isStarted = False

    def setChannelName(self, channelName):
        if self.channel != None and self.isStarted:
            self.stop()
        self.firstStart = True
        self.channelName = channelName

    def getChannelName(self):
        return self.channelName

    def start(self):
        if self.isStarted:
            self.stop()
        self.isStarted = True
        if self.firstStart:
            self.channel = Channel(self.channelName)
            self.firstStart = False
            self.channel.setConnectionCallback(self.pvapyconnectioncallback)
        self.channel.monitor(
            self.pvapymonitorcallback,
            "field(value,dimension,codec,compressedSize,uncompressedSize)",
        )

    def stop(self):
        self.isStarted = False
        self.channel.stopMonitor()

    def callViewerCallback(self, arg):
        self.NTNDA_Viewer.callback(arg)

    def pvapyconnectioncallback(self, arg):
        data = dict()
        if arg == True:
            data["status"] = "connected"
        elif arg == False:
            data["status"] = "disconnected"
        else:
            data["exception"] = "bad pvapy connection callback =" + str(arg)
        self.connectdata = data
        self.connectCallbacksignal.emit()
        result = self.ConnectDoneEvent.wait(1.0)
        self.ConnectDoneEvent.clear()

    def connectionCallback(self):
        arg = self.connectdata
        self.callViewerCallback(arg)
        self.ConnectDoneEvent.set()

    def pvapymonitorcallback(self, arg):
        self.monitordata = arg
        self.monitorCallbacksignal.emit()
        self.monitorDoneEvent.wait()
        self.monitorDoneEvent.clear()

    def monitorCallback(self):
        if not self.isStarted:
            self.monitorDoneEvent.set()
            return
        arg = dict()
        val = self.monitordata["value"][0]
        if len(val) != 1:
            arg["exception"] = "value length not 1"
            self.callViewerCallback(arg)
            self.monitorDoneEvent.set()
            return
        element = None
        for x in val:
            element = x
        if element == None:
            arg["exception"] = "value is not numpy  array"
            self.callViewerCallback(arg)
            self.monitorDoneEvent.set()
            return
        value = val[element]
        arg["value"] = value
        arg["dimension"] = self.monitordata["dimension"]
        codec = self.monitordata["codec"]
        codecName = codec["name"]
        if len(codecName) < 1:
            arg["codec"] = self.monitordata["codec"]
        else:
            parameters = codec["parameters"]
            typevalue = parameters[0]["value"]
            cod = dict()
            cod["name"] = codecName
            cod["parameters"] = typevalue
            arg["codec"] = cod
        arg["compressedSize"] = self.monitordata["compressedSize"]
        arg["uncompressedSize"] = self.monitordata["uncompressedSize"]
        self.callViewerCallback(arg)
        self.monitorDoneEvent.set()


if __name__ == "__main__":
    app = QApplication(sys.argv)
    PVAPYProvider = PVAPYProvider()
    nargs = len(sys.argv)
    if nargs >= 2:
        channelName = sys.argv[1]
        PVAPYProvider.setChannelName(channelName)
    viewer = NTNDA_Viewer(PVAPYProvider, "PVAPY")
    sys.exit(app.exec_())
