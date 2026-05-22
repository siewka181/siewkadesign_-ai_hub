package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun test_console_header_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme {
        ConsoleHeader(
          activeTab = "CONSOLE",
          selectedModel = "gemini-3.5-flash",
          isGenerating = false
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/console_header.png")
  }

  @Test
  fun test_console_footer_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme {
        ConsoleNavigationFooter(
          activeTab = "CONSOLE",
          onTabSelected = {}
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/console_footer.png")
  }
}

