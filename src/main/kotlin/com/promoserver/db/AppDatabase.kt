package com.promoserver.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.sql.Connection
import java.sql.DriverManager

object AppDatabase {
    @Volatile
    var currentPath: String? = null
        private set

    fun connect(path: String): Database {
        java.io.File(path).absoluteFile.parentFile?.mkdirs()
        currentPath = path
        val database = Database.connect("jdbc:sqlite:$path?busy_timeout=5000", driver = "org.sqlite.JDBC")
        // Recomendado pela documentação do Exposed para SQLite.
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
        transaction(database) {
            SchemaUtils.create(OffersTable, PriceHistoryTable, CouponsTable, ClicksTable, RunsTable)
        }
        return database
    }

    /** Cópia consistente do banco (VACUUM INTO), feita com o servidor rodando. Serve para backup em hospedagens sem acesso ao disco. */
    fun backup(): ByteArray {
        val source = checkNotNull(currentPath) { "Banco não conectado" }
        val dir = Files.createTempDirectory("promo-backup")
        val target = dir.resolve("backup.db")
        try {
            DriverManager.getConnection("jdbc:sqlite:$source").use { connection ->
                connection.createStatement().use { it.execute("VACUUM INTO '${target.toString().replace("'", "''")}'") }
            }
            return Files.readAllBytes(target)
        } finally {
            Files.deleteIfExists(target)
            Files.deleteIfExists(dir)
        }
    }
}
