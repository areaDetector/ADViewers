#!/usr/bin/env python
'''
Copyright - See the COPYRIGHT that is included with this distribution.
    NTNDA_Viewer is distributed subject to a Software License Agreement found
    in file LICENSE that is included with this distribution.

author Marty Kraimer
    latest date 2020.03.02
    original development started 2019.12
'''

from NTNDA_Viewer import NTNDA_Viewer,NTNDA_Channel_Provider
from p4p.client.thread import Context
import sys
from threading import Event
from PyQt5.QtWidgets import QApplication
from PyQt5.QtCore import QObject,pyqtSignal

class P4PProvider(QObject,NTNDA_Channel_Provider) :
    callbacksignal = pyqtSignal()
    def __init__(self):
        QObject.__init__(self)
        NTNDA_Channel_Provider.__init__(self)
        self.callbacksignal.connect(self.mycallback)
        self.callbackDoneEvent = Event()
        self.firstCallback = True
        self.isClosed = True
        
    def start(self) :
        self.ctxt = Context('pva')
        self.firstCallback = True
        self.isClosed = False
        self.subscription = self.ctxt.monitor(
              self.getChannelName(),
              self.p4pcallback,
              request='field(value,dimension,codec,compressedSize,uncompressedSize)',
              notify_disconnect=True)
    def stop(self) :
        self.isClosed = True
        self.ctxt.close()
    def done(self) :
        pass
    def callback(self,arg) :
        self.NTNDA_Viewer.callback(arg)
    def p4pcallback(self,arg) :
        if self.isClosed : return
        self.struct = arg;
        self.callbacksignal.emit()
        self.callbackDoneEvent.wait()
        self.callbackDoneEvent.clear()
    def mycallback(self) :
        struct = self.struct
        arg = dict()
        try :
            argtype = str(type(struct))
            if argtype.find('Disconnected')>=0 :
                arg["status"] = "disconnected"
                self.callback(arg)
                self.firstCallback = True
                self.callbackDoneEvent.set()
                return
            if self.firstCallback :
                arg = dict()
                arg["status"] = "connected"
                self.callback(arg)
                self.firstCallback = False
                self.callback(arg)
            arg = dict()
            arg['value'] = struct['value']
            arg['dimension'] = struct['dimension']
            arg['codec'] = struct['codec']
            arg['compressedSize'] = struct['compressedSize']
            arg['uncompressedSize'] = struct['uncompressedSize']
            self.callback(arg)
            self.callbackDoneEvent.set()
            return
        except Exception as error:
            arg["exception"] = repr(error)
            self.callback(arg)
            self.callbackDoneEvent.set()
            return

if __name__ == '__main__':
    app = QApplication(sys.argv)
    p4pProvider = P4PProvider()
    channelName = ""
    nargs = len(sys.argv)
    if nargs>=2 :
        channelName = sys.argv[1]
    p4pProvider.setChannelName(channelName)
    viewer = NTNDA_Viewer(p4pProvider,"P4P")
    sys.exit(app.exec_())

