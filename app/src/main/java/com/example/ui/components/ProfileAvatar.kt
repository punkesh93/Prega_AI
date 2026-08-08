package com.example.ui.components

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.PregaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Her face in the app — or her initial until she chooses one.
 *
 * The photo is picked with the system Photo Picker (no permission) and
 * copied to one fixed file in private storage; like every other image in
 * this app it exists nowhere but the phone. A bumping [refreshKey] state
 * forces the decode to re-run after a new photo overwrites the same path.
 *
 * [editable] avatars open the picker on tap; non-editable ones (the home
 * header chip, which opens the menu instead) just display.
 */
private const val AVATAR_DIR = "profile"
private const val AVATAR_FILE = "avatar.jpg"

// Session-wide cache-buster shared by every avatar instance, so changing
// the photo in the drawer instantly refreshes the header chip too.
private val avatarVersion = mutableStateOf(0)

@Composable
fun ProfileAvatar(
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    editable: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val version by avatarVersion

    val bitmap by produceState<android.graphics.Bitmap?>(null, version) {
        value = withContext(Dispatchers.IO) {
            val f = avatarFile(context)
            if (f.exists()) runCatching {
                android.graphics.BitmapFactory.decodeFile(f.absolutePath)
            }.getOrNull() else null
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                withContext(Dispatchers.IO) { copyAvatar(context, uri) }
                avatarVersion.value += 1
            }
        }
    }

    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(PregaTheme.colors.sageSoft)
            .clickable {
                when {
                    editable -> picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                    onClick != null -> onClick()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = "Profile photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        } else {
            Text(
                name.take(1).uppercase().ifBlank { "P" },
                style = if (size >= 56.dp) MaterialTheme.typography.headlineMedium
                else MaterialTheme.typography.titleMedium,
                color = PregaTheme.colors.sage,
            )
        }
    }
}

private fun avatarFile(context: Context): File =
    File(File(context.filesDir, AVATAR_DIR).apply { mkdirs() }, AVATAR_FILE)

private fun copyAvatar(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            avatarFile(context).outputStream().use { output -> input.copyTo(output) }
        }
    }
}
