package com.netzone.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun RuleStatusBadge(
    wifiBlocked: Boolean,
    mobileBlocked: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        StatusDot(
            isActive = wifiBlocked,
            activeColor = MaterialTheme.colorScheme.error,
            inactiveColor = Color(0xFF4CAF50)
        )
        StatusDot(
            isActive = mobileBlocked,
            activeColor = MaterialTheme.colorScheme.error,
            inactiveColor = Color(0xFF4CAF50)
        )
    }
}

@Composable
private fun StatusDot(
    isActive: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(8.dp),
        shape = MaterialTheme.shapes.small,
        color = if (isActive) activeColor else inactiveColor.copy(alpha = 0.5f)
    ) {}
}

@Composable
fun EmptyState(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    action: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        icon()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        action?.let {
            Spacer(modifier = Modifier.height(16.dp))
            it()
        }
    }
}

@Composable
fun LoadingSkeleton(
    modifier: Modifier = Modifier
) {
    val shimmerColor = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier = modifier.fillMaxWidth()) {
        repeat(5) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = shimmerColor
                ) {}
                Spacer(modifier = Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        modifier = Modifier
                            .width(120.dp)
                            .height(16.dp),
                        shape = MaterialTheme.shapes.small,
                        color = shimmerColor
                    ) {}
                    Surface(
                        modifier = Modifier
                            .width(200.dp)
                            .height(12.dp),
                        shape = MaterialTheme.shapes.small,
                        color = shimmerColor.copy(alpha = 0.6f)
                    ) {}
                }
            }
        }
    }
}
