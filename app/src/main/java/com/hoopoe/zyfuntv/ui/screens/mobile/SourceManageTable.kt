package com.hoopoe.zyfuntv.ui.screens.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hoopoe.zyfuntv.ui.component.mobile.EmptyHint
import com.hoopoe.zyfuntv.ui.theme.mobile.MobileSize

/** 管理表格一行的中立展示模型，屏蔽站点 / 直播源 / 解析源 / 网盘的实体差异 */
data class TblRow(
    val id: String,
    val name: String,
    val typeLabel: String,
    val groupLabel: String?,
    val isActive: Boolean,
    val isDefault: Boolean
)

private val TblCheckW = 38.dp
private val TblTypeW = 96.dp
private val TblGroupW = 108.dp
private val TblStatusW = 88.dp
private val TblActionW = 188.dp

/**
 * 影视站点 / 直播源 / 解析源 / 网盘共用的批量管理表格：
 * 顶部工具条（添加 / 启用 / 禁用 / 删除 / 可选检测）+ 搜索资源框；
 * 表头：复选框、名称、类型、(分组)、状态、操作。
 * 批量操作由调用方按 [selected] 勾选集合落地；行内单项回调以 id 回传。
 */
@Composable
fun SourceManageTable(
    rows: List<TblRow>,
    showGroup: Boolean,
    showDetect: Boolean,
    selected: Set<String>,
    onSelectChange: (Set<String>) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    onAdd: () -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onDelete: () -> Unit,
    onDetect: (() -> Unit)? = null,
    onToggleActive: (String, Boolean) -> Unit,
    onSetDefault: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDeleteOne: (String) -> Unit
) {
    val q = query.trim().lowercase()
    val visible = rows.filter {
        q.isEmpty() ||
            it.name.lowercase().contains(q) ||
            it.typeLabel.lowercase().contains(q) ||
            (it.groupLabel ?: "").lowercase().contains(q)
    }
    val allChecked = visible.isNotEmpty() && visible.all { selected.contains(it.id) }

    Column(verticalArrangement = Arrangement.spacedBy(MobileSize.smallMargin)) {
        // 工具条：左侧批量操作，右侧搜索资源
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MobileSize.miniMargin)
        ) {
            TblToolButton("添加", primary = true, onClick = onAdd)
            TblToolButton("启用", onClick = onEnable)
            TblToolButton("禁用", onClick = onDisable)
            TblToolButton("删除", danger = true, onClick = onDelete)
            if (showDetect) {
                TblToolButton("检测", onClick = { onDetect?.invoke() })
            }
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("搜索资源") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                textStyle = MaterialTheme.typography.bodyMedium
            )
        }

        // 表头
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MobileSize.miniMargin, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MobileSize.miniMargin)
        ) {
            Checkbox(
                checked = allChecked,
                onCheckedChange = {
                    onSelectChange(
                        if (it) selected + visible.map { r -> r.id }.toSet()
                        else selected - visible.map { r -> r.id }.toSet()
                    )
                },
                modifier = Modifier.width(TblCheckW),
                colors = CheckboxDefaults.colors()
            )
            Text(
                "名称",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "类型",
                modifier = Modifier.width(TblTypeW),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (showGroup) {
                Text(
                    "分组",
                    modifier = Modifier.width(TblGroupW),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "状态",
                modifier = Modifier.width(TblStatusW),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "操作",
                modifier = Modifier.width(TblActionW),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (visible.isEmpty()) {
            EmptyHint(if (q.isEmpty()) "暂无数据" else "无匹配资源")
        } else {
            visible.forEach { row ->
                TblDataRow(
                    row = row,
                    showGroup = showGroup,
                    checked = selected.contains(row.id),
                    onCheckedChange = { checked ->
                        onSelectChange(if (checked) selected + row.id else selected - row.id)
                    },
                    onToggleActive = onToggleActive,
                    onSetDefault = onSetDefault,
                    onEdit = onEdit,
                    onDeleteOne = onDeleteOne
                )
            }
        }
    }
}

@Composable
private fun TblDataRow(
    row: TblRow,
    showGroup: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onToggleActive: (String, Boolean) -> Unit,
    onSetDefault: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDeleteOne: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MobileSize.middleRadius))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = if (checked) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(MobileSize.middleRadius)
            )
            .padding(horizontal = MobileSize.miniMargin, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MobileSize.miniMargin)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.width(TblCheckW)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.name + if (row.isDefault) "（默认）" else "",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(modifier = Modifier.width(TblTypeW)) {
            TypeChip(row.typeLabel)
        }
        if (showGroup) {
            Text(
                text = row.groupLabel ?: "未分组",
                modifier = Modifier.width(TblGroupW),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(modifier = Modifier.width(TblStatusW), contentAlignment = Alignment.CenterStart) {
            Switch(
                checked = row.isActive,
                onCheckedChange = { onToggleActive(row.id, it) }
            )
        }
        Row(
            modifier = Modifier.width(TblActionW),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            TextButton(onClick = { onSetDefault(row.id) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                Text(
                    if (row.isDefault) "默认" else "设默认",
                    color = if (row.isDefault) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(onClick = { onEdit(row.id) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                Text("编辑", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { onDeleteOne(row.id) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                Text("删除", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun TypeChip(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(MobileSize.middleRadius))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TblToolButton(
    label: String,
    primary: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val color = when {
        danger -> MaterialTheme.colorScheme.error
        primary -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label, color = color, style = MaterialTheme.typography.labelLarge)
    }
}
