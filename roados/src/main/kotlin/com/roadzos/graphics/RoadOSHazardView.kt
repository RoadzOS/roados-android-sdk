package com.roadzos.graphics

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Android View showing a RoadOS hazard icon. Re-renders off the main thread
 * when the hazard, size or density changes.
 *
 * ```xml
 * <com.roadzos.graphics.RoadOSHazardView
 *     android:layout_width="64dp" android:layout_height="64dp"
 *     app:roadosHazardId="RDM-003" app:roadosSeverity="s4" app:roadosTheme="dark" />
 * ```
 */
class RoadOSHazardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ImageView(context, attrs, defStyleAttr) {

    var hazard: RoadOSHazard? = null
        set(value) {
            if (field != value) {
                field = value
                requestRender()
            }
        }

    /** Called on the main thread when rendering fails (the image is cleared). */
    var onError: ((RoadOSException) -> Unit)? = null

    private var scope: CoroutineScope? = null
    private var job: Job? = null
    private var lastKey: Triple<RoadOSHazard, Int, Float>? = null

    init {
        scaleType = ScaleType.FIT_CENTER
        val a = context.obtainStyledAttributes(attrs, R.styleable.RoadOSHazardView, defStyleAttr, 0)
        try {
            a.getString(R.styleable.RoadOSHazardView_roadosHazardId)?.let { id ->
                val level = a.getInt(R.styleable.RoadOSHazardView_roadosSeverity, 0)
                val theme = a.getInt(R.styleable.RoadOSHazardView_roadosTheme, 0)
                hazard = RoadOSHazard(
                    id,
                    severity = if (level in 1..5) HazardSeverity.of(level) else null,
                    theme = if (theme == 1) RoadOSTheme.LIGHT else RoadOSTheme.DARK,
                )
            }
        } finally {
            a.recycle()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scope = MainScope()
        lastKey = null
        requestRender()
    }

    override fun onDetachedFromWindow() {
        scope?.cancel()
        scope = null
        job = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        requestRender()
    }

    private fun requestRender() {
        val h = hazard ?: return
        val s = scope ?: return
        val px = min(width - paddingLeft - paddingRight, height - paddingTop - paddingBottom)
        if (px <= 0) return
        val density = resources.displayMetrics.density
        val points = (px / density).roundToInt().coerceAtLeast(1)
        val key = Triple(h, points, density)
        if (key == lastKey) return
        lastKey = key
        job?.cancel()
        val app = context.applicationContext
        job = s.launch {
            try {
                val (bitmap, name) = withContext(Dispatchers.Default) {
                    val catalogue = RoadOSCatalogue.bundled(app)
                    h.copy(size = points).render(catalogue, density).toBitmap() to catalogue.entry(h.id).name
                }
                setImageBitmap(bitmap)
                contentDescription = name
            } catch (e: RoadOSException) {
                setImageDrawable(null)
                onError?.invoke(e)
            }
        }
    }
}
