package com.gympad.data

/** A device preference, so it lives outside the database and a backup import can't replace it. */
interface LanguageStore {
    fun load(): String?
    fun save(code: String)
}
