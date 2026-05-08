package uesugi.plugin.rollpig.service

import io.github.oshai.kotlinlogging.KotlinLogging
import uesugi.plugin.rollpig.store.PigData
import uesugi.plugin.rollpig.store.RollPigStore
import java.awt.Color
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.random.Random

class RollPigService(
    private val store: RollPigStore
) {
    private val log = KotlinLogging.logger {}

    companion object {
        private const val CANVAS_WIDTH = 800
        private const val CANVAS_HEIGHT = 800
        private const val PADDING_HORIZONTAL = 60
        private const val PADDING_VERTICAL = 60
        private const val AVATAR_SIZE = 280
        private const val SPACING_AVATAR_NAME = 40
        private const val SPACING_NAME_DESC = 25
        private const val SPACING_DESC_ANALYSIS = 30
        private const val DESC_FONT_SIZE = 32
        private const val ANALYSIS_FONT_SIZE = 28
        private const val ANALYSIS_LINE_HEIGHT_FACTOR = 1.6
        private const val NAME_FONT_SIZE = 66
        private val AVATAR_EXTENSIONS = listOf("png", "jpg", "jpeg", "webp", "gif")
    }

    suspend fun rollPigForUser(userId: String, forceNew: Boolean = false): PigData {
        if (!forceNew) {
            store.getUserPig(userId)?.let { return it }
        }

        val pigList = store.getPigList()
        if (pigList.isEmpty()) {
            return PigData("pig", "猪", "普通小猪", "你性格温和，喜欢简单的生活，容易满足。")
        }

        val pig = pigList[Random.nextInt(pigList.size)]
        store.setUserPig(userId, pig)
        return pig
    }

    /**
     * 渲染小猪图片
     *
     * 布局：头像、名称、描述、解析从上到下整体垂直居中，水平居中
     */
    fun renderPigImage(pigData: PigData): ByteArray? {
        return try {
            val canvas = BufferedImage(CANVAS_WIDTH, CANVAS_HEIGHT, BufferedImage.TYPE_INT_RGB)
            val g2d = canvas.createGraphics()
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

            g2d.color = Color.WHITE
            g2d.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT)

            // 预定义字体
            val nameFont = Font(null, Font.BOLD, NAME_FONT_SIZE)
            val descFont = Font(null, Font.PLAIN, DESC_FONT_SIZE)
            val analysisFont = Font(null, Font.PLAIN, ANALYSIS_FONT_SIZE)

            // 获取各字体度量（必须先设置 g2d.font）
            g2d.font = nameFont
            val nameFm = g2d.fontMetrics
            g2d.font = descFont
            val descFm = g2d.fontMetrics
            g2d.font = analysisFont
            val analysisFm = g2d.fontMetrics

            // 解析文本换行
            val maxAnalysisWidth = CANVAS_WIDTH - 2 * PADDING_HORIZONTAL
            val analysisLines = wrapText(pigData.analysis, analysisFm, maxAnalysisWidth)
            val analysisLineHeight = (ANALYSIS_FONT_SIZE * ANALYSIS_LINE_HEIGHT_FACTOR).toInt()

            // 计算内容总高度
            val contentHeight = AVATAR_SIZE +
                SPACING_AVATAR_NAME + nameFm.height +
                SPACING_NAME_DESC + descFm.height +
                SPACING_DESC_ANALYSIS +
                (analysisLines.size * analysisLineHeight)

            // 计算起始 Y，使内容整体垂直居中
            val availableHeight = CANVAS_HEIGHT - 2 * PADDING_VERTICAL
            var currentY = PADDING_VERTICAL + ((availableHeight - contentHeight) / 2).coerceAtLeast(0)
            val centerX = CANVAS_WIDTH / 2
            val avatarX = centerX - AVATAR_SIZE / 2

            // 绘制头像
            val avatar = loadAvatar(pigData.id)
            if (avatar != null) {
                g2d.drawImage(avatar, avatarX, currentY, AVATAR_SIZE, AVATAR_SIZE, null)
            } else {
                g2d.color = Color.LIGHT_GRAY
                g2d.fillRect(avatarX, currentY, AVATAR_SIZE, AVATAR_SIZE)
                g2d.color = Color.RED
                g2d.font = Font(null, Font.PLAIN, 24)
                val errorText = "图片加载失败"
                val textWidth = g2d.fontMetrics.stringWidth(errorText)
                g2d.drawString(errorText, centerX - textWidth / 2, currentY + AVATAR_SIZE / 2)
            }

            // 绘制名称（直接粗体，不使用描边模拟）
            currentY += AVATAR_SIZE + SPACING_AVATAR_NAME
            g2d.font = nameFont
            g2d.color = Color.BLACK
            val nameWidth = nameFm.stringWidth(pigData.name)
            g2d.drawString(pigData.name, centerX - nameWidth / 2, currentY + nameFm.ascent)

            // 绘制描述
            currentY += nameFm.height + SPACING_NAME_DESC
            g2d.font = descFont
            g2d.color = Color(85, 85, 85)
            val descWidth = descFm.stringWidth(pigData.description)
            g2d.drawString(pigData.description, centerX - descWidth / 2, currentY + descFm.ascent)

            // 绘制解析（逐行，带行高）
            currentY += descFm.height + SPACING_DESC_ANALYSIS
            g2d.font = analysisFont
            g2d.color = Color(51, 51, 51)
            for ((index, line) in analysisLines.withIndex()) {
                val lineWidth = analysisFm.stringWidth(line)
                val baseline = currentY + analysisFm.ascent + (index * analysisLineHeight)
                g2d.drawString(line, centerX - lineWidth / 2, baseline)
            }

            g2d.dispose()

            val baos = ByteArrayOutputStream()
            ImageIO.write(canvas, "PNG", baos)
            baos.toByteArray()
        } catch (e: Exception) {
            log.error(e) { "Failed to render pig image" }
            null
        }
    }

    private fun loadAvatar(pigId: String): BufferedImage? {
        val classLoader = this::class.java.classLoader
        for (ext in AVATAR_EXTENSIONS) {
            val resourcePath = "image/$pigId.$ext"
            val inputStream = classLoader.getResourceAsStream(resourcePath)
            if (inputStream != null) {
                return try {
                    ImageIO.read(inputStream)
                } catch (e: Exception) {
                    log.warn(e) { "Failed to load avatar: $resourcePath" }
                    null
                } finally {
                    inputStream.close()
                }
            }
        }
        log.warn { "No avatar found for pig: $pigId" }
        return null
    }

    /**
     * 按像素宽度自动换行。
     * 当累积字符宽度超过 maxWidth 时，把前一个字符之前的内容作为一行，
     * 当前字符起新行。
     */
    private fun wrapText(text: String, fm: FontMetrics, maxWidth: Int): List<String> {
        if (text.isEmpty()) return emptyList()
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()
        for (char in text) {
            currentLine.append(char)
            if (fm.stringWidth(currentLine.toString()) > maxWidth && currentLine.length > 1) {
                lines.add(currentLine.dropLast(1).toString())
                currentLine = StringBuilder().append(char)
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        return lines
    }
}
