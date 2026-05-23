package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.ConsoleViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("CloudConsole AI", appName)
  }

  @Test
  fun `test console view model tab switching`() = runTest {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = ConsoleViewModel(app)
    
    // Verify initial active tab is CONSOLE
    assertEquals("CONSOLE", viewModel.activeTab.value)
    
    // Change tab to FILES
    viewModel.selectTab("FILES")
    assertEquals("FILES", viewModel.activeTab.value)
    
    // Change tab to PROMPTS
    viewModel.selectTab("PROMPTS")
    assertEquals("PROMPTS", viewModel.activeTab.value)
    
    // Change tab to OPENCL
    viewModel.selectTab("OPENCL")
    assertEquals("OPENCL", viewModel.activeTab.value)
  }

  @Test
  fun `test console view model available models`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = ConsoleViewModel(app)
    
    // Check initial model info
    assertEquals("gemini-3.5-flash", viewModel.selectedModel.value)
    assertNotNull(viewModel.availableModels)
    assert(viewModel.availableModels.size >= 2)
    assertEquals("gemini-3.5-flash", viewModel.availableModels[0])
    assertEquals("gemini-3.1-pro-preview", viewModel.availableModels[1])
  }

  @Test
  fun `test console view model select model`() = runTest {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = ConsoleViewModel(app)
    
    // Select alternative model
    viewModel.selectModelName("gemini-3.1-pro-preview")
    assertEquals("gemini-3.1-pro-preview", viewModel.selectedModel.value)
  }
}

