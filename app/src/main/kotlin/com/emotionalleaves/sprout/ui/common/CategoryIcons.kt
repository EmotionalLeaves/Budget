package com.emotionalleaves.sprout.ui.common

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
        else -> Icons.AutoMirrored.Rounded.Label
    }
}
