package com.lhacenmed.sona.feature.onboarding

import androidx.compose.ui.graphics.Path
import com.lhacenmed.sona.core.datastore.OnboardingStep
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * How many points go round a companion shape - its distance from the centre at each, every 3 degrees clockwise from the
 * top - as Sona's mark itself is drawn: see `sona_mark_cookie_path`.
 */
internal const val ShapePoints = 120

/** The centre every shape is drawn round, in the units of the box the mark's path is drawn in - see [markRadii]. */
private const val MarkBoxUnits = 168f
private const val MarkCentre = 84f

/**
 * A shape as its distance from its centre at each of [ShapePoints] angles, in a box one unit across - so any two turn into
 * each other point by point, every frame between them a shape too, as the design morphs them. Sona's mark is one already.
 */
internal class CompanionShape(val radii: FloatArray)

/** Sona's mark's cookie, read from the very path it is drawn from - [pathData], in units of a [MarkBoxUnits] box. */
internal fun markShapeOf(pathData: String): CompanionShape {
    val points = Regex("""(-?[\d.]+)[ ,](-?[\d.]+)""").findAll(pathData).map { it.groupValues }.toList()
    check(points.size == ShapePoints) { "The mark's path has ${points.size} points, not $ShapePoints" }
    return CompanionShape(FloatArray(ShapePoints) { hypot(points[it][1].toFloat() - MarkCentre, points[it][2].toFloat() - MarkCentre) / MarkBoxUnits })
}

/** [step]'s shape - or, for the first step and for none, Sona's mark, [mark]. */
internal fun shapeOf(step: OnboardingStep?, mark: CompanionShape): CompanionShape = when (step) {
    null, OnboardingStep.WELCOME -> mark
    OnboardingStep.ACCESS -> AccessShape
    OnboardingStep.RESTORE -> RestoreShape
    OnboardingStep.FOLDERS -> FoldersShape
    OnboardingStep.LIBRARY -> LibraryShape
    OnboardingStep.DOWNLOADS -> DownloadsShape
    OnboardingStep.LOOK -> LookShape
    OnboardingStep.UPDATES -> UpdatesShape
    OnboardingStep.DONE -> DoneShape
}

internal val Sines = FloatArray(ShapePoints) { sin(2 * PI * it / ShapePoints).toFloat() }
internal val Cosines = FloatArray(ShapePoints) { cos(2 * PI * it / ShapePoints).toFloat() }

/**
 * Lays into this path the shape whose distance from its centre at each point is [radiusAt] of the point's index - a radius
 * in units of [side], the box it is drawn in. The one path, rewound, every frame, so a morph draws without making anything.
 */
internal inline fun Path.setToShape(side: Float, radiusAt: (Int) -> Float) {
    rewind()
    val centre = side / 2f
    for (i in 0 until ShapePoints) {
        val radius = radiusAt(i) * side
        val x = centre + radius * Sines[i]
        val y = centre - radius * Cosines[i]
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** The shape [fraction] of the way from [from] to [to], as a shape of its own - where a morph is cut short, for the next. */
internal fun morphed(from: CompanionShape, to: CompanionShape, fraction: Float): CompanionShape =
    CompanionShape(FloatArray(ShapePoints) { from.radii[it] + (to.radii[it] - from.radii[it]) * fraction })

// Each step's shape, as the design draws it: Material's expressive shapes, in the mark's box and points.

private val AccessShape = CompanionShape(floatArrayOf(0.4762f, 0.4741f, 0.4680f, 0.4580f, 0.4447f, 0.4286f, 0.4104f, 0.3909f, 0.3710f, 0.3515f, 0.3333f, 0.3173f, 0.3039f, 0.2940f, 0.2878f, 0.2857f, 0.2878f, 0.2940f, 0.3039f, 0.3173f, 0.3333f, 0.3515f, 0.3710f, 0.3909f, 0.4104f, 0.4286f, 0.4447f, 0.4580f, 0.4680f, 0.4741f, 0.4762f, 0.4741f, 0.4680f, 0.4580f, 0.4447f, 0.4286f, 0.4104f, 0.3909f, 0.3710f, 0.3515f, 0.3333f, 0.3173f, 0.3039f, 0.2940f, 0.2878f, 0.2857f, 0.2878f, 0.2940f, 0.3039f, 0.3173f, 0.3333f, 0.3515f, 0.3710f, 0.3909f, 0.4104f, 0.4286f, 0.4447f, 0.4580f, 0.4680f, 0.4741f, 0.4762f, 0.4741f, 0.4680f, 0.4580f, 0.4447f, 0.4286f, 0.4104f, 0.3909f, 0.3710f, 0.3515f, 0.3333f, 0.3173f, 0.3039f, 0.2940f, 0.2878f, 0.2857f, 0.2878f, 0.2940f, 0.3039f, 0.3173f, 0.3333f, 0.3515f, 0.3710f, 0.3909f, 0.4104f, 0.4286f, 0.4447f, 0.4580f, 0.4680f, 0.4741f, 0.4762f, 0.4741f, 0.4680f, 0.4580f, 0.4447f, 0.4286f, 0.4104f, 0.3909f, 0.3710f, 0.3515f, 0.3333f, 0.3173f, 0.3039f, 0.2940f, 0.2878f, 0.2857f, 0.2878f, 0.2940f, 0.3039f, 0.3173f, 0.3333f, 0.3515f, 0.3710f, 0.3909f, 0.4104f, 0.4286f, 0.4447f, 0.4580f, 0.4680f, 0.4741f))
private val RestoreShape = CompanionShape(floatArrayOf(0.4762f, 0.4717f, 0.4589f, 0.4400f, 0.4183f, 0.3976f, 0.3814f, 0.3725f, 0.3726f, 0.3814f, 0.3976f, 0.4183f, 0.4400f, 0.4589f, 0.4717f, 0.4762f, 0.4717f, 0.4589f, 0.4400f, 0.4183f, 0.3976f, 0.3814f, 0.3726f, 0.3725f, 0.3814f, 0.3976f, 0.4183f, 0.4400f, 0.4589f, 0.4717f, 0.4762f, 0.4717f, 0.4589f, 0.4400f, 0.4183f, 0.3976f, 0.3814f, 0.3725f, 0.3726f, 0.3814f, 0.3976f, 0.4183f, 0.4400f, 0.4589f, 0.4717f, 0.4762f, 0.4717f, 0.4589f, 0.4400f, 0.4183f, 0.3976f, 0.3814f, 0.3726f, 0.3725f, 0.3814f, 0.3976f, 0.4183f, 0.4400f, 0.4589f, 0.4717f, 0.4762f, 0.4717f, 0.4589f, 0.4400f, 0.4183f, 0.3976f, 0.3814f, 0.3725f, 0.3726f, 0.3814f, 0.3976f, 0.4183f, 0.4400f, 0.4589f, 0.4717f, 0.4762f, 0.4717f, 0.4589f, 0.4400f, 0.4183f, 0.3976f, 0.3814f, 0.3726f, 0.3725f, 0.3814f, 0.3976f, 0.4183f, 0.4400f, 0.4589f, 0.4717f, 0.4762f, 0.4717f, 0.4589f, 0.4400f, 0.4183f, 0.3976f, 0.3814f, 0.3725f, 0.3726f, 0.3814f, 0.3976f, 0.4183f, 0.4400f, 0.4589f, 0.4717f, 0.4762f, 0.4717f, 0.4589f, 0.4400f, 0.4183f, 0.3976f, 0.3814f, 0.3726f, 0.3725f, 0.3814f, 0.3976f, 0.4183f, 0.4400f, 0.4589f, 0.4717f))
private val FoldersShape = CompanionShape(floatArrayOf(0.3429f, 0.3461f, 0.3556f, 0.3703f, 0.3889f, 0.4096f, 0.4301f, 0.4487f, 0.4635f, 0.4729f, 0.4762f, 0.4729f, 0.4635f, 0.4487f, 0.4301f, 0.4095f, 0.3889f, 0.3703f, 0.3556f, 0.3461f, 0.3428f, 0.3461f, 0.3556f, 0.3703f, 0.3889f, 0.4096f, 0.4301f, 0.4487f, 0.4634f, 0.4729f, 0.4762f, 0.4729f, 0.4634f, 0.4487f, 0.4301f, 0.4096f, 0.3889f, 0.3703f, 0.3556f, 0.3461f, 0.3428f, 0.3461f, 0.3556f, 0.3703f, 0.3889f, 0.4095f, 0.4301f, 0.4487f, 0.4635f, 0.4729f, 0.4762f, 0.4729f, 0.4635f, 0.4487f, 0.4301f, 0.4096f, 0.3889f, 0.3703f, 0.3556f, 0.3461f, 0.3429f, 0.3461f, 0.3556f, 0.3703f, 0.3889f, 0.4096f, 0.4301f, 0.4487f, 0.4635f, 0.4729f, 0.4762f, 0.4729f, 0.4635f, 0.4487f, 0.4301f, 0.4095f, 0.3889f, 0.3703f, 0.3556f, 0.3461f, 0.3428f, 0.3461f, 0.3556f, 0.3703f, 0.3889f, 0.4096f, 0.4301f, 0.4487f, 0.4634f, 0.4729f, 0.4762f, 0.4729f, 0.4634f, 0.4487f, 0.4301f, 0.4096f, 0.3889f, 0.3703f, 0.3556f, 0.3461f, 0.3428f, 0.3461f, 0.3556f, 0.3703f, 0.3889f, 0.4095f, 0.4301f, 0.4487f, 0.4635f, 0.4729f, 0.4762f, 0.4729f, 0.4635f, 0.4487f, 0.4301f, 0.4096f, 0.3889f, 0.3703f, 0.3556f, 0.3461f))
private val LibraryShape = CompanionShape(floatArrayOf(0.4004f, 0.4010f, 0.4026f, 0.4053f, 0.4092f, 0.4140f, 0.4199f, 0.4266f, 0.4341f, 0.4421f, 0.4503f, 0.4583f, 0.4654f, 0.4712f, 0.4749f, 0.4762f, 0.4749f, 0.4712f, 0.4654f, 0.4583f, 0.4503f, 0.4421f, 0.4341f, 0.4266f, 0.4199f, 0.4140f, 0.4092f, 0.4053f, 0.4026f, 0.4010f, 0.4004f, 0.4010f, 0.4026f, 0.4053f, 0.4092f, 0.4140f, 0.4199f, 0.4266f, 0.4341f, 0.4421f, 0.4503f, 0.4583f, 0.4654f, 0.4712f, 0.4749f, 0.4762f, 0.4749f, 0.4712f, 0.4654f, 0.4583f, 0.4503f, 0.4421f, 0.4341f, 0.4266f, 0.4199f, 0.4140f, 0.4092f, 0.4053f, 0.4026f, 0.4010f, 0.4004f, 0.4010f, 0.4026f, 0.4053f, 0.4092f, 0.4140f, 0.4199f, 0.4266f, 0.4341f, 0.4421f, 0.4503f, 0.4583f, 0.4654f, 0.4712f, 0.4749f, 0.4762f, 0.4749f, 0.4712f, 0.4654f, 0.4583f, 0.4503f, 0.4421f, 0.4341f, 0.4266f, 0.4199f, 0.4140f, 0.4092f, 0.4053f, 0.4026f, 0.4010f, 0.4004f, 0.4010f, 0.4026f, 0.4053f, 0.4092f, 0.4140f, 0.4199f, 0.4266f, 0.4341f, 0.4421f, 0.4503f, 0.4583f, 0.4654f, 0.4712f, 0.4749f, 0.4762f, 0.4749f, 0.4712f, 0.4654f, 0.4583f, 0.4503f, 0.4421f, 0.4341f, 0.4266f, 0.4199f, 0.4140f, 0.4092f, 0.4053f, 0.4026f, 0.4010f))
private val DownloadsShape = CompanionShape(floatArrayOf(0.4762f, 0.4698f, 0.4531f, 0.4325f, 0.4159f, 0.4096f, 0.4159f, 0.4325f, 0.4532f, 0.4698f, 0.4762f, 0.4698f, 0.4532f, 0.4325f, 0.4159f, 0.4095f, 0.4159f, 0.4325f, 0.4532f, 0.4698f, 0.4762f, 0.4698f, 0.4532f, 0.4325f, 0.4159f, 0.4096f, 0.4159f, 0.4325f, 0.4531f, 0.4698f, 0.4762f, 0.4698f, 0.4531f, 0.4325f, 0.4159f, 0.4096f, 0.4159f, 0.4325f, 0.4532f, 0.4698f, 0.4762f, 0.4698f, 0.4532f, 0.4325f, 0.4159f, 0.4095f, 0.4159f, 0.4325f, 0.4532f, 0.4698f, 0.4762f, 0.4698f, 0.4532f, 0.4325f, 0.4159f, 0.4096f, 0.4159f, 0.4325f, 0.4531f, 0.4698f, 0.4762f, 0.4698f, 0.4531f, 0.4325f, 0.4159f, 0.4096f, 0.4159f, 0.4325f, 0.4532f, 0.4698f, 0.4762f, 0.4698f, 0.4532f, 0.4325f, 0.4159f, 0.4095f, 0.4159f, 0.4325f, 0.4532f, 0.4698f, 0.4762f, 0.4698f, 0.4532f, 0.4325f, 0.4159f, 0.4096f, 0.4159f, 0.4325f, 0.4531f, 0.4698f, 0.4762f, 0.4698f, 0.4531f, 0.4325f, 0.4159f, 0.4096f, 0.4159f, 0.4325f, 0.4532f, 0.4698f, 0.4762f, 0.4698f, 0.4532f, 0.4325f, 0.4159f, 0.4095f, 0.4159f, 0.4325f, 0.4532f, 0.4698f, 0.4762f, 0.4698f, 0.4532f, 0.4325f, 0.4159f, 0.4096f, 0.4159f, 0.4325f, 0.4531f, 0.4698f))
private val LookShape = CompanionShape(floatArrayOf(0.3714f, 0.3740f, 0.3814f, 0.3930f, 0.4076f, 0.4238f, 0.4400f, 0.4546f, 0.4662f, 0.4736f, 0.4762f, 0.4736f, 0.4662f, 0.4546f, 0.4400f, 0.4238f, 0.4076f, 0.3930f, 0.3814f, 0.3740f, 0.3714f, 0.3740f, 0.3814f, 0.3930f, 0.4076f, 0.4238f, 0.4400f, 0.4546f, 0.4662f, 0.4736f, 0.4762f, 0.4736f, 0.4662f, 0.4546f, 0.4400f, 0.4238f, 0.4076f, 0.3930f, 0.3814f, 0.3740f, 0.3714f, 0.3740f, 0.3814f, 0.3930f, 0.4076f, 0.4238f, 0.4400f, 0.4546f, 0.4662f, 0.4736f, 0.4762f, 0.4736f, 0.4662f, 0.4546f, 0.4400f, 0.4238f, 0.4076f, 0.3930f, 0.3814f, 0.3740f, 0.3714f, 0.3740f, 0.3814f, 0.3930f, 0.4076f, 0.4238f, 0.4400f, 0.4546f, 0.4662f, 0.4736f, 0.4762f, 0.4736f, 0.4662f, 0.4546f, 0.4400f, 0.4238f, 0.4076f, 0.3930f, 0.3814f, 0.3740f, 0.3714f, 0.3740f, 0.3814f, 0.3930f, 0.4076f, 0.4238f, 0.4400f, 0.4546f, 0.4662f, 0.4736f, 0.4762f, 0.4736f, 0.4662f, 0.4546f, 0.4400f, 0.4238f, 0.4076f, 0.3930f, 0.3814f, 0.3740f, 0.3714f, 0.3740f, 0.3814f, 0.3930f, 0.4076f, 0.4238f, 0.4400f, 0.4546f, 0.4662f, 0.4736f, 0.4762f, 0.4736f, 0.4662f, 0.4546f, 0.4400f, 0.4238f, 0.4076f, 0.3930f, 0.3814f, 0.3740f))
private val UpdatesShape = CompanionShape(floatArrayOf(0.4238f, 0.4374f, 0.4500f, 0.4609f, 0.4692f, 0.4744f, 0.4762f, 0.4744f, 0.4692f, 0.4608f, 0.4500f, 0.4374f, 0.4238f, 0.4102f, 0.3976f, 0.3868f, 0.3784f, 0.3732f, 0.3714f, 0.3732f, 0.3784f, 0.3868f, 0.3976f, 0.4102f, 0.4238f, 0.4373f, 0.4500f, 0.4609f, 0.4692f, 0.4744f, 0.4762f, 0.4744f, 0.4692f, 0.4609f, 0.4500f, 0.4373f, 0.4238f, 0.4102f, 0.3976f, 0.3868f, 0.3784f, 0.3732f, 0.3714f, 0.3732f, 0.3784f, 0.3868f, 0.3976f, 0.4102f, 0.4238f, 0.4374f, 0.4500f, 0.4608f, 0.4692f, 0.4744f, 0.4762f, 0.4744f, 0.4692f, 0.4609f, 0.4500f, 0.4374f, 0.4238f, 0.4103f, 0.3976f, 0.3868f, 0.3784f, 0.3732f, 0.3714f, 0.3732f, 0.3784f, 0.3868f, 0.3976f, 0.4102f, 0.4238f, 0.4374f, 0.4500f, 0.4609f, 0.4692f, 0.4744f, 0.4762f, 0.4744f, 0.4692f, 0.4608f, 0.4500f, 0.4374f, 0.4238f, 0.4102f, 0.3976f, 0.3868f, 0.3784f, 0.3732f, 0.3714f, 0.3732f, 0.3784f, 0.3868f, 0.3976f, 0.4102f, 0.4238f, 0.4374f, 0.4500f, 0.4608f, 0.4692f, 0.4744f, 0.4762f, 0.4744f, 0.4692f, 0.4609f, 0.4500f, 0.4374f, 0.4238f, 0.4102f, 0.3976f, 0.3868f, 0.3784f, 0.3732f, 0.3714f, 0.3732f, 0.3784f, 0.3868f, 0.3976f, 0.4103f))
private val DoneShape = CompanionShape(floatArrayOf(0.4762f, 0.4616f, 0.4236f, 0.3765f, 0.3383f, 0.3238f, 0.3384f, 0.3764f, 0.4235f, 0.4616f, 0.4762f, 0.4616f, 0.4235f, 0.3765f, 0.3384f, 0.3238f, 0.3384f, 0.3765f, 0.4235f, 0.4616f, 0.4762f, 0.4616f, 0.4235f, 0.3764f, 0.3384f, 0.3238f, 0.3383f, 0.3765f, 0.4236f, 0.4616f, 0.4762f, 0.4616f, 0.4236f, 0.3765f, 0.3383f, 0.3238f, 0.3384f, 0.3764f, 0.4235f, 0.4616f, 0.4762f, 0.4616f, 0.4235f, 0.3765f, 0.3384f, 0.3238f, 0.3384f, 0.3765f, 0.4235f, 0.4616f, 0.4762f, 0.4616f, 0.4235f, 0.3764f, 0.3384f, 0.3238f, 0.3383f, 0.3765f, 0.4236f, 0.4616f, 0.4762f, 0.4616f, 0.4236f, 0.3765f, 0.3383f, 0.3238f, 0.3384f, 0.3764f, 0.4235f, 0.4616f, 0.4762f, 0.4616f, 0.4235f, 0.3765f, 0.3384f, 0.3238f, 0.3384f, 0.3765f, 0.4235f, 0.4616f, 0.4762f, 0.4616f, 0.4235f, 0.3764f, 0.3384f, 0.3238f, 0.3383f, 0.3765f, 0.4236f, 0.4616f, 0.4762f, 0.4616f, 0.4236f, 0.3765f, 0.3383f, 0.3238f, 0.3384f, 0.3764f, 0.4235f, 0.4616f, 0.4762f, 0.4616f, 0.4235f, 0.3765f, 0.3384f, 0.3238f, 0.3384f, 0.3765f, 0.4235f, 0.4616f, 0.4762f, 0.4616f, 0.4235f, 0.3764f, 0.3384f, 0.3238f, 0.3383f, 0.3765f, 0.4236f, 0.4616f))
