package com.example.scenic_avatar_guide_app.ui.components.scenicintro

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.scenic_avatar_guide_app.ui.theme.ScenicPrimary
import com.example.scenic_avatar_guide_app.ui.theme.ScenicPrimaryBg
import com.example.scenic_avatar_guide_app.ui.theme.ScenicPrimaryDark
import com.example.scenic_avatar_guide_app.ui.theme.ScenicPrimaryDeep

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
                    containerColor = ScenicPrimaryBg
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
                    CircularProgressIndicator(color = ScenicPrimary)
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
                                containerColor = ScenicPrimaryDark
                            )
                        ) {
                            Text("重试")
                        }
                    }
                }
            }
            is UiState.Success -> {
                val content = state.data

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
                        tint = ScenicPrimaryDark,
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
                color = ScenicPrimaryDeep
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
                color = ScenicPrimaryDeep
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
                color = ScenicPrimaryDeep,
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
                color = ScenicPrimaryDeep
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
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(highlights) { highlight ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = ScenicPrimaryDeep.copy(alpha = 0.1f)
            ) {
                Text(
                    text = highlight,
                    fontSize = 12.sp,
                    color = ScenicPrimaryDeep,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
                    color = ScenicPrimaryDark
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
                color = ScenicPrimaryDeep
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (stats.size == 5) {
            FivePointHighlightsLayout(stats = stats)
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                stats.forEach { stat ->
                    HighlightStatisticCard(
                        stat = stat,
                        titleFontSize = 20,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun FivePointHighlightsLayout(stats: List<ContentItem>) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(228.dp)
    ) {
        val cardWidth = minOf(112.dp, maxWidth * 0.5f - 8.dp)
        val cardHeight = 72.dp
        val bottomInset = ((maxWidth - cardWidth * 2f - 8.dp) * 0.5f).coerceAtLeast(0.dp)

        HighlightStatisticCard(
            stat = stats[0],
            titleFontSize = 18,
            labelMaxLines = 1,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .width(cardWidth)
                .height(cardHeight)
        )
        HighlightStatisticCard(
            stat = stats[1],
            titleFontSize = 18,
            labelMaxLines = 1,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(cardWidth)
                .height(cardHeight)
        )
        HighlightStatisticCard(
            stat = stats[2],
            titleFontSize = 18,
            labelMaxLines = 1,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(cardWidth)
                .height(cardHeight)
        )
        HighlightStatisticCard(
            stat = stats[3],
            titleFontSize = 18,
            labelMaxLines = 1,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = bottomInset)
                .width(cardWidth)
                .height(cardHeight)
        )
        HighlightStatisticCard(
            stat = stats[4],
            titleFontSize = 18,
            labelMaxLines = 1,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = -bottomInset)
                .width(cardWidth)
                .height(cardHeight)
        )
    }
}

@Composable
private fun HighlightStatisticCard(
    stat: ContentItem,
    titleFontSize: Int,
    modifier: Modifier = Modifier,
    labelMaxLines: Int = Int.MAX_VALUE
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = ScenicPrimaryDark.copy(alpha = 0.08f),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 12.dp)
        ) {
            Text(
                text = stat.statisticValue ?: "",
                modifier = Modifier.fillMaxWidth(),
                fontSize = titleFontSize.sp,
                fontWeight = FontWeight.Bold,
                color = ScenicPrimaryDark,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stat.statisticLabel ?: "",
                modifier = Modifier.fillMaxWidth(),
                fontSize = 12.sp,
                color = Color(0xFF5A6772),
                textAlign = TextAlign.Center,
                maxLines = labelMaxLines,
                overflow = TextOverflow.Ellipsis
            )
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
                color = ScenicPrimaryDeep
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        val lineColor = ScenicPrimaryDark.copy(alpha = 0.28f)
        val nodeColor = ScenicPrimaryDark

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
                color = ScenicPrimaryDeep
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
                    color = ScenicPrimaryDark.copy(alpha = 0.5f)
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
                                color = ScenicPrimaryDark.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = tag,
                                    fontSize = 10.sp,
                                    color = ScenicPrimaryDark,
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
