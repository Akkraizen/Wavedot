package space.wavedot.app.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowScope
import space.wavedot.ui.designsystem.WavedotMark
import java.awt.Cursor
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.Window

private val TitleBarHeight = 40.dp
private val ControlWidth = 46.dp
private val CloseHover = Color(0xFFE81123)

class WindowController(private val window: Window) {
    private var restoreBounds by mutableStateOf<Rectangle?>(null)

    val isMaximized: Boolean get() = restoreBounds != null

    fun minimize() {
        (window as? java.awt.Frame)?.extendedState = java.awt.Frame.ICONIFIED
    }

    fun toggleMaximize() {
        val previous = restoreBounds
        if (previous != null) {
            window.bounds = previous
            restoreBounds = null
        } else {
            restoreBounds = window.bounds
            window.bounds = usableScreenBounds()
        }
    }

    fun moveBy(dx: Int, dy: Int) {
        if (isMaximized) return
        window.setLocation(window.x + dx, window.y + dy)
    }

    fun restoreForDrag(pointer: Point) {
        val previous = restoreBounds ?: return
        val ratio = (pointer.x - window.x).toDouble() / window.width
        restoreBounds = null
        val x = pointer.x - (previous.width * ratio).toInt()
        window.bounds = Rectangle(x, window.y, previous.width, previous.height)
    }

    private fun usableScreenBounds(): Rectangle {
        val configuration = window.graphicsConfiguration
        val screen = configuration.bounds
        val insets = window.toolkit.getScreenInsets(configuration)
        return Rectangle(
            screen.x + insets.left,
            screen.y + insets.top,
            screen.width - insets.left - insets.right,
            screen.height - insets.top - insets.bottom,
        ).takeIf { it.width > 0 && it.height > 0 }
            ?: GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    }
}

@Composable
fun WindowScope.WavedotTitleBar(controller: WindowController, onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(TitleBarHeight)
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .windowDrag(controller),
                contentAlignment = Alignment.CenterStart,
            ) {
                WavedotMark(Modifier.padding(start = 18.dp), logoSize = 22.dp)
            }
            WindowButton(onClick = controller::minimize) { color -> MinimizeGlyph(color) }
            WindowButton(onClick = controller::toggleMaximize) { color ->
                if (controller.isMaximized) RestoreGlyph(color) else MaximizeGlyph(color)
            }
            WindowButton(onClick = onClose, hoverColor = CloseHover, hoverContent = Color.White) { color -> CloseGlyph(color) }
        }
    }
}

private fun Modifier.windowDrag(controller: WindowController): Modifier = this
    .pointerInput(controller) {
        detectTapGestures(onDoubleTap = { controller.toggleMaximize() })
    }
    .pointerInput(controller) {
        var last = Point()
        detectDragGestures(
            onDragStart = {
                last = MouseInfo.getPointerInfo().location
                controller.restoreForDrag(last)
            },
            onDrag = { change, _ ->
                change.consume()
                val now = MouseInfo.getPointerInfo().location
                controller.moveBy(now.x - last.x, now.y - last.y)
                last = now
            },
        )
    }

@Composable
private fun WindowButton(
    onClick: () -> Unit,
    hoverColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    hoverContent: Color = MaterialTheme.colorScheme.onSurface,
    glyph: @Composable BoxScope.(Color) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val content = if (hovered) hoverContent else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier
            .width(ControlWidth)
            .fillMaxHeight()
            .background(if (hovered) hoverColor else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        glyph(content)
    }
}

@Composable
private fun MinimizeGlyph(color: Color) = Canvas(Modifier.size(10.dp)) {
    val y = size.height / 2
    drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
}

@Composable
private fun MaximizeGlyph(color: Color) = Canvas(Modifier.size(10.dp)) {
    drawRect(color, style = Stroke(width = 1.dp.toPx()))
}

@Composable
private fun RestoreGlyph(color: Color) = Canvas(Modifier.size(10.dp)) {
    val stroke = Stroke(width = 1.dp.toPx())
    val offset = 2.dp.toPx()
    drawRect(color, topLeft = Offset(0f, offset), size = size.copy(size.width - offset, size.height - offset), style = stroke)
    drawLine(color, Offset(offset, 0f), Offset(size.width, 0f), strokeWidth = stroke.width)
    drawLine(color, Offset(size.width, 0f), Offset(size.width, size.height - offset), strokeWidth = stroke.width)
}

@Composable
private fun CloseGlyph(color: Color) = Canvas(Modifier.size(10.dp)) {
    val width = 1.dp.toPx()
    drawLine(color, Offset.Zero, Offset(size.width, size.height), strokeWidth = width)
    drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = width)
}

private enum class Edge(val cursor: Int, val alignment: Alignment) {
    TOP(Cursor.N_RESIZE_CURSOR, Alignment.TopCenter),
    BOTTOM(Cursor.S_RESIZE_CURSOR, Alignment.BottomCenter),
    LEFT(Cursor.W_RESIZE_CURSOR, Alignment.CenterStart),
    RIGHT(Cursor.E_RESIZE_CURSOR, Alignment.CenterEnd),
    TOP_LEFT(Cursor.NW_RESIZE_CURSOR, Alignment.TopStart),
    TOP_RIGHT(Cursor.NE_RESIZE_CURSOR, Alignment.TopEnd),
    BOTTOM_LEFT(Cursor.SW_RESIZE_CURSOR, Alignment.BottomStart),
    BOTTOM_RIGHT(Cursor.SE_RESIZE_CURSOR, Alignment.BottomEnd),
}

@Composable
fun BoxScope.WindowResizeEdges(
    window: Window,
    controller: WindowController,
    minWidth: Int = 720,
    minHeight: Int = 480,
    thickness: Dp = 5.dp,
) {
    if (controller.isMaximized) return
    Edge.entries.forEach { edge ->
        val sizeModifier = when (edge) {
            Edge.TOP, Edge.BOTTOM -> Modifier.fillMaxWidth().height(thickness)
            Edge.LEFT, Edge.RIGHT -> Modifier.fillMaxHeight().width(thickness)
            else -> Modifier.size(thickness * 2)
        }
        Spacer(
            sizeModifier
                .align(edge.alignment)
                .pointerHoverIcon(PointerIcon(Cursor(edge.cursor)))
                .pointerInput(edge) {
                    var start = Point()
                    var initial = Rectangle()
                    detectDragGestures(
                        onDragStart = {
                            start = MouseInfo.getPointerInfo().location
                            initial = window.bounds
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val now = MouseInfo.getPointerInfo().location
                            window.bounds = resize(edge, initial, now.x - start.x, now.y - start.y, minWidth, minHeight)
                        },
                    )
                },
        )
    }
}

private fun resize(edge: Edge, from: Rectangle, dx: Int, dy: Int, minWidth: Int, minHeight: Int): Rectangle {
    var x = from.x
    var y = from.y
    var width = from.width
    var height = from.height
    val left = edge == Edge.LEFT || edge == Edge.TOP_LEFT || edge == Edge.BOTTOM_LEFT
    val right = edge == Edge.RIGHT || edge == Edge.TOP_RIGHT || edge == Edge.BOTTOM_RIGHT
    val top = edge == Edge.TOP || edge == Edge.TOP_LEFT || edge == Edge.TOP_RIGHT
    val bottom = edge == Edge.BOTTOM || edge == Edge.BOTTOM_LEFT || edge == Edge.BOTTOM_RIGHT
    if (right) width = maxOf(minWidth, from.width + dx)
    if (bottom) height = maxOf(minHeight, from.height + dy)
    if (left) {
        width = maxOf(minWidth, from.width - dx)
        x = from.x + from.width - width
    }
    if (top) {
        height = maxOf(minHeight, from.height - dy)
        y = from.y + from.height - height
    }
    return Rectangle(x, y, width, height)
}
