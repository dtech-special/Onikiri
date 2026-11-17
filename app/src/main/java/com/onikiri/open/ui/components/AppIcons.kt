package com.onikiri.open.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter

/**
 * The only place that knows where the app's icons come from. Every screen asks this object for a
 * [Painter]. To use custom artwork (for example Vector Drawables exported from Figma), replace the
 * body of a function with `painterResource(R.drawable.ic_something)`; no screen needs to change.
 */
object AppIcons {

    @Composable
    private fun vector(image: ImageVector): Painter = rememberVectorPainter(image)

    // Bottom navigation (active = filled/rounded, inactive = outlined)
    @Composable fun modules(selected: Boolean = false): Painter =
        vector(if (selected) Icons.Rounded.Extension else Icons.Outlined.Extension)

    @Composable fun installed(selected: Boolean = false): Painter =
        vector(if (selected) Icons.Rounded.Inventory2 else Icons.Outlined.Inventory2)

    @Composable fun search(selected: Boolean = true): Painter =
        vector(if (selected) Icons.Rounded.Search else Icons.Outlined.Search)

    @Composable fun settings(selected: Boolean = true): Painter =
        vector(if (selected) Icons.Rounded.Settings else Icons.Outlined.Settings)

    // Actions and status
    @Composable fun add(): Painter = vector(Icons.Rounded.Add)
    @Composable fun back(): Painter = vector(Icons.AutoMirrored.Rounded.ArrowBack)
    @Composable fun chevronRight(): Painter = vector(Icons.AutoMirrored.Rounded.KeyboardArrowRight)
    @Composable fun check(): Painter = vector(Icons.Rounded.Check)
    @Composable fun checkCircle(): Painter = vector(Icons.Rounded.CheckCircle)
    @Composable fun clear(): Painter = vector(Icons.Rounded.Clear)
    @Composable fun close(): Painter = vector(Icons.Rounded.Close)
    @Composable fun copy(): Painter = vector(Icons.Rounded.ContentCopy)
    @Composable fun delete(): Painter = vector(Icons.Rounded.Delete)
    @Composable fun deleteSweep(): Painter = vector(Icons.Rounded.DeleteSweep)
    @Composable fun download(): Painter = vector(Icons.Rounded.Download)
    @Composable fun error(): Painter = vector(Icons.Rounded.Error)
    @Composable fun expandMore(): Painter = vector(Icons.Rounded.ExpandMore)
    @Composable fun importFile(): Painter = vector(Icons.Rounded.FileDownload)
    @Composable fun exportFile(): Painter = vector(Icons.Rounded.FileUpload)
    @Composable fun lock(): Painter = vector(Icons.Rounded.Lock)
    @Composable fun openInNew(): Painter = vector(Icons.Rounded.OpenInNew)
    @Composable fun power(): Painter = vector(Icons.Rounded.PowerSettingsNew)
    @Composable fun refresh(): Painter = vector(Icons.Rounded.Refresh)
    @Composable fun restart(): Painter = vector(Icons.Rounded.RestartAlt)
    @Composable fun update(): Painter = vector(Icons.Rounded.Update)
    @Composable fun warning(): Painter = vector(Icons.Rounded.Warning)

    // Settings categories and sections
    @Composable fun customization(): Painter = vector(Icons.Rounded.Palette)
    @Composable fun repositories(): Painter = vector(Icons.Rounded.Dns)
    @Composable fun storage(): Painter = vector(Icons.Rounded.Storage)
    @Composable fun systemRoot(): Painter = vector(Icons.Rounded.Security)
    @Composable fun about(): Painter = vector(Icons.Rounded.Info)
    @Composable fun licenses(): Painter = vector(Icons.Rounded.Gavel)
    @Composable fun code(): Painter = vector(Icons.Rounded.Code)
    @Composable fun font(): Painter = vector(Icons.Rounded.TextFields)
    @Composable fun description(): Painter = vector(Icons.Rounded.Description)
}
