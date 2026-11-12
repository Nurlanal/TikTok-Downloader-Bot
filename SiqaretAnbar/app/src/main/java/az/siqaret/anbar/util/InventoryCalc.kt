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

    /** Paçka qiyməti × 10 (paçka qiyməti 3 onluq rəqəmə qədər ola bilər, məs. 4.197 → 41.97). */
    fun blockPriceFromBox(boxPrice: Double): Double =
        BigDecimal.valueOf(boxPrice).multiply(BigDecimal.valueOf(BOXES_PER_BLOCK.toLong()))
            .setScale(2, RoundingMode.HALF_UP).toDouble()

    /**
     * Blok qiyməti ÷ 10. Qəpik itməsin deyə 2 yox, 3 onluq rəqəm saxlanılır:
     * 41.97 → 4.197 (4.20 deyil). Əks halda 4.20 × 10 = 42.00 olur və 10 blok 420 ₼ göstərir.
     */
    fun boxPriceFromBlock(blockPrice: Double): Double =
        BigDecimal.valueOf(blockPrice).divide(BigDecimal.valueOf(BOXES_PER_BLOCK.toLong()), 3, RoundingMode.HALF_UP)
            .toDouble()

    /** Bir neçə məbləğin dəqiq cəmi (double toplama xətası olmadan). */
    fun sum(values: Iterable<Double>): Double =
        values.fold(BigDecimal.ZERO) { acc, v -> acc.add(BigDecimal.valueOf(v)) }
            .setScale(2, RoundingMode.HALF_UP).toDouble()
}
