package com.example.util

object CustomerNumberExtractor {

    /**
     * Extracts a customer number (typically ~6 digits, often starting with 9 or 7,
     * or leading numbers at the very front of the text) from one or more candidate strings.
     * Returns ONLY the pure digit sequence (e.g. "912345", "765432"), without names,
     * spaces, prefixes, or phone numbers.
     */
    fun extractCustomerNumber(vararg candidates: String?): String? {
        for (raw in candidates) {
            val extracted = extractFromSingleText(raw)
            if (!extracted.isNullOrBlank()) {
                return extracted
            }
        }
        return null
    }

    private fun extractFromSingleText(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val text = raw.trim()

        // 1. Explicit Prefix Match: e.g. "Kd. 912345", "Kd-Nr: 765432", "Kundennummer: 987654", "KdNr. 912345"
        val prefixRegex = Regex("""(?i)(?:kd(?:\.|-|\s*nr\.?)?|kunde(?:nnummer)?|kdnr|vertragsnummer|kundennr\.?|kunden-nr\.?|nr\.?)\s*[:#\-]?\s*([79]\d{4,7})\b""")
        val prefixMatch = prefixRegex.find(text)
        if (prefixMatch != null) {
            return prefixMatch.groupValues[1]
        }
        
        val anyPrefixRegex = Regex("""(?i)(?:kd(?:\.|-|\s*nr\.?)?|kunde(?:nnummer)?|kdnr|vertragsnummer|kundennr\.?|kunden-nr\.?)\s*[:#\-]?\s*(\d{5,8})\b""")
        val anyPrefixMatch = anyPrefixRegex.find(text)
        if (anyPrefixMatch != null) {
            return anyPrefixMatch.groupValues[1]
        }

        // 2. Leading number at the very beginning of the text:
        // "Diese Zahlen sind meistens immer ganz vorne ... ungefähr 6 Stellen ... beginnt oft mit 9 oder mit 7"
        // e.g. "912345 Max Mustermann", "765432 017612345678", "912345\t0151...", "789123 - Firma"
        val leading97Regex = Regex("""^\s*([79]\d{4,7})(?:[^\d]|$)""")
        val leading97Match = leading97Regex.find(text)
        if (leading97Match != null) {
            return leading97Match.groupValues[1]
        }

        val leadingAny6Regex = Regex("""^\s*([1-9]\d{4,7})(?:[^\d]|$)""")
        val leadingAny6Match = leadingAny6Regex.find(text)
        if (leadingAny6Match != null) {
            return leadingAny6Match.groupValues[1]
        }

        // 3. Exact 6-digit number starting with 9 or 7 anywhere in the text as a standalone word/token:
        // e.g. "Max Mustermann 912345 0176...", "Müller (712345)"
        val standalone6With97Regex = Regex("""\b([79]\d{5})\b""")
        val standalone6With97Match = standalone6With97Regex.find(text)
        if (standalone6With97Match != null) {
            return standalone6With97Match.groupValues[1]
        }

        // 4. 5 to 8 digit number starting with 9 or 7 anywhere in the text:
        val standalone58With97Regex = Regex("""\b([79]\d{4,7})\b""")
        val standalone58With97Match = standalone58With97Regex.find(text)
        if (standalone58With97Match != null) {
            return standalone58With97Match.groupValues[1]
        }

        // 5. Standalone exact 6-digit number anywhere (not starting with 0):
        val standalone6Regex = Regex("""\b([1-9]\d{5})\b""")
        val standalone6Match = standalone6Regex.find(text)
        if (standalone6Match != null) {
            return standalone6Match.groupValues[1]
        }

        // 6. If the entire text consists solely of digits (e.g. "912345", "765432", "123456")
        val digitsOnly = text.filter { it.isDigit() }
        if (digitsOnly.isNotEmpty()) {
            if (digitsOnly.length in 5..8 && !digitsOnly.startsWith("0")) {
                return digitsOnly
            }
            if ((digitsOnly.startsWith("9") || digitsOnly.startsWith("7")) && digitsOnly.length >= 6) {
                return digitsOnly.take(6)
            }
        }

        return null
    }

    /**
     * Checks if a string is a valid customer number (5 to 8 digits, typically 6 digits, non-zero starting).
     */
    fun isCustomerNumber(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        val trimmed = raw.trim()
        val digitsOnly = trimmed.filter { it.isDigit() }
        if (digitsOnly.length in 5..8 && !digitsOnly.startsWith("0")) {
            return trimmed.length == digitsOnly.length
        }
        return false
    }
}
