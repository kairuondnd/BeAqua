package com.example.beaqua

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdminChatTest {
    @Test fun pendingApplicantCanBeChattedWithoutDocumentsOrApproval() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_BeAqua)
            val applicant = User(username = "sample_pending", accountType = "Station Owner", kycStatus = User.KYC_PENDING)
            var contacted: User? = null
            var reviewed = false
            val adapter = KycApplicationAdapter(listOf(applicant), { _, _ -> }, { reviewed = true },
                { reviewed = true }, { contacted = it })
            val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
            adapter.onBindViewHolder(holder, 0)
            assertTrue(holder.chatButton.isEnabled)
            assertEquals(View.VISIBLE, holder.chatButton.visibility)
            holder.chatButton.performClick()
            assertEquals(applicant, contacted)
            assertFalse(reviewed)
            assertEquals(User.KYC_PENDING, applicant.kycStatus)
        }
    }

    @Test fun restrictedDashboardHasChatAndRoutesBothDirectionsToSameParticipants() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val app = ApplicationProvider.getApplicationContext<Context>()
            val themed = ContextThemeWrapper(app, R.style.Theme_BeAqua)
            val view = LayoutInflater.from(themed).inflate(R.layout.station_owner_restricted, null)
            assertEquals(View.VISIBLE, view.findViewById<View>(R.id.btnRestrictedAdminChat).visibility)
            var opened: Intent? = null
            val capture = object : ContextWrapper(app) {
                override fun startActivity(intent: Intent) { opened = intent }
            }
            AdminChat.open(capture, "sample_pending", AdminChat.USERNAME)
            assertEquals("sample_pending", opened!!.getStringExtra("CURRENT_USERNAME"))
            assertEquals("admin", opened!!.getStringExtra("CHAT_WITH_USERNAME"))
            AdminChat.open(capture, AdminChat.USERNAME, "sample_pending")
            assertEquals("admin", opened!!.getStringExtra("CURRENT_USERNAME"))
            assertEquals("sample_pending", opened!!.getStringExtra("CHAT_WITH_USERNAME"))
        }
    }
}
