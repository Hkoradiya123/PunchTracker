package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.time.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("PunchTracker", appName)
    }

    @Test
    fun `test time utils formatting`() {
        val durationMillis = (8 * 3600 + 21 * 60) * 1000L
        val formatted = TimeUtils.formatDuration(durationMillis)
        assertEquals("08h 21m", formatted)
    }

    @Test
    fun `launch MainActivity test`() {
        val activityController = Robolectric.buildActivity(MainActivity::class.java)
        activityController.setup()
        val activity = activityController.get()
        assert(activity != null)
    }
}
