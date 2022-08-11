package tech.inner.hawk.bewit

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration

/**
 * A test clock, generally used for testing and prototyping.
 */
class TestClock(var seed: Instant): Clock {
  override fun now(): Instant = seed
  fun fastForward(duration: Duration) {
    seed = seed.plus(duration)
  }
}

/**
 * Access pattern similar to `Clock.System`.
 */
@Suppress("FunctionName")
fun Clock.Companion.Fixed(seed: Instant = Clock.System.now()) = TestClock(seed)
