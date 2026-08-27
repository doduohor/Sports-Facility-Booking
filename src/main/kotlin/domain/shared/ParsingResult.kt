package com.doduohor.domain.shared

sealed interface ParsingResult<out T> {
    data class Success<T>(val value: T): ParsingResult<T>
    data class Error(
        val field: String,
        val value: String,
        val expected: List<String>
    ): ParsingResult<Nothing>
}