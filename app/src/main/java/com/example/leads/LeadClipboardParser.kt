package com.example.leads

data class LeadClipboardValue(
    val customerNumber: String? = null,
    val phone: String? = null,
    val email: String? = null
)

object LeadClipboardParser {
    fun parse(raw: String): LeadClipboardValue {
        val text = raw.trim()
        if (text.isBlank()) return LeadClipboardValue()
        val email = Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE)
            .find(text)?.value
        if (email != null) return LeadClipboardValue(email = email)
        val digits = text.filter(Char::isDigit)
        return when {
            digits.length == 6 -> LeadClipboardValue(customerNumber = digits)
            digits.length in 7..15 -> LeadClipboardValue(
                phone = if (text.trimStart().startsWith("+") && digits.startsWith("49")) "+$digits" else digits
            )
            else -> LeadClipboardValue()
        }
    }
}
