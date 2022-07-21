package vn.vhn.virtualmouse

import android.view.PointerIcon

interface PointerIconChangedListen {
    interface Listener {
        fun onPointerIconChanged(pointerIcon: PointerIcon?)
    }

    fun setPointerIconChangedListener(listener: Listener?)
}