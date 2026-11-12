package az.catdir.app.model

/** Pul qəpiklə (1 ₼ = 100 qəpik) Long kimi saxlanılır — heç vaxt Double yox. */
object Money {
    fun format(q: Long): String {
        val neg = q < 0
        val abs = if (neg) -q else q
        val manat = (abs / 100).toString().reversed().chunked(3).joinToString(" ").reversed()
        val qepik = (abs % 100).toString().padStart(2, '0')
        return (if (neg) "−" else "") + "$manat.$qepik ₼"
    }

    /** "12" → 1200, "12,5" → 1250, "12.05" → 1205; 2 onluqdan çox və ya səhv mətn → null */
    fun parse(s: String): Long? {
        val t = s.trim().replace(" ", "").replace(',', '.')
        if (t.isEmpty()) return null
        val m = Regex("^(\\d{1,9})(?:\\.(\\d{0,2}))?$").matchEntire(t) ?: return null
        val manat = m.groupValues[1].toLong()
        val q = m.groupValues[2].padEnd(2, '0').ifEmpty { "00" }.toLong()
        return manat * 100 + q
    }

    /** Daxiletmə sahəsi üçün: yalnız rəqəm, bir ayırıcı, maks. 2 onluq. */
    fun sanitize(s: String): String {
        val sb = StringBuilder()
        var sep = false
        var dec = 0
        for (ch in s) when {
            ch.isDigit() -> {
                if (sep) { if (dec >= 2) continue; dec++ }
                if (sb.length < 12) sb.append(ch)
            }
            (ch == '.' || ch == ',') && !sep -> { sep = true; sb.append(if (sb.isEmpty()) "0." else ".") }
        }
        return sb.toString()
    }

    fun input(q: Long): String = if (q % 100 == 0L) (q / 100).toString() else "${q / 100}.${(q % 100).toString().padStart(2, '0')}"
}
