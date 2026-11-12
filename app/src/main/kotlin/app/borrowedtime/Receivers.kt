package app.borrowedtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Repo.init(context)
        if (Store.joined && Store.onboarded) {
            runCatching { GuardService.start(context) }
        }
    }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Repo.init(context)
        val id = intent.getStringExtra("id") ?: return
        val approve = intent.getBooleanExtra("approve", false)
        val minutes = intent.getIntExtra("minutes", 5)
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                Repo.decide(id, approve, minutes)
                Notifier.cancel(context, id.hashCode())
            } finally {
                pending.finish()
            }
        }
    }
}
