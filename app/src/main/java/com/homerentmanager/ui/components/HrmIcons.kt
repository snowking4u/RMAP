package com.homerentmanager.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ElectricMeter
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** One Material Outlined icon family for the whole app. */
object HrmIcons {
    val Home: ImageVector = Icons.Outlined.Home
    val Rooms: ImageVector = Icons.Outlined.MeetingRoom
    val Tenants: ImageVector = Icons.Outlined.People
    val Billing: ImageVector = Icons.Outlined.ReceiptLong
    val Electricity: ImageVector = Icons.Outlined.ElectricMeter
    val Payments: ImageVector = Icons.Outlined.Payments
    val Reports: ImageVector = Icons.Outlined.Insights
    val Backup: ImageVector = Icons.Outlined.Backup
    val Upload: ImageVector = Icons.Outlined.CloudUpload
    val Open: ImageVector = Icons.Outlined.FileOpen
    val Restore: ImageVector = Icons.Outlined.Restore
    val Lock: ImageVector = Icons.Outlined.Lock
    val Warning: ImageVector = Icons.Outlined.WarningAmber
    val Settings: ImageVector = Icons.Outlined.Settings
    val Save: ImageVector = Icons.Outlined.Save
    val AddRoom: ImageVector = Icons.Outlined.Add
    val EditRoom: ImageVector = Icons.Outlined.Edit
    val EditTenant: ImageVector = Icons.Outlined.Edit
    val Archive: ImageVector = Icons.Outlined.Archive
    val Delete: ImageVector = Icons.Outlined.Delete
    val Unarchive: ImageVector = Icons.Outlined.Unarchive
    val DownloadPdf: ImageVector = Icons.Outlined.PictureAsPdf
    val Print: ImageVector = Icons.Outlined.Print
    val Share: ImageVector = Icons.Outlined.Share
    val MeterReading: ImageVector = Icons.Outlined.ElectricMeter
    val History: ImageVector = Icons.Outlined.History
    val More: ImageVector = Icons.Outlined.MoreHoriz
    val Back: ImageVector = Icons.AutoMirrored.Outlined.ArrowBack
}

object HrmIconSize {
    val Small = 18.dp
    val Normal = 24.dp
    val Large = 32.dp
}
