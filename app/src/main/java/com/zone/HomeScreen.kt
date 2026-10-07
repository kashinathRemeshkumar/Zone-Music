package com.zone


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zone.ui.theme.ZoneTheme
import org.intellij.lang.annotations.JdkConstants


@Composable
fun HomeScreen(engine: MusicEngine,onPillClick:()-> Unit){

    HomeScreenUI()

}

@Composable
fun HomeScreenUI(){
    Column(Modifier
        .fillMaxSize()
        .background(color = MaterialTheme.colorScheme.background)
        .statusBarsPadding(),
    ){
        Row(Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Zone Music",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp,
                modifier = Modifier.offset(x=20.dp)
            )

            IconButton(onClick = {}) {
                Icon(imageVector = Icons.Filled.Search,
                    tint = MaterialTheme.colorScheme.onBackground,
                    contentDescription = "Search",
                    modifier = Modifier.size(30.dp))
            }
        }
        Spacer(modifier = Modifier.size(25.dp))


        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp))
                .padding(bottom = 30.dp)
                .background(color = MaterialTheme.colorScheme.surface),
            contentPadding = PaddingValues(horizontal = 10.dp)
        ) {
                items(1000,key={it}){
                    Row(modifier = Modifier.fillMaxWidth()
                        .height(85.dp)
                        .padding(5.dp)
                        .border(width = 3.dp, color = MaterialTheme.colorScheme.outline),
                        verticalAlignment = Alignment.CenterVertically
                        ) {
                        Box(modifier = Modifier
                            .padding(start = 10.dp)
                            .size(55.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(color= MaterialTheme.colorScheme.primary)
                            .aspectRatio(1f)
                        ) {

                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 20.dp)
                        ) {
                            Text(
                                text = "Song $it",
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Artist",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(onClick = {}){
                            Icon(imageVector = Icons.Filled.MoreVert,
                                contentDescription = "play",
                                tint = MaterialTheme.colorScheme.onBackground,
                                )
                        }
                    }
                }
            }
        }
 }



@Composable
@Preview(showBackground = true)
fun HomeScreenPreview(){
    ZoneTheme {
        HomeScreenUI()
    }

}