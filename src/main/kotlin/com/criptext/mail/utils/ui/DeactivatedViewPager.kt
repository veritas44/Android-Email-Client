package com.criptext.mail.utils.ui

import android.content.Context
import android.util.AttributeSet
import androidx.viewpager.widget.ViewPager


class DeactivatedViewPager : ViewPager {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    override fun canScrollHorizontally(direction: Int): Boolean {
        return false
    }
}