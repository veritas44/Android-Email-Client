package com.criptext.mail.utils.removedevice

import com.criptext.mail.db.AppDatabase
import com.criptext.mail.db.KeyValueStorage

object RemoveDeviceUtils{
    fun removeDevice(db: AppDatabase, storage: KeyValueStorage){
        db.clearAllTables()
        storage.clearAll()
    }
}