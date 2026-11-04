package com.example.blacknote

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NoteListenerService : NotificationListenerService() {

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName != packageName) return
        val store = NoteStore(this)
        if (store.consumeRemoving(sbn.id)) return
        val note = store.all().firstOrNull { it.id == sbn.id } ?: return
        NoteNotifier.show(this, note)
    }
}
