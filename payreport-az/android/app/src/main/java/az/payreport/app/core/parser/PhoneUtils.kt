package az.payreport.app.core.parser

object PhoneUtils {
    private val PREFIX_RE = Regex("^(10|12|50|51|55|60|70|77|99)\\d{7}$")

    /** İstənilən formatı +994XXXXXXXXX formatına çevirir, alınmasa null. */
    fun normalize(input: String?): String? {
        val digits = input.orEmpty().filter { it.isDigit() }
        val local = when {
            digits.length == 12 && digits.startsWith("994") -> digits.substring(3)
            digits.length == 10 && digits.startsWith("0") -> digits.substring(1)
            digits.length == 9 -> digits
            else -> return null
        }
        return if (PREFIX_RE.matches(local)) "+994$local" else null
    }

    /** +994557776655 -> 055 777 66 55 */
    fun format(phone: String?): String {
        if (phone == null) return "—"
        val m = Regex("^\\+994(\\d{2})(\\d{3})(\\d{2})(\\d{2})$").find(phone) ?: return phone
        val (a, b, c, d) = m.destructured
        return "0$a $b $c $d"
    }

    /** Qismən axtarış üçün rəqəmlər (aparıcı 0 olmadan). */
    fun searchDigits(input: String): String = input.filter { it.isDigit() }.removePrefix("994").removePrefix("0")
}
