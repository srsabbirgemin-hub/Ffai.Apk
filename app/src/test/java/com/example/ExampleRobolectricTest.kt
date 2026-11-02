package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
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
    assertEquals("Floating AI", appName)
  }

  @Test
  fun `groq client parses comma-separated keys`() {
    val client = com.example.data.remote.GroqClient()
    val keys = client.parseKeys(" gsk_123, gsk_456 , gsk_789 ")
    assertEquals(3, keys.size)
    assertEquals("gsk_123", keys[0])
    assertEquals("gsk_456", keys[1])
    assertEquals("gsk_789", keys[2])
  }
}
