package com.bissoy.smssender

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bissoy.smssender.ui.theme.SmsSenderTheme
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmsSenderTheme {
                val context = LocalContext.current
                var isLoggedIn by remember { mutableStateOf(isUserLoggedIn(context)) }
                var username by remember { mutableStateOf(getUsername(context)) }
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        if (isLoggedIn) {
                            AppMenu(
                                onLogout = {
                                    setUserLoggedIn(context, false)
                                    setUsername(context, "")
                                    isLoggedIn = false
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    if (!isLoggedIn) {
                        LoginScreen(
                            modifier = Modifier.padding(innerPadding),
                            onLoginSuccess = { user ->
                                setUserLoggedIn(context, true)
                                setUsername(context, user)
                                username = user
                                isLoggedIn = true
                            }
                        )
                    } else {
                        Greeting(
                            name = username,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

// Helper functions for login state
fun isUserLoggedIn(context: Context): Boolean {
    val prefs = context.getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
    return prefs.getBoolean("logged_in", false)
}

fun setUserLoggedIn(context: Context, loggedIn: Boolean) {
    val prefs = context.getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
    prefs.edit().putBoolean("logged_in", loggedIn).apply()
}

// Username helpers
fun setUsername(context: Context, username: String) {
    val prefs = context.getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
    prefs.edit().putString("username", username).apply()
}

fun getUsername(context: Context): String {
    val prefs = context.getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
    return prefs.getString("username", "") ?: ""
}

// LoginScreen Composable
@Composable
fun LoginScreen(modifier: Modifier = Modifier, onLoginSuccess: (String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        TextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                loading = true
                error = null
                loginApi(email, password,
                    onSuccess = { user ->
                        loading = false
                        onLoginSuccess(user)
                    },
                    onError = {
                        loading = false
                        error = it
                    }
                )
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (loading) "Logging in..." else "Login")
        }
        error?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}

// API call using OkHttp
fun loginApi(
    email: String,
    password: String,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
) {
    val client = OkHttpClient()
    val json = JSONObject()
    json.put("email", email)
    json.put("password", password)
    val body = RequestBody.create(
        "application/json; charset=utf-8".toMediaType(),
        json.toString()
    )
    val request = Request.Builder()
        .url("https://www.bissoy.com/api/login")
        .post(body)
        .build()
    val mainHandler = Handler(Looper.getMainLooper())
    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Log.e("LoginApi", "Network error", e)
            mainHandler.post { onError("Network error: ${e.localizedMessage ?: "Unknown error"}") }
        }
        override fun onResponse(call: Call, response: Response) {
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                try {
                    val obj = JSONObject(responseBody ?: "{}")
                    val username = obj.optString("username", obj.optString("name", "User"))
                    mainHandler.post { onSuccess(username) }
                } catch (e: JSONException) {
                    mainHandler.post { onSuccess("User") }
                }
            } else {
                val errorBody = response.body?.string()
                Log.e("LoginApi", "API error: $errorBody")
                mainHandler.post { onError("Invalid credentials") }
            }
        }
    })
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = if (name.isNotBlank()) "Welcome, $name!" else "Hello!",
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppMenu(onLogout: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text("SmsSender") },
        actions = {
            IconButton(onClick = { expanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Menu")
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Logout") },
                    onClick = {
                        expanded = false
                        onLogout()
                    }
                )
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SmsSenderTheme {
        Greeting("Android")
    }
}