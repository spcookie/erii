package uesugi.core.component.embedding

import cn.hutool.core.io.FileTypeUtil
import kotlin.io.encoding.Base64

fun ByteArray.toDataUrl(): String {
    val base64 = Base64.encode(this)
    val mimeType = inputStream().use { stream ->
        when (val type = FileTypeUtil.getType(stream)) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            else -> throw IllegalArgumentException("Unsupported image type: $type")
        }
    }
    return "data:$mimeType;base64,$base64"
}
