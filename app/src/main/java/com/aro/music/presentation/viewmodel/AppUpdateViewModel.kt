package com.aro.music.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aro.music.data.update.AppUpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class AppUpdateViewModel @Inject constructor(private val repository: AppUpdateRepository) : ViewModel() {
    val state = repository.state
    fun checkAutomatically() { viewModelScope.launch { repository.check() } }
    fun checkNow() { viewModelScope.launch { repository.check(manual = true) } }
    fun dismiss() = repository.dismiss()
}
