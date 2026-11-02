package com.peaspilaf.copyparty

import android.app.Application
import android.content.Context
import com.peaspilaf.copyparty.data.AndroidStorageAccess
import com.peaspilaf.copyparty.data.ConfigStore
import com.peaspilaf.copyparty.data.JsonConfigStore
import com.peaspilaf.copyparty.data.StorageAccess
import com.peaspilaf.copyparty.data.saf.AndroidSafImporter
import com.peaspilaf.copyparty.data.saf.SafImporter
import com.peaspilaf.copyparty.server.AndroidServerRuntime
import com.peaspilaf.copyparty.server.ServerRuntime
import java.io.File

class CopypartyApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Hand-rolled dependency container: small app, no DI framework in the build. */
class AppContainer(context: Context) {
    val configStore: ConfigStore = JsonConfigStore(File(context.filesDir, "app-state.json"))
    val storage: StorageAccess = AndroidStorageAccess(context)
    val safImporter: SafImporter = AndroidSafImporter(context, context.filesDir)
    val serverRuntime: ServerRuntime = AndroidServerRuntime(context.applicationContext, configStore)
}
