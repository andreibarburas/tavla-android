package com.brbrs.tavla.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DesignServices
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Rocket
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.ViewKanban
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A curated subset of Material Symbols for board icons (#02) -- the full extended icon set is
 * thousands strong, which isn't a useful picker. Add more here freely; [DEFAULT_ICON] is the
 * fallback used for any name no longer in this map (e.g. after a future trim).
 */
object IconCatalog {
    const val DEFAULT_ICON = "view_kanban"

    val icons: Map<String, ImageVector> = linkedMapOf(
        DEFAULT_ICON to Icons.Filled.ViewKanban,
        "work" to Icons.Filled.Work,
        "business_center" to Icons.Filled.BusinessCenter,
        "code" to Icons.Filled.Code,
        "bug_report" to Icons.Filled.BugReport,
        "design_services" to Icons.Filled.DesignServices,
        "rocket" to Icons.Filled.Rocket,
        "lightbulb" to Icons.Filled.Lightbulb,
        "flag" to Icons.Filled.Flag,
        "timeline" to Icons.Filled.Timeline,
        "article" to Icons.Filled.Article,
        "menu_book" to Icons.Filled.MenuBook,
        "school" to Icons.Filled.School,
        "psychology" to Icons.Filled.Psychology,
        "campaign" to Icons.Filled.Campaign,
        "groups" to Icons.Filled.Groups,
        "phone" to Icons.Filled.Phone,
        "home" to Icons.Filled.Home,
        "apartment" to Icons.Filled.Apartment,
        "family_restroom" to Icons.Filled.FamilyRestroom,
        "kitchen" to Icons.Filled.Kitchen,
        "handyman" to Icons.Filled.Handyman,
        "inventory" to Icons.Filled.Inventory2,
        "local_grocery_store" to Icons.Filled.LocalGroceryStore,
        "shopping_cart" to Icons.Filled.ShoppingCart,
        "restaurant" to Icons.Filled.Restaurant,
        "pets" to Icons.Filled.Pets,
        "directions_car" to Icons.Filled.DirectionsCar,
        "flight" to Icons.Filled.Flight,
        "travel_explore" to Icons.Filled.TravelExplore,
        "terrain" to Icons.Filled.Terrain,
        "landscape" to Icons.Filled.Landscape,
        "fitness_center" to Icons.Filled.FitnessCenter,
        "sports_esports" to Icons.Filled.SportsEsports,
        "music_note" to Icons.Filled.MusicNote,
        "celebration" to Icons.Filled.Celebration,
        "favorite" to Icons.Filled.Favorite,
        "backpack" to Icons.Filled.Backpack,
        "bolt" to Icons.Filled.Bolt,
    )

    fun iconFor(name: String?): ImageVector = icons[name] ?: icons.getValue(DEFAULT_ICON)
}
