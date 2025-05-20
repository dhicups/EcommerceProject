package com.example.ecommerceproject

import android.util.Log
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Dashboard(navController: NavController) {
    val auth = FirebaseAuth.getInstance()
    val dbHelper = DatabaseHelper()
    var userProfile by remember { mutableStateOf<Map<String, Any>?>(null) }
    var message by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Memeriksa autentikasi pengguna
    if (auth.currentUser == null) {
        LaunchedEffect(Unit) {
            Log.w("Dashboard", "Pengguna tidak terautentikasi, mengalihkan ke login")
            message = "Silakan login untuk mengakses dashboard."
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = message,
                    duration = SnackbarDuration.Long
                )
            }
            navController.navigate("login") {
                popUpTo(navController.graph.startDestinationId)
                launchSingleTop = true
            }
        }
        return
    }

    // Memuat profil pengguna
    LaunchedEffect(Unit) {
        try {
            isLoading = true
            Log.d("Dashboard", "Memuat profil pengguna untuk userId=${auth.currentUser?.uid}")
            val currentUser = auth.currentUser ?: throw IllegalStateException("Pengguna tidak terautentikasi")
            if (!currentUser.isEmailVerified && userProfile?.get("role") != DatabaseHelper.UserRole.CUSTOMER) {
                Log.w("Dashboard", "Email belum diverifikasi untuk userId=${currentUser.uid}")
                throw IllegalStateException("Verifikasi email Anda untuk mengakses dashboard.")
            }
            userProfile = dbHelper.getUserProfile()
            if (userProfile == null) {
                Log.w("Dashboard", "Profil pengguna tidak ditemukan untuk userId=${currentUser.uid}")
                throw IllegalStateException("Profil pengguna tidak ditemukan")
            }
        } catch (e: DatabaseException) {
            message = when {
                e.message?.contains("Permission denied") == true -> {
                    "Akses ditolak. Pastikan akun Anda memiliki role yang benar (Admin, Pengelola, atau Supervisor)."
                }
                e.message?.contains("network") == true -> {
                    "Kesalahan jaringan. Periksa koneksi internet Anda."
                }
                else -> {
                    "Gagal memuat data: ${e.message}"
                }
            }
            Log.e("Dashboard", "Gagal memuat data: ${e.message}", e)
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = message,
                    duration = SnackbarDuration.Long
                )
            }
        } catch (e: Exception) {
            message = e.message ?: "Gagal memuat data"
            Log.e("Dashboard", "Gagal memuat data: ${e.message}", e)
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = message,
                    duration = SnackbarDuration.Long
                )
            }
        } finally {
            isLoading = false
        }
    }

    // Delegasi ke dashboard berdasarkan role
    val role = userProfile?.get("role") as? String ?: DatabaseHelper.UserRole.CUSTOMER
    when (role) {
        DatabaseHelper.UserRole.ADMIN -> AdminDashboard(navController, userProfile, isLoading, message, snackbarHostState)
        DatabaseHelper.UserRole.SUPERVISOR -> SupervisorDashboard(navController, userProfile, isLoading, message, snackbarHostState)
        DatabaseHelper.UserRole.PENGELOLA -> PengelolaDashboard(navController, userProfile, isLoading, message, snackbarHostState)
        DatabaseHelper.UserRole.CUSTOMER -> CustomerDashboard(navController, userProfile, isLoading, message, snackbarHostState)
    }
}
