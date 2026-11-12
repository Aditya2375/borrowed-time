package app.borrowedtime

import android.app.Application

class BtApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        Notifier.channels(this)
    }
}
