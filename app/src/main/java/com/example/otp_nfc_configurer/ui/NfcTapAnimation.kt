/*
 * SPDX-FileCopyrightText: 2026 Taler Systems S.A.
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This file is part of GNU Taler
 * (C) 2026 Taler Systems S.A.
 *
 * GNU Taler is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * GNU Taler is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with GNU Taler.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.example.otp_nfc_configurer.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One moment of the "tap phone to device" illustration (Penpot: NFC Anim 1–5).
 * All frames share the calculator; only the phone, the contact ring and the signal colour change.
 */
@Immutable
data class NfcTapPose(
    /** Phone position along its path: 0 = touching the device (Contact), 1 = Approach, more = further out. */
    val distance: Float,
    val phoneAlpha: Float,
    /** 0 = no link, 1 = linked: ring around the device, green signal. */
    val contact: Float,
    /** Progress of a ring spreading out while data is exchanged; 0 = none. */
    val pulse: Float = 0f
) {
    companion object {
        val Idle = NfcTapPose(distance = ENTER_DISTANCE, phoneAlpha = 0f, contact = 0f)
        val Approach = NfcTapPose(distance = 1f, phoneAlpha = 1f, contact = 0f)
        val Near = NfcTapPose(distance = 0.5f, phoneAlpha = 1f, contact = 0f)
        val Contact = NfcTapPose(distance = 0f, phoneAlpha = 1f, contact = 1f)
        val Retreat = NfcTapPose(distance = 0.7f, phoneAlpha = 1f, contact = 0f)
    }
}

/**
 * Phone moving to the device and back in a loop. While [communicating] it stays in contact
 * with a pulsing ring; without NFC ([active] false) or with animations turned off it is still.
 */
@Composable
fun NfcTapAnimation(active: Boolean, communicating: Boolean, modifier: Modifier = Modifier) {
    val reduceMotion = rememberReduceMotion()
    val distance = remember { Animatable(NfcTapPose.Idle.distance) }
    val phoneAlpha = remember { Animatable(NfcTapPose.Idle.phoneAlpha) }
    val contact = remember { Animatable(NfcTapPose.Idle.contact) }
    val pulse = remember { Animatable(0f) }

    suspend fun approach() {
        distance.snapTo(ENTER_DISTANCE)
        together(
            { distance.animateTo(0f, tween(1100, easing = FastOutSlowInEasing)) },
            { phoneAlpha.animateTo(1f, tween(400)) }
        )
        contact.animateTo(1f, tween(300))
    }

    suspend fun retreat() {
        contact.animateTo(0f, tween(250))
        pulse.snapTo(0f)
        together(
            { distance.animateTo(ENTER_DISTANCE, tween(700, easing = FastOutLinearInEasing)) },
            { phoneAlpha.animateTo(0f, tween(300, delayMillis = 400)) }
        )
    }

    // Animatables keep their values when the keys change, so every branch starts from where the phone is.
    LaunchedEffect(active, communicating, reduceMotion) {
        when {
            !active || reduceMotion -> {
                val pose = if (communicating) NfcTapPose.Contact else NfcTapPose.Near
                distance.snapTo(pose.distance)
                phoneAlpha.snapTo(pose.phoneAlpha)
                contact.snapTo(pose.contact)
                pulse.snapTo(0f)
            }
            communicating -> {
                together(
                    { distance.animateTo(0f, tween(450, easing = FastOutSlowInEasing)) },
                    { phoneAlpha.animateTo(1f, tween(200)) },
                    { contact.animateTo(1f, tween(450)) }
                )
                while (true) {
                    pulse.snapTo(0f)
                    pulse.animateTo(1f, tween(1000, easing = LinearOutSlowInEasing))
                }
            }
            else -> {
                if (phoneAlpha.value > 0f) retreat()
                while (true) {
                    delay(800)
                    approach()
                    delay(900)
                    retreat()
                }
            }
        }
    }

    NfcTapArtwork(
        pose = NfcTapPose(distance.value, phoneAlpha.value, contact.value, pulse.value),
        dimmed = !active,
        modifier = modifier
    )
}

/**
 * Draws [pose] in the 140×140 coordinates of the Penpot frames. The phone may leave that area:
 * it is drawn into a margin around it that fades out instead of being clipped.
 */
@Composable
fun NfcTapArtwork(pose: NfcTapPose, modifier: Modifier = Modifier, dimmed: Boolean = false) {
    val ringColor = MaterialTheme.colorScheme.primary
    // The dark calculator disappears on a dark background without an outline.
    val outline = MaterialTheme.colorScheme.outline.takeIf { MaterialTheme.colorScheme.background.luminance() < 0.5f }
    Box(modifier.size(ARTWORK_SIZE.dp), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .requiredSize((ARTWORK_SIZE + 2 * BLEED).dp)
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    alpha = if (dimmed) 0.45f else 1f
                }
        ) {
            val unit = size.width / (ARTWORK_SIZE + 2 * BLEED)
            scale(unit, pivot = Offset.Zero) {
                translate(BLEED, BLEED) {
                    drawCalculator(outline)
                    drawContactRing(ringColor, pose)
                    drawPhone(pose)
                }
            }
            drawRect(
                Brush.radialGradient(
                    FADE_START / (ARTWORK_SIZE / 2 + BLEED) to Color.Black,
                    1f to Color.Transparent,
                    center = center,
                    radius = size.width / 2
                ),
                blendMode = BlendMode.DstIn
            )
        }
    }
}

private fun DrawScope.drawCalculator(outline: Color?) = rotate(-14f, pivot = Offset(58f, 72f)) {
    outline?.let { drawRoundRect(it, Offset(28.5f, 28.5f), Size(59f, 87f), CornerRadius(12.5f)) }
    drawRoundRect(CalculatorBody, Offset(30f, 30f), Size(56f, 84f), CornerRadius(11f))
    drawRoundRect(CalculatorFace, Offset(33f, 32f), Size(50f, 78f), CornerRadius(9f))
    drawRoundRect(CalculatorDisplay, Offset(38f, 39f), Size(40f, 14f), CornerRadius(3f))
    for (row in 0 until 4) for (column in 0 until 3) {
        drawRoundRect(CalculatorKey, Offset(38f + column * 14.5f, 60f + row * 13f), Size(11f, 9f), CornerRadius(2.5f))
    }
}

private fun DrawScope.drawContactRing(color: Color, pose: NfcTapPose) {
    if (pose.contact <= 0f) return
    val center = Offset(74f, 84f)
    val grow = 0.85f + 0.15f * pose.contact
    drawCircle(color, 36f * grow, center, alpha = 0.08f * pose.contact)
    drawCircle(color, 50f * grow, center, alpha = 0.25f * pose.contact, style = Stroke(2f))
    if (pose.pulse > 0f) {
        drawCircle(color, 50f + 14f * pose.pulse, center, alpha = 0.25f * (1f - pose.pulse) * pose.contact, style = Stroke(2f))
    }
}

private fun DrawScope.drawPhone(pose: NfcTapPose) {
    if (pose.phoneAlpha <= 0f) return
    val center = PHONE_CONTACT_CENTER + PHONE_TRAVEL * pose.distance
    rotate(30f, pivot = center) {
        translate(center.x, center.y) {
            // One layer, so the overlapping parts do not show through each other while fading.
            drawContext.canvas.saveLayer(Rect(-26f, -45f, 29f, 49f), Paint().apply { alpha = pose.phoneAlpha })
            drawRoundRect(PhoneShadow, Offset(-20f, -38f), Size(46f, 84f), CornerRadius(11f), alpha = 0.12f)
            drawRoundRect(PhoneBezel, Offset(-23f, -42f), Size(46f, 84f), CornerRadius(11f))
            drawRoundRect(PhoneScreen, Offset(-20.5f, -39.5f), Size(41f, 79f), CornerRadius(9f))
            drawRoundRect(PhoneSpeaker, Offset(-6f, -36f), Size(12f, 3f), CornerRadius(1.5f))

            val source = Offset(-7f, 7f)
            val linked = lerp(Color.White, SignalGreen, pose.contact)
            drawCircle(linked, 2.4f, source)
            SIGNAL_ARCS.forEachIndexed { index, (radius, idleAlpha) ->
                drawArc(
                    color = if (index == 0) linked else Color.White,
                    startAngle = -90f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = source - Offset(radius, radius),
                    size = Size(2 * radius, 2 * radius),
                    alpha = idleAlpha + (1f - idleAlpha) * pose.contact,
                    style = Stroke(3f, cap = StrokeCap.Round)
                )
            }
            drawContext.canvas.restore()
        }
    }
}

private suspend fun together(vararg animations: suspend () -> Unit) = coroutineScope {
    animations.forEach { launch { it() } }
}

/** "Remove animations" in the system settings: animations would jump between frames. */
@Composable
private fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

private const val ARTWORK_SIZE = 140f
/** Margin around the artwork where the phone enters and leaves. */
private const val BLEED = 18f
/** Distance from the artwork centre where the fade-out starts; the calculator and Contact stay inside. */
private const val FADE_START = 72f
/** Phone fully outside the faded area. */
private const val ENTER_DISTANCE = 1.6f

private val PHONE_CONTACT_CENTER = Offset(80f, 90f)
private val PHONE_TRAVEL = Offset(44f, 44f)
/** Radius and alpha before contact of the signal arcs. */
private val SIGNAL_ARCS = listOf(7f to 1f, 13f to 0.85f, 19f to 0.7f)

// Illustration colours from the Penpot design; they stay the same in the dark theme.
private val CalculatorBody = Color(0xFF1E2B4D)
private val CalculatorFace = Color(0xFF2B3D6B)
private val CalculatorDisplay = Color(0xFFD6DCE6)
private val CalculatorKey = Color(0xFFF0B429)
private val PhoneShadow = Color(0xFF0F172A)
private val PhoneBezel = Color(0xFFE2E6EE)
private val PhoneScreen = Color(0xFF2563EB)
private val PhoneSpeaker = Color(0xFF1D4ED8)
private val SignalGreen = Color(0xFF4ADE80)
