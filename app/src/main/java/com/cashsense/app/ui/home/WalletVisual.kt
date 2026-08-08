package com.cashsense.app.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashsense.app.domain.DenominationStack
import com.cashsense.app.domain.DenominationType
import com.cashsense.app.domain.StackDelta
import com.cashsense.app.ui.theme.CreditGreen
import com.cashsense.app.ui.theme.DebitRed
import com.cashsense.app.ui.theme.denominationColor
import com.cashsense.app.ui.theme.denominationTextColor
import kotlinx.coroutines.delay

@Composable
fun WalletGrid(
    stacks: List<DenominationStack>,
    deltas: List<StackDelta>,
    modifier: Modifier = Modifier
) {
    val deltaByValue = remember(deltas) { deltas.associateBy { it.denomination.value } }
    if (stacks.isEmpty()) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "Your wallet is empty",
                modifier = Modifier.padding(24.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        return
    }

    // A plain (non-lazy) grid: there are at most 9 denominations, so laziness would only
    // buy us a crash from nesting a scrollable grid inside the outer LazyColumn.
    // Two columns (not three) so each note is big enough to hold its detail without crowding.
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        stacks.chunked(2).forEach { rowStacks ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                rowStacks.forEach { stack ->
                    key(stack.denomination.value) {
                        DenominationCard(
                            stack = stack,
                            delta = deltaByValue[stack.denomination.value],
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                repeat(2 - rowStacks.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun darken(color: Color, amount: Float): Color = lerp(color, Color.Black, amount)
private fun lighten(color: Color, amount: Float): Color = lerp(color, Color.White, amount)

/**
 * The note/coin art is a fixed-layout illustration, not reading text — if its type scaled with
 * the user's system font-size setting the way normal UI text should, it would outgrow the card
 * and get clipped. Rendering it under a [Density] with `fontScale` pinned to 1 keeps every plain
 * `.sp` size inside visually stable regardless of that setting.
 */
@Composable
private fun NonScalingDensity(content: @Composable () -> Unit) {
    val base = LocalDensity.current
    val fixed = remember(base.density) { Density(density = base.density, fontScale = 1f) }
    CompositionLocalProvider(LocalDensity provides fixed, content = content)
}

/** How many extra "notes" to imply behind the front one — a visual stand-in for stack thickness. */
private fun stackLayerCount(count: Int): Int = when {
    count <= 1 -> 0
    count <= 5 -> 1
    count <= 15 -> 2
    else -> 3
}

/**
 * Real RBI notes physically grow with denomination — a ₹500 note is noticeably longer than a
 * ₹10 one. Mirroring the actual width:height ratios (rather than one fixed shape for every
 * value) is a purely dimensional cue, not protected artwork, and it's an authenticity signal
 * most people recognise instinctively even if they couldn't name it.
 */
private fun noteAspectRatio(value: Int): Float = when (value) {
    500 -> 2.27f
    200 -> 2.25f
    100 -> 2.15f
    50 -> 2.05f
    20 -> 2.05f
    else -> 1.95f // ₹10
}

/** More lines for higher-value notes — evokes the tactile bleed-mark convention real notes use
 *  for the visually impaired, without claiming to match RBI's actual counts. */
private fun bleedLineCount(value: Int): Int = when (value) {
    500 -> 6
    200 -> 5
    100 -> 4
    50 -> 3
    20 -> 2
    else -> 1 // ₹10
}

@Composable
fun DenominationCard(
    stack: DenominationStack,
    delta: StackDelta?,
    modifier: Modifier = Modifier
) {
    val isCoin = stack.denomination.type == DenominationType.COIN
    val value = stack.denomination.value
    val baseColor = denominationColor(value)
    val textColor = denominationTextColor(value)
    val layers = stackLayerCount(stack.count)

    var pulsing by remember { mutableStateOf(false) }
    var pulseIsCredit by remember { mutableStateOf(true) }

    LaunchedEffect(stack.count) {
        if (delta != null && delta.change != 0) {
            pulseIsCredit = delta.change > 0
            pulsing = true
            delay(450)
            pulsing = false
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (pulsing) 1.12f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "stackScale"
    )
    val ringColor by animateColorAsState(
        targetValue = when {
            !pulsing -> Color.Transparent
            pulseIsCredit -> CreditGreen
            else -> DebitRed
        },
        label = "stackRing"
    )

    Column(
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NonScalingDensity {
            Box(contentAlignment = Alignment.TopStart) {
                if (isCoin) {
                    CoinVisual(value = value, baseColor = baseColor, textColor = textColor, ringColor = ringColor, layers = layers)
                } else {
                    NoteVisual(value = value, baseColor = baseColor, textColor = textColor, ringColor = ringColor, layers = layers)
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-8).dp)
                ) {
                    AnimatedContent(
                        targetState = stack.count,
                        transitionSpec = {
                            val direction = if (targetState > initialState) 1 else -1
                            (slideInVertically { h -> direction * h } + fadeIn())
                                .togetherWith(slideOutVertically { h -> -direction * h } + fadeOut())
                        },
                        label = "countBadge"
                    ) { count ->
                        Text(
                            text = "×$count",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteVisual(
    value: Int,
    baseColor: Color,
    textColor: Color,
    ringColor: Color,
    layers: Int
) {
    val shape = RoundedCornerShape(8.dp)
    val aspect = noteAspectRatio(value)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
    ) {
        // Back layers: offset, darkened copies standing in for the notes underneath.
        for (i in layers downTo 1) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect)
                    .offset(x = (i * 3).dp, y = (i * 3).dp)
                    .clip(shape)
                    .background(darken(baseColor, 0.1f * i))
            )
        }

        // Front note. Content is a real Column (top row / weighted middle / bottom row) rather
        // than free-floating Box alignment, so the big numeral can never collide with the
        // corner text above it — each row only ever occupies the space it's given.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspect)
                .shadow(elevation = 3.dp, shape = shape)
                .border(1.4.dp, ringColor, shape)
                .clip(shape)
                .background(
                    Brush.linearGradient(listOf(lighten(baseColor, 0.16f), baseColor, darken(baseColor, 0.14f)))
                )
                .drawBehind {
                    drawGuillocheLines(textColor)
                    drawSecurityThread()
                    drawBleedLines(textColor, bleedLineCount(value))
                    drawSheen()
                }
                .border(0.6.dp, Color.White.copy(alpha = 0.35f), shape)
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "₹$value",
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp
                )
                IdentificationMark(value = value, color = textColor, modifier = Modifier.size(9.dp))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                PortraitSilhouette(
                    color = textColor,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(width = 20.dp, height = 30.dp)
                )
                Text(
                    text = "$value",
                    color = textColor,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Serif,
                    fontSize = 19.sp,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "भारतीय रिज़र्व बैंक",
                        color = textColor.copy(alpha = 0.85f),
                        fontSize = 5.sp,
                        lineHeight = 6.sp
                    )
                    Text(
                        text = "RESERVE BANK OF INDIA",
                        color = textColor.copy(alpha = 0.85f),
                        fontSize = 5.sp,
                        lineHeight = 6.sp
                    )
                }
                PillarEmblem(color = textColor, modifier = Modifier.size(width = 10.dp, height = 13.dp))
            }
        }
    }
}

@Composable
private fun CoinVisual(
    value: Int,
    baseColor: Color,
    textColor: Color,
    ringColor: Color,
    layers: Int
) {
    val coinSize = when (value) {
        5 -> 68.dp
        2 -> 62.dp
        else -> 56.dp
    }

    Box(modifier = Modifier.size(coinSize + 6.dp)) {
        for (i in layers downTo 1) {
            Box(
                modifier = Modifier
                    .size(coinSize)
                    .offset(x = (i * 2).dp, y = (i * 2).dp)
                    .clip(CircleShape)
                    .background(darken(baseColor, 0.15f * i))
            )
        }

        Box(
            modifier = Modifier
                .size(coinSize)
                .shadow(elevation = 3.dp, shape = CircleShape)
                .border(1.4.dp, ringColor, CircleShape)
                .padding(2.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(listOf(lighten(baseColor, 0.35f), baseColor, darken(baseColor, 0.2f)))
                )
                .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                .padding(4.dp)
                .border(0.6.dp, darken(baseColor, 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("₹$value", color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

/**
 * A generic side-profile bust — the universal "portrait side of currency" convention used on
 * coins and banknotes worldwide. Deliberately featureless (no glasses, no specific likeness) so
 * it reads as "a portrait" without depicting any real person's photograph or engraving.
 */
@Composable
private fun PortraitSilhouette(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.95f, h * 1.0f)
            lineTo(w * 0.95f, h * 0.74f)
            cubicTo(w * 0.95f, h * 0.62f, w * 0.80f, h * 0.55f, w * 0.68f, h * 0.50f)
            cubicTo(w * 0.63f, h * 0.47f, w * 0.60f, h * 0.44f, w * 0.60f, h * 0.40f)
            cubicTo(w * 0.68f, h * 0.36f, w * 0.72f, h * 0.28f, w * 0.66f, h * 0.22f)
            cubicTo(w * 0.70f, h * 0.18f, w * 0.68f, h * 0.13f, w * 0.62f, h * 0.09f)
            cubicTo(w * 0.54f, h * 0.02f, w * 0.34f, h * 0.03f, w * 0.24f, h * 0.14f)
            cubicTo(w * 0.16f, h * 0.22f, w * 0.16f, h * 0.32f, w * 0.22f, h * 0.40f)
            lineTo(w * 0.20f, h * 0.58f)
            cubicTo(w * 0.15f, h * 0.63f, w * 0.05f, h * 0.68f, w * 0.05f, h * 0.80f)
            lineTo(w * 0.05f, h * 1.0f)
            close()
        }
        drawPath(path, color = color.copy(alpha = 0.62f))
    }
}

/**
 * A generic pillar-on-a-base silhouette — evokes "official emblem" the way currency worldwide
 * uses column/crest motifs, without reproducing the Ashoka Pillar's actual sculpted lions or
 * wheel artwork.
 */
@Composable
private fun PillarEmblem(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val tone = color.copy(alpha = 0.6f)
        drawOval(color = tone, topLeft = Offset(w * 0.15f, h * 0.08f), size = Size(w * 0.7f, h * 0.28f))
        drawRect(color = tone, topLeft = Offset(w * 0.4f, h * 0.32f), size = Size(w * 0.2f, h * 0.5f))
        drawLine(
            color = tone,
            start = Offset(w * 0.08f, h * 0.92f),
            end = Offset(w * 0.92f, h * 0.92f),
            strokeWidth = h * 0.1f
        )
    }
}

/**
 * A small shape unique to each denomination — inspired by how real notes give each value a
 * distinct tactile mark, without copying the actual official shapes.
 */
@Composable
private fun IdentificationMark(value: Int, color: Color, modifier: Modifier = Modifier) {
    val markColor = color.copy(alpha = 0.55f)
    Canvas(modifier = modifier) {
        val s = size.minDimension
        when (value) {
            500 -> drawCircle(color = markColor, radius = s / 2f)
            200 -> drawRect(color = markColor, size = Size(s, s))
            100 -> drawPath(
                path = Path().apply {
                    moveTo(s / 2f, 0f)
                    lineTo(s, s)
                    lineTo(0f, s)
                    close()
                },
                color = markColor
            )
            50 -> drawPath(
                path = Path().apply {
                    moveTo(s / 2f, 0f)
                    lineTo(s, s / 2f)
                    lineTo(s / 2f, s)
                    lineTo(0f, s / 2f)
                    close()
                },
                color = markColor
            )
            20 -> drawRoundRect(
                color = markColor,
                size = Size(s, s * 0.6f),
                topLeft = Offset(0f, s * 0.2f),
                cornerRadius = CornerRadius(s * 0.2f)
            )
            else -> drawCircle(color = markColor, radius = s / 2f, style = Stroke(width = s * 0.18f))
        }
    }
}

private fun DrawScope.drawGuillocheLines(base: Color) {
    val color = base.copy(alpha = 0.07f)
    val step = 7.dp.toPx()
    val strokeWidth = 0.6.dp.toPx()
    var x = -size.height
    while (x < size.width) {
        drawLine(
            color = color,
            start = Offset(x, 0f),
            end = Offset(x + size.height, size.height),
            strokeWidth = strokeWidth
        )
        x += step
    }
}

/** The dashed metallic strip real banknotes carry — one of the most recognisable "this is
 *  currency" cues, and pure drawing rather than text, so it adds authenticity with zero
 *  clipping risk. */
private fun DrawScope.drawSecurityThread() {
    val x = size.width * 0.63f
    drawLine(
        color = Color.White.copy(alpha = 0.6f),
        start = Offset(x, 0f),
        end = Offset(x, size.height),
        strokeWidth = 1.6.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f)
    )
    drawLine(
        color = Color.Black.copy(alpha = 0.15f),
        start = Offset(x + 1.dp.toPx(), 0f),
        end = Offset(x + 1.dp.toPx(), size.height),
        strokeWidth = 0.6.dp.toPx()
    )
}

/** Small raised-line marks near the edge, echoing the tactile identification bars real notes
 *  carry for visually impaired users — count grows with denomination. */
private fun DrawScope.drawBleedLines(base: Color, count: Int) {
    val color = base.copy(alpha = 0.5f)
    val x = size.width * 0.975f
    val lineHeight = size.height * 0.09f
    val gap = lineHeight * 0.6f
    val totalHeight = count * lineHeight + (count - 1) * gap
    var y = (size.height - totalHeight) / 2f
    val strokeWidth = 1.4.dp.toPx()
    repeat(count) {
        drawLine(color = color, start = Offset(x, y), end = Offset(x, y + lineHeight), strokeWidth = strokeWidth)
        y += lineHeight + gap
    }
}

/** A soft diagonal highlight standing in for the sheen printed currency has under light. */
private fun DrawScope.drawSheen() {
    val brush = Brush.linearGradient(
        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.2f), Color.Transparent),
        start = Offset(0f, size.height),
        end = Offset(size.width * 0.55f, 0f)
    )
    drawRect(brush = brush)
}
