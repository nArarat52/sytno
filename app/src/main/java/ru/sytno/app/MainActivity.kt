package ru.sytno.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.remember
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment

class MainActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SytnoTheme { val factory=remember(application){ViewModelProvider.AndroidViewModelFactory(application)};val vm:AppViewModel=viewModel(factory=factory); val state=vm.state; if(state==null)Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()} else SytnoApp(vm,state) } }
    }
}
