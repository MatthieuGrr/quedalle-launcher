package dev.mlg.quedalle.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mlg.quedalle.R
import dev.mlg.quedalle.ui.theme.LocalQuedallePalette

@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalQuedallePalette.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.onFocusChanged { onFocusChange(it.isFocused) },
        placeholder = { Text(stringResource(R.string.search_placeholder), color = palette.textMuted, fontSize = 14.sp) },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null,
                tint = palette.textMuted, modifier = Modifier.size(18.dp))
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor        = palette.textStrong,
            unfocusedTextColor      = palette.textStrong,
            focusedBorderColor      = palette.borderFocused,
            unfocusedBorderColor    = palette.borderIdle,
            cursorColor             = palette.textStrong,
            focusedContainerColor   = palette.fieldFocused,
            unfocusedContainerColor = palette.surfaceDim,
        ),
        shape = RoundedCornerShape(14.dp),
        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
    )
}
