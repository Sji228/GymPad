package com.gympad.data

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import com.gympad.domain.FoodItem
import com.gympad.domain.Macros
import com.gympad.domain.barcodeVariants
import com.gympad.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

private const val CATALOG_RESOURCE = "files/food_catalog.db"
private const val VERSION_RESOURCE = "files/food_catalog.version"

/**
 * The read-only product database shipped with the app (tools/catalog builds it). Resources can't
 * be opened by SQLite in place, so the first use copies it into [directory], once per version.
 */
class FoodCatalog(private val directory: File) {
    private val lock = Mutex()
    private var connection: SQLiteConnection? = null

    private suspend fun <T> query(block: (SQLiteConnection) -> T): T = withContext(Dispatchers.IO) {
        lock.withLock { block(connection ?: open().also { connection = it }) }
    }

    private suspend fun open(): SQLiteConnection {
        val version = Res.readBytes(VERSION_RESOURCE).decodeToString().trim()
        val file = File(directory, "food_catalog-$version.db")
        if (!file.exists()) {
            directory.mkdirs()
            directory.listFiles { f -> f.name.startsWith("food_catalog-") }?.forEach(File::delete)
            val partial = File(directory, "${file.name}.tmp")
            partial.writeBytes(Res.readBytes(CATALOG_RESOURCE))
            partial.renameTo(file)
        }
        return BundledSQLiteDriver().open(file.absolutePath, SQLITE_OPEN_READONLY)
    }

    /**
     * Every word must appear somewhere in the name or brand. Names with a word starting like the
     * query come first, then basic foods and popular products, then shorter names: "egg" finds
     * "Egg" before "Egg yolk".
     */
    suspend fun search(words: List<String>, limit: Int = 40): List<FoodItem> {
        if (words.isEmpty()) return emptyList()
        val conditions = words.joinToString(" AND ") { "search LIKE ?" }
        val sql = "SELECT $COLUMNS FROM food WHERE $conditions " +
            "ORDER BY (' ' || search LIKE ?) DESC, rank DESC, length(search) LIMIT $limit"
        return query { db ->
            db.prepare(sql).use { statement ->
                words.forEachIndexed { i, word -> statement.bindText(i + 1, "%$word%") }
                statement.bindText(words.size + 1, "% ${words.first()}%")
                buildList { while (statement.step()) add(statement.toItem()) }
            }
        }
    }

    suspend fun findBarcode(code: String): FoodItem? {
        val variants = barcodeVariants(code)
        if (variants.isEmpty()) return null
        val placeholders = variants.joinToString(",") { "?" }
        return query { db ->
            db.prepare("SELECT $COLUMNS FROM food WHERE code IN ($placeholders) LIMIT 1").use { statement ->
                variants.forEachIndexed { i, variant -> statement.bindText(i + 1, variant) }
                if (statement.step()) statement.toItem() else null
            }
        }
    }

    fun close() {
        connection?.close()
        connection = null
    }

    private companion object {
        const val COLUMNS = "code, name, name_pl, brand, kcal, protein, carbs, fat, portion_g, package_g"

        fun SQLiteStatement.textOrNull(i: Int) = if (isNull(i)) null else getText(i)
        fun SQLiteStatement.doubleOrNull(i: Int) = if (isNull(i)) null else getDouble(i)

        fun SQLiteStatement.toItem(): FoodItem {
            val code = getText(0)
            return FoodItem(
                foodId = null,
                catalogCode = code,
                name = getText(1),
                namePl = textOrNull(2),
                brand = textOrNull(3),
                barcode = code.takeIf { it.all(Char::isDigit) },
                per100 = Macros(getDouble(4), getDouble(5), getDouble(6), getDouble(7)),
                portionGrams = doubleOrNull(8),
                packageGrams = doubleOrNull(9),
            )
        }
    }
}
