package com.triparchive.presentation.home.components

import android.view.WindowManager
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.triparchive.R
import com.triparchive.core.designsystem.TripArchiveTheme
import com.triparchive.domain.navigation.DestinationCategory
import com.triparchive.presentation.management.Ink
import com.triparchive.presentation.management.Pink

/** OTHER is a deliberate choice; null [CategoryChoice] means no choice has been made yet. */
internal enum class CategoryChoice(
    val category: DestinationCategory?,
    val label: String,
    @DrawableRes val icon: Int,
) {
    MART(DestinationCategory.MART, "마트", R.drawable.category_choice_mart),
    SCHOOL(DestinationCategory.SCHOOL, "학교", R.drawable.category_choice_school),
    TOURISM(DestinationCategory.TOURISM, "관광", R.drawable.category_choice_tourism),
    FOOD(DestinationCategory.FOOD, "음식점", R.drawable.category_choice_restaurant),
    HOSPITAL(DestinationCategory.HOSPITAL, "병원", R.drawable.category_choice_hospital),
    OTHER(null, "기타", R.drawable.category_choice_other),
}

@Composable
internal fun CategorySelectionDialog(
    selected: CategoryChoice?,
    secondsRemaining: Int,
    scale: Float,
    onSelect: (CategoryChoice) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window?.setDimAmount(0f)
        }
        CategorySelectionDialogContent(selected, secondsRemaining, scale, onSelect, onConfirm)
    }
}

@Composable
private fun CategorySelectionDialogContent(
    selected: CategoryChoice?,
    secondsRemaining: Int,
    scale: Float,
    onSelect: (CategoryChoice) -> Unit,
    onConfirm: () -> Unit,
) {
    Column(
        Modifier
            .width((758 * scale).dp)
            .height((936 * scale).dp)
            .background(Color.White, RoundedCornerShape((20 * scale).dp))
            .padding((10 * scale).dp)
            .testTag("category-selection-dialog"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy((32 * scale).dp, Alignment.CenterVertically),
    ) {
        Text(
            "어떤 목적지인가요?",
            modifier = Modifier.fillMaxWidth().padding((10 * scale).dp),
            color = Ink,
            fontSize = (42 * scale).sp,
            lineHeight = (64 * scale).sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy((32 * scale).dp)) {
            CategoryChoice.entries.chunked(3).forEach { row ->
                Row(
                    Modifier.width((648 * scale).dp).height((263 * scale).dp),
                    horizontalArrangement = Arrangement.spacedBy((24 * scale).dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    row.forEach { choice ->
                        CategoryChoiceItem(choice, selected == choice, scale) { onSelect(choice) }
                    }
                }
            }
        }
        Row(
            Modifier
                .width((570 * scale).dp)
                .height((104 * scale).dp)
                .background(if (selected != null) Pink else Color(0xFFE5E8EB), RoundedCornerShape((24 * scale).dp))
                .clickable(
                    enabled = selected != null,
                    role = Role.Button,
                    interactionSource = null,
                    indication = null,
                    onClick = onConfirm,
                ).testTag("category-selection-confirm"),
            horizontalArrangement = Arrangement.spacedBy((12 * scale).dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("확인", color = Color.White, fontSize = (32 * scale).sp, fontWeight = FontWeight.Bold)
            Box(
                Modifier.size((36 * scale).dp).background(Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    secondsRemaining.toString(),
                    color = Pink,
                    fontSize = (22 * scale).sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun CategoryChoiceItem(
    choice: CategoryChoice,
    selected: Boolean,
    scale: Float,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .width((200 * scale).dp)
            .height((228 * scale).dp)
            .border((3 * scale).dp, if (selected) Pink else Color.Transparent, RoundedCornerShape((20 * scale).dp))
            .selectable(
                selected = selected,
                interactionSource = null,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            ).padding(vertical = (4 * scale).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size((156 * scale).dp), contentAlignment = Alignment.Center) {
            val iconWidth = if (choice == CategoryChoice.TOURISM) 72 else 96
            val iconHeight =
                when (choice) {
                    CategoryChoice.SCHOOL -> 77
                    CategoryChoice.TOURISM -> 72
                    else -> 96
                }
            Image(
                painter = painterResource(choice.icon),
                contentDescription = null,
                modifier = Modifier.width((iconWidth * scale).dp).height((iconHeight * scale).dp),
                colorFilter = ColorFilter.tint(if (selected) Pink else Color.Black),
            )
        }
        Text(
            choice.label,
            modifier = Modifier.padding(top = (20 * scale).dp),
            color = Color(0xFF4E5968),
            fontSize = (28 * scale).sp,
            lineHeight = (44 * scale).sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Preview(widthDp = 758, heightDp = 936)
@Composable
private fun CategorySelectionDialogPreview() {
    TripArchiveTheme { CategorySelectionDialogContent(CategoryChoice.HOSPITAL, 5, 1f, {}, {}) }
}
