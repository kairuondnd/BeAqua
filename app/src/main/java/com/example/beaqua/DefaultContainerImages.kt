package com.example.beaqua

import androidx.annotation.DrawableRes

object DefaultContainerImages {
    const val SMALL_BOTTLE = "drawable://container_default_small_bottle"
    const val WATER_JUG = "drawable://container_default_water_jug"
    const val JERRY_CAN = "drawable://container_default_jerry_can"

    data class Option(
        val source: String,
        val label: String,
        @param:DrawableRes val drawableRes: Int
    )

    val options = listOf(
        Option(SMALL_BOTTLE, "Water bottle", R.drawable.container_default_small_bottle),
        Option(WATER_JUG, "Water jug", R.drawable.container_default_water_jug),
        Option(JERRY_CAN, "Jerry can", R.drawable.container_default_jerry_can)
    )

    fun find(source: String?): Option? = options.firstOrNull { it.source == source }

    fun isDefault(source: String?): Boolean = find(source) != null
}
