package ru.golfnorth.schedule

import org.jsoup.nodes.Document

interface Subject {
    fun registerObserver(o: Observer)
    fun removeObserver(o: Observer)
    fun notifyObserver(doc: Document)
}