package com.aro.music.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aro.music.data.appearance.AppIcon
import com.aro.music.data.appearance.AppIconManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppIconViewModel @Inject constructor(private val manager: AppIconManager) : ViewModel() {
    val selected = manager.selected
    private val _changing = MutableStateFlow(false)
    val changing = _changing.asStateFlow()
    private val _failed = MutableStateFlow(false)
    val failed = _failed.asStateFlow()

    fun select(icon: AppIcon) {
        if (_changing.value) return
        _changing.value = true
        _failed.value = false
        viewModelScope.launch {
            try {
                manager.select(icon)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _failed.value = true
            } finally {
                _changing.value = false
            }
        }
    }

    fun clearFailure() { _failed.value = false }
}
