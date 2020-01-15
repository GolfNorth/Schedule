package ru.golfnorth.schedule

import org.jsoup.nodes.Document

interface Observer {
    fun update(doc: Document)
}