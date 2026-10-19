package com.example.aikeyboard.ui

import android.content.Context
import android.widget.LinearLayout

/** Root container with an explicit IME height; child panels cannot make the window grow. */
class FixedImeLayout(context: Context) : LinearLayout(context) {
    private var fixedHeightPx = 0

    fun setFixedHeightPx(value: Int) {
        fixedHeightPx = value.coerceAtLeast(0)
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.EXACTLY, MeasureSpec.AT_MOST -> MeasureSpec.getSize(widthMeasureSpec)
            else -> suggestedMinimumWidth
        }
        val height = if (fixedHeightPx > 0) {
            fixedHeightPx
        } else {
            MeasureSpec.getSize(heightMeasureSpec)
        }
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
        setMeasuredDimension(width, height)
    }
}
