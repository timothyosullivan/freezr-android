package com.freezr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FreezrApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreezrApp() {
    var items by remember { mutableStateOf(listOf<String>()) }
    var text by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Freezr") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = {
                if (text.isNotBlank()) {
                    items = items + text.trim()
                    text = ""
                }
            }, text = { Text("Add") })
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("New item") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                items(items) { item ->
                    ListItem(headlineContent = { Text(item) })
                    Divider()
                }
            }
        }
    }
}
