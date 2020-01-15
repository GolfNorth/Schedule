package ru.golfnorth.schedule

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.DatePickerDialog.OnDateSetListener
import android.app.NotificationManager
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.webkit.WebView
import android.widget.TextView
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import kotlinx.android.synthetic.main.activity_main.*
import org.jsoup.nodes.Document
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.*
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity(), Observer {
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var webView: WebView
    private lateinit var textView: TextView
    private lateinit var calendar: Calendar
    private lateinit var scheduleMode: ScheduleMode
    private lateinit var scheduleTitle: String
    private lateinit var template: String
    private lateinit var dialogTitle: String
    private lateinit var sharedPref: SharedPreferences
    private lateinit var calendarHelper: CalendarHelper

    private var scheduleId: Int = 0
    private var isSet: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPref = this.getSharedPreferences("Settings", MODE_PRIVATE) ?: return

        swipeRefreshLayout = findViewById(R.id.swipeRefresh)
        swipeRefreshLayout.setColorSchemeResources(R.color.blue, R.color.green, R.color.yellow, R.color.red)
        swipeRefreshLayout.setOnRefreshListener { handleRefresh() }

        textView = findViewById(R.id.title)

        webView = findViewById(R.id.webView)
        webView.settings.javaScriptEnabled = true

        calendarHelper = CalendarHelper(sharedPref.getString("lastDay", "0000-00-00"))
        calendar = calendarHelper.getActualDay()
        scheduleMode = ScheduleMode.valueOf(sharedPref.getString("lastMode", "Group"))
        scheduleId = sharedPref.getInt("lastId", 0)
        scheduleTitle = sharedPref.getString("lastTitle", getString(R.string.action_group))
        template = ""

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(1)

        val simpleRequest = PeriodicWorkRequest.Builder(BackgroundWorker::class.java, 45, TimeUnit.MINUTES).build()
        val workManager = WorkManager.getInstance()
        workManager.cancelAllWork()
        workManager.enqueue(simpleRequest)

        loadSchedule()
    }

    private fun handleRefresh() {
        loadSchedule()
    }

    private fun showSchedule(content: String) {
        swipeRefreshLayout.isRefreshing = true

        if (template == "") {
            val buffer = StringBuilder()
            val stream: InputStream = assets.open("template.html")
            val reader = BufferedReader(InputStreamReader(stream, "UTF-8"))
            var line: String? = null

            while ({ line = reader.readLine(); line }() != null) {
                buffer.append(line)
            }

            template = buffer.toString()
            stream.close()
        }

        webView.loadDataWithBaseURL("about:blank", template.replace("{REPLACE}", content), "text/html", "UTF-8", "about:blank")

        swipeRefreshLayout.isRefreshing = false
    }

    private fun loadSchedule() {
        if (scheduleId > 0) {
            swipeRefreshLayout.isRefreshing = true

            val loadHTMLTask = LoadHTMLTask()
            loadHTMLTask.registerObserver(this)

            var url = when (scheduleMode) {
                ScheduleMode.Group -> "http://www.ishnk.ru/schedule/group?group=${scheduleId}"
                ScheduleMode.Teacher -> "http://www.ishnk.ru/schedule/teacher?teacher=${scheduleId}"
                ScheduleMode.Call -> "http://www.ishnk.ru/schedule/calls?type=all"
            }

            loadHTMLTask.execute(url, calendarHelper.dateFormat(calendar))

            textView.text = scheduleTitle

            with(sharedPref.edit()) {
                putString("lastMode", scheduleMode.name)
                putString("lastTitle", scheduleTitle)
                putInt("lastId", scheduleId)
                commit()
            }
        } else {
            setTeacherOrGroup(fabGroup)
        }
    }

    fun setDate(v: View) {
        var datePicker = DatePickerDialog(v.context, datePickerCallBack, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))

        datePicker.show()
        fabMenu.close(true)
    }

    fun setTeacherOrGroup(v: View) {
        var url = ""

        swipeRefreshLayout.isRefreshing = true
        isSet = true

        when (v.id) {
            R.id.fabTeacher -> {
                dialogTitle = getString(R.string.select_teacher)
                scheduleMode = ScheduleMode.Teacher
                url = "http://www.ishnk.ru/teachers"
            }
            R.id.fabGroup -> {
                dialogTitle = getString(R.string.select_group)
                scheduleMode = ScheduleMode.Group
                url = "http://www.ishnk.ru/groups"
            }
        }

        if (url != "") {
            with(sharedPref.edit()) {
                putString("lastHTML", "")
                putString("lastNotificationDay", "0000-00-00")
                putString("lastTitle", scheduleTitle)
                putInt("lastId", scheduleId)
                commit()
            }

            val loadHTMLTask = LoadHTMLTask()

            loadHTMLTask.registerObserver(this)
            loadHTMLTask.execute(url)
        }

        fabMenu.close(true)
    }

    fun loadCallSchedule(v: View) {
        scheduleMode = ScheduleMode.Call
        scheduleTitle = getString(R.string.action_call)

        loadSchedule()

        fabMenu.close(true)
    }

    private var datePickerCallBack: OnDateSetListener = OnDateSetListener { _, year, month, dayOfMonth ->
        calendar.set(Calendar.YEAR, year)
        calendar.set(Calendar.MONTH, month)
        calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

        with(sharedPref.edit()) {
            putString("lastDay", calendarHelper.dateFormat(calendar))
            commit()
        }

        loadSchedule()
    }

    override fun update(doc: Document) {
        if (isSet) {
            isSet = false

            val mapItems = mutableMapOf<String, Int>()

            val tdElementIterator = doc.select("td").listIterator()

            while (tdElementIterator.hasNext()) {
                val element = tdElementIterator.next()

                if (element.attr("data-id") != "") {
                    mapItems.put(element.text(), element.attr("data-id").toInt())
                }
            }

            val dialogBuilder = AlertDialog.Builder(this@MainActivity)
            val sorted = mapItems.toSortedMap().keys.toTypedArray()

            dialogBuilder.setTitle(dialogTitle)
                    .setCancelable(true)
                    .setItems(sorted, DialogInterface.OnClickListener { _, which ->
                        scheduleId = mapItems.get(sorted[which])!!.toInt()
                        scheduleTitle = sorted[which]

                        loadSchedule()
                    })
                    .create()
                    .show()
        } else {
            val html = doc.toString()

            showSchedule(html)
        }

        swipeRefreshLayout.isRefreshing = false
    }
}
