package dev.opentrack.app.data.export

import java.io.Reader
import java.io.Writer

object Csv {
    /** Prefixes user-authored text that spreadsheet apps could otherwise interpret as a formula. */
    fun spreadsheetSafeText(value: String?): String? = value?.let {
        val firstMeaningful = it.dropWhile { char -> char == ' ' || char == '\t' || char == '\r' }.firstOrNull()
        if (firstMeaningful in setOf('=', '+', '-', '@')) "'$it" else it
    }

    fun writeRow(writer: Writer, cells: Iterable<String?>) {
        cells.forEachIndexed { index, cell ->
            if (index > 0) writer.append(',')
            val value = cell.orEmpty()
            if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
                writer.append('"').append(value.replace("\"", "\"\"")).append('"')
            } else {
                writer.append(value)
            }
        }
        writer.append('\n')
    }

    fun parse(reader: Reader, maxRows: Int = 1_000_000, maxCellChars: Int = 1_000_000): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var pendingQuote = false

        while (true) {
            val code = reader.read()
            if (code == -1) break
            val char = code.toChar()
            if (quoted) {
                if (pendingQuote) {
                    if (char == '"') {
                        cell.append('"')
                        pendingQuote = false
                    } else {
                        quoted = false
                        pendingQuote = false
                        when (char) {
                            ',' -> finishCell(row, cell)
                            '\n' -> finishRow(rows, row, cell, maxRows).also { row = mutableListOf() }
                            '\r' -> Unit
                            else -> throw IllegalArgumentException("Unexpected character after closing CSV quote")
                        }
                    }
                } else if (char == '"') {
                    pendingQuote = true
                } else {
                    cell.append(char)
                }
            } else {
                when (char) {
                    '"' -> if (cell.isEmpty()) quoted = true else throw IllegalArgumentException("Quote inside unquoted CSV cell")
                    ',' -> finishCell(row, cell)
                    '\n' -> finishRow(rows, row, cell, maxRows).also { row = mutableListOf() }
                    '\r' -> Unit
                    else -> cell.append(char)
                }
            }
            require(cell.length <= maxCellChars) { "CSV cell is too large" }
        }
        require(!quoted || pendingQuote) { "Unclosed CSV quote" }
        if (cell.isNotEmpty() || row.isNotEmpty()) finishRow(rows, row, cell, maxRows)
        return rows
    }

    private fun finishCell(row: MutableList<String>, cell: StringBuilder) {
        row += cell.toString()
        cell.setLength(0)
    }

    private fun finishRow(
        rows: MutableList<List<String>>,
        row: MutableList<String>,
        cell: StringBuilder,
        maxRows: Int,
    ) {
        finishCell(row, cell)
        rows += row
        require(rows.size <= maxRows) { "CSV has too many rows" }
    }
}
