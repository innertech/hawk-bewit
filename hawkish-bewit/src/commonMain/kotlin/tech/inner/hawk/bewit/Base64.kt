package tech.inner.hawk.bewit

import okio.Buffer
import okio.ByteString.Companion.decodeBase64

internal fun ByteArray.toBase64Url(): String = Buffer().write(this).readByteString().base64Url().trimEnd('=')

internal fun String.base64UrlToBytes(): ByteArray? = decodeBase64()?.toByteArray()
