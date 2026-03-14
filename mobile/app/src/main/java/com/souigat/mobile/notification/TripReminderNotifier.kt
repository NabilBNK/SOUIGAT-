package com.souigat.mobile.notification

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
import com.souigat.mobile.MainActivity
import com.souigat.mobile.R
import com.souigat.mobile.data.local.entity.TripEntity
import com.souigat.mobile.util.Constants
import com.souigat.mobile.util.toRouteDateTime

object TripReminderNotifier {

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            Constants.TRIP_REMINDER_CHANNEL_ID,
            "Alertes trajets",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Rappels conducteur pour les trajets proches du depart."
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun showReminder(
        context: Context,
        trip: TripEntity,
        reminderType: String
    ) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        createChannels(context)

        val tripKey = trip.serverId ?: trip.id
        val (title, body) = when (reminderType) {
            TripReminderWorker.REMINDER_ONE_DAY -> {
                "Trajet demain" to "${trip.originOffice} -> ${trip.destinationOffice} le ${trip.departureDateTime.toRouteDateTime()}"
            }

            TripReminderWorker.REMINDER_TWO_HOURS -> {
                "Trajet dans 2 heures" to "${trip.originOffice} -> ${trip.destinationOffice} au depart de ${trip.busPlate}"
            }

            else -> {
                "Heure de depart atteinte" to "Le trajet ${trip.originOffice} -> ${trip.destinationOffice} peut commencer maintenant."
            }
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            tripKey.toInt(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, Constants.TRIP_REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(reminderId(tripKey, reminderType), notification)
    }

    private fun reminderId(tripKey: Long, reminderType: String): Int {
        return "$tripKey:$reminderType".hashCode()
    }
}
