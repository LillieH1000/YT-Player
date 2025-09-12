package h.lillie.ytplayer.application

import android.app.Application
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.google.android.gms.net.CronetProviderInstaller

class Application : Application() {
    override fun onCreate() {
        super.onCreate()
        if (!CronetProviderInstaller.isInstalled()) {
            CronetProviderInstaller.installProvider(this)
        }
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
    }
}