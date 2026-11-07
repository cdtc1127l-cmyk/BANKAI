package com.bankai.anime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private val BG=Color(0xFF080311)
private val PANEL=Color(0xCC160A2E)
private val PURPLE=Color(0xFF7C22FF)
private val PINK=Color(0xFFD52BFF)
private val TEXT=Color(0xFFF7F1FF)

data class Anime(val title:String,val season:Int,val episode:Int,val total:Int,val image:String,val progress:Int,val status:String,val weekly:String,var inWeekly:Boolean=true)

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{BankaiApp()}}
}

@Composable
fun BankaiApp(){
 var page by remember{mutableStateOf("Home")}\n var selectedAnime by remember{mutableStateOf<Anime?>(null)}\n var watched by remember{mutableStateOf(setOf<String>())}
 val anime=listOf(
  Anime("The Lost Demon",1,12,24,"https://images.unsplash.com/photo-1578632767115-351597cf2477?w=1000",50,"Watching","Friday"),
  Anime("Shadow Academy",1,8,12,"https://images.unsplash.com/photo-1541560052-77ec1bbc09f7?w=1000",67,"Watching","Tuesday"),
  Anime("Crimson Moon",1,24,24,"https://images.unsplash.com/photo-1534447677768-be436bb09401?w=1000",100,"Watched","Monday"),
  Anime("Sakura Blade",2,4,12,"https://images.unsplash.com/photo-1518005020951-eccb494ad742?w=1000",33,"Watching","Thursday"),
  Anime("Moonlit Ronin",1,0,10,"https://images.unsplash.com/photo-1499346030926-9a72daac6c63?w=1000",0,"Watchlist","Friday")
 )
 Row(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF210743),BG))).padding(12.dp)){
  Sidebar(page){page=it}
  Column(Modifier.fillMaxSize().padding(start=14.dp)){
   TopBar()
   Spacer(Modifier.height(10.dp))
   when(page){
    "Home"->Home(anime)
    page=="My Anime"->Library(anime){selectedAnime=it}
    page=="Episodes"->Episodes(anime){selectedAnime=it}
    page=="Weekly"->Weekly(anime)
    "Stats"->Stats(anime)
   }
  }
 }
}

@Composable
fun Sidebar(page:String,go:(String)->Unit){
 Column(Modifier.width(118.dp).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(Color(0xB20C061D)).padding(9.dp)){
  Text("☁",fontSize=30.sp,color=Color.Red,modifier=Modifier.align(Alignment.CenterHorizontally))
  Text("Anime Tracker",color=TEXT,fontSize=15.sp,fontWeight=FontWeight.Bold,modifier=Modifier.align(Alignment.CenterHorizontally))
  Spacer(Modifier.height(16.dp))
  listOf("Home" to Icons.Default.Home,"My Anime" to Icons.Default.Star,"Episodes" to Icons.Default.PlayArrow,"Weekly" to Icons.Default.DateRange,"Stats" to Icons.Default.BarChart).forEach{item->
   Nav(item.first,item.second,page==item.first){go(item.first)}
  }
  Spacer(Modifier.weight(1f))
  Nav("Library",Icons.Default.Folder,false){}
  Nav("Settings",Icons.Default.Settings,false){}
  Row(verticalAlignment=Alignment.CenterVertically){Text("Dark Mode",color=Color.LightGray,fontSize=9.sp);Switch(true,{},modifier=Modifier.scale(.55f))}
 }
}
@Composable
fun Nav(name:String,icon:ImageVector,active:Boolean,onClick:()->Unit){
 Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if(active)Brush.horizontalGradient(listOf(PURPLE,PINK))else Brush.horizontalGradient(listOf(Color.Transparent,Color.Transparent))).clickable{onClick()}.padding(9.dp),verticalAlignment=Alignment.CenterVertically){
  Icon(icon,null,tint=TEXT,modifier=Modifier.size(19.dp));Spacer(Modifier.width(7.dp));Text(name,color=TEXT,fontSize=10.sp)
 }
 Spacer(Modifier.height(5.dp))
}
@Composable
fun TopBar(){
 Row(Modifier.fillMaxWidth().height(50.dp),verticalAlignment=Alignment.CenterVertically){
  Spacer(Modifier.weight(1f))
  OutlinedTextField("",{},placeholder={Text("Search anime...",fontSize=11.sp)},leadingIcon={Icon(Icons.Default.Search,null)},singleLine=true,shape=RoundedCornerShape(30.dp),modifier=Modifier.width(260.dp).height(46.dp))
  IconButton({}){Icon(Icons.Default.Notifications,null,tint=TEXT)}
  Box(Modifier.size(38.dp).clip(RoundedCornerShape(50)).background(Brush.linearGradient(listOf(PURPLE,PINK))),contentAlignment=Alignment.Center){Text("A",color=TEXT,fontWeight=FontWeight.Bold)}
 }
}
@Composable
fun Home(a:List<Anime>){
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.Top){Hero(a[0],Modifier.weight(1.8f));StatsCard(a,Modifier.weight(.65f))}
  Spacer(Modifier.height(12.dp))
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
   Section("Recently Watched",a.take(2),Modifier.weight(1f))
   Section("New Episodes",a.drop(1).take(2),Modifier.weight(1f))
   WeeklyMini(a,Modifier.weight(1f))
  }
  Spacer(Modifier.height(12.dp)); Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.Top){Section("My Watchlist",a.filter{it.status=="Watchlist"},Modifier.weight(1.7f)); QuotePanel(Modifier.weight(.8f))}
 }
}
@Composable
fun Hero(x:Anime,m:Modifier){
 Card(m,shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=PANEL)){
  Box(Modifier.fillMaxWidth().height(310.dp)){
   AsyncImage(x.image,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
   Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color(0xEE080311)))))
   Column(Modifier.align(Alignment.BottomStart).padding(22.dp)){
    Text("▶  Continue Watching",color=TEXT,fontSize=11.sp,fontWeight=FontWeight.Bold,modifier=Modifier.background(PURPLE,RoundedCornerShape(20.dp)).padding(horizontal=13.dp,vertical=8.dp))
    Text(x.title,color=TEXT,fontSize=32.sp,fontWeight=FontWeight.Bold)
    Text("Season "+x.season+"  •  Episode "+x.episode+" / "+x.total,color=TEXT,fontSize=14.sp)
    Text("A journey of destiny, power and forgotten memories...",color=Color.LightGray,fontSize=11.sp)
    Row(verticalAlignment=Alignment.CenterVertically){Button({},colors=ButtonDefaults.buttonColors(containerColor=PURPLE)){Text("▶ Continue")};Spacer(Modifier.width(8.dp));OutlinedButton({}){Text("☁ Upload")};Spacer(Modifier.width(8.dp));OutlinedButton({}){Text("↻ Replace")}}
   }
  }
 }
}
@Composable
fun StatsCard(a:List<Anime>,m:Modifier){
 Card(m.height(310.dp),shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=Color(0xCC180A37))){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){Text("Your Anime Stats",color=TEXT,fontWeight=FontWeight.Bold);MiniStat("Watching",a.count{it.status=="Watching"}.toString(),Icons.Default.PlayArrow,PURPLE);MiniStat("Completed",a.count{it.status=="Watched"}.toString(),Icons.Default.Check,Color.Cyan);MiniStat("Episodes Watched",a.sumOf{it.episode}.toString(),Icons.Default.DateRange,PINK);MiniStat("Watch Time","86h 20m",Icons.Default.Schedule,Color(0xFF55C8FF))}}
}
@Composable fun MiniStat(n:String,v:String,ic:ImageVector,c:Color){Row(verticalAlignment=Alignment.CenterVertically){Icon(ic,null,tint=c,modifier=Modifier.size(25.dp));Spacer(Modifier.width(10.dp));Column{Text(n,color=TEXT,fontSize=10.sp);Text(v,color=TEXT,fontSize=18.sp,fontWeight=FontWeight.Bold)}}}
@Composable
fun Section(title:String,a:List<Anime>,m:Modifier){Column(m){Row{Text(title,color=TEXT,fontWeight=FontWeight.Bold,fontSize=14.sp);Spacer(Modifier.weight(1f));Text("See all →",color=Color.LightGray,fontSize=10.sp)};Spacer(Modifier.height(7.dp));LazyRow(horizontalArrangement=Arrangement.spacedBy(9.dp)){items(a){CardItem(it)}}}}
@Composable
fun CardItem(x:Anime){Card(Modifier.width(245.dp),shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=Color(0xCC140825))){Row(Modifier.padding(8.dp)){AsyncImage(x.image,null,Modifier.size(78.dp).clip(RoundedCornerShape(11.dp)),contentScale=ContentScale.Crop);Column(Modifier.padding(start=9.dp)){Text(x.title,color=TEXT,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis);Text("S"+x.season+" • E"+x.episode,color=Color.LightGray,fontSize=10.sp);Text(x.progress.toString()+"% watched",color=Color(0xFFE2B0FF),fontSize=10.sp);Button({},contentPadding=PaddingValues(horizontal=12.dp),modifier=Modifier.height(30.dp),colors=ButtonDefaults.buttonColors(containerColor=PURPLE)){Text("▶ Play",fontSize=9.sp)}}}}}
@Composable
fun WeeklyMini(a:List<Anime>,m:Modifier){Card(m,shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=PANEL)){Column(Modifier.padding(13.dp)){Text("Weekly Releases",color=TEXT,fontWeight=FontWeight.Bold,fontSize=14.sp);Row(Modifier.horizontalScroll(rememberScrollState())){a.take(5).forEach{x->Column(Modifier.width(70.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(x.weekly,color=TEXT,fontSize=9.sp);AsyncImage(x.image,null,Modifier.size(53.dp).clip(RoundedCornerShape(9.dp)),contentScale=ContentScale.Crop)}}}}}}
@Composable
fun Library(a:List<Anime>,open:(Anime)->Unit){Column(Modifier.fillMaxSize()){Text("My Anime",color=TEXT,fontSize=28.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(12.dp));LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)){items(a){x->Column(Modifier.width(145.dp)){AsyncImage(x.image,null,Modifier.height(195.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable{open(x)},contentScale=ContentScale.Crop);Text(x.title,color=TEXT,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis);Text(x.status+" • "+x.progress+"%",color=Color.LightGray,fontSize=10.sp)}}}}}
@Composable
fun Episodes(a:List<Anime>,open:(Anime)->Unit){Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){Text("Episodes",color=TEXT,fontSize=28.sp,fontWeight=FontWeight.Bold);Text("Season • Episode • Video management",color=Color.LightGray);Spacer(Modifier.height(10.dp));a.forEach{x->Card(Modifier.fillMaxWidth().padding(bottom=8.dp),colors=CardDefaults.cardColors(containerColor=PANEL)){Column(Modifier.padding(13.dp)){Text(x.title,color=TEXT,fontWeight=FontWeight.Bold,modifier=Modifier.clickable{open(x)});Text("Season "+x.season+" • "+x.total+" episodes",color=Color.LightGray);Row(Modifier.horizontalScroll(rememberScrollState())){for(e in 1..minOf(x.total,12)){FilterChip(selected=e<=x.episode,onClick={},label={Text("E"+e,fontSize=9.sp)})}}}}}}}
@Composable
fun Weekly(a:List<Anime>){Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){Text("Weekly",color=TEXT,fontSize=28.sp,fontWeight=FontWeight.Bold);Text("Add or remove anime without deleting your library.",color=Color.LightGray);Spacer(Modifier.height(10.dp));a.forEach{x->Card(Modifier.fillMaxWidth().padding(bottom=8.dp),colors=CardDefaults.cardColors(containerColor=PANEL)){Row(Modifier.padding(11.dp),verticalAlignment=Alignment.CenterVertically){AsyncImage(x.image,null,Modifier.size(58.dp).clip(RoundedCornerShape(10.dp)),contentScale=ContentScale.Crop);Column(Modifier.weight(1f).padding(horizontal=10.dp)){Text(x.title,color=TEXT,fontWeight=FontWeight.Bold);Text(x.weekly,color=Color.LightGray)};OutlinedButton({}){Text("Remove")}}}}}}


@Composable
fun EpisodeManager(x:Anime,watched:Set<String>,toggle:(String)->Unit,back:()->Unit){
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){
  Row(verticalAlignment=Alignment.CenterVertically){IconButton(back){Icon(Icons.Default.ArrowBack,null,tint=TEXT)};Column{Text(x.title,color=TEXT,fontSize=25.sp,fontWeight=FontWeight.Bold);Text("Season "+x.season+" • "+x.total+" Episodes",color=Color.LightGray)}} 
  Spacer(Modifier.height(10.dp))
  Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=PANEL)){
   Column(Modifier.padding(15.dp)){
    Text("Episode Manager",color=TEXT,fontSize=15.sp,fontWeight=FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    for(e in 1..x.total){
     val key=x.title+"-"+x.season+"-"+e
     Row(Modifier.fillMaxWidth().padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically){
      Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(if(key in watched) Color(0xFF15D6A0) else Color(0xFF29104C)),contentAlignment=Alignment.Center){Text("E$e",color=TEXT,fontSize=10.sp,fontWeight=FontWeight.Bold)}
      Column(Modifier.weight(1f).padding(horizontal=10.dp)){Text("Episode $e",color=TEXT,fontWeight=FontWeight.Bold);Text(if(key in watched)"Watched" else "Unwatched",color=if(key in watched) Color(0xFF54F5C2) else Color.LightGray,fontSize=9.sp)}
      TextButton({toggle(key)}){Text(if(key in watched)"✓ Watched" else "Mark Watched",color=if(key in watched)Color(0xFF54F5C2) else TEXT,fontSize=9.sp)}
      OutlinedButton({launchPlayerPlaceholder(x,e)},contentPadding=PaddingValues(horizontal=9.dp)){Text("▶",fontSize=10.sp)}
     }
    }
   }
  }
 }
}

fun launchPlayerPlaceholder(x:Anime,e:Int){ /* External VLC/MX launch is wired when an episode video URI is imported. */ }

@Composable
fun Stats(a:List<Anime>){Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){Text("Stats",color=TEXT,fontSize=28.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(10.dp));listOf("Total Anime" to a.size,"Watched Episodes" to a.sumOf{it.episode},"Watchlist" to a.count{it.status=="Watchlist"},"Weekly Anime" to a.size).forEach{p->Card(Modifier.fillMaxWidth().padding(bottom=8.dp),colors=CardDefaults.cardColors(containerColor=PANEL)){Row(Modifier.padding(18.dp)){Text(p.first,color=TEXT,modifier=Modifier.weight(1f));Text(p.second.toString(),color=TEXT,fontSize=20.sp,fontWeight=FontWeight.Bold)}}}}}
