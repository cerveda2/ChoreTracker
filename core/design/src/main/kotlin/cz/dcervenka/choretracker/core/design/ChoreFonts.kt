package cz.dcervenka.choretracker.core.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

// Both families ship from Google Fonts as single variable-weight TTFs (see
// core/design/src/main/assets/fonts/OFL_*.txt for licensing), loaded as assets rather than
// through R.font so a later KMP migration only has to replace this one file, not the resource
// system the rest of the type scale would otherwise depend on.
private const val BRICOLAGE_GROTESQUE_PATH = "fonts/bricolage_grotesque.ttf"
private const val PLUS_JAKARTA_SANS_PATH = "fonts/plus_jakarta_sans.ttf"

object ChoreFonts {

    @Composable
    fun bricolageGrotesque(): FontFamily {
        val assets = LocalContext.current.assets
        return remember {
            FontFamily(
                Font(
                    BRICOLAGE_GROTESQUE_PATH,
                    assets,
                    weight = FontWeight.Medium,
                    variationSettings = FontVariation.Settings(FontVariation.weight(500)),
                ),
                Font(
                    BRICOLAGE_GROTESQUE_PATH,
                    assets,
                    weight = FontWeight.SemiBold,
                    variationSettings = FontVariation.Settings(FontVariation.weight(600)),
                ),
                Font(
                    BRICOLAGE_GROTESQUE_PATH,
                    assets,
                    weight = FontWeight.Bold,
                    variationSettings = FontVariation.Settings(FontVariation.weight(700)),
                ),
            )
        }
    }

    @Composable
    fun plusJakartaSans(): FontFamily {
        val assets = LocalContext.current.assets
        return remember {
            FontFamily(
                Font(
                    PLUS_JAKARTA_SANS_PATH,
                    assets,
                    weight = FontWeight.Normal,
                    variationSettings = FontVariation.Settings(FontVariation.weight(400)),
                ),
                Font(
                    PLUS_JAKARTA_SANS_PATH,
                    assets,
                    weight = FontWeight.Medium,
                    variationSettings = FontVariation.Settings(FontVariation.weight(500)),
                ),
                Font(
                    PLUS_JAKARTA_SANS_PATH,
                    assets,
                    weight = FontWeight.SemiBold,
                    variationSettings = FontVariation.Settings(FontVariation.weight(600)),
                ),
                Font(
                    PLUS_JAKARTA_SANS_PATH,
                    assets,
                    weight = FontWeight.Bold,
                    variationSettings = FontVariation.Settings(FontVariation.weight(700)),
                ),
            )
        }
    }
}
