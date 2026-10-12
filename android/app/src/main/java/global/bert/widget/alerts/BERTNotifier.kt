package global.bert.widget.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import global.bert.widget.BERTEntry
import global.bert.widget.MainActivity
import global.bert.widget.R
import global.bert.widget.data.AlertChannel
import global.bert.widget.data.AlertNotice

object BERTNotifier {
    fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        for (channel in AlertChannel.entries) {
            manager.createNotificationChannel(NotificationChannel(channel.id, channel.title, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = channel.description
            })
        }
    }

    /** Returns how many notices were posted; none when the person has not allowed notifications. */
    fun post(context: Context, notices: List<AlertNotice>): Int {
        if (notices.isEmpty() || !canPost(context)) return 0
        ensureChannels(context)
        val manager = NotificationManagerCompat.from(context)
        for (notice in notices) {
            val notification = NotificationCompat.Builder(context, notice.channel.id)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(notice.title)
                .setContentText(notice.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(notice.text))
                .setContentIntent(openIntent(context, notice.channel))
                .setAutoCancel(true)
                .build()
            try { manager.notify(notice.id, notification) } catch (_: SecurityException) { return 0 }
        }
        return notices.size
    }

    /** Price alerts open Market; Bert's updates and tournaments open Home. One request code per channel keeps the extras apart. */
    private fun openIntent(context: Context, channel: AlertChannel): PendingIntent {
        val entry = if (channel == AlertChannel.PRICE) BERTEntry.MARKET else BERTEntry.HOME
        val intent = Intent(context, MainActivity::class.java).putExtra(BERTEntry.EXTRA, entry.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, channel.ordinal, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
