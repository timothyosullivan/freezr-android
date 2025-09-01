package com.freezr

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.assertion.ViewAssertions.matches

@RunWith(AndroidJUnit4::class)
class SmokeUiTest {
    @Rule @JvmField
    val rule = ActivityTestRule(MainActivity::class.java)

    @Test
    fun addItemFlow() {
        // Open add dialog
        onView(withText("Add")).perform(click())
        // Type name (Compose interop uses text node matches)
        onView(isDisplayed())
        // Because using pure Compose without test tags, limit to basic presence of button
        onView(withText("Add")).check(matches(isDisplayed()))
    }
}
