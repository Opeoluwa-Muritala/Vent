package app.vent

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import app.vent.composeapp.generated.resources.Res
import app.vent.composeapp.generated.resources.conference_stage
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun VenuePhoto(modifier: Modifier) {
    Image(painterResource(Res.drawable.conference_stage), null, modifier, contentScale = ContentScale.Crop)
}
