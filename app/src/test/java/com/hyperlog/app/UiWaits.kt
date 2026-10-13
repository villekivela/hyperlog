package com.hyperlog.app

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performClick

/*
 * The one way UI tests wait. With [FreshDatabaseRule] Room runs on the main thread, so after
 * `waitForIdle` every write a tap caused has landed and the screen shows it. What can still lag
 * is work off the main thread (the library seed, program parsing), so a test waits for the result
 * it expects: a node, a count of nodes, or a node gone.
 */

/** Waits until [matcher] finds a number of nodes [expected] accepts (at least one by default). */
fun ComposeTestRule.await(
    matcher: SemanticsMatcher,
    timeoutMillis: Long = 10_000,
    useUnmergedTree: Boolean = false,
    expected: (Int) -> Boolean = { it > 0 },
) {
    waitForIdle()
    waitUntil(timeoutMillis) { expected(onAllNodes(matcher, useUnmergedTree).fetchSemanticsNodes().size) }
}

/** Waits until [text] shows. */
fun ComposeTestRule.waitFor(text: String, substring: Boolean = false) = await(hasText(text, substring = substring))

/** Waits until [text] shows [count] times. */
fun ComposeTestRule.waitForCount(text: String, count: Int) = await(hasText(text)) { it == count }

/** Waits until [text] is gone. */
fun ComposeTestRule.waitGone(text: String, substring: Boolean = false) = await(hasText(text, substring = substring)) { it == 0 }

/** Waits until a node described as [description] shows. */
fun ComposeTestRule.waitForDescription(description: String) = await(hasContentDescription(description))

/** Waits until no node is described as [description]. */
fun ComposeTestRule.waitForNoDescription(description: String) = await(hasContentDescription(description)) { it == 0 }

/** Waits until [text] can be tapped. */
fun ComposeTestRule.waitForTappable(text: String) = await(hasText(text).and(hasClickAction()))

/** The last node described as [description] (a pad key sits below anything on the strip sharing it), once it shows. */
fun ComposeTestRule.key(description: String): SemanticsNodeInteraction {
    waitForDescription(description)
    return onAllNodes(hasContentDescription(description)).let { it[it.fetchSemanticsNodes().size - 1] }
}

/** Taps each key by its description, letting each tap land before the next. */
fun ComposeTestRule.press(vararg descriptions: String) = descriptions.forEach {
    key(it).performClick()
    waitForIdle()
}

/**
 * A dialog's buttons in the platform's order: the way out ([cancel]) first, the [action] last, at
 * the right.
 */
fun ComposeTestRule.assertButtonOrder(cancel: String, action: String) {
    val left = onNode(hasText(cancel).and(hasClickAction())).fetchSemanticsNode().boundsInRoot.left
    val right = onNode(hasText(action).and(hasClickAction())).fetchSemanticsNode().boundsInRoot.left
    assert(left < right) { "$cancel should sit left of $action" }
}

/** Taps [text] once it shows, and lets the tap land. */
fun ComposeTestRule.click(text: String, substring: Boolean = false) {
    waitFor(text, substring)
    onNode(hasText(text, substring = substring)).performClick()
    waitForIdle()
}
