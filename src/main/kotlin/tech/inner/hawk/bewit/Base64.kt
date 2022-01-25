package tech.inner.hawk.bewit

import java.util.*

private val base64EncoderUrl = Base64.getUrlEncoder().withoutPadding()!!

private val base64DecoderUrl = Base64.getUrlDecoder()

fun ByteArray.toBase64Url(): String = base64EncoderUrl.encodeToString(this)

fun String.base64UrlToBytes(): ByteArray = base64DecoderUrl.decode(this)
