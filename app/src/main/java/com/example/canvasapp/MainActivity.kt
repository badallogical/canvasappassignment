package com.example.canvasapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.canvasapp.ui.theme.LatoFontFamily
import com.example.canvasapp.ui.theme.MonstrateFontFamily
import com.example.canvasapp.ui.theme.OpenSansFontFamily
import com.example.canvasapp.ui.theme.RobotoFontFamily

data class TextElement(
    val id: Int,
    val text: String,
    val position: Offset,
    val fontFamily: String,
    val fontSize: Float = 20f,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val bounds: Pair<Offset, Offset>? = null
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TextEditorApp()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorApp() {
    var nextId by remember { mutableStateOf(0) }
    var textElements by remember { mutableStateOf(listOf<TextElement>()) }
    var selectedElementId by remember { mutableStateOf<Int?>(0) }
    var undoStack by remember { mutableStateOf(listOf<List<TextElement>>()) }
    var redoStack by remember { mutableStateOf(listOf<List<TextElement>>()) }

    var showEditDialog by remember { mutableStateOf(false) }
    var editingText by remember { mutableStateOf("") }

    var selectedFont by remember { mutableStateOf("Default") }
    var fontSize by remember { mutableStateOf(30f) }
    var isBold by remember { mutableStateOf(false) }
    var isItalic by remember { mutableStateOf(false) }
    var isUnderline by remember { mutableStateOf(false) }

    val textMeasurer = rememberTextMeasurer()
    val scrollState = rememberScrollState()

    // State for canvas size
    var canvasSize by remember { mutableStateOf(Offset(0f, 0f)) }


    // Helper functions for bounds calculations and hit detection
    fun calculateTextBounds(element: TextElement, textLayoutResult: TextLayoutResult): Pair<Offset, Offset> {
        val width = textLayoutResult.size.width.toFloat()
        val height = textLayoutResult.size.height.toFloat()
        val topLeft = element.position
        val bottomRight = Offset(element.position.x + width, element.position.y + height)
        return Pair(topLeft, bottomRight)
    }

    fun isPointInRectangle(point: Offset, rect: Pair<Offset, Offset>): Boolean {
        return point.x >= rect.first.x && point.x <= rect.second.x &&
                point.y >= rect.first.y && point.y <= rect.second.y
    }

    // Function to push the current state to undo stack
    fun pushToUndoStack() {
        undoStack = listOf(textElements) + undoStack
        redoStack = emptyList()
    }

    // Function to add a new text element
    fun addTextElement(position: Offset) {
        val newTextElement = TextElement(
            id = nextId,
            text = "New Text",
            position = position,
            fontFamily = selectedFont,
            fontSize = fontSize,
            isBold = isBold,
            isItalic = isItalic,
            isUnderline = isUnderline
        )
        selectedElementId = nextId
        nextId++
        textElements = textElements + newTextElement
        pushToUndoStack()
    }

    val fontFamilyMap = mapOf(
        "Default" to FontFamily.Default,
        "Serif" to FontFamily.Serif,
        "SansSerif" to FontFamily.SansSerif,
        "Monospace" to FontFamily.Monospace,
        "Lato" to LatoFontFamily,
        "Roboto" to RobotoFontFamily,
        "Monstrate" to MonstrateFontFamily,
        "OpenSans" to OpenSansFontFamily
    )

    MaterialTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Assignment") },
                actions = {
                    IconButton(onClick = {
                        if (undoStack.isNotEmpty()) {
                            redoStack = listOf(textElements) + redoStack
                            textElements = undoStack.first()
                            undoStack = undoStack.drop(1)
                        }
                    }) {
                        Icon(Icons.Default.Undo, "Undo")
                    }

                    IconButton(onClick = {
                        if (redoStack.isNotEmpty()) {
                            undoStack = listOf(textElements) + undoStack
                            textElements = redoStack.first()
                            redoStack = redoStack.drop(1)
                        }
                    }) {
                        Icon(Icons.Default.Redo, "Redo")
                    }
                }
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { size ->
                            // Save the canvas size on size change
                            canvasSize = Offset(size.width.toFloat(), size.height.toFloat())
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                selectedElementId?.let { id ->
                                    textElements = textElements.map { element ->
                                        if (element.id == id) {
                                            val newPosition = element.position + Offset(dragAmount.x, dragAmount.y)
                                            element.copy(position = newPosition)
                                        } else element
                                    }
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { offset ->
                                    var found = false
                                    textElements.forEach { element ->
                                        val style = TextStyle(
                                            fontSize = element.fontSize.sp,
                                            fontWeight = if (element.isBold) FontWeight.Bold else FontWeight.Normal,
                                            fontStyle = if (element.isItalic) FontStyle.Italic else FontStyle.Normal,
                                            textDecoration = if (element.isUnderline) TextDecoration.Underline else TextDecoration.None,
                                            fontFamily = fontFamilyMap[element.fontFamily]?: FontFamily.Default
                                        )
                                        val textLayoutResult = textMeasurer.measure(text = element.text, style = style)
                                        val bounds = calculateTextBounds(element, textLayoutResult)

                                        if (isPointInRectangle(offset, bounds) && !found) {
                                            selectedElementId = element.id
                                            fontSize = element.fontSize
                                            isBold = element.isBold
                                            isItalic = element.isItalic
                                            found = true
                                        }
                                    }
                                    if (!found) selectedElementId = null
                                },
                                // Editing
                                onDoubleTap = { offset ->
                                    textElements.forEach { element ->
                                        val style = TextStyle(
                                            fontSize = element.fontSize.sp,
                                            fontWeight = if (element.isBold) FontWeight.Bold else FontWeight.Normal,
                                            fontStyle = if (element.isItalic) FontStyle.Italic else FontStyle.Normal,
                                            textDecoration = if (element.isUnderline) TextDecoration.Underline else TextDecoration.None,
                                            fontFamily = fontFamilyMap[element.fontFamily]?: FontFamily.Default
                                        )
                                        val textLayoutResult = textMeasurer.measure(text = element.text, style = style)
                                        val bounds = calculateTextBounds(element, textLayoutResult)

                                        if (isPointInRectangle(offset, bounds)) {
                                            selectedElementId = element.id
                                            editingText = element.text
                                            showEditDialog = true
                                        }
                                    }
                                }
                            )
                        }
                ) {
                    textElements.forEach { element ->
                        val style = TextStyle(
                            fontSize = element.fontSize.sp,
                            fontWeight = if (element.isBold) FontWeight.Bold else FontWeight.Normal,
                            fontStyle = if (element.isItalic) FontStyle.Italic else FontStyle.Normal,
                            textDecoration = if (element.isUnderline) TextDecoration.Underline else TextDecoration.None,
                            fontFamily = fontFamilyMap[element.fontFamily]?: FontFamily.Default
                        )
                        val textLayoutResult = textMeasurer.measure(text = element.text, style = style)
                        val bounds = calculateTextBounds(element, textLayoutResult)

                        // Draw selection rectangle if selected
                        if (element.id == selectedElementId) {
                            drawRect(
                                color = Color.Gray.copy(alpha = 0.1f),
                                topLeft = bounds.first - Offset(4f, 4f),
                                size = Size(
                                    bounds.second.x - bounds.first.x + 8f,
                                    bounds.second.y - bounds.first.y + 8f
                                )
                            )
                        }

                        // Draw text
                        drawText(
                            textLayoutResult = textLayoutResult,
                            topLeft = element.position,
                            brush = SolidColor(if (element.id == selectedElementId) Color.Blue else Color.Black),
                            alpha = 1f
                        )
                    }
                }
            }

            // Text Editing Controls
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Font Selection Dropdown
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { expanded = true }) {
                            Text(selectedFont)
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            listOf("Default", "Roboto", "Serif", "OpenSans","Lato","Monstrate","Monospace").forEach { font ->
                                DropdownMenuItem(
                                    text = { Text(font) },
                                    onClick = {
                                        selectedFont = font
                                        expanded = false
                                        selectedElementId?.let { id ->
                                            pushToUndoStack()
                                            textElements = textElements.map { element ->
                                                if (element.id == id) element.copy(fontFamily = font)
                                                else element
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Font Size Controls
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            if (fontSize > 12f) {
                                fontSize -= 2f
                                selectedElementId?.let { id ->
                                    pushToUndoStack()
                                    textElements = textElements.map { element ->
                                        if (element.id == id) element.copy(fontSize = fontSize)
                                        else element
                                    }
                                }
                            }
                        }) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease Font Size")
                        }
                        Text(fontSize.toInt().toString())
                        IconButton(onClick = {
                            if (fontSize < 60f) {
                                fontSize += 2f
                                selectedElementId?.let { id ->
                                    pushToUndoStack()
                                    textElements = textElements.map { element ->
                                        if (element.id == id) element.copy(fontSize = fontSize)
                                        else element
                                    }
                                }
                            }
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Increase Font Size")
                        }
                    }

                    // Font Style Toggles
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconToggleButton(
                            checked = isBold,
                            onCheckedChange = { newBold ->
                                isBold = newBold
                                selectedElementId?.let { id ->
                                    pushToUndoStack()
                                    textElements = textElements.map { element ->
                                        if (element.id == id) element.copy(isBold = newBold)
                                        else element
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.FormatBold, contentDescription = "Bold")
                        }

                        IconToggleButton(
                            checked = isItalic,
                            onCheckedChange = { newItalic ->
                                isItalic = newItalic
                                selectedElementId?.let { id ->
                                    pushToUndoStack()
                                    textElements = textElements.map { element ->
                                        if (element.id == id) element.copy(isItalic = newItalic)
                                        else element
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.FormatItalic, contentDescription = "Italic")
                        }

                        IconToggleButton(
                            checked = isUnderline,
                            onCheckedChange = { newUnderline ->
                                isUnderline = newUnderline
                                selectedElementId?.let { id ->
                                    pushToUndoStack()
                                    textElements = textElements.map { element ->
                                        if (element.id == id) element.copy(isUnderline = newUnderline)
                                        else element
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.FormatUnderlined, contentDescription = "Underline")
                        }
                    }
                }
            }

            Button(
                onClick = {
                    addTextElement(Offset(canvasSize.x/2 - 100f, canvasSize.y/2)) // Add a new text at a default position
                },
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth() // To make it span the width
                    .wrapContentSize(Alignment.BottomCenter) // Align to the bottom center
            ) {
                Text("Add Text")
            }


            if (showEditDialog) {
                // Dialog to edit text
                AlertDialog(
                    onDismissRequest = { showEditDialog = false },
                    title = { Text("Edit Text") },
                    text = {
                        TextField(
                            value = editingText,
                            onValueChange = { newText -> editingText = newText },
                            label = { Text("Text") }
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            selectedElementId?.let { id ->
                                pushToUndoStack()
                                textElements = textElements.map { element ->
                                    if (element.id == id) element.copy(text = editingText)
                                    else element
                                }
                            }
                            showEditDialog = false
                        }) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showEditDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}
