package az.catdir.app

import android.graphics.Bitmap
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Demo rejimdə bütün axın: müştəri sifariş verir → admin qəbul edir → kuryer çatdırır → müştəri görür. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DemoFlowUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) {
        val dir = System.getenv("SHOT_DIR") ?: return
        rule.waitForIdle()
        lateinit var bmp: Bitmap
        rule.runOnUiThread {
            val v = rule.activity.window.decorView
            bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(android.graphics.Canvas(bmp))
        }
        File(dir).mkdirs()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun waitText(t: String) = rule.waitUntil(5_000) {
        rule.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun click(t: String) {
        rule.waitUntil(5_000) { rule.onAllNodesWithText(t, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        val n = rule.onAllNodesWithText(t, useUnmergedTree = true).onFirst()
        runCatching { n.performScrollTo() }
        n.performClick()
    }

    private fun back() {
        rule.waitUntil(5_000) { rule.onAllNodes(hasContentDescription("Geri")).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(hasContentDescription("Geri")).onFirst().performClick()
    }

    private fun addProduct(name: String) {
        val n = rule.onNode(hasContentDescription("Səbətə əlavə et") and hasAnySibling(hasText(name)))
        n.performScrollTo()
        n.performClick()
    }

    private fun plus(@Suppress("UNUSED_PARAMETER") name: String) {
        // mağazada yalnız bir məhsul səbətdədir, onun "Artır" düyməsi
        rule.onAllNodes(hasContentDescription("Artır")).onFirst().performClick()
    }

    @Test
    fun customerAdminCourierFlow() {
        waitText("Demo rejim")
        shot("01_login")

        // ---- Müştəri
        click("Müştəri kimi daxil ol")
        waitText("Bu gün nə sifariş edirik?")
        shot("02_stores")
        click("Təzə Market")
        waitText("Süd 1 l")
        addProduct("Süd 1 l")
        plus("Süd 1 l")
        addProduct("Yumurta (10 əd.)")
        waitText("Səbətə bax · 3 məhsul")
        waitText("7.70 ₼")
        shot("03_store")
        click("Səbətə bax · 3 məhsul")
        waitText("Ödəniş: nağd")
        waitText("9.20 ₼")  // 7.70 + 1.50 çatdırılma
        shot("04_cart")
        click("Sifariş ver")
        waitText("Sifariş göndərildi")
        shot("05_order_customer")

        // ---- Admin
        back()
        click("Profil")
        click("Admin")
        waitText("Bu gün sifariş")
        waitText("Yeni (1)")
        shot("06_admin_orders")
        click("Qəbul et")
        click("Hazırlanır (1)")
        click("Hazırdır")
        waitText("Hazırdır (1)")

        // ---- Kuryer
        click("Profil")
        click("Kuryer")
        waitText("Kuryer paneli")
        waitText("Mövcud (1)")
        shot("07_courier_available")
        click("Götür")
        waitText("Mənim (1)")
        click("Təzə Market")
        waitText("Mağazadan götürdüm")
        shot("08_courier_order")
        click("Mağazadan götürdüm")
        waitText("Çatdırdım (9.20 ₼ aldım)")
        click("Çatdırdım (9.20 ₼ aldım)")
        waitText("Çatdırıldı")
        back()
        waitText("Bu gün çatdırıldı")

        // ---- Müştəri nəticəni görür
        click("Profil")
        click("Müştəri")
        click("Sifarişlər")
        waitText("Keçmiş")
        click("Təzə Market")
        waitText("Elvin Həsənov")
        shot("09_customer_delivered")
    }
}
