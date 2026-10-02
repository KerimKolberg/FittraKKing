package com.kkfittracking.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kkfittracking.model.Tendon
import com.kkfittracking.model.TendonArea

/**
 * A simple sketch of the joint a tendon crosses: bones in grey, the muscle faint, and the tendon
 * itself in the highlight color, running from the muscle to the bone it attaches to. Drawn here,
 * so there is no artwork to license.
 */
@Composable
fun TendonIcon(tendon: Tendon, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val bone = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val muscle = MaterialTheme.colorScheme.error.copy(alpha = 0.25f)
    val highlight = MaterialTheme.colorScheme.tertiary
    Canvas(modifier.size(size)) {
        val sketch = Sketch(this, this.size.minDimension / 24f, bone, muscle, highlight)
        when (tendon.area) {
            TendonArea.KNEE -> sketch.knee(tendon)
            TendonArea.ANKLE -> sketch.ankle(tendon)
            TendonArea.HIP -> sketch.hip(tendon)
            TendonArea.SHOULDER -> sketch.shoulder(tendon)
            TendonArea.ELBOW -> sketch.elbow(tendon)
            TendonArea.HAND -> sketch.hand(tendon)
        }
    }
}

/** Drawing helpers on a 24 × 24 grid scaled by [u]. */
private class Sketch(val scope: DrawScope, val u: Float, val bone: Color, val muscle: Color, val highlight: Color) {
    fun p(x: Float, y: Float) = Offset(x * u, y * u)

    fun line(color: Color, x1: Float, y1: Float, x2: Float, y2: Float, width: Float) =
        scope.drawLine(color, p(x1, y1), p(x2, y2), width * u, StrokeCap.Round)

    fun dot(color: Color, x: Float, y: Float, radius: Float) = scope.drawCircle(color, radius * u, p(x, y))

    fun tendon(x1: Float, y1: Float, x2: Float, y2: Float) = line(highlight, x1, y1, x2, y2, 1.8f)

    /** Side view of a knee: thigh bone, kneecap in front, shinbone; the quads above the kneecap. */
    fun knee(tendon: Tendon) {
        line(muscle, 13.5f, 1.5f, 14.5f, 8f, 5f)
        line(bone, 10.5f, 1f, 11f, 11f, 3f)
        line(bone, 11f, 13.5f, 11f, 23f, 3f)
        dot(bone, 11f, 12.2f, 2.2f)
        dot(bone, 14.8f, 12f, 1.7f)
        when (tendon) {
            Tendon.PATELLAR -> tendon(14.8f, 13.8f, 12.8f, 17f)
            // Down the outside of the thigh to the shinbone.
            Tendon.ILIOTIBIAL -> tendon(12.6f, 1f, 12.6f, 14.8f)
            else -> tendon(15f, 6.5f, 15f, 10.3f)
        }
    }

    /** Side view of a lower leg and foot, toes to the right. */
    fun ankle(tendon: Tendon) {
        line(muscle, 7.5f, 2f, 7.5f, 10f, 5f)
        line(bone, 10.5f, 1f, 10.5f, 16f, 2.6f)
        dot(bone, 7.5f, 18.5f, 2f)
        line(bone, 8f, 19f, 21f, 20.5f, 2.6f)
        when (tendon) {
            Tendon.ACHILLES -> tendon(6.8f, 9f, 6.4f, 17f)
            Tendon.PLANTAR_FASCIA -> tendon(7.5f, 21f, 20f, 22f)
            Tendon.PERONEAL -> {
                line(muscle, 9f, 3f, 9f, 11f, 2.2f)
                tendon(9f, 11f, 9.4f, 17.5f)
                tendon(9.4f, 17.5f, 12.5f, 19.4f)
            }
            Tendon.POSTERIOR_TIBIAL -> {
                // Seen from the inside: behind the inner ankle bone and under the arch.
                line(muscle, 9.2f, 4f, 9.2f, 11f, 2.2f)
                tendon(9.2f, 11f, 9.2f, 17f)
                tendon(9.2f, 17f, 13.5f, 21.2f)
            }
            else -> {
                line(muscle, 12.5f, 3f, 12.5f, 11f, 2.4f)
                tendon(12.6f, 11f, 13.8f, 18f)
            }
        }
    }

    /** Front view of a hip: pelvis, both thigh bones. */
    fun hip(tendon: Tendon) {
        scope.drawArc(bone, 0f, 180f, false, p(3f, -4f), Size(18f * u, 16f * u), style = Stroke(2.6f * u))
        line(bone, 7.5f, 13f, 6f, 23f, 2.8f)
        line(bone, 16.5f, 13f, 18f, 23f, 2.8f)
        dot(bone, 7.8f, 12.5f, 1.8f)
        dot(bone, 16.2f, 12.5f, 1.8f)
        when (tendon) {
            Tendon.HAMSTRING -> {
                dot(bone, 9.5f, 12.2f, 1.2f)
                line(muscle, 9.5f, 15f, 9f, 22f, 3.4f)
                tendon(9.5f, 12.8f, 9.3f, 16f)
            }
            Tendon.HIP_FLEXOR -> {
                line(muscle, 11f, 2.5f, 10.8f, 7f, 2.6f)
                tendon(10.8f, 7f, 8.8f, 14f)
            }
            Tendon.ADDUCTOR -> {
                line(muscle, 11f, 14f, 7.5f, 20f, 3f)
                tendon(12f, 11.8f, 10.5f, 15f)
            }
            else -> {
                line(muscle, 3.5f, 6f, 5.5f, 10f, 3f)
                tendon(4.5f, 8.5f, 6.2f, 11.8f)
            }
        }
    }

    /** Front view of a shoulder: collarbone, the ball of the upper arm and the arm bone. */
    fun shoulder(tendon: Tendon) {
        line(bone, 2f, 6f, 13f, 7f, 2.2f)
        dot(bone, 15f, 10f, 3.2f)
        line(bone, 15.5f, 12f, 17f, 23f, 3f)
        if (tendon == Tendon.ROTATOR_CUFF) {
            scope.drawArc(highlight, 190f, 200f, false, p(11f, 6f), Size(8f * u, 8f * u), style = Stroke(1.8f * u, cap = StrokeCap.Round))
        } else {
            line(muscle, 14f, 14f, 15f, 20f, 3.2f)
            tendon(13.2f, 7.8f, 13.8f, 14f)
        }
    }

    /** An elbow bent at a right angle: upper arm going up, forearm to the right. */
    fun elbow(tendon: Tendon) {
        line(bone, 7f, 1f, 7f, 15f, 3f)
        line(bone, 8f, 16f, 23f, 16f, 3f)
        dot(bone, 6.5f, 16f, 2.2f)
        when (tendon) {
            Tendon.TRICEPS -> {
                line(muscle, 4.2f, 2f, 4.2f, 11f, 3f)
                tendon(4.2f, 11f, 5f, 16.5f)
            }
            Tendon.LATERAL_ELBOW -> {
                line(muscle, 11f, 13.2f, 20f, 13.2f, 2.4f)
                tendon(7.5f, 13.5f, 11.5f, 13.2f)
            }
            else -> {
                line(muscle, 11f, 18.8f, 20f, 18.8f, 2.4f)
                tendon(7.5f, 18.5f, 11.5f, 18.8f)
            }
        }
    }

    /** A hand from the palm side: forearm, palm and fingers, with the flexor tendons and their rings. */
    fun hand(tendon: Tendon) {
        line(muscle, 12f, 17f, 12f, 23f, 6f)
        scope.drawRoundRect(bone, p(7f, 9f), Size(10f * u, 8f * u), CornerRadius(2f * u))
        val fingers = tendon == Tendon.FINGER_FLEXORS
        listOf(8f, 10.7f, 13.3f, 16f).forEach { x ->
            line(bone, x, 9f, x, 2f, 1.9f)
            if (fingers) {
                tendon(x, 16f, x, 3.5f)
                dot(highlight, x, 6f, 1.1f)
            }
        }
        line(bone, 7f, 14f, 3.5f, 10f, 1.9f)
        // The thumb's tendons run from the forearm over the thumb side of the wrist.
        if (!fingers) tendon(9.5f, 21f, 3.8f, 10.5f)
    }
}
