package com.brbrs.tavla.ui.viewmodels

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.tavla.auth.UserAvatarRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Thin Compose-facing bridge to [UserAvatarRepository]: a `mutableStateMapOf` so [UserAvatar]
 * can just call [avatarFor] from any recomposition and get a reactive, de-duplicated fetch
 * without every call site needing its own LaunchedEffect/remember boilerplate.
 */
@HiltViewModel
class UserAvatarViewModel @Inject constructor(
    private val repository: UserAvatarRepository,
) : ViewModel() {
    private val cache = mutableStateMapOf<String, Bitmap?>()

    /** Null while unresolved (no fetch yet, or still loading) or if the user has no photo. */
    fun avatarFor(uid: String): Bitmap? {
        if (uid.isBlank()) return null
        if (!cache.containsKey(uid)) {
            cache[uid] = null
            viewModelScope.launch { cache[uid] = repository.avatarFor(uid) }
        }
        return cache[uid]
    }
}
