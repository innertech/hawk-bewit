package tech.inner.hawk.bewit

data class HawkCredentials(val keyId: String, val key: String, val algorithm: Algorithm) {
  enum class Algorithm(val jceAlgorithmName: String) {
    SHA1("HmacSHA1"),
    SHA256("HmacSHA256"),
  }
}
