package com.promoserver.db

import com.promoserver.model.CollectionReport
import com.promoserver.model.RunDto
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

/** Histórico das coletas, para saber se cada loja está funcionando. */
class RunRepository {

    fun save(report: CollectionReport, startedAt: Long, durationMs: Long) {
        transaction {
            RunsTable.insert {
                it[collector] = report.collector
                it[RunsTable.startedAt] = startedAt
                it[RunsTable.durationMs] = durationMs
                it[fetched] = report.fetched
                it[accepted] = report.accepted
                it[inserted] = report.inserted
                it[updated] = report.updated
                it[expired] = report.expired
                it[error] = report.error?.take(MAX_ERROR)
            }
        }
    }

    fun recent(limit: Int = 50): List<RunDto> = transaction {
        RunsTable.selectAll().orderBy(RunsTable.startedAt to SortOrder.DESC).limit(limit).map {
            RunDto(
                collector = it[RunsTable.collector],
                startedAt = it[RunsTable.startedAt],
                durationMs = it[RunsTable.durationMs],
                fetched = it[RunsTable.fetched],
                accepted = it[RunsTable.accepted],
                inserted = it[RunsTable.inserted],
                updated = it[RunsTable.updated],
                expired = it[RunsTable.expired],
                error = it[RunsTable.error],
            )
        }
    }

    /** A execução mais recente de cada coletor. */
    fun latestPerCollector(): List<RunDto> = recent(LATEST_WINDOW).distinctBy { it.collector }.sortedBy { it.collector }

    fun deleteOlderThan(cutoff: Long): Int = transaction {
        val count = RunsTable.selectAll().where { RunsTable.startedAt less cutoff }.count().toInt()
        exec("DELETE FROM collection_runs WHERE started_at < $cutoff")
        count
    }

    private companion object {
        const val MAX_ERROR = 500
        const val LATEST_WINDOW = 200
    }
}
