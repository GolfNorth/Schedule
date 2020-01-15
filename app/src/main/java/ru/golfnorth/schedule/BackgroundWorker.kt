package ru.golfnorth.schedule

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.support.v4.app.NotificationCompat
import android.support.v4.app.TaskStackBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.jsoup.nodes.Document


class BackgroundWorker(_appContext: Context, workerParams: WorkerParameters) : Worker(_appContext, workerParams), Observer {
    private var appContext: Context
    private lateinit var sharedPref: SharedPreferences
    private lateinit var calendarHelper: CalendarHelper

    init {
        appContext = _appContext
    }

    override fun doWork(): Result {
        try {
            sharedPref = appContext.getSharedPreferences("Settings", MODE_PRIVATE)

            val scheduleId = sharedPref.getInt("lastId", 0)

            if (scheduleId > 0) {
                val url = when (ScheduleMode.valueOf(sharedPref.getString("lastMode", "Group"))) {
                    ScheduleMode.Group -> "http://www.ishnk.ru/schedule/group?group=${scheduleId}"
                    ScheduleMode.Teacher -> "http://www.ishnk.ru/schedule/teacher?teacher=${scheduleId}"
                    else -> ""
                }

                if (url != "") {
                    calendarHelper = CalendarHelper(sharedPref.getString("lastNotificationDay", "0000-00-00"))

                    val loadHTMLTask = LoadHTMLTask()
                    loadHTMLTask.registerObserver(this)
                    loadHTMLTask.execute(url, calendarHelper.dateFormat(calendarHelper.getNextDay()))
                }
            }
        } catch (e: InterruptedException) {
            e.printStackTrace()

            return Result.failure()
        }

        return Result.success()
    }

    private fun sendNotification(title: String, text: String) {
        val notifyID = 1
        val CHANNEL_ID = "new_schedule"
        val name = appContext.getString(R.string.channel_name)
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_HIGH)
            channel.enableLights(true)
            channel.enableVibration(true)
            notificationManager.createNotificationChannel(channel)
        }

        val notificationIntent = Intent(appContext, MainActivity::class.java)

        val stackBuilder: TaskStackBuilder = TaskStackBuilder.create(appContext)
        stackBuilder.addParentStack(MainActivity::class.java)
        stackBuilder.addNextIntent(notificationIntent)

        val contentIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_small_icon)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(contentIntent)

        val notification = builder.build()

        notificationManager.notify(notifyID, notification)
    }

    override fun update(doc: Document) {
        val lastHTML = sharedPref.getString("lastHTML", "")
        val html = doc.toString()

        if (lastHTML != html) {
            val lastDay = calendarHelper.getLastDay()
            val nextDay = calendarHelper.getNextDay()

            with(sharedPref.edit()) {
                putString("lastHTML", html)
                putString("lastNotificationDay", calendarHelper.dateFormat(nextDay))
                commit()
            }

            if (lastDay == nextDay) {
                val title = sharedPref.getString("lastTitle", appContext.getString(R.string.app_name))
                sendNotification(title, appContext.getString(R.string.notification_text))
            }
        }
    }
}