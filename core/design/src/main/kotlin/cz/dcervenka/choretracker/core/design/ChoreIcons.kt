package cz.dcervenka.choretracker.core.design

import androidx.annotation.StringRes
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

@StringRes
fun ChoreCategory.toStringRes(): Int = when (this) {
    ChoreCategory.CLEANING -> R.string.chore_category_cleaning
    ChoreCategory.COOKING -> R.string.chore_category_cooking
    ChoreCategory.SHOPPING -> R.string.chore_category_shopping
    ChoreCategory.OUTDOOR -> R.string.chore_category_outdoor
    ChoreCategory.OTHER -> R.string.chore_category_other
}
