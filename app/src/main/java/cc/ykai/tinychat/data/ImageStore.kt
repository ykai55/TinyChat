package cc.ykai.tinychat.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Base64
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImageStore(private val context: Context) {
  private val imageDirectory = File(context.filesDir, "chat_images")

  suspend fun importImage(uri: Uri): MessageImage =
    withContext(Dispatchers.IO) {
      val bitmap = decodeScaledBitmap(uri)
      val hasAlpha = bitmap.hasAlpha()
      val extension = if (hasAlpha) "png" else "jpg"
      val mimeType = if (hasAlpha) "image/png" else "image/jpeg"
      val destination = createImageFile(extension)
      destination.outputStream().use { output ->
        val format = if (hasAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        check(bitmap.compress(format, 86, output)) { "无法压缩图片" }
      }
      bitmap.recycle()
      MessageImage(destination.absolutePath, mimeType)
    }

  suspend fun saveGeneratedImage(source: String): MessageImage =
    withContext(Dispatchers.IO) {
      if (!source.startsWith("data:")) {
        return@withContext MessageImage(source = source, mimeType = "image/*")
      }
      val separator = source.indexOf(',')
      require(separator > 5) { "图片数据格式无效" }
      val metadata = source.substring(5, separator)
      val mimeType = metadata.substringBefore(';').ifBlank { "image/png" }
      val extension =
        when (mimeType) {
          "image/jpeg" -> "jpg"
          "image/webp" -> "webp"
          else -> "png"
        }
      val destination = createImageFile(extension)
      destination.writeBytes(Base64.decode(source.substring(separator + 1), Base64.DEFAULT))
      MessageImage(destination.absolutePath, mimeType)
    }

  suspend fun delete(image: MessageImage) {
    withContext(Dispatchers.IO) {
      if (!image.source.startsWith("http://") && !image.source.startsWith("https://")) {
        File(image.source).delete()
      }
    }
  }

  private fun decodeScaledBitmap(uri: Uri): Bitmap {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      return ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) {
        decoder,
        info,
        _ ->
        val largestSide = maxOf(info.size.width, info.size.height)
        if (largestSide > MAX_IMAGE_SIDE) {
          val scale = MAX_IMAGE_SIDE.toFloat() / largestSide
          decoder.setTargetSize(
            (info.size.width * scale).toInt(),
            (info.size.height * scale).toInt(),
          )
        }
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
      }
    }

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri).use { input ->
      BitmapFactory.decodeStream(input, null, bounds)
    }
    var sampleSize = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_IMAGE_SIDE) {
      sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return context.contentResolver.openInputStream(uri).use { input ->
      BitmapFactory.decodeStream(input, null, options)
        ?: error("无法读取所选图片")
    }
  }

  private fun createImageFile(extension: String): File {
    check(imageDirectory.exists() || imageDirectory.mkdirs()) { "无法创建图片目录" }
    return File(imageDirectory, "${UUID.randomUUID()}.$extension")
  }

  private companion object {
    const val MAX_IMAGE_SIDE = 1600
  }
}
