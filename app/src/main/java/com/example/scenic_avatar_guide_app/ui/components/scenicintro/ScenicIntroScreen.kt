package com.example.scenic_avatar_guide_app.ui.components.scenicintro

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.scenic_avatar_guide_app.core.common.UiState
import com.example.scenic_avatar_guide_app.domain.model.*
import com.example.scenic_avatar_guide_app.ui.screens.ScenicIntroViewModel

// ==================== Main Screen ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScenicIntroScreen(
    modifier: Modifier = Modifier,
    viewModel: ScenicIntroViewModel = hiltViewModel(),
    showScenicSelector: Boolean = true
) {
    val introState by viewModel.introState.collectAsState()
    val indexState by viewModel.indexState.collectAsState()
    val selectedScenicId by viewModel.selectedScenicId.collectAsState()

    val indexItems = when (val state = indexState) {
        is UiState.Success -> state.data.scenics
        else -> emptyList()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "景区导览",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1C2328)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFFFFBFA)
                ),
                actions = {
                    if (showScenicSelector && indexItems.isNotEmpty()) {
                        ScenicSelectorChip(
                            indexItems = indexItems,
                            selectedScenicId = selectedScenicId,
                            onScenicSelected = viewModel::selectScenic,
                            modifier = Modifier.padding(end = 16.dp, top = 8.dp, bottom = 8.dp)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        when (val state = introState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFFB91C1C))
                }
            }
            is UiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.message,
                            color = Color(0xFFC44536),
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.loadScenicIntro(selectedScenicId) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFB91C1C)
                            )
                        ) {
                            Text("重试")
                        }
                    }
                }
            }
            is UiState.Success -> {
                val content = state.data
                if (content.scenicId == "1911museum") {
                    XinhaiImmersiveIntro(
                        indexItems = indexItems,
                        selectedScenicId = selectedScenicId,
                        onScenicSelected = viewModel::selectScenic,
                        showScenicSelector = showScenicSelector
                    )
                    return@Scaffold
                }
                if (content.scenicId == "site_of_the_august_7th_conference") {
                    XinhaiImmersiveIntro(
                        pages = BaqiStoryBookPages,
                        indexItems = indexItems,
                        selectedScenicId = selectedScenicId,
                        onScenicSelected = viewModel::selectScenic,
                        showScenicSelector = showScenicSelector
                    )
                    return@Scaffold
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    content.heroImage?.let { hero ->
                        item {
                            HeroImageSection(heroImage = hero, scenicName = content.scenicName, subtitle = content.subtitle)
                        }
                    }

                    content.highlights?.let { highlights ->
                        if (highlights.isNotEmpty()) {
                            item {
                                HighlightsSection(highlights = highlights)
                            }
                        }
                    }

                    items(content.sections) { section ->
                        SectionRenderer(section = section)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScenicSelectorChip(
    indexItems: List<ScenicIndexItem>,
    selectedScenicId: String,
    onScenicSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedItem = indexItems.find { it.scenicId == selectedScenicId }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterEnd
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(min = 116.dp, max = 184.dp)
                    .height(34.dp)
                    .menuAnchor()
                    .clickable { expanded = true },
                shape = RoundedCornerShape(18.dp),
                color = Color.White.copy(alpha = 0.78f),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = selectedItem?.name ?: "选择景区",
                        modifier = Modifier.weight(1f, fill = false),
                        fontSize = 12.sp,
                        color = Color(0xFF5A6772),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color(0xFF8C4B45),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color.White)
            ) {
                indexItems.forEach { item ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = item.name,
                                fontSize = 14.sp,
                                color = Color(0xFF1C2328),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        onClick = {
                            onScenicSelected(item.scenicId)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

// ==================== Xinhai Immersive Intro ====================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun XinhaiImmersiveIntro(
    pages: List<XinhaiStoryBookPageData> = XinhaiStoryBookPages,
    indexItems: List<ScenicIndexItem>,
    selectedScenicId: String,
    onScenicSelected: (String) -> Unit,
    showScenicSelector: Boolean
) {
    val storyPages = remember(pages) { pages }
    val pagerState = rememberPagerState(pageCount = { storyPages.size })
    val coroutineScope = rememberCoroutineScope()
    val currentPage = storyPages[pagerState.currentPage]
    val firstPageId = storyPages.firstOrNull()?.id.orEmpty()

    fun goToPage(id: String) {
        val targetIndex = storyPages.indexOfFirst { it.id == id }
        if (targetIndex >= 0) {
            coroutineScope.launch { pagerState.animateScrollToPage(targetIndex) }
        }
    }

    fun goToNext() {
        if (pagerState.currentPage < storyPages.lastIndex) {
            coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF120809),
                        Color(0xFF301014),
                        Color(0xFF7C2D22)
                    )
                )
            )
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { storyPages[it].id }
        ) { pageIndex ->
            XinhaiStoryBookPage(
                page = storyPages[pageIndex],
                pageIndex = pageIndex,
                pageCount = storyPages.size,
                firstPageId = firstPageId,
                onCardClick = ::goToPage,
                onNextClick = ::goToNext
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            storyPages.forEachIndexed { index, page ->
                Box(
                    modifier = Modifier
                        .width(if (index == pagerState.currentPage) 22.dp else 7.dp)
                        .height(7.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (index == pagerState.currentPage) page.accent
                            else Color.White.copy(alpha = 0.32f)
                        )
                )
            }
        }

        if (showScenicSelector && indexItems.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 2.dp)
            ) {
                ScenicSelectorChip(
                    indexItems = indexItems,
                    selectedScenicId = selectedScenicId,
                    onScenicSelected = onScenicSelected,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        Text(
            text = "第 ${pagerState.currentPage + 1} 幕 / ${storyPages.size} · ${currentPage.levelName}",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.58f)
        )
    }
}

@Composable
private fun XinhaiStoryBookPage(
    page: XinhaiStoryBookPageData,
    pageIndex: Int,
    pageCount: Int,
    firstPageId: String,
    onCardClick: (String) -> Unit,
    onNextClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 54.dp, bottom = 40.dp)
    ) {
        item {
            XinhaiImmersiveImagePanel(page = page)
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Text(
                    text = page.eyebrow,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = page.accent
                )
                Text(
                    text = page.title,
                    modifier = Modifier.padding(top = 5.dp),
                    fontSize = 28.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = page.subtitle,
                    modifier = Modifier.padding(top = 8.dp),
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = Color.White.copy(alpha = 0.82f)
                )
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    page.tags.take(3).forEach { tag ->
                        XinhaiBookTag(tag = tag, accent = page.accent)
                    }
                }
            }
        }

        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 4.dp),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFFFFFBF4).copy(alpha = 0.95f),
                shadowElevation = 8.dp
            ) {
                Text(
                    text = page.body,
                    modifier = Modifier.padding(18.dp),
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                    color = Color(0xFF2D1B1C)
                )
            }
        }

        if (page.detailBlocks.isNotEmpty()) {
            item {
                XinhaiDetailBlocksPanel(
                    blocks = page.detailBlocks,
                    accent = page.accent
                )
            }
        }

        if (page.focusItems.isNotEmpty()) {
            item {
                XinhaiFocusPanel(page = page)
            }
        }

        if (page.gallery.isNotEmpty()) {
            item {
                XinhaiGalleryPanel(page = page)
            }
        }

        if (page.cards.isNotEmpty()) {
            item {
                Text(
                    text = "继续探索",
                    modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 8.dp),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            items(page.cards) { card ->
                XinhaiBookLinkCard(
                    card = card,
                    accent = page.accent,
                    onClick = { onCardClick(card.targetPageId) }
                )
            }
        }

        item {
            XinhaiContinueCard(
                page = page,
                isLast = pageIndex == pageCount - 1,
                onNextClick = onNextClick,
                onRestartClick = { onCardClick(firstPageId) }
            )
        }
    }
}

@Composable
private fun XinhaiImmersiveImagePanel(page: XinhaiStoryBookPageData) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (page.level == 0) 320.dp else 260.dp)
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(28.dp))
    ) {
        AsyncImage(
            model = page.imageUrl,
            contentDescription = page.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color(0xFF120809).copy(alpha = 0.18f),
                            Color(0xFF120809).copy(alpha = 0.82f)
                        )
                    )
                )
        )
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp),
            shape = RoundedCornerShape(999.dp),
            color = Color.Black.copy(alpha = 0.32f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
        ) {
            Text(
                text = page.levelName,
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                fontSize = 12.sp,
                color = Color.White
            )
        }
        page.caption?.let { caption ->
            Text(
                text = caption,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = Color.White.copy(alpha = 0.78f)
            )
        }
    }
}

@Composable
private fun XinhaiBookLinkCard(
    card: XinhaiBookCard,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFFFFCF7).copy(alpha = 0.94f),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = card.imageUrl,
                contentDescription = card.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(112.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 13.dp, vertical = 10.dp)
            ) {
                Text(
                    text = card.badge,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                Text(
                    text = card.title,
                    modifier = Modifier.padding(top = 3.dp),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2A181A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = card.subtitle,
                    modifier = Modifier.padding(top = 4.dp),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = Color(0xFF6F5C56),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun XinhaiFocusPanel(page: XinhaiStoryBookPageData) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "本幕看点",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(10.dp))
            page.focusItems.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 7.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        modifier = Modifier.size(34.dp),
                        shape = CircleShape,
                        color = page.accent.copy(alpha = 0.95f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = (index + 1).toString().padStart(2, '0'),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp)
                    ) {
                        Text(
                            text = item.label,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.62f)
                        )
                        Text(
                            text = item.value,
                            modifier = Modifier.padding(top = 1.dp),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = item.description,
                            modifier = Modifier.padding(top = 4.dp),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = Color.White.copy(alpha = 0.72f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun XinhaiGalleryPanel(page: XinhaiStoryBookPageData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
    ) {
        Text(
            text = "图像线索",
            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(page.gallery) { image ->
                Surface(
                    modifier = Modifier
                        .width(236.dp)
                        .height(202.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFFFFFCF7).copy(alpha = 0.94f),
                    shadowElevation = 5.dp
                ) {
                    Column {
                        AsyncImage(
                            model = image.imageUrl,
                            contentDescription = image.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(118.dp)
                                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                        )
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                            Text(
                                text = image.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2A181A),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = image.caption,
                                modifier = Modifier.padding(top = 4.dp),
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                color = Color(0xFF6F5C56),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun XinhaiContinueCard(
    page: XinhaiStoryBookPageData,
    isLast: Boolean,
    onNextClick: () -> Unit,
    onRestartClick: () -> Unit
) {
    val action = if (isLast) "回到开篇" else page.nextLabel
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 20.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable { if (isLast) onRestartClick() else onNextClick() },
        shape = RoundedCornerShape(24.dp),
        color = page.accent.copy(alpha = 0.96f),
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = if (isLast) "已到达最后一幕" else "读到这里，进入下一幕",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.78f)
            )
            Text(
                text = action,
                modifier = Modifier.padding(top = 5.dp),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "也可以左右滑动切换页面",
                modifier = Modifier.padding(top = 6.dp),
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.72f)
            )
        }
    }
}

// ==================== Detail Blocks Panel (公众号风格) ====================

@Composable
private fun XinhaiDetailBlocksPanel(
    blocks: List<XinhaiDetailBlock>,
    accent: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        // 区域标题
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent)
            )
            Text(
                text = "主题详解",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        blocks.forEachIndexed { index, block ->
            when (block.style) {
                DetailStyle.IMAGE_LEAD -> ImageLeadBlock(block = block, accent = accent)
                DetailStyle.QUOTE -> QuoteBlock(block = block, accent = accent)
                DetailStyle.HIGHLIGHT -> HighlightBlock(block = block, accent = accent)
                DetailStyle.STANDARD -> StandardBlock(block = block, accent = accent)
            }

            if (index < blocks.lastIndex) {
                Spacer(modifier = Modifier.height(16.dp))
                Divider(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    color = Color.White.copy(alpha = 0.12f),
                    thickness = 1.dp
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun StandardBlock(block: XinhaiDetailBlock, accent: Color) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // 顶部装饰条
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(accent.copy(alpha = 0.7f))
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
            color = Color(0xFFFFFBF4).copy(alpha = 0.96f),
            shadowElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = block.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2A181A),
                    lineHeight = 24.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                block.imageUrl?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = block.imageCaption,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    block.imageCaption?.let { caption ->
                        Text(
                            text = caption,
                            modifier = Modifier.padding(top = 6.dp, start = 2.dp),
                            fontSize = 11.sp,
                            color = Color(0xFF8B7D77)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Text(
                    text = block.body,
                    fontSize = 14.sp,
                    lineHeight = 23.sp,
                    color = Color(0xFF3D2E2A)
                )
            }
        }
    }
}

@Composable
private fun ImageLeadBlock(block: XinhaiDetailBlock, accent: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFFFFBF4).copy(alpha = 0.96f),
        shadowElevation = 6.dp
    ) {
        Column {
            block.imageUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = block.imageCaption,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                )
            }
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = block.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2A181A)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = block.body,
                    fontSize = 14.sp,
                    lineHeight = 23.sp,
                    color = Color(0xFF3D2E2A)
                )
            }
        }
    }
}

@Composable
private fun QuoteBlock(block: XinhaiDetailBlock, accent: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = accent.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "\"",
                fontSize = 36.sp,
                color = accent,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = block.body,
                fontSize = 15.sp,
                lineHeight = 24.sp,
                color = Color.White.copy(alpha = 0.92f),
                fontStyle = FontStyle.Italic
            )
            block.imageCaption?.let { caption ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "—— $caption",
                    fontSize = 12.sp,
                    color = accent.copy(alpha = 0.85f),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
private fun HighlightBlock(block: XinhaiDetailBlock, accent: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFFFFBF4).copy(alpha = 0.96f),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = block.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2A181A)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = block.body,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = Color(0xFF3D2E2A)
                )
            }
        }
    }
}

@Composable
private fun XinhaiBookTag(tag: String, accent: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = accent.copy(alpha = 0.18f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.36f))
    ) {
        Text(
            text = tag,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            fontSize = 11.sp,
            color = Color.White
        )
    }
}

// ==================== Data Classes ====================

private data class XinhaiDetailBlock(
    val title: String,
    val body: String,
    val imageUrl: String? = null,
    val imageCaption: String? = null,
    val style: DetailStyle = DetailStyle.STANDARD
)

private enum class DetailStyle {
    STANDARD,
    IMAGE_LEAD,
    QUOTE,
    HIGHLIGHT
}

private data class XinhaiStoryBookPageData(
    val id: String,
    val level: Int,
    val levelName: String,
    val eyebrow: String,
    val title: String,
    val subtitle: String,
    val body: String,
    val imageUrl: String,
    val caption: String? = null,
    val tags: List<String>,
    val accent: Color,
    val focusItems: List<XinhaiFocusItem> = emptyList(),
    val gallery: List<XinhaiGalleryImage> = emptyList(),
    val cards: List<XinhaiBookCard> = emptyList(),
    val detailBlocks: List<XinhaiDetailBlock> = emptyList(),
    val nextLabel: String = "继续"
)

private data class XinhaiFocusItem(
    val label: String,
    val value: String,
    val description: String
)

private data class XinhaiGalleryImage(
    val imageUrl: String,
    val title: String,
    val caption: String
)

private data class XinhaiBookCard(
    val title: String,
    val subtitle: String,
    val imageUrl: String,
    val badge: String,
    val targetPageId: String
)

private const val XinhaiImageBase = "file:///android_asset/scenic_intro/images/xinhai/immersive/"

private const val XinhaiArtifactBase = "file:///android_asset/scenic_intro/images/xinhai/artifacts/"

private const val BaqiImageBase = "file:///android_asset/scenic_intro/images/baqi/"

// ==================== Page Data ====================

private val BaqiStoryBookPages = listOf(
    XinhaiStoryBookPageData(
        id = "baqi_opening",
        level = 0,
        levelName = "会址印象",
        eyebrow = "八七会议会址纪念馆",
        title = "危急关头的历史转折",
        subtitle = "1927年8月7日，中共中央在汉口秘密召开紧急会议，重新回答中国革命向何处去。",
        body = "八七会议会址纪念馆依托汉口鄱阳街旧址而建。这里原是一排西式公寓的一部分，会议就在二楼小房间里举行。大革命失败后，党组织遭受严重破坏，许多人转入地下；正是在这样的危险环境中，会议完成了路线和方向的重大调整。",
        imageUrl = BaqiImageBase + "memorial_1.jpg",
        caption = "八七会议会址纪念馆外观。",
        tags = listOf("汉口旧址", "1927", "历史转折"),
        accent = Color(0xFFE26D4D),
        detailBlocks = listOf(
            XinhaiDetailBlock(
                title = "旧址与会议现场",
                body = "会址并不宏大，却承载了极重的历史分量。参观时可以重点看二楼复原场景、会议桌椅、代表图像和馆名题写，它们把一次秘密会议从文献记载拉回到真实空间。",
                imageUrl = BaqiImageBase + "second_floor.jpg",
                imageCaption = "会址二楼复原场景。",
                style = DetailStyle.IMAGE_LEAD
            ),
            XinhaiDetailBlock(
                title = "为什么必须召开",
                body = "1927年春夏，国共合作破裂，大革命遭遇失败。继续依赖旧的合作方式，还是独立领导群众斗争；继续回避武装问题，还是建立自己的革命武装，成为必须回答的问题。",
                style = DetailStyle.STANDARD
            )
        ),
        focusItems = listOf(
            XinhaiFocusItem("时间地点", "1927年8月7日 · 汉口", "会议在白色恐怖笼罩下秘密举行，时间短，议题重。"),
            XinhaiFocusItem("参观重点", "二楼会场复原", "从真实空间理解秘密会议的紧张处境。"),
            XinhaiFocusItem("历史定位", "危机中的重新出发", "会议让中国共产党从被动应对转向独立领导革命斗争。")
        ),
        gallery = listOf(
            XinhaiGalleryImage(BaqiImageBase + "memorial_1.jpg", "纪念馆外观", "会址建筑保存了汉口近代街区的历史肌理。"),
            XinhaiGalleryImage(BaqiImageBase + "second_floor.jpg", "二楼会场", "复原空间帮助观众进入会议现场。")
        ),
        cards = listOf(
            XinhaiBookCard("一天会议", "看八七会议如何完成重大政治转向。", BaqiImageBase + "meeting_painting.jpg", "下一幕", "baqi_meeting")
        ),
        nextLabel = "进入会议现场"
    ),
    XinhaiStoryBookPageData(
        id = "baqi_meeting",
        level = 1,
        levelName = "会议现场",
        eyebrow = "一天之内的重大决定",
        title = "从总结失败到明确方向",
        subtitle = "会议集中纠正右倾错误，确定土地革命和武装斗争总方针。",
        body = "八七会议不是简单回顾过去，而是在极端危险中作出面向未来的政治决断。会议总结大革命失败教训，改组中央领导机构，通过重要文件，并把武装斗争问题放到新的位置。",
        imageUrl = BaqiImageBase + "meeting_painting.jpg",
        caption = "油画作品《八七会议》。",
        tags = listOf("纠正错误", "土地革命", "武装斗争"),
        accent = Color(0xFFE5A94B),
        detailBlocks = listOf(
            XinhaiDetailBlock(
                title = "会议记录留下的紧张讨论",
                body = "馆内展示的会议记录复制件提示我们：转折不是凭空发生的，而是在大革命失败后的痛苦反思中形成的。代表们围绕农民运动、职工运动、军事斗争和党的组织问题展开讨论。",
                imageUrl = BaqiImageBase + "meeting_record.jpg",
                imageCaption = "会议记录手稿复制件。",
                style = DetailStyle.IMAGE_LEAD
            ),
            XinhaiDetailBlock(
                title = "重要论断",
                body = "毛泽东在会上提出以后要非常重视军事，须知政权是由枪杆子中取得的。这句话来自血的教训，也成为理解此后革命道路转变的重要钥匙。",
                imageCaption = "八七会议的核心记忆",
                style = DetailStyle.QUOTE
            )
        ),
        focusItems = listOf(
            XinhaiFocusItem("核心任务", "总结失败，重建方向", "会议把失败教训转化为新的组织和斗争路线。"),
            XinhaiFocusItem("关键方针", "土地革命与武装斗争", "中国革命开始更加明确地走向独立领导武装斗争。"),
            XinhaiFocusItem("代表记忆", "秘密环境中的集中讨论", "有限空间里完成了关系革命前途的重大判断。")
        ),
        gallery = listOf(
            XinhaiGalleryImage(BaqiImageBase + "meeting_painting.jpg", "八七会议", "油画将会议现场转化为可感知的历史图像。"),
            XinhaiGalleryImage(BaqiImageBase + "meeting_record.jpg", "会议记录", "文字记录保留了危机时刻的判断过程。"),
            XinhaiGalleryImage(BaqiImageBase + "delegates.jpg", "参会代表", "代表图像让宏大转折落到具体人物身上。")
        ),
        cards = listOf(
            XinhaiBookCard("人物与题写", "从代表、会务和馆名题写看纪念馆的历史延伸。", BaqiImageBase + "inscription.jpg", "下一幕", "baqi_memory")
        ),
        nextLabel = "查看人物记忆"
    ),
    XinhaiStoryBookPageData(
        id = "baqi_memory",
        level = 2,
        levelName = "人物记忆",
        eyebrow = "代表、会务与馆名题写",
        title = "历史转折背后的具体人物",
        subtitle = "会场中的发言者、组织者和后来题写馆名的人，共同构成八七会议的公共记忆。",
        body = "八七会议的历史意义很宏大，但参观时也要看见具体的人。参会代表的讨论、会务组织的隐蔽工作、邓小平后来题写馆名与指导复原，让这处旧址不只是会议发生地，也成为持续被纪念和阐释的历史现场。",
        imageUrl = BaqiImageBase + "delegates.jpg",
        caption = "参加八七会议的部分代表。",
        tags = listOf("参会代表", "邓小平题写", "旧址复原"),
        accent = Color(0xFFD85B45),
        detailBlocks = listOf(
            XinhaiDetailBlock(
                title = "代表图像与会场复原",
                body = "代表图像让观众意识到，重大历史转折由具体的人在具体空间中完成。二楼小房间、紧闭门窗和有限桌椅，都让会议的危险处境变得可感。",
                imageUrl = BaqiImageBase + "delegates.jpg",
                imageCaption = "参加八七会议的部分代表。",
                style = DetailStyle.IMAGE_LEAD
            ),
            XinhaiDetailBlock(
                title = "题写馆名",
                body = "1980年，邓小平同志题写“八七会议会址”馆名，并来馆指导会址复原工作。这一细节把革命年代的亲历记忆和改革开放后的纪念建设连接起来。",
                imageUrl = BaqiImageBase + "inscription.jpg",
                imageCaption = "邓小平题写馆名。",
                style = DetailStyle.STANDARD
            )
        ),
        focusItems = listOf(
            XinhaiFocusItem("人物入口", "从代表图像进入历史", "避免只记结论，也看见结论背后的人与讨论。"),
            XinhaiFocusItem("旧址复原", "让会议回到真实空间", "复原陈设强化了秘密会议的现场感。"),
            XinhaiFocusItem("纪念延续", "题写馆名与开放展示", "会址由历史发生地转化为公共教育空间。")
        ),
        gallery = listOf(
            XinhaiGalleryImage(BaqiImageBase + "delegates.jpg", "代表图像", "人物线索支撑会议叙事。"),
            XinhaiGalleryImage(BaqiImageBase + "inscription.jpg", "馆名题写", "题写馆名成为纪念馆的重要视觉记忆。"),
            XinhaiGalleryImage(BaqiImageBase + "memorial_2.jpg", "纪念馆展陈", "展陈空间组织会议史料与现场记忆。")
        ),
        cards = listOf(
            XinhaiBookCard("历史意义", "理解八七会议为什么是重要转折点。", BaqiImageBase + "memorial_3.jpg", "下一幕", "baqi_legacy")
        ),
        nextLabel = "理解历史意义"
    ),
    XinhaiStoryBookPageData(
        id = "baqi_legacy",
        level = 3,
        levelName = "历史意义",
        eyebrow = "从八七会议走向新的道路",
        title = "在最低谷中重新出发",
        subtitle = "八七会议使中国共产党开始更加自觉地独立领导革命战争、创建人民军队、开展土地革命。",
        body = "八七会议的重要，不只在于提出新的路线，更在于它发生在失败、牺牲和迷茫之中。参观会址时，可以把它理解为一次危机中的重新组织：面对错误，重新判断形势；面对屠杀，重新确认斗争方式；面对低谷，重新寻找中国革命的道路。",
        imageUrl = BaqiImageBase + "memorial_3.jpg",
        caption = "纪念馆展陈空间。",
        tags = listOf("历史转折", "武装斗争", "继续前行"),
        accent = Color(0xFFE26D4D),
        detailBlocks = listOf(
            XinhaiDetailBlock(
                title = "为什么值得重点参观",
                body = "如果说辛亥革命博物院适合看近代中国如何走向共和，那么八七会议会址更适合理解一个政党如何在失败中重新站起来。它所回答的问题，关乎路线、组织、斗争方式，也关乎危机时刻的清醒选择。",
                style = DetailStyle.HIGHLIGHT
            ),
            XinhaiDetailBlock(
                title = "参观后的核心记忆",
                body = "不要只记住一句口号，也要看到口号背后的历史逻辑：实事求是分析失败，敢于面对错误，敢于在危机中重新组织力量。",
                imageCaption = "历史转折的现场记忆",
                style = DetailStyle.QUOTE
            )
        ),
        focusItems = listOf(
            XinhaiFocusItem("会议意义", "从失败走向新道路", "会议推动革命由城市高潮转入长期艰苦的道路探索。"),
            XinhaiFocusItem("精神线索", "清醒、担当、继续前行", "最低谷中的判断和选择，是会址最打动人的部分。"),
            XinhaiFocusItem("观展收束", "从空间回到问题", "这处旧址最终要回答的是危机中如何重新出发。")
        ),
        gallery = listOf(
            XinhaiGalleryImage(BaqiImageBase + "memorial_3.jpg", "展陈空间", "从展陈回望八七会议的历史意义。"),
            XinhaiGalleryImage(BaqiImageBase + "memorial_1.jpg", "会址外观", "旧址建筑承载了危机时刻的历史记忆。")
        ),
        nextLabel = "完成导览"
    )
)

private val XinhaiStoryBookPages = listOf(
    XinhaiStoryBookPageData(
        id = "opening",
        level = 0,
        levelName = "第一幕 · 入馆",
        eyebrow = "辛亥革命博物院",
        title = "先看懂这座馆，再进入那场革命",
        subtitle = "辛亥革命博物院把现代展馆、首义广场和红楼旧址组织在同一条参观轴线上，适合用逐幕观看的方式进入。",
        body = "辛亥革命博物院不是一座孤立陈列的展馆，而是一条被精心组织过的历史动线。观众先在南侧现代展馆中理解晚清危局、革命动员与武昌首义的全过程，再经由首义广场把视线推向北侧红楼旧址，从展陈走向现场。",
        imageUrl = XinhaiImageBase + "museum_axis.jpg",
        caption = "辛亥革命博物馆、首义广场与红楼馆区形成南北轴线。",
        tags = listOf("双馆区", "首义广场", "逐幕导览"),
        accent = Color(0xFFE15842),
        detailBlocks = listOf(
            XinhaiDetailBlock(
                title = "首义广场上的历史轴线",
                body = "辛亥革命博物院位于武汉市武昌区首义广场南侧，是为纪念辛亥革命武昌首义100周年而建设的重要文化设施。南侧展馆负责铺陈历史过程，北侧红楼负责把历史落到真实现场——两者共同构成首义之区的空间叙事。",
                imageUrl = XinhaiImageBase + "museum_axis.jpg",
                imageCaption = "博物馆、广场与红楼在同一条参观轴线上",
                style = DetailStyle.IMAGE_LEAD
            ),
            XinhaiDetailBlock(
                title = "南馆与红楼：两种讲历史的方式",
                body = "红楼原为湖北咨议局旧址，武昌起义后成为湖北军政府所在地。南侧展馆用现代展陈语言梳理历史脉络，北侧红楼则把历史保留在真实建筑空间中。观众先在展馆中获得「为什么」的解释，再在红楼前体会「就在这里」的现场感。",
                style = DetailStyle.HIGHLIGHT
            ),
            XinhaiDetailBlock(
                title = "荣誉与资质",
                body = "辛亥革命武昌起义纪念馆是依托中华民国军政府鄂军都督府旧址而建立的纪念性博物馆。\n\n博物院先后荣获：\n• 国家国防教育示范基地\n• 武汉市2011年度文化工作绩效管理先进单位\n• 2012～2013年度中国建设工程鲁班奖（国家优质工程）\n\n目前是国家一级博物馆、第一批全国重点文物保护单位、全国百个爱国主义教育示范基地、全国青少年教育基地、海峡两岸交流基地、中国华侨文化交流基地、全国社会科学普及教育基地、全国红色旅游经典景区、国家AAAA级旅游景区。",
                imageUrl = XinhaiImageBase + "museum_logo.jpg",
                imageCaption = "国家一级博物馆 · 国家AAAA级旅游景区",
                style = DetailStyle.STANDARD
            ),
            XinhaiDetailBlock(
                title = "建馆历程：从动工到整合",
                body = "博物馆于2009年8月动工兴建，2011年9月落成，同年10月15日起免费对公众开放。博物院总建筑面积22142平方米，是首义文化区的核心建筑。\n\n2022年3月，辛亥革命博物院由北区（原辛亥革命武昌起义纪念馆）和南区（原辛亥革命博物馆）整合而成。北区是1981年依托武昌起义军政府旧址建立的纪念馆，因旧址主体建筑红墙红瓦，武汉人称之为红楼。南区是2011年建立的一座现代建筑形式的专题博物馆，外观为楚国红色调，呈V字造型。",
                imageUrl = XinhaiImageBase + "museum_sunset.jpg",
                imageCaption = "夕阳下的首义广场与博物院南区建筑",
                style = DetailStyle.STANDARD
            ),
            XinhaiDetailBlock(
                title = "故事继续：从一座馆进入一座城",
                body = "完整的参观不应只停留在展厅内部。南馆把晚清危局、革命动员、武昌首义和共和创建串成时间线，首义广场把观众带回城市空间，红楼旧址则让历史落到具体建筑。沿着这条动线行走，观众会发现辛亥革命不是一组孤立展品，而是一座城市在近代中国转型中的集中见证。",
                imageUrl = XinhaiImageBase + "museum_roof_top.jpg",
                imageCaption = "从建筑屋顶回望首义文化区的空间关系",
                style = DetailStyle.STANDARD
            ),
            XinhaiDetailBlock(
                title = "",
                body = "纪念不是结束，而是重新进入历史。辛亥革命博物院把事件、人物、旧址和展陈转化为可参观、可理解的公共记忆——从这里出发，我们进入七幕导览。",
                imageCaption = "博物院导览",
                style = DetailStyle.QUOTE
            )
        ),
        focusItems = listOf(
            XinhaiFocusItem("空间格局", "南馆看展陈，红楼看旧址", "先在南侧现代展馆理解历史脉络，再经首义广场走向北侧红楼，从展陈走向现场。"),
            XinhaiFocusItem("建筑特色", "楚国红与V字造型", "南区建筑外观为楚国红色调、呈V字造型，总建筑面积22142平方米，是首义文化区的核心地标。"),
            XinhaiFocusItem("历史坐标", "红楼与广场的空间轴线", "博物馆、首义广场与红楼在同一条参观轴线上，构成首义之区的完整空间叙事。"),
            XinhaiFocusItem("参观贴士", "2011年10月起免费开放", "博物馆于2009年动工、2011年落成，免费对公众开放，是国家一级博物馆、国家AAAA级旅游景区。"),
            XinhaiFocusItem("展览主旨", "共和之基：从武昌首义到制度转折", "展览讲述武昌首义如何推动近代中国从帝制走向共和，开启现代国家建设的探索之路。")
        ),
        gallery = listOf(
            XinhaiGalleryImage(XinhaiImageBase + "museum_hall_1.jpg", "展厅入口", "现代展馆入口区域，引导观众进入历史叙事。"),
            XinhaiGalleryImage(XinhaiImageBase + "museum_hall_2.jpg", "展陈空间", "宽敞的展览空间，以时间线索组织内容。"),
            XinhaiGalleryImage(XinhaiImageBase + "museum_hall_3.jpg", "展厅掠影", "展馆内部展陈细节，呈现丰富的历史资料。"),
            XinhaiGalleryImage(XinhaiImageBase + "museum_hall_4.jpg", "历史长廊", "连续的展线设计带领观众穿越历史时空。"),
            XinhaiGalleryImage(XinhaiImageBase + "museum_hall_5.jpg", "专题展区", "不同主题的展陈空间，多角度呈现辛亥历史。"),
            XinhaiGalleryImage(XinhaiImageBase + "museum_hall_6.jpg", "尾厅空间", "展览尾声的总结性空间，引发观众思考。"),
            XinhaiGalleryImage(XinhaiImageBase + "museum_logo.jpg", "馆徽标识", "辛亥革命博物院的标志形象。"),
            XinhaiGalleryImage(XinhaiImageBase + "museum_sunset.jpg", "首义暮色", "夕阳下的首义广场与博物馆建筑。"),
            XinhaiGalleryImage(XinhaiImageBase + "red_building_aerial.jpg", "红楼俯瞰", "红楼馆区与首义广场构成真实历史坐标。"),
            XinhaiGalleryImage(XinhaiImageBase + "museum_exhibition_1.png", "展馆内景", "展馆内部陈设与展品陈列。"),
            XinhaiGalleryImage(XinhaiImageBase + "museum_exhibition_2.png", "展馆细节", "展陈设计中的细节与亮点。")
        ),
        cards = listOf(
            XinhaiBookCard("晚清危局", "先理解革命为什么会成为一种历史选择。", XinhaiImageBase + "late_qing.png", "下一幕", "late_qing")
        ),
        nextLabel = "进入晚清危局"
    ),
    XinhaiStoryBookPageData(
        id = "late_qing",
        level = 1,
        levelName = "第二幕 · 背景",
        eyebrow = "晚清中国",
        title = "危机不是一夜发生的",
        subtitle = "展厅用压迫感很强的空间，把内忧外患、制度困局和社会变动放在观众面前。",
        body = "辛亥革命的爆发并非偶然。19世纪中后期以来，中国在战争、通商、赔款、割地中不断承压，社会追问如何救中国。洋务运动试图以器物求自强，戊戌变法尝试制度变革，却都未能完成根本转型。展馆把这一段历史放在开篇，让观众明白：武昌起义不是突然出现的火光，而是长期压力下的爆发。",
        imageUrl = XinhaiImageBase + "late_qing.png",
        caption = "晚清中国展区以沉重材质和低照度营造历史压迫感。",
        tags = listOf("晚清危局", "洋务运动", "戊戌变法"),
        accent = Color(0xFFD2A24F),
        detailBlocks = listOf(
            XinhaiDetailBlock(
                title = "晚清中国的多重危机",
                body = "19世纪中后期，中国在战争失败、不平等条约、巨额赔款和领土割让中不断承压。鸦片战争打开国门后，通商口岸的开放让传统经济体系遭遇冲击，而甲午战争的惨败则彻底暴露了清王朝的虚弱。财政枯竭、军事落后、外交被动，社会各阶层对「如何救中国」的追问越来越急迫。",
                imageUrl = XinhaiImageBase + "late_qing.png",
                imageCaption = "晚清中国展区以沉重材质营造历史压迫感",
                style = DetailStyle.IMAGE_LEAD
            ),
            XinhaiDetailBlock(
                title = "洋务运动：器物层面的自救",
                body = "洋务运动试图以「师夷长技以制夷」的思路，通过引进西方工业、军事技术和新式教育来求自强。江南制造总局、福州船政局的建立，标志着中国近代工业的起步。但洋务派坚持「中体西用」，只学技术不改制度，最终未能触及问题的根本。",
                imageUrl = XinhaiImageBase + "westernization.png",
                imageCaption = "舰船装置提示近代工业、军事与海防问题",
                style = DetailStyle.STANDARD
            ),
            XinhaiDetailBlock(
                title = "戊戌变法：制度变革的首次尝试",
                body = "1898年的戊戌变法试图从政治制度层面改变旧秩序，倡导君主立宪、废除八股、兴办新式学堂。然而，这场改革仅持续百余天便以失败告终。戊戌变法的失败留下一个清晰问题：如果只在旧体制内修补，能否真正完成国家转型？",
                imageUrl = XinhaiImageBase + "reform_1898.png",
                imageCaption = "改革失败后，更多人重新思考救亡路径",
                style = DetailStyle.STANDARD
            ),
            XinhaiDetailBlock(
                title = "为什么是武汉？",
                body = "汉口开埠后，武汉成为内地最大的通商口岸之一，华洋杂处、工商汇聚，社会流动性远超内陆城市。长江与汉水在此交汇，九省通衢的地理位置让它成为信息、人员和物资的集散地。租界区的存在既带来屈辱，也让武汉人更早接触到外部世界的新思想。",
                imageUrl = XinhaiImageBase + "hankou_concession.png",
                imageCaption = "汉口沿江大道租界区街景",
                style = DetailStyle.STANDARD
            ),
            XinhaiDetailBlock(
                title = "故事继续：保路风潮与社会动员",
                body = "进入20世纪后，清政府在铁路、财政和地方利益问题上的失措进一步激化社会矛盾。1911年夏，保路运动在多省展开，四川风潮尤其激烈，清廷调兵入川又削弱了湖北防务。地方士绅、商人、学生、新军士兵和革命党人的情绪在不同议题上汇合，危机由制度层面传导到城市和军营，为武昌起义提供了更直接的社会背景。",
                style = DetailStyle.HIGHLIGHT
            ),
            XinhaiDetailBlock(
                title = "",
                body = "危机不是一夜发生的，但改变往往在一夜之间开始。晚清的内忧外患为革命准备了土壤，而改革者的失败则让更多人相信：唯有彻底变革，才能救亡图存。",
                style = DetailStyle.QUOTE
            )
        ),
        focusItems = listOf(
            XinhaiFocusItem("列强压力", "战争、赔款与割地", "鸦片战争后，战败条约、巨额赔款和领土割让不断冲击传统秩序，社会各阶层开始追问国家的出路。"),
            XinhaiFocusItem("自救尝试", "洋务运动与戊戌变法", "从江南制造总局到百日维新，改革者试图以技术和制度求变，却未能完成根本转型。"),
            XinhaiFocusItem("为什么是武汉", "九省通衢的觉醒之地", "汉口开埠后华洋杂处、工商汇聚，长江与汉水交汇处的特殊位置，让武汉成为信息与新思想传播的枢纽。")
        ),
        gallery = listOf(
            XinhaiGalleryImage(XinhaiImageBase + "westernization.png", "洋务运动", "舰船装置提示近代工业、军事与海防问题。"),
            XinhaiGalleryImage(XinhaiImageBase + "reform_1898.png", "戊戌变法", "改革失败后，更多人重新思考救亡路径。"),
            XinhaiGalleryImage(XinhaiImageBase + "hankou_concession.png", "汉口沿江大道", "租界街景表现近代城市空间中的中外碰撞。"),
            XinhaiGalleryImage(XinhaiImageBase + "river_history.png", "江湖浩荡", "长江与汉水交汇的武汉，见证近代中国的风云变幻。")
        ),
        cards = listOf(
            XinhaiBookCard("革命动员", "看革命力量如何从思想、组织和行动中形成。", XinhaiImageBase + "revolution_origin.png", "下一幕", "revolution_origin")
        ),
        nextLabel = "进入革命源起"
    ),
    XinhaiStoryBookPageData(
        id = "revolution_origin",
        level = 1,
        levelName = "第三幕 · 动员",
        eyebrow = "革命源起",
        title = "革命先从人群和组织中成形",
        subtitle = "革命团体、革命报刊、海外华侨、留学生、新军和地方网络，共同构成革命动员的土壤。",
        body = "孙中山等革命者长期奔走，革命团体逐步形成组织网络。革命不是一夜爆发，而是多年宣传、筹款、联络与失败经验积累后的结果。这一页把革命思想传播、组织建立和起义行动合并呈现，让观众理解武昌新军为何能在1911年形成行动能力。",
        imageUrl = XinhaiImageBase + "revolution_origin.png",
        caption = "革命源起展陈以人物场景表现革命者的组织讨论。",
        tags = listOf("孙中山", "同盟会", "革命报刊"),
        accent = Color(0xFFB83232),
        detailBlocks = listOf(
            XinhaiDetailBlock(
                title = "革命思想的传播网络",
                body = "革命不是一场单点爆发的军事冲突。展馆中的人物群像、报刊墙、场景复原和孙中山形象，把抽象的革命风潮具体化为一张张面孔、一份份文本和一次次行动。观众先看到思想如何传播，再看到组织如何建立，最后理解行动能力的来源。",
                imageUrl = XinhaiImageBase + "revolution_origin.png",
                imageCaption = "革命源起展陈以人物场景表现革命者的组织讨论",
                style = DetailStyle.IMAGE_LEAD
            ),
            XinhaiDetailBlock(
                title = "孙中山与革命者群像",
                body = "孙中山等革命者长期奔走于海内外，从兴中会到同盟会，革命团体逐步形成组织网络。人物不是孤立肖像，而是组织、思想和行动网络的入口。他们的面孔背后，是多年宣传、筹款、联络和无数次失败的经验积累。",
                imageUrl = XinhaiImageBase + "sun_yatsen_portrait.png",
                imageCaption = "孙中山肖像——革命领导者线索",
                style = DetailStyle.STANDARD
            ),
            XinhaiDetailBlock(
                title = "革命报刊与演说：共识如何形成",
                body = "革命主张需要通过文本、演讲、社团和校园不断传播。革命报刊是思想火种的载体，演说则是点燃情绪的引线。在海内外华侨、留学生、新军士兵和知识分子之间，一种关于「推翻专制、建立共和」的共识逐渐形成。",
                style = DetailStyle.HIGHLIGHT
            ),
            XinhaiDetailBlock(
                title = "从起义失败中学习",
                body = "早期起义虽多有失败，但每一次行动都在训练组织能力、测试社会反应、积累经验教训。从广州起义到黄花岗之役，革命者用鲜血换来了对敌我力量的更清晰认知，也为最终的武昌首义铺平了道路。",
                imageUrl = XinhaiImageBase + "revolution_uprisings.png",
                imageCaption = "战斗场景提示早期起义的行动经验",
                style = DetailStyle.STANDARD
            ),
            XinhaiDetailBlock(
                title = "故事继续：新军为何成为关键力量",
                body = "革命思想最终能够转化为行动，离不开湖北新军中的组织基础。新军士兵受新式教育影响较深，接触报刊、学校和社团的机会更多，也更容易理解民族危机与制度问题。革命党人在军营中秘密联络，借助文学社、共进会等组织积累力量。到1911年秋，思想传播、组织网络和军事力量开始在武昌汇合。",
                imageUrl = XinhaiImageBase + "revolution_groups.png",
                imageCaption = "革命团体与新军网络共同形成行动基础",
                style = DetailStyle.STANDARD
            ),
            XinhaiDetailBlock(
                title = "",
                body = "革命先从人群和组织中成形。没有多年的思想传播和组织建设，1911年10月10日那个夜晚只会是历史中的又一个普通日期。",
                style = DetailStyle.QUOTE
            )
        ),
        focusItems = listOf(
            XinhaiFocusItem("革命先驱", "孙中山与革命者群像", "从兴中会到同盟会，看革命者如何在海内外奔走，把个人命运与民族未来紧紧相连。"),
            XinhaiFocusItem("思想传播", "报刊、演说与校园", "革命主张通过文本、演讲和社团不断扩散，在海外华侨、留学生和新军中形成推翻专制、建立共和的共识。"),
            XinhaiFocusItem("起义之路", "从失败走向武昌首义", "从广州起义到黄花岗之役，每一次失败都在积蓄力量、锤炼组织，为最终的胜利铺平道路。")
        ),
        gallery = listOf(
            XinhaiGalleryImage(XinhaiImageBase + "revolution_groups.png", "革命团体", "报刊与人物群像表现革命传播网络。"),
            XinhaiGalleryImage(XinhaiImageBase + "revolution_uprisings.png", "革命起义", "战斗场景提示早期起义的行动经验。"),
            XinhaiGalleryImage(XinhaiImageBase + "sun_yatsen_portrait.png", "孙中山", "新增人物图片，用于强化革命领导者线索。")
        ),
        cards = listOf(
            XinhaiBookCard("武昌首义", "进入1911年10月10日晚的武昌现场。", XinhaiImageBase + "uprising_sculpture.png", "下一幕", "wuchang_uprising")
        ),
        nextLabel = "进入武昌首义"
    ),
    XinhaiStoryBookPageData(
        id = "wuchang_uprising",
        level = 1,
        levelName = "第四幕 · 首义",
        eyebrow = "1911年10月10日",
        title = "武昌城的夜晚改变了历史方向",
        subtitle = "湖北新军革命党人发动起义，起义力量迅速控制关键地点，武昌首义由此爆发。",
        body = "1911年10月10日晚，湖北新军中的革命党人发动起义，迅速控制军械、城门等关键节点。这一幕把武昌起义群雕、起义街巷、中和门放在同一页里讲，强调新军、革命党人和城市空间共同构成的起义现场。",
        imageUrl = XinhaiImageBase + "uprising_sculpture.png",
        caption = "武昌起义群雕以强烈动态表现首义现场。",
        tags = listOf("武昌起义", "湖北新军", "1911"),
        accent = Color(0xFFE14A35),
        detailBlocks = listOf(
            XinhaiDetailBlock(
                title = "1911年10月10日的夜晚",
                body = "页面不把复杂史实简化为单一人物的第一枪，而是强调新军、革命党人和城市空间共同构成的起义现场。群雕负责制造行动的冲击力，街巷负责把历史从文字变成空间，中和门则把起义与武昌城防联系起来。",
                imageUrl = XinhaiImageBase + "uprising_sculpture.png",
                imageCaption = "武昌起义群雕以强烈动态表现首义现场",
                style = DetailStyle.IMAGE_LEAD
            ),
            XinhaiDetailBlock(
                title = "新军与革命党：起义的主力军",
                body = "湖北新军是清末编练的新式陆军，士兵多受过新式教育，对时局有清醒认知。革命党人长期在新军中发展组织，把军营变成了革命思想的传播场所。1911年10月10日晚，工程第八营率先发难，随即各营响应。",
                style = DetailStyle.HIGHLIGHT
            ),
            XinhaiDetailBlock(
                title = "城市空间中的起义",
                body = "起义不是抽象事件，而是在真实城市空间中推进。革命力量控制军械所获得武器，占领城门切断清廷援军通道，攻占总督署瓦解行政中枢。武昌城的街巷、城墙和衙门，共同构成了这场改变历史走向的舞台。",
                imageUrl = XinhaiImageBase + "wuchang_uprising_scene.png",
                imageCaption = "沉浸式街巷让观众靠近武昌城的夜晚",
                style = DetailStyle.STANDARD
            ),
            XinhaiDetailBlock(
                title = "中和门：入城的记忆之门",
                body = "中和门是武昌城的重要城门，起义军由此入城、由此推进。原中和门与今日起义门承载入城记忆，门楼不仅是建筑，更是历史行动的坐标。理解中和门，就能理解武昌起义为什么能够迅速控制全城。",
                imageUrl = XinhaiImageBase + "zhonghe_gate.png",
                imageCaption = "原中和门与今日起义门承载入城记忆",
                style = DetailStyle.STANDARD
            ),
            XinhaiDetailBlock(
                title = "故事继续：从偶发风险到全城响应",
                body = "起义前夕，革命机关因意外暴露，名单和文书面临泄露风险，许多革命党人已经没有继续等待的余地。10月10日晚的行动带有被迫提前发动的紧迫性，但长期积累的组织基础使它没有停留在局部骚动。工程营发难后，各营陆续响应，起义力量迅速夺取军械、控制要地，并把战斗推向总督署等核心目标。",
                style = DetailStyle.HIGHLIGHT
            ),
            XinhaiDetailBlock(
                title = "",
                body = "武昌城的夜晚改变了历史方向。不是因为某一个人扣动了扳机，而是因为一群人、一座城、一个时刻共同构成了革命的现场。",
                style = DetailStyle.QUOTE
            )
        ),
        focusItems = listOf(
            XinhaiFocusItem("起义之夜", "1911年10月10日的武昌", "回到那个改变历史方向的夜晚，看湖北新军如何率先发难，迅速控制军械、城门和总督署。"),
            XinhaiFocusItem("城市现场", "街巷、城门与军械所", "起义发生在真实的城市空间中——在军械所获得武器，占领城门切断援军，攻陷总督署瓦解行政中枢。"),
            XinhaiFocusItem("起义之门", "中和门与起义门", "中和门是起义军入城的关键通道，门楼不仅是建筑，更是这场革命行动的地理坐标。")
        ),
        gallery = listOf(
            XinhaiGalleryImage(XinhaiImageBase + "wuchang_uprising_scene.png", "起义街巷", "沉浸式街巷让观众靠近武昌城的夜晚。"),
            XinhaiGalleryImage(XinhaiImageBase + "zhonghe_gate.png", "中和门", "原中和门与今日起义门承载入城记忆。"),
            XinhaiGalleryImage(XinhaiImageBase + "wuchang_uprising_scene.png", "首义现场", "报刊、门楼和人物共同组织城市记忆。")
        ),
        cards = listOf(
            XinhaiBookCard("红楼成府", "从军事行动进入革命政权建立。", XinhaiImageBase + "red_building_front.jpg", "下一幕", "red_building")
        ),
        nextLabel = "进入红楼成府"
    ),
    XinhaiStoryBookPageData(
        id = "red_building",
        level = 1,
        levelName = "第五幕 · 政权",
        eyebrow = "红楼成府",
        title = "从湖北咨议局到鄂军都督府",
        subtitle = "红楼原为湖北咨议局旧址，武昌起义后成为革命政权的重要空间象征。",
        body = "武昌起义成功后，革命力量在红楼成立湖北军政府（鄂军都督府）。红楼的意义因此发生转换：它不再只是清末地方议政建筑，而成为革命政权建立和共和转折的标志性现场。理解红楼，就能理解武昌首义为什么不只是一次战斗，而是从军事行动进入政治建构。",
        imageUrl = XinhaiImageBase + "red_building_front.jpg",
        caption = "红楼即辛亥革命武昌起义纪念馆核心旧址。",
        tags = listOf("红楼", "湖北军政府", "鄂军都督府"),
        accent = Color(0xFFC94B3E),
        detailBlocks = listOf(
            XinhaiDetailBlock(
                title = "红楼：从咨议局到军政府",
                body = "红楼原为湖北咨议局旧址，是清末地方绅士议政的场所。武昌起义成功后，革命力量在此成立湖北军政府，也称鄂军都督府。建筑的身份转换，标志着这片土地从清末地方政治空间变为革命政权的诞生地。",
                imageUrl = XinhaiImageBase + "red_building_front.jpg",
                imageCaption = "红楼即辛亥革命武昌起义纪念馆核心旧址",
                style = DetailStyle.IMAGE_LEAD
            ),
            XinhaiDetailBlock(
                title = "建筑身份的转变",
                body = "红楼本身承载晚清地方政治空间的历史背景。理解它的前身是咨议局，才能理解起义成功后为什么选择这里作为军政府所在地——它既有官方建筑的权威性，又处于城市中心位置，便于发号施令。",
                style = DetailStyle.HIGHLIGHT
            ),
            XinhaiDetailBlock(
                title = "孙中山与共和想象",
                body = "孙中山铜像和人物图像不是孤立装饰，而是连接革命组织与共和理想的视觉坐标。它们提醒观众：红楼中的政权建立不是终点，而是走向更大制度转折的起点。",
                imageUrl = XinhaiImageBase + "sun_yatsen_bust.png",
                imageCaption = "展厅中的人物坐标连接革命组织与共和理想",
                style = DetailStyle.STANDARD
            ),
            XinhaiDetailBlock(
                title = "广场轴线：从现代到旧址",
                body = "从空中俯瞰，红楼与首义广场、南侧现代博物馆构成一条清晰的空间轴线。这条轴线不只是地理上的连接，更是历史叙事的延伸：从展陈到旧址，从文字到现场，从理解到感受。",
                imageUrl = XinhaiImageBase + "red_building_aerial.jpg",
                imageCaption = "从空中理解红楼与首义广场的关系",
                style = DetailStyle.STANDARD
            ),
            XinhaiDetailBlock(
                title = "故事继续：军事胜利之后的政治考题",
                body = "占领武昌只是第一步，革命力量很快面对更复杂的问题：谁来组织军政秩序，如何发布号令，怎样稳定城市和争取各方响应。湖北军政府的成立，把起义从军事行动推进到政治建构。红楼因此成为一个分界点，在这里，革命者不只推翻旧秩序，也开始尝试建立新的公共权力。",
                style = DetailStyle.HIGHLIGHT
            ),
            XinhaiDetailBlock(
                title = "",
                body = "从湖北咨议局到鄂军都督府，一座建筑的身份转换，折射出整个国家的方向转换。",
                style = DetailStyle.QUOTE
            )
        ),
        focusItems = listOf(
            XinhaiFocusItem("建筑前身", "从湖北咨议局到军政府", "红楼原为清末地方绅士议政之所，起义成功后成为湖北军政府所在地，建筑身份的转变折射出国家的方向。"),
            XinhaiFocusItem("政权建立", "武昌首义的政治成果", "革命不只是一次军事行动——在红楼中成立的军政府，标志着从战斗走向政治建构的关键一步。"),
            XinhaiFocusItem("共和憧憬", "孙中山铜像与人物图像", "展厅中的孙中山铜像和人物图像，连接着革命组织的奋斗与建立共和国家的远大理想。")
        ),
        gallery = listOf(
            XinhaiGalleryImage(XinhaiImageBase + "red_building_aerial.jpg", "红楼俯瞰", "从空中理解红楼与首义广场的关系。"),
            XinhaiGalleryImage(XinhaiImageBase + "sun_yatsen_bust.png", "孙中山铜像", "展厅中的人物坐标连接革命组织与共和理想。"),
            XinhaiGalleryImage(XinhaiImageBase + "sun_yatsen_portrait.png", "孙中山肖像", "新增人物图像，用于强化革命领导者叙事。")
        ),
        cards = listOf(
            XinhaiBookCard("创建共和", "看武昌首义如何扩展为全国性政治转折。", XinhaiImageBase + "found_republic.png", "下一幕", "found_republic")
        ),
        nextLabel = "进入创建共和"
    ),
    XinhaiStoryBookPageData(
        id = "found_republic",
        level = 1,
        levelName = "第六幕 · 共和",
        eyebrow = "创建中华民国 · 辛亥纪念",
        title = "纪念不是结束，而是重新进入历史",
        subtitle = "多省响应、清帝退位和中华民国建立，使辛亥革命成为近代中国制度转型的关键事件。",
        body = "武昌起义成功后，各省陆续响应，革命由地方起义扩展为全国性变革。1912年中华民国建立，延续两千多年的君主专制制度走向终结。但共和创建并不意味着历史问题全部解决，辛亥革命后的制度建设、社会动员和观念更新仍然艰难复杂。",
        imageUrl = XinhaiImageBase + "found_republic.png",
        caption = "创建中华民国展区以环形空间呈现共和建立。",
        tags = listOf("中华民国", "共和", "辛亥百年"),
        accent = Color(0xFFE4A03F),
        detailBlocks = listOf(
            XinhaiDetailBlock(
                title = "从武昌到全国：共和的扩展",
                body = "展厅用环形空间、旗帜意象、民国展区和纪念墙，让观众感到历史正在从武昌现场扩展到整个中国。多省响应、清帝退位和中华民国建立，使辛亥革命成为近代中国制度转型的关键事件。",
                imageUrl = XinhaiImageBase + "found_republic.png",
                imageCaption = "创建中华民国展区以环形空间呈现共和建立",
                style = DetailStyle.IMAGE_LEAD
            ),
            XinhaiDetailBlock(
                title = "终结两千年的君主专制",
                body = "中华民国的建立不只是政权的更迭，更是政治想象的根本转变。从「天下」到「国家」，从「臣民」到「国民」，从「君权神授」到「主权在民」——这些观念的转变，才是辛亥革命最深远的遗产。",
                style = DetailStyle.HIGHLIGHT
            ),
            XinhaiDetailBlock(
                title = "民国初建与制度探索",
                body = "民国建立后，临时政府、国会、宪法草案相继出现。但这些制度建设在实践中遭遇重重困难：军阀割据、政党纷争、社会撕裂。共和的理想与现实之间的落差，正是辛亥革命后中国面临的真正挑战。",
                imageUrl = XinhaiImageBase + "republic.png",
                imageCaption = "民国时期的视觉符号与历史印记",
                style = DetailStyle.STANDARD
            ),
            XinhaiDetailBlock(
                title = "辛亥百年与今日纪念",
                body = "今天的辛亥革命博物院之所以重要，正在于它让公众重新进入这段历史：既看到革命的突破，也看到转型的长期性；既纪念武昌首义，也理解现代中国政治与社会变迁的起点之一。纪念不是结束，而是重新进入历史。",
                imageUrl = XinhaiImageBase + "centenary.png",
                imageCaption = "从历史事件走向公共纪念",
                style = DetailStyle.STANDARD
            ),
            XinhaiDetailBlock(
                title = "故事继续：共和理想为何仍需理解",
                body = "辛亥革命结束帝制，却没有立即解决近代中国的全部难题。临时政府、议会政治、宪法文本和地方军政关系都在探索中摇摆，袁世凯掌权和此后的军阀割据说明共和制度的建立远比宣布共和更艰难。正因为如此，参观这一幕时既要看到推翻帝制的历史功绩，也要理解现代国家建设需要长期制度实践和社会动员。",
                style = DetailStyle.HIGHLIGHT
            ),
            XinhaiDetailBlock(
                title = "",
                body = "辛亥革命的突破在于它终结了一个旧时代，而它的意义在于它开启了一个需要长期探索的新时代。",
                style = DetailStyle.QUOTE
            )
        ),
        focusItems = listOf(
            XinhaiFocusItem("全国浪潮", "从武昌到各省响应", "武昌起义后，各省陆续响应，革命从地方行动扩展为全国性变革，延续两千多年的帝制走向终结。"),
            XinhaiFocusItem("观念转变", "从臣民到国民", "中华民国的建立不仅是政权更迭，更是政治想象的根本转变——主权在民取代了君权神授。"),
            XinhaiFocusItem("转型之艰", "共和理想与现实挑战", "民国建立后，军阀割据、政党纷争，共和的理想与现实之间存在巨大落差，这正是辛亥革命后中国面临的真正挑战。")
        ),
        gallery = listOf(
            XinhaiGalleryImage(XinhaiImageBase + "republic_gallery.png", "民国展区", "人物、文献与场景共同呈现共和初建。"),
            XinhaiGalleryImage(XinhaiImageBase + "republic.png", "民国印象", "民国时期的视觉符号与历史印记。"),
            XinhaiGalleryImage(XinhaiImageBase + "centenary.png", "辛亥百年", "从历史事件走向公共纪念。"),
            XinhaiGalleryImage(XinhaiImageBase + "memorial_wall.png", "辛亥纪念", "纪念空间将历史转化为公共记忆。")
        ),
        cards = listOf(
            XinhaiBookCard("馆藏珍品", "走进博物院珍藏，看四件见证历史的重要文物。", XinhaiArtifactBase + "cai_jimin_portrait.jpg", "进入珍藏", "artifacts")
        ),
        nextLabel = "进入馆藏珍品"
    ),
    XinhaiStoryBookPageData(
        id = "artifacts",
        level = 1,
        levelName = "第七幕 · 珍藏",
        eyebrow = "馆藏珍品",
        title = "四件文物，四个历史切片",
        subtitle = "墨彩蔡济民肖像瓷板、熊秉坤勋五位章、孙中山\"博爱\"横披与《经铿黄氏家谱》，承载着革命记忆与时代温度。",
        body = "辛亥革命博物院馆藏丰富，涵盖书画、瓷器、徽章、文献等多种类型。以下四件珍品从不同角度见证了那段峥嵘岁月，每一件都承载着革命记忆与时代温度。",
        imageUrl = XinhaiArtifactBase + "cai_jimin_portrait.jpg",
        caption = "墨彩蔡济民肖像瓷板，笔法工细，神形兼具。",
        tags = listOf("墨彩瓷板", "勋五位章", "博爱横披", "黄氏家谱"),
        accent = Color(0xFFB8976B),
        detailBlocks = listOf(
            XinhaiDetailBlock(
                title = "馆藏珍品概览",
                body = "辛亥革命博物院馆藏涵盖瓷器、徽章、书画、文献等多种类型。以下四件珍品从不同角度见证了那段峥嵘岁月——它们既是物质遗存，也是理解那个时代的人、事与精神的入口。",
                imageUrl = XinhaiArtifactBase + "cai_jimin_portrait.jpg",
                imageCaption = "墨彩蔡济民肖像瓷板",
                style = DetailStyle.IMAGE_LEAD
            ),
            XinhaiDetailBlock(
                title = "墨彩蔡济民肖像瓷板",
                body = "墨彩蔡济民肖像瓷板为陈设瓷，造型规整，长方形制式，纵38.8厘米，横25.7厘米，胎厚0.7厘米。在白地瓷板上绘有一幅椭圆形墨彩工笔人物肖像，像主蔡济民西装革履、器宇轩昂，神形兼具，笔法工细，像左下有一方朱文款印。蔡济民是辛亥革命的重要参与者，此瓷板生动再现了他的风采。",
                imageUrl = XinhaiArtifactBase + "cai_jimin_portrait.jpg",
                imageCaption = "椭圆形墨彩工笔人物肖像，笔法工细",
                style = DetailStyle.STANDARD
            ),
            XinhaiDetailBlock(
                title = "熊秉坤勋五位章",
                body = "熊秉坤勋五位章为银胎景泰蓝徽章，星形，直径6.4厘米，厚1.5厘米，重50克。背面以别针佩挂，配脱胎黑漆盒，纵100厘米，横82厘米，高2厘米，盒面篆书「勋五位章」。熊秉坤是武昌起义的重要人物，这枚勋章见证了他在革命中的功勋。",
                imageUrl = XinhaiArtifactBase + "xiong_bingkun_medal.jpg",
                imageCaption = "银胎景泰蓝星形徽章，配脱胎黑漆盒",
                style = DetailStyle.STANDARD
            ),
            XinhaiDetailBlock(
                title = "「博爱」横披",
                body = "孙中山为曹亚伯题「博爱」横披，纸质，纵55厘米，横167厘米。「博爱」后面书写「亚伯兄属」，落款「孙文」，后钤「孙文之印」白文印。「博爱」是孙中山一生倡导的重要理念，这幅横披既是他书法艺术的体现，也是其革命思想的真实写照。",
                imageUrl = XinhaiArtifactBase + "boai_calligraphy.jpg",
                imageCaption = "孙中山手迹，纵55厘米，横167厘米",
                style = DetailStyle.STANDARD
            ),
            XinhaiDetailBlock(
                title = "《经铿黄氏家谱》",
                body = "《经铿黄氏家谱》纂修于清光绪壬辰年（1892），为明崇祯以降黄氏一族七修谱。线装，毛边纸木活字印刷，纵28厘米，横16.5厘米，一部三十卷。这部家谱不仅是宗族文化的珍贵遗存，也为研究清末民初的社会结构、家族制度与地域文化提供了重要史料。",
                imageUrl = XinhaiArtifactBase + "huang_family_tree.jpg",
                imageCaption = "清光绪壬辰年七修谱，线装木活字印刷",
                style = DetailStyle.STANDARD
            ),
            XinhaiDetailBlock(
                title = "故事继续：从文物回到人",
                body = "这些文物的价值不只在材质和尺寸。瓷板肖像让革命者有了可被凝视的面容，勋章记录战斗之后的荣誉确认，手迹把政治理想浓缩为可传承的文字，家谱则提示革命发生在真实的宗族、地方和社会关系之中。把四件文物连起来看，辛亥革命就不再只是宏大叙事，而是由具体人物、家庭记忆和时代选择共同组成的历史。",
                imageUrl = XinhaiArtifactBase + "boai_calligraphy.jpg",
                imageCaption = "文物把宏大历史重新带回具体的人与记忆",
                style = DetailStyle.HIGHLIGHT
            )
        ),
        focusItems = listOf(
            XinhaiFocusItem("瓷板肖像", "蔡济民：西装革履的革命者", "椭圆形墨彩工笔肖像，笔法工细，像主器宇轩昂——一件瓷器凝固了一个革命者的风采。"),
            XinhaiFocusItem("革命勋章", "熊秉坤的勋五位章", "银胎景泰蓝星形徽章配脱胎黑漆盒，盒面篆书「勋五位章」，见证武昌起义中的真实功勋。"),
            XinhaiFocusItem("领袖手迹", "孙中山题写的「博爱」", "纵55厘米、横167厘米的横披，「博爱」二字力透纸背，是孙中山一生理念与书法艺术的双重见证。"),
            XinhaiFocusItem("家族记忆", "黄氏家谱中的时代", "清光绪壬辰年七修谱，线装木活字印刷三十卷——一部家谱，也是清末社会结构与宗族文化的缩影。")
        ),
        gallery = listOf(
            XinhaiGalleryImage(XinhaiArtifactBase + "cai_jimin_portrait.jpg", "墨彩蔡济民肖像瓷板", "椭圆形墨彩工笔人物肖像，像主西装革履、器宇轩昂。"),
            XinhaiGalleryImage(XinhaiArtifactBase + "xiong_bingkun_medal.jpg", "熊秉坤勋五位章", "银胎景泰蓝星形徽章，配脱胎黑漆盒，盒面篆书\"勋五位章\"。"),
            XinhaiGalleryImage(XinhaiArtifactBase + "boai_calligraphy.jpg", "\"博爱\"横披", "孙中山题写的\"博爱\"二字，后书\"亚伯兄属\"，落款\"孙文\"。"),
            XinhaiGalleryImage(XinhaiArtifactBase + "huang_family_tree.jpg", "《经铿黄氏家谱》", "清光绪壬辰年七修谱，线装木活字印刷，一部三十卷。")
        ),
        cards = listOf(
            XinhaiBookCard("回到展馆入口", "重新从首义广场轴线查看完整动线。", XinhaiImageBase + "museum_roof_top.jpg", "回望", "opening")
        ),
        nextLabel = "完成导览"
    )
)

// ==================== Section Renderer ====================

@Composable
fun SectionRenderer(section: ContentSection) {
    when (section.layout) {
        SectionLayout.TEXT_ONLY -> TextSection(section = section)
        SectionLayout.IMAGE_FULL -> ImageFullSection(section = section)
        SectionLayout.IMAGE_GALLERY -> ImageGallerySection(section = section)
        SectionLayout.TEXT_IMAGE_RIGHT -> TextImageSection(section = section, imageOnRight = true)
        SectionLayout.TEXT_IMAGE_LEFT -> TextImageSection(section = section, imageOnRight = false)
        SectionLayout.QUOTE -> QuoteSection(section = section)
        SectionLayout.HIGHLIGHTS -> HighlightsStatisticSection(section = section)
        SectionLayout.TIMELINE -> TimelineSection(section = section)
        SectionLayout.SPOTS_GRID -> SpotsGridSection(section = section)
        SectionLayout.STATISTICS -> StatisticsSection(section = section)
    }
}

// ==================== Hero Image ====================

@Composable
fun HeroImageSection(
    heroImage: HeroImage,
    scenicName: String,
    subtitle: String?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(16.dp))
    ) {
        AsyncImage(
            model = heroImage.url,
            contentDescription = heroImage.caption,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.6f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Text(
                text = scenicName,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            subtitle?.let {
                Text(
                    text = it,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
    }
}

// ==================== Text Section ====================

@Composable
fun TextSection(section: ContentSection) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        section.title?.let {
            Text(
                text = it,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1D7A6D)
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        section.subtitle?.let {
            Text(
                text = it,
                fontSize = 14.sp,
                color = Color(0xFF5A6772)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        section.items.forEach { item ->
            when (item.type) {
                ContentType.PARAGRAPH -> {
                    item.text?.let { text ->
                        Text(
                            text = text,
                            fontSize = 16.sp,
                            color = Color(0xFF1C2328),
                            lineHeight = 24.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
                ContentType.HEADING -> {
                    item.text?.let { text ->
                        Text(
                            text = text,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1C2328),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
                else -> {}
            }
        }
    }
}

// ==================== Image Full Section ====================

@Composable
fun ImageFullSection(section: ContentSection) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        section.title?.let {
            Text(
                text = it,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1D7A6D)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        section.items.forEach { item ->
            when (item.type) {
                ContentType.IMAGE -> {
                    item.imageUrl?.let { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = item.imageCaption,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        item.imageCaption?.let { caption ->
                            Text(
                                text = caption,
                                fontSize = 12.sp,
                                color = Color(0xFF5A6772),
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }
                ContentType.PARAGRAPH -> {
                    item.text?.let { text ->
                        Text(
                            text = text,
                            fontSize = 16.sp,
                            color = Color(0xFF1C2328),
                            lineHeight = 24.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
                else -> {}
            }
        }
    }
}

// ==================== Image Gallery Section ====================

@Composable
fun ImageGallerySection(section: ContentSection) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        section.title?.let {
            Text(
                text = it,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1D7A6D),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        val images = section.items.filter { it.type == ContentType.IMAGE && it.imageUrl != null }
        if (images.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(images) { item ->
                    Column(modifier = Modifier.width(180.dp)) {
                        AsyncImage(
                            model = item.imageUrl,
                            contentDescription = item.imageCaption,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        item.imageCaption?.let { caption ->
                            Text(
                                text = caption,
                                fontSize = 11.sp,
                                color = Color(0xFF5A6772),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== Text + Image Section ====================

@Composable
fun TextImageSection(section: ContentSection, imageOnRight: Boolean) {
    val textItem = section.items.find { it.type == ContentType.PARAGRAPH }
    val imageItem = section.items.find { it.type == ContentType.IMAGE }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        section.title?.let {
            Text(
                text = it,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1D7A6D)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        section.subtitle?.let {
            Text(
                text = it,
                fontSize = 14.sp,
                color = Color(0xFF5A6772),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            if (!imageOnRight && imageItem != null) {
                AsyncImage(
                    model = imageItem.imageUrl,
                    contentDescription = imageItem.imageCaption,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(140.dp)
                        .height(100.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            textItem?.text?.let { text ->
                Text(
                    text = text,
                    fontSize = 15.sp,
                    color = Color(0xFF1C2328),
                    lineHeight = 22.sp,
                    modifier = Modifier.weight(1f)
                )
            }

            if (imageOnRight && imageItem != null) {
                Spacer(modifier = Modifier.width(12.dp))
                AsyncImage(
                    model = imageItem.imageUrl,
                    contentDescription = imageItem.imageCaption,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(140.dp)
                        .height(100.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            }
        }
    }
}

// ==================== Quote Section ====================

@Composable
fun QuoteSection(section: ContentSection) {
    val quoteItem = section.items.find { it.type == ContentType.QUOTE }
    quoteItem?.text?.let { text ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .background(
                    color = Color(0xFFF2A541).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "\"",
                    fontSize = 40.sp,
                    color = Color(0xFFF2A541),
                    lineHeight = 20.sp,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = text,
                    fontSize = 17.sp,
                    fontStyle = FontStyle.Italic,
                    color = Color(0xFF1C2328),
                    textAlign = TextAlign.Center,
                    lineHeight = 26.sp
                )
                quoteItem.imageCaption?.let { caption ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = caption,
                        fontSize = 13.sp,
                        color = Color(0xFF5A6772),
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }
}

// ==================== Highlights Section ====================

@Composable
fun HighlightsSection(highlights: List<String>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        highlights.forEach { highlight ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF1D7A6D).copy(alpha = 0.1f)
            ) {
                Text(
                    text = highlight,
                    fontSize = 12.sp,
                    color = Color(0xFF1D7A6D),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

// ==================== Statistics Section ====================

@Composable
fun StatisticsSection(section: ContentSection) {
    val stats = section.items.filter { it.type == ContentType.STATISTIC }
    if (stats.isEmpty()) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        stats.forEach { stat ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stat.statisticValue ?: "",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFB91C1C)
                )
                Text(
                    text = stat.statisticLabel ?: "",
                    fontSize = 12.sp,
                    color = Color(0xFF5A6772)
                )
            }
        }
    }
}

// ==================== Highlights + Statistics Combined ====================

@Composable
fun HighlightsStatisticSection(section: ContentSection) {
    val stats = section.items.filter { it.type == ContentType.STATISTIC }
    if (stats.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        section.title?.let {
            Text(
                text = it,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1D7A6D)
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            stats.forEach { stat ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFB91C1C).copy(alpha = 0.08f),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = stat.statisticValue ?: "",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB91C1C)
                        )
                        Text(
                            text = stat.statisticLabel ?: "",
                            fontSize = 12.sp,
                            color = Color(0xFF5A6772),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// ==================== Timeline Section ====================

@Composable
fun TimelineSection(section: ContentSection) {
    val timelineItem = section.items.find { it.type == ContentType.TIMELINE }
    val events = timelineItem?.timelineEvents ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        section.title?.let {
            Text(
                text = it,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1D7A6D)
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        val lineColor = Color(0xFFB91C1C).copy(alpha = 0.28f)
        val nodeColor = Color(0xFFB91C1C)

        events.forEachIndexed { index, event ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .width(86.dp)
                        .fillMaxHeight()
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(
                                top = if (index == 0) 10.dp else 0.dp,
                                end = 6.dp
                            )
                            .width(2.dp)
                            .then(
                                if (index == events.lastIndex) Modifier.height(10.dp)
                                else Modifier.fillMaxHeight()
                            )
                            .background(lineColor)
                    )
                    Text(
                        text = event.year,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .width(68.dp)
                            .padding(top = 1.dp),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = nodeColor,
                        textAlign = TextAlign.End,
                        maxLines = 1
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 4.dp, end = 1.dp)
                            .size(12.dp)
                            .background(nodeColor, CircleShape)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = if (index == events.lastIndex) 0.dp else 16.dp)
                ) {
                    Text(
                        text = event.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1C2328)
                    )
                    Text(
                        text = event.description,
                        fontSize = 13.sp,
                        color = Color(0xFF5A6772),
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

// ==================== Spots Grid Section ====================

@Composable
fun SpotsGridSection(section: ContentSection) {
    val spotsItem = section.items.find { it.type == ContentType.SPOTS }
    val spots = spotsItem?.spots ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        section.title?.let {
            Text(
                text = it,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1D7A6D)
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        val rows = spots.chunked(2)
        rows.forEach { rowSpots ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowSpots.forEach { spot ->
                    SpotCardItem(
                        spot = spot,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowSpots.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
fun SpotCardItem(spot: SpotCard, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = modifier
    ) {
        Column {
            spot.imageUrl?.takeIf { it.isNotBlank() }?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = spot.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                )
            } ?: Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(Color(0xFFF0F0F0)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = spot.name.take(2),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFB91C1C).copy(alpha = 0.5f)
                )
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = spot.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1C2328),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = spot.description,
                    fontSize = 12.sp,
                    color = Color(0xFF5A6772),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
                spot.tags?.let { tags ->
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        tags.take(2).forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFB91C1C).copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = tag,
                                    fontSize = 10.sp,
                                    color = Color(0xFFB91C1C),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
