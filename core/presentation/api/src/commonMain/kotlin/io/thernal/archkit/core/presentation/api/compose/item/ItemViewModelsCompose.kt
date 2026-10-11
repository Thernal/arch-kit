package io.thernal.archkit.core.presentation.api.compose.item

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

/** The [ItemViewModels] of a list whose items are [keys], kept in the screen's ViewModel store. */
@Composable
fun rememberItemViewModels(
    keys: List<Any>,
    name: String = "items",
): ItemViewModels {
    val items = viewModel(key = "ItemViewModels:$name") { ItemViewModels() }
    val keySet = remember(keys) { keys.toSet() }
    SideEffect { items.retain(keySet) }
    return items
}

/** [content] with the item's own ViewModel store: `viewModel { … }` inside it is the item's. */
@Composable
fun ItemScope(
    items: ItemViewModels,
    key: Any,
    content: @Composable () -> Unit,
) {
    DisposableEffect(key1 = items, key2 = key) {
        items.attach(key)
        onDispose { items.detach(key) }
    }
    CompositionLocalProvider(
        value = LocalViewModelStoreOwner provides items.owner(key),
        content = content,
    )
}
