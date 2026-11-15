package com.onikiri.open

import android.app.Application
import com.topjohnwu.superuser.Shell

class OnikiriApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Must be configured before the first Shell.getShell() call.
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER or Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(20)
        )
    }
}
