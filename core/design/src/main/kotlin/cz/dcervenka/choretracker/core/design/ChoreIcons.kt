package cz.dcervenka.choretracker.core.design

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector
import cz.dcervenka.choretracker.core.model.chore.ChoreCategory

fun ChoreCategory.toIcon(): ImageVector = when (this) {
    ChoreCategory.CLEANING -> Icons.Outlined.CleaningServices
    ChoreCategory.COOKING -> Icons.Outlined.Restaurant
    ChoreCategory.SHOPPING -> Icons.Outlined.ShoppingCart
    ChoreCategory.OUTDOOR -> Icons.Outlined.Park
    ChoreCategory.OTHER -> Icons.Outlined.Category
}
