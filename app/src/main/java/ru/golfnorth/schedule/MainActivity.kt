package ru.golfnorth.schedule

import android.app.DatePickerDialog
import android.app.DatePickerDialog.OnDateSetListener
import android.content.Context
import android.content.SharedPreferences
import android.os.AsyncTask
import android.os.Bundle
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.webkit.WebView
import org.jsoup.Jsoup
import java.io.*
import java.net.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.android.synthetic.main.activity_main.*
import android.app.AlertDialog
import android.content.DialogInterface
import android.util.Log
import android.widget.TextView


class MainActivity : AppCompatActivity() {
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var webView: WebView
    private lateinit var textView: TextView
    private lateinit var scheduleCalendar: Calendar
    private lateinit var scheduleMode: String
    private lateinit var scheduleTitle: String
    private lateinit var template: String
    private lateinit var dialogTitle: String
    private lateinit var sharedPref: SharedPreferences
    private var scheduleId: Int = 0

    private val dateFormat: DateFormat = SimpleDateFormat("yyyy-MM-dd")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPref = this.getPreferences(Context.MODE_PRIVATE) ?: return

        swipeRefreshLayout=findViewById(R.id.swipeRefresh)
        swipeRefreshLayout.setColorSchemeResources(R.color.blue, R.color.green, R.color.yellow, R.color.red)
        swipeRefreshLayout.setOnRefreshListener { handleRefresh() }

        textView = findViewById(R.id.title)

        webView = findViewById(R.id.webView)
        webView.settings.javaScriptEnabled = true

        scheduleCalendar = getScheduleCalendar()
        scheduleMode = sharedPref.getString("lastMode", "Group")
        scheduleId = sharedPref.getInt("lastId", 0)
        scheduleTitle = sharedPref.getString("lastTitle", getString(R.string.action_group))
        template = ""

        loadSchedule()
    }

    private fun handleRefresh () {
        loadSchedule()
    }

    private fun showSchedule (content: String) {
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
        if (scheduleId > 0){
            when (scheduleMode) {
                "Group" -> LoadContentTask().execute(dateFormat.format(scheduleCalendar.time), "http://www.ishnk.ru/schedule/group?group=" + scheduleId.toString())
                "Teacher" -> LoadContentTask().execute(dateFormat.format(scheduleCalendar.time), "http://www.ishnk.ru/schedule/teacher?teacher=" + scheduleId.toString())
                "Call" -> LoadContentTask().execute(dateFormat.format(scheduleCalendar.time), "http://www.ishnk.ru/schedule/calls?type=all")
            }

            textView.text = scheduleTitle

            with (sharedPref.edit()) {
                putString("lastMode", scheduleMode)
                putString("lastTitle", scheduleTitle)
                putInt("lastId", scheduleId)
                commit()
            }
        } else {
            setGroup(fabGroup)
        }
    }

    private fun getScheduleCalendar(): Calendar {
        val now: Calendar = Calendar.getInstance()

        val lastDay = sharedPref.getString("lastDay", "0000-00-00")
        val last: Calendar = Calendar.getInstance()
        last.time = dateFormat.parse(lastDay)

        val today: Calendar = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)

        val endOfTheDay: Calendar = Calendar.getInstance()
        endOfTheDay.set(Calendar.HOUR_OF_DAY, 16)
        endOfTheDay.set(Calendar.MINUTE, 15)
        endOfTheDay.set(Calendar.SECOND, 0)
        endOfTheDay.set(Calendar.MILLISECOND, 0)

        val tomorrow: Calendar = Calendar.getInstance()
        tomorrow.add(Calendar.DATE, 1)
        tomorrow.set(Calendar.HOUR_OF_DAY, 0)
        tomorrow.set(Calendar.MINUTE, 0)
        tomorrow.set(Calendar.SECOND, 0)
        tomorrow.set(Calendar.MILLISECOND, 0)

        return when {
            last.after(endOfTheDay) -> last
            now.after(endOfTheDay) -> tomorrow
            else -> today
        }
    }

    public fun setDate (v: View) {
        var datePicker = DatePickerDialog(v.context, datePickerCallBack, scheduleCalendar.get(Calendar.YEAR), scheduleCalendar.get(Calendar.MONTH), scheduleCalendar.get(Calendar.DAY_OF_MONTH))

        datePicker.show()
        fabMenu.close(true)
    }

    public fun setTeacher (v: View) {
        dialogTitle = getString(R.string.select_teacher)
        scheduleMode = "Teacher"

        LoadItemsTask().execute("http://www.ishnk.ru/teachers")

        fabMenu.close(true)
    }

    public fun setGroup (v: View) {
        dialogTitle = getString(R.string.select_group)
        scheduleMode = "Group"

        LoadItemsTask().execute("http://www.ishnk.ru/groups")

        fabMenu.close(true)
    }

    public fun loadCallSchedule (v: View) {
        scheduleMode = "Call"
        scheduleTitle = getString(R.string.action_call)

        loadSchedule()

        fabMenu.close(true)
    }

    private var datePickerCallBack: OnDateSetListener = OnDateSetListener { _, year, month, dayOfMonth ->
        scheduleCalendar.set(Calendar.YEAR, year)
        scheduleCalendar.set(Calendar.MONTH, month)
        scheduleCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

        with (sharedPref.edit()) {
            putString("lastDay", dateFormat.format(scheduleCalendar.time))
            commit()
        }

        loadSchedule()
    }

    private inner class LoadContentTask : AsyncTask<String, Void, String>() {
        override fun doInBackground(vararg params: String): String {
            CookieHandler.setDefault(CookieManager(null, CookiePolicy.ACCEPT_ALL))

            loadContent("http://www.ishnk.ru/save?dateSched=" + params[0] + "&academicYear=" + params[0])

            return loadContent(params[1])
        }

        override fun onPreExecute() {
            swipeRefreshLayout.isRefreshing = true
        }

        override fun onPostExecute(result: String) {
            this@MainActivity.showSchedule(result)
            println(5)

            swipeRefreshLayout.isRefreshing = false
        }

        private fun readStream(`is`: InputStream): String {
            val sb = StringBuilder()
            val r = BufferedReader(InputStreamReader(`is`), 1000)
            var line = r.readLine()
            while (line != null) {
                sb.append(line)
                line = r.readLine()
            }
            `is`.close()
            return sb.toString()
        }

        private fun loadContent(url: String): String {
            val urlConnection = URL(url).openConnection() as HttpURLConnection
            try {
                urlConnection.setRequestProperty("X-Requested-With", "XMLHttpRequest")
                return readStream(BufferedInputStream(urlConnection.inputStream))
            } finally {
                urlConnection.disconnect()
            }
        }
    }

    private inner class LoadItemsTask : AsyncTask<String, Void, MutableMap<String, Int>>() {
        override fun doInBackground(vararg params: String): MutableMap<String, Int> {
            val doc = Jsoup.connect(params[0]).header("X-Requested-With", "XMLHttpRequest").get()

            val mapItems = mutableMapOf<String, Int>()

            val tdElementIterator = doc.select("td").listIterator()

            while (tdElementIterator.hasNext()) {
                val element = tdElementIterator.next()

                if (element.attr("data-id") != "") {
                    mapItems.put(element.text(), element.attr("data-id").toInt())
                }
            }

            return mapItems
        }

        override fun onPreExecute() {
            swipeRefreshLayout.isRefreshing = true
        }

        override fun onPostExecute(result: MutableMap<String, Int>) {
            val dialogBuilder = AlertDialog.Builder(this@MainActivity)
            val sorted = result.toSortedMap().keys.toTypedArray()

            dialogBuilder.setTitle(dialogTitle)
                    .setCancelable(true)
                    .setItems(sorted, DialogInterface.OnClickListener { _, which ->
                        scheduleId = result.get(sorted[which])!!.toInt()
                        scheduleTitle = sorted[which]

                        loadSchedule()
                    })
                    .create()
                    .show()

            swipeRefreshLayout.isRefreshing = false
        }
    }
}
