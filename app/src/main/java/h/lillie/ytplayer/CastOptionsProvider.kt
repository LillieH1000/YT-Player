package h.lillie.ytplayer

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import java.util.Collections

class CastOptionsProvider : OptionsProvider {
    @OptIn(UnstableApi::class)
    override fun getCastOptions(p0: Context): CastOptions {
        return CastOptions.Builder()
            .setCastMediaOptions(CastMediaOptions.Builder()
                .setMediaSessionEnabled(false)
                .setNotificationOptions(null)
                .build())
            .setEnableReconnectionService(false)
            .setReceiverApplicationId("8A965F92")
            .setResumeSavedSession(false)
            .setStopReceiverApplicationWhenEndingSession(true)
            .build()
    }

    override fun getAdditionalSessionProviders(p0: Context): MutableList<SessionProvider>? {
        return Collections.emptyList()
    }
}