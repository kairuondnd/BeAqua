package com.example.beaqua

import android.content.Intent
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignupPrivacyTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun acknowledgmentStartsUncheckedAndStopsSignupForBothRoles() {
        ActivityScenario.launch<SignupActivity>(Intent(context, SignupActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                val checkbox = activity.findViewById<CheckBox>(R.id.cbPrivacyAcknowledgment)
                assertFalse(checkbox.isChecked)
                activity.findViewById<View>(R.id.btnSignupSubmit).performClick()
                assertEquals(View.VISIBLE, activity.findViewById<TextView>(R.id.tvPrivacyError).visibility)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.signupProgress).visibility)
                activity.findViewById<View>(R.id.radioOwner).performClick()
                activity.findViewById<View>(R.id.btnSignupSubmit).performClick()
                assertFalse(checkbox.isChecked)
                assertEquals(View.VISIBLE, activity.findViewById<TextView>(R.id.tvPrivacyError).visibility)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.signupProgress).visibility)
            }
        }
    }

    @Test fun openingNoticeDoesNotCheckAcknowledgment() {
        ActivityScenario.launch<SignupActivity>(Intent(context, SignupActivity::class.java)).use { scenario ->
            scenario.onActivity { it.findViewById<View>(R.id.btnReadPrivacyNotice).performClick() }
            onView(withText("Version ${PrivacyNotice.VERSION}")).check(matches(isDisplayed()))
            pressBack()
            scenario.onActivity { assertFalse(it.findViewById<CheckBox>(R.id.cbPrivacyAcknowledgment).isChecked) }
        }
    }

    @Test fun explicitAcknowledgmentSurvivesRecreationAndCanBeWithdrawnBeforeSubmit() {
        ActivityScenario.launch<SignupActivity>(Intent(context, SignupActivity::class.java)).use { scenario ->
            scenario.onActivity { it.findViewById<CheckBox>(R.id.cbPrivacyAcknowledgment).isChecked = true }
            scenario.recreate()
            scenario.onActivity { activity ->
                val checkbox = activity.findViewById<CheckBox>(R.id.cbPrivacyAcknowledgment)
                assertTrue(checkbox.isChecked)
                checkbox.isChecked = false
                activity.findViewById<View>(R.id.btnSignupSubmit).performClick()
                assertEquals(View.VISIBLE, activity.findViewById<TextView>(R.id.tvPrivacyError).visibility)
            }
        }
    }
}
