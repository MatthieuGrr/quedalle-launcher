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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mlg.quedalle.R
import dev.mlg.quedalle.ui.theme.QuedalleColors

@Composable
fun EditToolbar(
    columns: Int, rows: Int,
    onColumnsChange: (Int) -> Unit, onRowsChange: (Int) -> Unit,
    onAddSpacer: () -> Unit, onAddDivider: () -> Unit,
    onSettings: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(QuedalleColors.SurfaceDim),
    ) {
        // Row 1: add buttons + settings + done
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onAddSpacer,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) { Text(stringResource(R.string.action_add_spacer), fontSize = 12.sp) }

            TextButton(
                onClick = onAddDivider,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) { Text(stringResource(R.string.action_add_divider), fontSize = 12.sp) }

            TextButton(
                onClick = onSettings,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) { Text(stringResource(R.string.action_settings), fontSize = 12.sp) }

            Spacer(Modifier.weight(1f))

            TextButton(onClick = onDone) {
                Text(stringResource(R.string.action_done), color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium, fontSize = 13.sp)
            }
        }

        // Row 2: grid size controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = QuedalleColors.TextMuted, fontSize = 11.sp)
        Spacer(Modifier.width(6.dp))
        IconButton(onClick = onDec, enabled = value > min, modifier = Modifier.size(28.dp)) {
            Text("−", color = if (value > min) Color.White else QuedalleColors.TextDisabled, fontSize = 16.sp)
        }
        Text("$value", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 20.dp))
        IconButton(onClick = onInc, enabled = value < max, modifier = Modifier.size(28.dp)) {
            Text("+", color = if (value < max) Color.White else QuedalleColors.TextDisabled, fontSize = 16.sp)
        }
    }
}
