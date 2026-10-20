package com.example.desayuno.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.desayuno.model.UserRole

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFFD84315),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCF),
    onPrimaryContainer = Color(0xFF3B0900),
    secondary = Color(0xFF00695C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2DFDB),
    onSecondaryContainer = Color(0xFF00201A),
    tertiary = Color(0xFF6D4C41),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD7CCC8),
    onTertiaryContainer = Color(0xFF271A16),
    background = Color(0xFFFFF8F6),
    surface = Color.White,
    surfaceVariant = Color(0xFFFBEBE6),
    onSurfaceVariant = Color(0xFF53433F),
    outline = Color(0xFF85736E),
    outlineVariant = Color(0xFFD8C2BC)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF8A65),
    onPrimary = Color(0xFF5F1500),
    primaryContainer = Color(0xFF8C2800),
    onPrimaryContainer = Color(0xFFFFDBCF),
    secondary = Color(0xFF80CBC4),
    onSecondary = Color(0xFF003730),
    secondaryContainer = Color(0xFF005047),
    onSecondaryContainer = Color(0xFFB2DFDB),
    tertiary = Color(0xFFBCAAA4),
    onTertiary = Color(0xFF3E2723),
    tertiaryContainer = Color(0xFF5D4037),
    onTertiaryContainer = Color(0xFFD7CCC8),
    background = Color(0xFF1A110F),
    surface = Color(0xFF251A17),
    surfaceVariant = Color(0xFF352623),
    onSurfaceVariant = Color(0xFFD8C2BC),
    outline = Color(0xFFA08C87),
    outlineVariant = Color(0xFF53433F)
)

val M3Shapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun DesayunoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colors,
        shapes = M3Shapes,
        content = content
    )
}

@Composable
fun DesayunoApp(
    viewModel: DesayunoViewModel = viewModel()
) {
    val currentUser by viewModel.currentUser.collectAsState()

    DesayunoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Crossfade(targetState = currentUser, label = "UserRoleTransition") { user ->
                if (user == null) {
                    AuthScreen(viewModel = viewModel)
                } else {
                    when (user.role) {
                        UserRole.CLIENT -> ClientMainScreen(viewModel = viewModel)
                        UserRole.COOK -> CookMainScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
