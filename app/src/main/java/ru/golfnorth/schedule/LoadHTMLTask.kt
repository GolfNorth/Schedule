package ru.golfnorth.schedule

import android.os.AsyncTask
import org.jsoup.Connection
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class LoadHTMLTask : AsyncTask<String, Void, Document>(), Subject {
    private var observers = mutableListOf<Observer>()
    private var elcoSession: String = ""

    override fun doInBackground(vararg params: String): Document {
        if (params.count() > 1) {
            val dateRes = Jsoup.connect("http://www.ishnk.ru/save?dateSched=${params[1]}&academicYear=${params[1]}")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .cookie("elco_session", elcoSession)
                    .method(Connection.Method.GET)
                    .execute()

            elcoSession = dateRes.cookie("elco_session")
        }

        val res = Jsoup.connect(params[0])
                .header("X-Requested-With", "XMLHttpRequest")
                .cookie("elco_session", elcoSession)
                .method(Connection.Method.GET)
                .execute()

        elcoSession = res.cookie("elco_session")

        return res.parse()
    }

    override fun onPostExecute(result: Document) {
        notifyObserver(result)
    }

    override fun registerObserver(o: Observer) {
        observers.add(o)
    }

    override fun removeObserver(o: Observer) {
        observers.remove(o)
    }

    override fun notifyObserver(doc: Document) {
        for (o in observers) {
            o.update(doc)
        }
    }
}