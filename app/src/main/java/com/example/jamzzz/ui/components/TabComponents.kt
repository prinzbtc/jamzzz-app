package com.example.jamzzz.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jamzzz.ui.theme.TextWhite
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import com.google.accompanist.pager.pagerTabIndicatorOffset
import kotlinx.coroutines.launch

// Tab data class
data class TabItem(
    val title: String,
    val icon: ImageVector,
    val screen: @Composable () -> Unit
)

// Tabbed screen composable
@OptIn(com.google.accompanist.pager.ExperimentalPagerApi::class)
@Composable
fun TabbedScreen(
    tabs: List<TabItem>,
    initialTabIndex: Int = 0,
    onTabChanged: (Int) -> Unit = {}
) {
    val pagerState = rememberPagerState(initialPage = initialTabIndex)
    val coroutineScope = rememberCoroutineScope()
    
    // Notify about tab changes
    LaunchedEffect(pagerState.currentPage) {
        onTabChanged(pagerState.currentPage)
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Tab row
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            backgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.9f),
            contentColor = MaterialTheme.colors.onPrimary,
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    modifier = Modifier.pagerTabIndicatorOffset(pagerState, tabPositions),
                    color = MaterialTheme.colors.secondary
                )
            }
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title,
                            tint = if (pagerState.currentPage == index) 
                                   MaterialTheme.colors.secondary 
                                   else TextWhite.copy(alpha = 0.6f)
                        )
                    },
                    text = {
                        Text(
                            text = tab.title,
                            color = if (pagerState.currentPage == index) 
                                   MaterialTheme.colors.secondary 
                                   else TextWhite.copy(alpha = 0.6f)
                        )
                    }
                )
            }
        }
        
        // Tab content
        HorizontalPager(
            state = pagerState,
            count = tabs.size,
            modifier = Modifier.weight(1f)
        ) { page ->
            tabs[page].screen()
        }
    }
}

// Empty state composable
@Composable
fun EmptyState(
    icon: ImageVector,
    message: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colors.primary.copy(alpha = 0.5f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = message,
            style = MaterialTheme.typography.h6,
            color = TextWhite.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        
        if (actionText != null && onAction != null) {
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.primary
                )
            ) {
                Text(text = actionText)
            }
        }
    }
}

// Playlist item composable
@Composable
fun PlaylistItem(
    name: String,
    songCount: Int,
    onClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        elevation = 2.dp,
        backgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.9f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.QueueMusic,
                contentDescription = "Playlist",
                tint = MaterialTheme.colors.primary,
                modifier = Modifier.size(40.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.h6,
                    color = TextWhite
                )
                
                Text(
                    text = "$songCount songs",
                    style = MaterialTheme.typography.body2,
                    color = TextWhite.copy(alpha = 0.7f)
                )
            }
            
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "More Options",
                        tint = MaterialTheme.colors.primary
                    )
                }
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(MaterialTheme.colors.surface)
                ) {
                    DropdownMenuItem(onClick = {
                        showMenu = false
                        onRenameClick()
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Rename Playlist",
                                tint = TextWhite
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Rename playlist", color = TextWhite)
                        }
                    }
                    
                    DropdownMenuItem(onClick = {
                        showMenu = false
                        onDeleteClick()
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Delete Playlist",
                                tint = Color.Red
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete playlist", color = Color.Red)
                        }
                    }
                }
            }
        }
    }
}
