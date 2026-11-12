package space.wavedot.infra.playback.android

import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

interface PlayerHost {
    val sessionPlayer: Player
}

class WavedotPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val host = application as? PlayerHost ?: error("Application must implement PlayerHost")
        session = MediaSession.Builder(this, host.sessionPlayer).setCallback(TrustedControllersOnly()).build()
    }

    private inner class TrustedControllersOnly : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val allowed = controller.packageName == packageName ||
                controller.isTrusted ||
                session.isMediaNotificationController(controller)
            return if (allowed) super.onConnect(session, controller) else MediaSession.ConnectionResult.reject()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            release()
        }
        session = null
        super.onDestroy()
    }
}
