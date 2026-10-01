package com.aranustech.onvifcam.channel

import io.flutter.plugin.common.EventChannel

class EventStreamHandler : EventChannel.StreamHandler {
    override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
        EventBridge.attach(events)
    }

    override fun onCancel(arguments: Any?) {
        EventBridge.detach()
    }
}
