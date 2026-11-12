package az.rafiqmarket.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import az.rafiqmarket.core.network.Urls
import az.rafiqmarket.core.network.dto.ProductDto
import az.rafiqmarket.core.ui.theme.PriceStyle
import az.rafiqmarket.core.ui.theme.RafiqColors
import az.rafiqmarket.core.util.money
import coil.compose.SubcomposeAsyncImage

@Composable
fun ProductImage(url: String?, modifier: Modifier = Modifier) {
    val resolved = Urls.resolve(url)
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (resolved == null) {
            Icon(Icons.Outlined.ShoppingBasket, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(40.dp))
        } else {
            SubcomposeAsyncImage(
                model = resolved, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) } },
                error = { Icon(Icons.Outlined.ShoppingBasket, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(40.dp)) },
            )
        }
    }
}

/**
 * Məhsul kartı: şəkil, ad, qiymət, ölçü vahidi, stok vəziyyəti, səbətə əlavə et / say dəyiş.
 */
@Composable
fun ProductCard(
    product: ProductDto,
    quantityInCart: Int,
    onAdd: () -> Unit,
    onMinus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier, shape = RoundedCornerShape(20.dp), elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)) {
        Box {
            ProductImage(product.imageUrl, Modifier.fillMaxWidth().aspectRatio(1.15f).clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)))
            Column(Modifier.padding(8.dp)) {
                val old = product.oldPrice?.toBigDecimalOrNull()
                val price = product.price.toBigDecimalOrNull()
                if (old != null && price != null && old > price) {
                    val pct = ((old - price) * 100.toBigDecimal() / old).toInt()
                    Badge(containerColor = RafiqColors.Red) { Text("-$pct%", Modifier.padding(2.dp)) }
                    Spacer(Modifier.height(4.dp))
                }
                if (product.isNew) Badge(containerColor = RafiqColors.Green600) { Text("Yeni", Modifier.padding(2.dp)) }
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(product.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.heightIn(min = 44.dp))
            Text("1 ${product.unit}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            StockLabel(product.stock, product.lowStockThreshold)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(money(product.price), style = PriceStyle, color = MaterialTheme.colorScheme.primary)
                    product.oldPrice?.let {
                        if ((it.toBigDecimalOrNull() ?: 0.toBigDecimal()) > (product.price.toBigDecimalOrNull() ?: 0.toBigDecimal()))
                            Text(money(it), style = MaterialTheme.typography.labelSmall, textDecoration = TextDecoration.LineThrough, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            AnimatedContent(quantityInCart > 0, label = "cart") { inCart ->
                if (inCart) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        QuantityStepper(quantityInCart, onMinus, onAdd, plusEnabled = quantityInCart < product.stock, compact = true)
                    }
                } else {
                    Button(
                        onClick = onAdd, enabled = product.stock > 0, modifier = Modifier.fillMaxWidth().height(36.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (product.stock > 0) "Səbətə əlavə et" else "Bitib", maxLines = 1, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun StockLabel(stock: Int, low: Int) {
    val (text, color) = when {
        stock <= 0 -> "Stokda yoxdur" to RafiqColors.Red
        stock <= low -> "Az qalıb: $stock" to RafiqColors.Orange
        else -> "Stokda var" to RafiqColors.Green600
    }
    Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
}

@Suppress("unused")
private val unusedColor = Color.Unspecified
