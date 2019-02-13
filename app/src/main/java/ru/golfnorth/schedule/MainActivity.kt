package ru.golfnorth.schedule

import android.os.Bundle
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.webkit.WebView

import kotlinx.android.synthetic.main.activity_main.*

import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

import org.json.JSONObject
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.DefaultHttpClient
import android.os.AsyncTask
import java.net.URLEncoder
import java.io.InputStream
import android.app.DatePickerDialog.OnDateSetListener
import android.app.DatePickerDialog
import android.content.Context
import android.content.SharedPreferences
import android.view.View


class MainActivity : AppCompatActivity() {
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var webView: WebView
    private lateinit var scheduleCalendar: Calendar
    private lateinit var template: String
    private lateinit var sharedPref: SharedPreferences

    private var teacherId: Int = 0 // ID учителя
    private var tokenId: String = "ХХХХХХХХХХХХХХХХХХХХХХ" // Токен PhantomJSCloud
    private val dateFormat: DateFormat = SimpleDateFormat("yyyy-MM-dd")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        sharedPref = this?.getPreferences(Context.MODE_PRIVATE) ?: return

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
        val onlyDate: String = dateFormat.format(calendar.time)

        val url: String = "http://phantomjscloud.com/api/browser/v2/$tokenId/?request=" +
                URLEncoder.encode("{url:\"http://www.ishnk.ru/staffs/schedule\",renderType:\"script\",requestSettings:{ignoreImages:true},scripts:{domReady:[\"\$.get('/save',{dateSched:'$onlyDate',academicYear:'$onlyDate%'},function(){\$.get('/schedule/teacher',{teacher:$teacherId},function(result){window._pjscMeta.scriptOutput={Result:result}})})\"]},outputAsJson:false}","UTF-8")

        LoadJsonTask().execute(url)
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

    private inner class LoadJsonTask : AsyncTask<String, Void, String>() {
        override fun doInBackground(vararg urls: String): String {
            val httpClient = DefaultHttpClient()
            val httpGet = HttpGet(urls[0])
            val response = httpClient.execute(httpGet)
            val entity = response.entity
            val stream = entity.content
            val reader = BufferedReader(InputStreamReader(stream, "utf-8"), 8)

            val json: String = reader.readLine()

            stream.close()

            val mainObject = JSONObject(json)
            return mainObject.getString("Result")
        }

        override fun onPreExecute() {
            swipeRefreshLayout.isRefreshing = true
        }

        override fun onPostExecute(result: String) {
            this@MainActivity.showSchedule(result)

            swipeRefreshLayout.isRefreshing = false
        }
    }
}
