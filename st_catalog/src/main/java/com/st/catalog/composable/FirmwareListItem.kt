/*
 * Copyright (c) 2022(-0001) STMicroelectronics.
 * All rights reserved.
 * This software is licensed under terms that can be found in the LICENSE file in
 * the root directory of this software component.
 * If no LICENSE file comes with this software, it is provided AS-IS.
 */
package com.st.catalog.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.datasource.LoremIpsum
import com.st.ui.theme.AVAILABLE_DEMOS_MAX_LINES
import com.st.ui.theme.DESCRIPTION_MAX_LINES
import com.st.ui.theme.LocalDimensions
import com.st.ui.theme.PreviewBlueMSTheme
import com.st.ui.theme.SUBTITLE_MAX_LINES
import com.st.ui.theme.TITLE_MAX_LINES
import com.st.ui.utils.asString

@Composable
fun FirmwareListItem(
    modifier: Modifier = Modifier,
    name: String,
    boardName: String,
    listOfDemos: String,
    description: String,
    version: String
) {
    Surface(
        modifier = modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(size = LocalDimensions.current.cornerNormal),
        shadowElevation = LocalDimensions.current.elevationNormal
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(all = LocalDimensions.current.paddingNormal)
        ) {
            Text(
                modifier = Modifier.padding(bottom = LocalDimensions.current.paddingSmall),
                text = name,
                maxLines = TITLE_MAX_LINES,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                modifier = Modifier.padding(bottom = LocalDimensions.current.paddingSmall),
                text = version,
                maxLines = TITLE_MAX_LINES,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                modifier = Modifier.padding(bottom = LocalDimensions.current.paddingSmall),
                text = boardName,
                maxLines = SUBTITLE_MAX_LINES,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = description,
                maxLines = DESCRIPTION_MAX_LINES,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )

            if(listOfDemos.isNotBlank()) {
                Divider(modifier = Modifier.padding(top = LocalDimensions.current.paddingSmall))

                Text(
                    modifier = Modifier.padding(bottom = LocalDimensions.current.paddingSmall),
                    text = "Available Demos:",
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    modifier = Modifier.padding(bottom = LocalDimensions.current.paddingSmall,start=  LocalDimensions.current.paddingSmall),
                    text = listOfDemos,
                    maxLines = AVAILABLE_DEMOS_MAX_LINES,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/** ----------------------- PREVIEW --------------------------------------- **/

@Preview(showBackground = true)
@Composable
private fun FirmwareListItemPreview() {
    PreviewBlueMSTheme {
        FirmwareListItem(
            name = "BlueCoin Starter Kit",
            boardName = "STEVAL-BCNKT01V1",
            description = LoremIpsum(words = 30).asString(),
            listOfDemos = "Demo1, Demo 2",
            version = "1.0.0"
        )
    }
}
