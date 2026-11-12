package az.siqaret.anbar

import android.graphics.Bitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Real tətbiqi (MainActivity + Room + ViewModel) işə salır, istifadəçi kimi
 * düymələrə basır və hesablamaların ekranda düzgün göründüyünü yoxlayır.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppFlowTest {
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
        rule.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun click(t: String) {
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText(t, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithText(t, useUnmergedTree = true).onFirst().performClick()
    }

    private fun field(label: String) = rule.onNode(hasSetTextAction() and hasText(label, substring = true))

    private fun addProduct(name: String, boxPrice: String?, blockPrice: String?, blocks: String) {
        click("Məhsul əlavə et")
        waitText("Yeni məhsul")
        field("Siqaretin adı").performTextInput(name)
        boxPrice?.let { field("Bir qutunun qiyməti").performTextInput(it) }
        blockPrice?.let { field("Bir blokun qiyməti").performTextInput(it) }
        rule.onNode(hasSetTextAction() and hasText("0")).performTextInput(blocks)
    }

    @Test
    fun fullFlow() {
        waitText("Hələ məhsul yoxdur")
        shot("01_home_empty")

        // 1) Qutu qiyməti 3.5 -> blok qiyməti avtomatik 35; 12 blok -> 120 qutu, 420 ₼
        addProduct("Marlboro Red", boxPrice = "3.5", blockPrice = null, blocks = "12")
        rule.onAllNodesWithText("35", substring = false).fetchSemanticsNodes() // blok qiyməti sahəsi
        waitText("12 blok × 35.00 ₼ = 420.00 ₼")
        waitText("12 blok × 10 = 120 qutu")
        shot("02_add_product_live_calc")
        click("Məhsulu əlavə et")
        waitText("Siqaret Anbarı")
        waitText("Marlboro Red")

        // 2) İkinci məhsul: yalnız blok qiyməti
        addProduct("Kent Nanotek", boxPrice = null, blockPrice = "30", blocks = "5")
        waitText("5 blok × 30.00 ₼ = 150.00 ₼")
        click("Məhsulu əlavə et")
        waitText("Siqaret Anbarı")
        waitText("Kent Nanotek")

        // Ana səhifə: 17 blok, 170 qutu, 570 ₼
        waitText("570.00 ₼")
        rule.onAllNodesWithText("17").onFirst().assertExists()
        rule.onAllNodesWithText("170").onFirst().assertExists()

        // 3) +1 blok (Kent — ada görə sıralamada birinci) -> 6 blok, 180 ₼, cəm 600 ₼
        rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("1 blok artır")).onFirst().performClick()
        waitText("600.00 ₼")
        waitText("180.00 ₼")

        // 4) Təkrar ad yoxlaması
        addProduct("marlboro red", boxPrice = "3", blockPrice = null, blocks = "1")
        click("Məhsulu əlavə et")
        waitText("artıq mövcuddur")
        rule.onNodeWithContentDescription("Geri").performClick()
        waitText("Siqaret Anbarı")

        // 5) Axtarış
        field("Ada görə axtar").performTextInput("kent")
        waitText("1 məhsul")
        shot("03_home_search")
        rule.onNodeWithContentDescription("Təmizlə").performClick()
        waitText("2 məhsul")
        shot("04_home")

        // 6) Tarixçə və Hesabat
        click("Tarixçə")
        waitText("Stok artırıldı")
        shot("05_history")
        click("Hesabat")
        waitText("PDF ixrac et")
        waitText("CƏMİ")
        shot("06_report")

        // 7) Qaranlıq rejim
        rule.onNodeWithContentDescription("Mövzu").performClick()
        click("Qaranlıq rejim")
        rule.waitForIdle()
        shot("07_report_dark")
        click("Ana səhifə")
        waitText("600.00 ₼")
        shot("08_home_dark")
    }
}
