package com.example.beaqua

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout

object BackNavigation {
    fun install(activity: AppCompatActivity, handleSection: () -> Boolean = { false }) {
        activity.onBackPressedDispatcher.addCallback(activity, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (closeDrawer(activity.window.decorView) || handleSection()) return
                goBack(activity)
            }
        })
    }

    private fun closeDrawer(view: View): Boolean {
        if (view is DrawerLayout && (0 until view.childCount).any { child ->
                val gravity = (view.getChildAt(child).layoutParams as DrawerLayout.LayoutParams).gravity
                gravity != 0 && view.isDrawerOpen(view.getChildAt(child))
            }) {
            view.closeDrawers()
            return true
        }
        return view is ViewGroup && (0 until view.childCount).any { closeDrawer(view.getChildAt(it)) }
    }

    fun goBack(activity: AppCompatActivity) {
        if (!activity.isTaskRoot) {
            activity.finish()
            return
        }
        // Only Back from an actual home screen may leave the app.
        if (activity is MainActivity || activity is UserHomeActivity ||
            activity is StationOwnerActivity || activity is AdminActivity) {
            activity.moveTaskToBack(true)
            return
        }
        val preferences = activity.getSharedPreferences("order_maintenance", Context.MODE_PRIVATE)
        val saved = RememberedSession.read(activity)
        val username = preferences.getString("username", null) ?: saved?.username
        val target = if (activity is LoginActivity || activity is SignupActivity || username.isNullOrBlank()) {
            Intent(activity, MainActivity::class.java)
        } else {
            val role = if (preferences.getBoolean("stationOwner", false)) "Station Owner" else saved?.role ?: "User"
            RememberedSession.destination(activity, username, role)
        }
        target.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        activity.startActivity(target)
        activity.finish()
    }
}
