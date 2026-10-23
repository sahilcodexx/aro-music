package com.aro.music.presentation.components

import androidx.compose.material3.*
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.aro.music.R

@Composable
fun SavedCollectionButton(saved: Boolean, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick) {
        Icon(if (saved) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(if (saved) R.string.collection_saved else R.string.collection_save))
    }
}
