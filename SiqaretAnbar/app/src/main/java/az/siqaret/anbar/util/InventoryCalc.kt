package az.siqaret.anbar.util

import java.math.BigDecimal
import java.math.RoundingMode

/** Bütün hesablama qaydaları bir yerdə. */
object InventoryCalc {
    const val BOXES_PER_BLOCK = 10

    fun round2(v: Double): Double = BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).toDouble()

    /** Blok sayı × 10 = ümumi paçka sayı */
    fun totalBoxes(blocks: Int): Long = blocks.toLong() * BOXES_PER_BLOCK

    /** Blok sayı × bir blokun qiyməti = ümumi məbləğ */
    fun totalAmount(blocks: Int, blockPrice: Double): Double =
        BigDecimal.valueOf(blockPrice).multiply(BigDecimal.valueOf(blocks.toLong()))
            .setScale(2, RoundingMode.HALF_UP).toDouble()

    fun blockPriceFromBox(boxPrice: Double): Double = round2(boxPrice * BOXES_PER_BLOCK)
    fun boxPriceFromBlock(blockPrice: Double): Double = round2(blockPrice / BOXES_PER_BLOCK)
}
