package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.brainer.otnor.andropi.AndropiRouter
import com.brainer.otnor.util.JsonHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Otnor", appName)
    }

    @Test
    fun `andropi router system status and device info`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val router = AndropiRouter(context)

        val statusResult = router.route("GET", "/andropi/v1/system/status", JsonHelper.parseObject("{}"))
        assertTrue(statusResult.success)

        val deviceResult = router.route("GET", "/andropi/v1/device/info", JsonHelper.parseObject("{}"))
        assertTrue(deviceResult.success)

        val commandsResult = router.route("GET", "/andropi/v1/system/commands", JsonHelper.parseObject("{}"))
        assertTrue(commandsResult.success)
    }
}
