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
from pvaccess import *
import sys
from threading import Event
from PyQt5.QtWidgets import QApplication
from PyQt5.QtCore import QObject,pyqtSignal

class GetChannel(object) :
    '''
       This exists because whenever a new channel was started a crash occured
    '''
    def __init__(self, parent=None):
        self.save = dict()
    def get(self,channelName) :
        channel = self.save.get(channelName)
        if channel!=None : return channel
        channel = Channel(channelName)
        self.save.update({channelName : channel})
        return channel

class PVAPYProvider(QObject,NTNDA_Channel_Provider) :
    callbacksignal = pyqtSignal()
    def __init__(self):
        QObject.__init__(self)
        NTNDA_Channel_Provider.__init__(self)
        self.getChannel = GetChannel()
        self.callbacksignal.connect(self.mycallback)
        self.callbackDoneEvent = Event()

    def start(self) :
        self.channel = self.getChannel.get(self.getChannelName())
        self.channel.monitor(self.pvapycallback,
              'field(value,dimension,codec,compressedSize,uncompressedSize)')
    def stop(self) :
        self.channel.stopMonitor()
    def done(self) :
        pass
    def pvapycallback(self,arg) :
        self.struct = arg;
        self.callbacksignal.emit()
        self.callbackDoneEvent.wait()
        self.callbackDoneEvent.clear()
    def callback(self,arg) :
        self.NTNDA_Viewer.callback(arg)
    def mycallback(self) :
        struct = self.struct
        arg = dict()
        try :
            val = struct['value'][0]
            if len(val) != 1 :
                raise Exception('value length not 1')
            element = None
            for x in val :
                element = x
            if element == None : 
                raise Exception('value is not numpy  array')
            value = val[element]
            arg['value'] = value
            arg['dimension'] = struct['dimension']
            codec = struct['codec']
            codecName = codec['name']
            if len(codecName)<1 :
                arg['codec'] = struct['codec']
            else :
                parameters = codec['parameters']
                typevalue = parameters[0]['value']
                cod = dict()
                cod['name'] = codecName
                cod['parameters'] = typevalue
                arg['codec'] = cod
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
    PVAPYProvider = PVAPYProvider()
    channelName = ""
    nargs = len(sys.argv)
    if nargs>=2 :
        channelName = sys.argv[1]
    PVAPYProvider.setChannelName(channelName)
    viewer = NTNDA_Viewer(PVAPYProvider,"PVAPY")
    sys.exit(app.exec_())

