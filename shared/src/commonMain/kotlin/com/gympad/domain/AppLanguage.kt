package com.gympad.domain

enum class AppLanguage(val code: String, val nativeName: String) {
    ENGLISH("en", "English"),
    POLISH("pl", "Polski");

    companion object {
        /** A choice the user made always wins; until then the app follows the device language. */
        fun resolve(storedCode: String?, systemCode: String): AppLanguage =
            entries.firstOrNull { it.code == storedCode }
                ?: entries.firstOrNull { it.code == systemCode }
                ?: ENGLISH
    }
}
