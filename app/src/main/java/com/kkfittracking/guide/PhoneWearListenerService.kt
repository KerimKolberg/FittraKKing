package com.kkfittracking.guide

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.kkfittracking.FitTraKKingApplication
import com.kkfittracking.wear.WearJson
import com.kkfittracking.wear.WearPaths
import kotlinx.coroutines.launch

/** Receives the watch app's commands, even when the phone app is closed, and hands them to the [WatchBridge]. */
class PhoneWearListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WearPaths.COMMAND) return
        val command = WearJson.decodeCommand(event.data) ?: return
        val container = (application as FitTraKKingApplication).container
        container.appScope.launch { container.watchBridge.handle(command) }
    }
}
