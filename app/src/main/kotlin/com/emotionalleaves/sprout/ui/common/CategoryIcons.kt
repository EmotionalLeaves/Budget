package com.emotionalleaves.sprout.ui.common

import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.LocalBar
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import com.emotionalleaves.sprout.domain.categorize.DefaultCategories as K

/** One icon per built-in category (by its stable key); categories you add get a tag. Decorative: names are always shown. */
object CategoryIcons {
    fun forKey(iconKey: String?): ImageVector = when (iconKey) {
        K.INCOME -> Icons.Rounded.Payments
        K.RENT -> Icons.Rounded.Home
        K.UTILITIES -> Icons.Rounded.Bolt
        K.PHONE -> Icons.Rounded.Wifi
        K.GROCERIES -> Icons.Rounded.ShoppingCart
        K.EATING_OUT -> Icons.Rounded.Restaurant
        K.GAS -> Icons.Rounded.LocalGasStation
        K.TRANSPORT -> Icons.Rounded.DirectionsBus
        K.SUBSCRIPTIONS -> Icons.Rounded.Autorenew
        K.SHOPPING -> Icons.Rounded.ShoppingBag
        K.HOBBIES -> Icons.Rounded.Palette
        K.HEALTH -> Icons.Rounded.LocalHospital
        K.PERSONAL_CARE -> Icons.Rounded.Spa
        K.ENTERTAINMENT -> Icons.Rounded.Movie
        K.INSURANCE -> Icons.Rounded.Shield
        K.FEES -> Icons.Rounded.Percent
        K.P2P -> Icons.Rounded.People
        K.GIFTS -> Icons.Rounded.CardGiftcard
        K.OTHER -> Icons.Rounded.MoreHoriz
        K.UNCATEGORIZED -> Icons.AutoMirrored.Rounded.HelpOutline
        K.TRANSFERS -> Icons.Rounded.SwapHoriz
        K.TRAVEL -> Icons.Rounded.Flight
        K.PETS -> Icons.Rounded.Pets
        else -> EXTRA[iconKey] ?: Icons.AutoMirrored.Rounded.Label
    }

    /** Extra icons you can pick for any category (stored in the category's iconKey). */
    private val EXTRA: Map<String, ImageVector> by lazy {
        mapOf(
            "i:pets" to Icons.Rounded.Pets, "i:child" to Icons.Rounded.ChildCare, "i:school" to Icons.Rounded.School,
            "i:flight" to Icons.Rounded.Flight, "i:gym" to Icons.Rounded.FitnessCenter, "i:coffee" to Icons.Rounded.LocalCafe,
            "i:car" to Icons.Rounded.DirectionsCar, "i:book" to Icons.Rounded.MenuBook, "i:music" to Icons.Rounded.MusicNote,
            "i:game" to Icons.Rounded.SportsEsports, "i:heart" to Icons.Rounded.Favorite, "i:tax" to Icons.Rounded.AccountBalance,
            "i:savings" to Icons.Rounded.Savings, "i:work" to Icons.Rounded.Work, "i:brush" to Icons.Rounded.Brush,
            "i:bar" to Icons.Rounded.LocalBar, "i:build" to Icons.Rounded.Build, "i:laundry" to Icons.Rounded.LocalLaundryService,
            "i:park" to Icons.Rounded.Park, "i:star" to Icons.Rounded.Star,
        )
    }

    /** Everything the icon picker offers: the built-in category icons, then the extras. */
    val pickable: List<String> by lazy {
        listOf(K.GROCERIES, K.EATING_OUT, K.RENT, K.UTILITIES, K.PHONE, K.GAS, K.TRANSPORT, K.SUBSCRIPTIONS, K.SHOPPING, K.HOBBIES,
            K.HEALTH, K.PERSONAL_CARE, K.ENTERTAINMENT, K.INSURANCE, K.GIFTS, K.P2P, K.INCOME, K.TRAVEL, K.PETS) + EXTRA.keys
    }
}
