package com.trucaller.backend.service

import com.trucaller.backend.data.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.bson.Document
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.Date

object SearchLogger {

    private val logger = LoggerFactory.getLogger(SearchLogger::class.java)

    private data class Entry(
        val number: String,
        val searchedBy: String,
        val cacheHit: Boolean,
        val lookupMs: Long,
        val found: Boolean
    )

    private val queue = Channel<Entry>(capacity = Channel.BUFFERED)

    fun start(scope: CoroutineScope) {
        scope.launch {
            for (entry in queue) {
                runCatching {
                    Collections.searches.insertOne(
                        Document()
                            .append("_id", ObjectId().toString())
                            .append("number", entry.number)
                            .append("searchedBy", entry.searchedBy)
                            .append("cacheHit", entry.cacheHit)
                            .append("lookupMs", entry.lookupMs)
                            .append("found", entry.found)
                            .append("createdAt", Date.from(Instant.now()))
                    )
                }.onFailure { logger.warn("Search log write failed: ${it.message}") }
            }
        }
    }

    fun log(number: String, searchedBy: String, cacheHit: Boolean, lookupMs: Long, found: Boolean) {
        queue.trySend(Entry(number, searchedBy, cacheHit, lookupMs, found))
    }
}
