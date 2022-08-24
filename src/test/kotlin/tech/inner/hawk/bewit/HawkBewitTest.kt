package tech.inner.hawk.bewit

import com.mercateo.test.clock.TestClock
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.net.URI
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

class HawkBewitTest {
  private val creds1 = HawkCredentials(
    keyId = "9aA4bFc9df",
    key = "4fDE242CacAFdEAFcb5e5b44CFfd7cf4adD53A4AfF32CF5deD7A92facDEC4b33",
    algorithm = HawkCredentials.Algorithm.SHA256,
  )
  private val creds2 = HawkCredentials(
    keyId = "545D9dC9d7",
    key = "32fe2DAF2DE9DcC5AE434Aa7C24CFae3ed42dad3eCe7CED5abf443fbbDFfcAdA",
    algorithm = HawkCredentials.Algorithm.SHA256,
  )
  private val uri1 = URI("https://localhost:1111/abc")
  private val uri1DiffScheme = URI("http://localhost:1111/abc")
  private val uri1DiffPath = URI("https://localhost:1111/abcd")
  private val uri1DiffHost = URI("https://otherhost:1111/abc")
  private val uri1DiffPort = URI("https://localhost:1112/abc")

  private val uri2 = URI("https://localhost:1111/testpath/subpath?param1=val1&param2=val2")
  private val uri2DiffQuery = URI("https://localhost:1111/testpath/subpath?param1=val1&param2=val3")

  private val uri3DefaultPort = URI("https://localhost/abc")
  private val uri3SetPort = URI("https://localhost:444/abc")

  private val clockSeed = OffsetDateTime.of(2022, 1, 25, 5, 0, 0, 0, ZoneOffset.UTC)
  private val testClock = TestClock.fixed(clockSeed)

  @Test
  fun `A valid bewit is good and has a correct expiry`() {
    with(HawkBewit(testClock)) {
      val ttl = 10.minutes.toJavaDuration()
      val bewit = generate(creds1, uri1, ttl)
      expectThat(validate(creds1, uri1, bewit)) isEqualTo
        BewitValidationResult.Good((clockSeed + ttl).toInstant())
    }
  }

  @Test
  fun `An expired bewit raises an Expired result`() {
    with(HawkBewit(testClock)) {
      val ttl = 1.minutes.toJavaDuration()
      val bewit = generate(creds1, uri1, ttl)
      testClock.fastForward(2.minutes.toJavaDuration())
      expectThat(validate(creds1, uri1, bewit)) isEqualTo
        BewitValidationResult.Expired((clockSeed + ttl).toInstant())
    }
  }

  @Test
  fun `A bewit does not authenticate if the expiry is changed in the envelope`() {
    with(HawkBewit(testClock)) {
      val ttl = 1.minutes.toJavaDuration()
      val bewit = generate(creds1, uri1, ttl)
      testClock.fastForward(2.minutes.toJavaDuration())

      // update to non-expired manually, but only in envelope
      // should fail but now with a MAC mismatch, as the MAC is based on the envelope timestamp
      val bewitUpdatedExpiry = bewit.base64UrlToBytes().decodeToString()
        .split("\\")
        .toMutableList()
        .apply {
          set(1, clockSeed.plusMinutes(3).toEpochSecond().toString())
        }
        .joinToString("\\")
        .encodeToByteArray()
        .toBase64Url()

      expectThat(validate(creds1, uri1, bewitUpdatedExpiry)) isEqualTo
        BewitValidationResult.AuthenticationError("MAC mismatch")
    }
  }

  @Test
  fun `A bewit does not authenticate if URIs do not match`() {
    with(HawkBewit(testClock)) {
      val ttl = 1.minutes.toJavaDuration()
      val bewit = generate(creds1, uri1, ttl)
      expectThat(validate(creds1, uri2, bewit)) isEqualTo
        BewitValidationResult.AuthenticationError("MAC mismatch")
    }
  }

  @Test
  fun `A bewit does not authenticate if key ids do not match`() {
    with(HawkBewit(testClock)) {
      val ttl = 1.minutes.toJavaDuration()
      val bewit = generate(creds1, uri1, ttl)
      expectThat(validate(creds1.copy(keyId = "abc"), uri1, bewit)) isEqualTo
        BewitValidationResult.Bad("Key id mismatch")
    }
  }

  @Test
  fun `A bewit does not authenticate if credentials do not match`() {
    with(HawkBewit(testClock)) {
      val ttl = 1.minutes.toJavaDuration()
      val bewit = generate(creds1, uri1, ttl)
      expectThat(validate(creds2, uri1, bewit)) isEqualTo
        BewitValidationResult.Bad("Key id mismatch")
    }
  }

  @Test
  fun `A bewit is invalid if it does not have every component`() {
    with(HawkBewit(testClock)) {
      val ttl = 1.minutes.toJavaDuration()
      val bewit = generate(creds1, uri1, ttl)
      val bewitNoKey = bewit.base64UrlToBytes().decodeToString()
        .split("\\")
        .drop(1)
        .joinToString("\\")
        .encodeToByteArray()
        .toBase64Url()

      expectThat(validate(creds1, uri1, bewitNoKey)) isEqualTo
        BewitValidationResult.Bad("Invalid bewit")
    }
  }

  @Test
  fun `A bewit does not authenticate if the mac is missing`() {
    with(HawkBewit(testClock)) {
      val ttl = 1.minutes.toJavaDuration()
      val bewit = generate(creds1, uri1, ttl)
      val bewitNoMac = bewit.base64UrlToBytes().decodeToString()
        .split("\\")
        .dropLast(2)
        .plus("")
        .plus("")
        .joinToString("\\")
        .encodeToByteArray()
        .toBase64Url()

      expectThat(validate(creds1, uri1, bewitNoMac)) isEqualTo
        BewitValidationResult.AuthenticationError("MAC mismatch")
    }
  }

  @Test
  fun `A bewit does not authenticate if path, host, or port are different`() {
    with(HawkBewit(testClock)) {
      val ttl = 1.minutes.toJavaDuration()
      val bewit = generate(creds1, uri1, ttl)

      expectThat(validate(creds1, uri1DiffPath, bewit)) isEqualTo
        BewitValidationResult.AuthenticationError("MAC mismatch")
      expectThat(validate(creds1, uri1DiffHost, bewit)) isEqualTo
        BewitValidationResult.AuthenticationError("MAC mismatch")
      expectThat(validate(creds1, uri1DiffPort, bewit)) isEqualTo
        BewitValidationResult.AuthenticationError("MAC mismatch")
    }
  }

  @Test
  fun `A bewit does not authenticate if default port is changed`() {
    with(HawkBewit(testClock)) {
      val ttl = 1.minutes.toJavaDuration()
      val bewit = generate(creds1, uri3DefaultPort, ttl)

      expectThat(validate(creds1, uri3SetPort, bewit)) isEqualTo
        BewitValidationResult.AuthenticationError("MAC mismatch")
    }
  }

  @Test
  fun `A bewit does not authenticate if scheme is different`() {
    with(HawkBewit(testClock)) {
      val ttl = 1.minutes.toJavaDuration()
      val bewit = generate(creds1, uri1, ttl)

      expectThat(validate(creds1, uri1DiffScheme, bewit)) isEqualTo
        BewitValidationResult.AuthenticationError("MAC mismatch")
    }
  }

  @Test
  fun `A bewit does not authenticate if query parameters are different`() {
    with(HawkBewit(testClock)) {
      val ttl = 1.minutes.toJavaDuration()
      val bewit = generate(creds1, uri2, ttl)

      expectThat(validate(creds1, uri2DiffQuery, bewit)) isEqualTo
        BewitValidationResult.AuthenticationError("MAC mismatch")
    }
  }
}
