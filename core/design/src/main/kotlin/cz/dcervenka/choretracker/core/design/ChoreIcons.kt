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
fun ChoreCategory.suggestions(): List<Int> = when (this) {
    ChoreCategory.CLEANING -> listOf(
        R.string.chore_suggestion_vacuuming,
        R.string.chore_suggestion_mopping,
        R.string.chore_suggestion_bathroom,
        R.string.chore_suggestion_dusting,
        R.string.chore_suggestion_laundry,
        R.string.chore_suggestion_trash,
        R.string.chore_suggestion_kitchen,
    )
    ChoreCategory.COOKING -> listOf(
        R.string.chore_suggestion_dinner,
        R.string.chore_suggestion_meal_prep,
        R.string.chore_suggestion_dishes,
        R.string.chore_suggestion_breakfast,
        R.string.chore_suggestion_lunch,
    )
    ChoreCategory.SHOPPING -> listOf(
        R.string.chore_suggestion_groceries,
        R.string.chore_suggestion_pharmacy,
        R.string.chore_suggestion_household_supplies,
    )
    ChoreCategory.OUTDOOR -> listOf(
        R.string.chore_suggestion_mow_lawn,
        R.string.chore_suggestion_water_plants,
        R.string.chore_suggestion_take_out_bins,
        R.string.chore_suggestion_rake_leaves,
        R.string.chore_suggestion_sweep_patio,
    )
    ChoreCategory.OTHER -> emptyList()
}

@StringRes
fun ChoreCategory.toStringRes(): Int = when (this) {
    ChoreCategory.CLEANING -> R.string.chore_category_cleaning
    ChoreCategory.COOKING -> R.string.chore_category_cooking
    ChoreCategory.SHOPPING -> R.string.chore_category_shopping
    ChoreCategory.OUTDOOR -> R.string.chore_category_outdoor
    ChoreCategory.OTHER -> R.string.chore_category_other
}
