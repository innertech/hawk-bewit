package tech.inner.hawk.bewit

data class HawkCredentials(val keyId: String, val key: String, val algorith: Algorithm) {
  enum class Algorithm(val jceAlgorithName: String) {
    SHA1("HmacSHA1"),
    SHA256("HmacSHA256"),
  }
}
