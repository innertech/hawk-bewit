package tech.inner.hawk.bewit

import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

sealed class BewitValidationResult {
  data class Bad(val message: String): BewitValidationResult()
  data class Expired(val expiry: Instant): BewitValidationResult()
  data class AuthenticationError(val message: String): BewitValidationResult()
  data class Good(val expiry: Instant): BewitValidationResult()
}

/**
 * Create a HawkBewit to generate and validate signed URLs. The Clock used for expiry is optionally provided
 * in the constructor to [assist with unit tests](https://stackoverflow.com/a/56026271/430128).
 *
 * It is the caller's responsibility to add the bewit to the link after generation, and then extract and remove
 * the bewit from the signed link before validation. This library makes no assumptions about where in the signed
 * URL the bewit is stored, as long as the unsigned URI passed to [generate] is the same as the unsigned URI
 * passed here. The Hawk spec states the bewit should be a query parameter, but we find it often works well as a
 * path parameter instead for better compatibility with tools that display links inline (e.g. email clients) and
 * applications like Microsoft Word that appear to strip query parameters in their latest incarnations.
 * The bewit could even be sent out of band for certain use cases.
 */
class HawkBewit(private val clock: Clock = Clock.systemUTC()) {
  companion object {
    // not compatible with Hawk directly, we validate scheme as well
    const val HAWK_VERSION = "1a"
    const val AUTH_TYPE_BEWIT = "BEWIT"

    private const val DEFAULT_HTTP_PORT = 80
    private const val DEFAULT_HTTPS_PORT = 443
    private const val BEWIT_FIELDS = 4
    private const val BEWIT_FIELD_ID = 0
    private const val BEWIT_FIELD_EXPIRY = 1
    private const val BEWIT_FIELD_MAC = 2
  }

  private data class BewitData(
    val keyId: String,
    val expiry: Instant,
    @Suppress("ArrayInDataClass") val mac: ByteArray,
  )

  /**
   * Generate a bewit, which uses an HMAC to create a signature based on the URI and expiry. It is the
   * responsibility of the caller to "stuff" the bewit into the URL in whatever mechanism makes sense for the
   * caller. The spec states the bewit should be a query parameter, but it is the caller's responsibility to
   * handle the bewit (see the docs for [HawkBewit]).
   */
  fun generate(
    credentials: HawkCredentials,
    uri: URI,
    ttl: Duration,
  ): String {
    require(!ttl.isNegative) { "TTL must be a positive duration" }

    val expiry = clock.instant().plus(ttl)
    val macBase64 = calculateMac(
      credentials,
      expiry,
      uri,
    ).toBase64Url()

    val bewit = buildString {
      append(credentials.keyId)
      append('\\')
      append(expiry.epochSecond)
      append('\\')
      append(macBase64)
      append('\\')
    }
    return bewit.encodeToByteArray().toBase64Url()
  }

  /**
   * Given credentials and a URI (unstuffed of any bewit), and the bewit itself, validate that the bewit is valid
   * for the provided URI.
   *
   * The bewit is provided as a parameter rather than extracted from the passed [URI]. The spec states the bewit
   * should be a query parameter, but it is the caller's responsibility to handle the bewit (see the docs for
   * [HawkBewit]). This library makes no assumptions about where the bewit was stored between [generate] and this
   * call.
   */
  fun validate(credentials: HawkCredentials, uri: URI, bewit: String): BewitValidationResult {
    val bewitData = try {
      decodeBewit(bewit)
    } catch (e: IllegalStateException) {
      return BewitValidationResult.Bad(e.message ?: "Illegal bewit format")
    }

    if (credentials.keyId != bewitData.keyId) {
      return BewitValidationResult.Bad("Key id mismatch")
    }

    if (clock.instant() > bewitData.expiry) {
      return BewitValidationResult.Expired(bewitData.expiry)
    }

    val calculatedMac = calculateMac(
      credentials,
      bewitData.expiry,
      uri,
    )

    // uses a constant-time algorithm to avoid timing attacks
    // https://codahale.com/a-lesson-in-timing-attacks/
    if (!MessageDigest.isEqual(calculatedMac, bewitData.mac)) {
      return BewitValidationResult.AuthenticationError("MAC mismatch")
    }

    return BewitValidationResult.Good(bewitData.expiry)
  }

  /**
   * Create an unsigned URI for input into Hawk. This is different from `URI(url)` in two respects: firstly, it does
   * not set the port value, which may not matter for bewits anyway. Secondly, and more importantly, the URI single-arg
   * constructor does not accept unencoded URLs i.e. it will fail on paths with spaces with a `URISyntaxException`.
   *
   * Also note URI encodes paths differently than URLEncoder -- URI encodes spaces with %20 and URLEncoder encodes them
   * with +. Since the encoding must be consistent with validation time, we use URL to parse the components of the
   * URLEncoder-encoded data, then decode the path before passing to the five-arg constructor of URI, which expects
   * decoded data and encodes it (though the docs seem to indicate it shouldn't, so hopefully we aren't relying on a
   * bug here).
   *
   * @param url The encoded URL string.
   */
  fun hawkUnsignedUri(url: String): URI =
    URL(url).let { URI(it.protocol, it.host, URLDecoder.decode(it.path, Charsets.UTF_8), it.query, it.ref) }

  private fun calculateMac(
    credentials: HawkCredentials,
    timestamp: Instant,
    uri: URI,
  ): ByteArray {
    val hawkString = buildString(1024) {
      append("hawk.")
      append(HAWK_VERSION)
      append('.')
      append(AUTH_TYPE_BEWIT)
      append('\n')
      append(timestamp.epochSecond)
      append('\n')
      append('\n')
      append("GET")
      append('\n')
      append(uri.rawPath)
      if (uri.query != null) {
        append('?')
        append(uri.rawQuery)
      }
      // add scheme to the auth, not part of the original hawk spec
      append('\n')
      append(uri.scheme.lowercase())
      append('\n')
      append(uri.host.lowercase())
      append('\n')
      append(uriPort(uri))
    }

    return calculateMac(credentials, hawkString)
  }

  private fun calculateMac(credentials: HawkCredentials, text: String): ByteArray {
    val mac = Mac.getInstance(credentials.algorithm.jceAlgorithmName)
    mac.init(SecretKeySpec(credentials.key.encodeToByteArray(), credentials.algorithm.jceAlgorithmName))
    return mac.doFinal(text.encodeToByteArray())
  }

  private fun decodeBewit(bewit: String): BewitData {
    val decodedBewit = bewit.base64UrlToBytes().decodeToString()
    val bewitFields = decodedBewit.split('\\')
    if (bewitFields.size != BEWIT_FIELDS) error("Invalid bewit")
    return BewitData(
      keyId = bewitFields[BEWIT_FIELD_ID],
      expiry = Instant.ofEpochSecond(bewitFields[BEWIT_FIELD_EXPIRY].toLong()),
      mac = bewitFields[BEWIT_FIELD_MAC].base64UrlToBytes(),
    )
  }

  private fun uriPort(uri: URI): Int {
    fun defaultPort() = when (uri.scheme) {
      "http" -> DEFAULT_HTTP_PORT
      "https" -> DEFAULT_HTTPS_PORT
      else -> error("Unknown URI scheme \"" + uri.scheme + "\"")
    }

    return when (uri.port) {
      -1 -> defaultPort()
      else -> uri.port
    }
  }
}
