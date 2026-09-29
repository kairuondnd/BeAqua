package com.example.beaqua

import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.RatingBar
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedbackQualityTest {
    @Test fun formLetsCustomerRateProductAndServiceIndependently() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_BeAqua)
            val view = LayoutInflater.from(context).inflate(R.layout.dialog_feedback, null)
            val product = view.findViewById<RatingBar>(R.id.ratingProductQuality)
            val service = view.findViewById<RatingBar>(R.id.ratingStation)
            assertFalse(product.isIndicator)
            assertFalse(service.isIndicator)
            product.rating = 5f
            service.rating = 2f
            assertEquals(5f, product.rating, 0f)
            assertEquals(2f, service.rating, 0f)
        }
    }

    @Test fun stationSeesSeparateScoresAndLegacyReviewsDoNotInventProductRatings() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_BeAqua)
            val adapter = FeedbackAdapter(listOf(
                Feedback(productQualityRating = 5f, serviceQualityRating = 2f, productName = "Water jug"),
                Feedback(stationRating = 4f)
            ))
            val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
            adapter.onBindViewHolder(holder, 0)
            assertEquals(5f, holder.productRating.rating, 0f)
            assertEquals(2f, holder.ratingStation.rating, 0f)
            assertEquals("Water jug", holder.product.text.toString())
            adapter.onBindViewHolder(holder, 1)
            assertEquals(View.GONE, holder.productRating.visibility)
            assertEquals(4f, holder.ratingStation.rating, 0f)
            assertTrue(holder.serviceLabel.text.contains("older review"))
        }
    }
}
