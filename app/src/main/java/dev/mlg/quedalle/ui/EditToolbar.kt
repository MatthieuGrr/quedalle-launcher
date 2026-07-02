package dev.mlg.quedalle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mlg.quedalle.R
import dev.mlg.quedalle.ui.theme.LocalQuedallePalette

/**
 * Toolbar shown in grid-edit mode: add tiles, resize the grid, done.
 * Settings is not here — it lives in the home menu (long-press empty space).
 */
@Composable
fun EditToolbar(
    columns: Int, rows: Int,
    onColumnsChange: (Int) -> Unit, onRowsChange: (Int) -> Unit,
    onAddSpacer: () -> Unit, onAddDivider: () -> Unit,
    onDone: () -> Unit,
) {
    val palette = LocalQuedallePalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.surfaceDim),
    ) {
        HorizontalDivider(color = palette.borderIdle)

        // Row 1: add tiles + done
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onAddSpacer,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) { Text(stringResource(R.string.action_add_spacer), fontSize = 13.sp) }

            TextButton(
                onClick = onAddDivider,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) { Text(stringResource(R.string.action_add_divider), fontSize = 13.sp) }

            Spacer(Modifier.weight(1f))

            TextButton(onClick = onDone) {
                Text(stringResource(R.string.action_done), color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }

        // Row 2: grid size
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StepControl(stringResource(R.string.label_columns), columns, 2, 5,  { onColumnsChange(columns - 1) }, { onColumnsChange(columns + 1) })
            StepControl(stringResource(R.string.label_rows),    rows,    1, 20, { onRowsChange(rows - 1) },       { onRowsChange(rows + 1) })
        }
    }
}

@Composable
private fun StepControl(
    label: String, value: Int, min: Int, max: Int,
    onDec: () -> Unit, onInc: () -> Unit,
) {
    val palette = LocalQuedallePalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = palette.textMuted, fontSize = 12.sp)
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onDec, enabled = value > min, modifier = Modifier.size(34.dp)) {
            Text("−", color = if (value > min) palette.textStrong else palette.textDisabled, fontSize = 18.sp)
        }
        Text("$value", color = palette.textStrong, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 22.dp))
        IconButton(onClick = onInc, enabled = value < max, modifier = Modifier.size(34.dp)) {
            Text("+", color = if (value < max) palette.textStrong else palette.textDisabled, fontSize = 18.sp)
        }
    }
}
