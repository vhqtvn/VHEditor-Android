package vn.vhn.vhscode.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.ScrollView

class ShrinkingScrollView(context: Context?, attrs: AttributeSet?) : ScrollView(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val totalHeight = getChildAt(0).measuredHeight
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val parentHeight = measuredHeight
        if (parentHeight < totalHeight) {
            //super.onMeasure(widthMeasureSpec, heightMeasureSpec) // just ran above
        } else {
            super.onMeasure(widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        }
    }
}