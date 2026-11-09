package az.payreport.app.core.parser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentParserTest {

    private fun JsonObject.str(key: String): String? =
        this[key].let { if (it == null || it is JsonNull) null else it.jsonPrimitive.content }

    @Test
    fun sharedFixturesMatchBackend() {
        val text = javaClass.classLoader!!.getResource("parser-fixtures.json")!!.readText()
        val cases = Json.parseToJsonElement(text).jsonObject["cases"]!!.jsonArray
        var checked = 0
        for (c in cases) {
            val obj = c.jsonObject
            val name = obj.str("name")
            val source = PaymentParser.SourceType.valueOf(obj.str("source")!!)
            val got = PaymentParser.parse(obj.str("text")!!, source)
            val expected = obj["expected"]!!.jsonArray
            assertEquals("$name: say", expected.size, got.size)
            expected.forEachIndexed { i, e ->
                val exp = e.jsonObject
                val g = got[i]
                assertEquals("$name[$i].phone", exp.str("phone"), g.phone)
                val amt = exp["amountMinor"]
                assertEquals("$name[$i].amount", if (amt is JsonNull) null else amt!!.jsonPrimitive.long, g.amountMinor)
                assertEquals("$name[$i].txn", exp.str("txnId"), g.txnId)
                assertEquals("$name[$i].paidAt", exp.str("paidAt"), g.paidAt)
                assertEquals("$name[$i].confidence", exp["confidence"]!!.jsonPrimitive.int, g.confidence)
                assertEquals("$name[$i].status", exp.str("status"), g.status)
            }
            checked++
        }
        assertEquals(cases.size, checked)
    }

    @Test
    fun hundredTimesFiveAzn() {
        val text = List(100) { "055 777 66 55 — 5 AZN" }.joinToString("\n")
        val res = PaymentParser.parse(text)
        assertEquals(100, res.size)
        assertEquals(50_000L, res.sumOf { it.amountMinor ?: 0 })
    }

    @Test
    fun phoneNormalization() {
        assertEquals("+994557776655", PhoneUtils.normalize("055 777 66 55"))
        assertEquals("+994557776655", PhoneUtils.normalize("+994 (55) 777-66-55"))
        assertEquals("+994557776655", PhoneUtils.normalize("557776655"))
        assertNull(PhoneUtils.normalize("0337776655"))
        assertEquals("055 777 66 55", PhoneUtils.format("+994557776655"))
    }

    @Test
    fun amounts() {
        assertEquals(500L, PaymentParser.amountToMinor("5"))
        assertEquals(1250L, PaymentParser.amountToMinor("12,5"))
        assertEquals(125075L, PaymentParser.amountToMinor("1,250.75"))
        assertEquals(125050L, PaymentParser.amountToMinor("1 250,50"))
    }
}
