package ru.golfnorth.schedule

import android.app.DatePickerDialog
import android.app.DatePickerDialog.OnDateSetListener
import android.content.Context
import android.content.SharedPreferences
import android.os.AsyncTask
import android.os.Bundle
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebView
import java.io.*
import java.net.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var webView: WebView
    private lateinit var scheduleCalendar: Calendar
    private lateinit var template: String
    private lateinit var sharedPref: SharedPreferences

    private val dateFormat: DateFormat = SimpleDateFormat("yyyy-MM-dd")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        //setSupportActionBar(toolbar)

        sharedPref = this.getPreferences(Context.MODE_PRIVATE) ?: return

        swipeRefreshLayout=findViewById(R.id.swipeRefresh)
        swipeRefreshLayout.setColorSchemeResources(R.color.blue, R.color.green, R.color.yellow, R.color.red)
        swipeRefreshLayout.setOnRefreshListener { handleRefresh() }

        webView = findViewById(R.id.webView)
        webView.settings.javaScriptEnabled = true

        scheduleCalendar = getScheduleCalendar()
        template = ""

        loadSchedule(scheduleCalendar)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun handleRefresh () {
        loadSchedule(scheduleCalendar)
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

    private fun loadSchedule(calendar: Calendar) {
        LoadContentTask().execute(dateFormat.format(calendar.time), 294.toString())
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

    fun setDate (v: View) {
        var datePicker = DatePickerDialog(v.context, datePickerCallBack, scheduleCalendar.get(Calendar.YEAR), scheduleCalendar.get(Calendar.MONTH), scheduleCalendar.get(Calendar.DAY_OF_MONTH))

        datePicker.show()
    }

    private var datePickerCallBack: OnDateSetListener = OnDateSetListener { _, year, month, dayOfMonth ->
        scheduleCalendar.set(Calendar.YEAR, year)
        scheduleCalendar.set(Calendar.MONTH, month)
        scheduleCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

        with (sharedPref.edit()) {
            putString("lastDay", dateFormat.format(scheduleCalendar.time))
            commit()
        }

        loadSchedule(scheduleCalendar)
    }

    private inner class LoadContentTask : AsyncTask<String, Void, String>() {
        override fun doInBackground(vararg params: String): String {
            CookieHandler.setDefault(CookieManager(null, CookiePolicy.ACCEPT_ALL))

            loadContent("http://www.ishnk.ru/save?dateSched=" + params[0] + "&academicYear=" + params[0])

            return loadContent("http://www.ishnk.ru/schedule/teacher?teacher=" + params[1])
        }

        override fun onPreExecute() {
            swipeRefreshLayout.isRefreshing = true
        }

        override fun onPostExecute(result: String) {
            this@MainActivity.showSchedule(result)

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
}
