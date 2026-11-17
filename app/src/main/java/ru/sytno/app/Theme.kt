package ru.sytno.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.caverock.androidsvg.SVG
import androidx.core.graphics.createBitmap
import ru.sytno.core.*

object C {
    val Bg=Color(0xFFF7F7F0); val Surface=Color.White; val Text=Color(0xFF202C25)
    val Secondary=Color(0xFF627066); val Border=Color(0xFFE2E6DF); val Brand=Color(0xFF245D42)
    val Deep=Color(0xFF183E2D); val Soft=Color(0xFFE9F1E6); val Accent=Color(0xFFD9EAA0)
    val Warning=Color(0xFF8D4F15); val Peach=Color(0xFFFFF0DB); val Danger=Color(0xFFA53832); val Rose=Color(0xFFFCEAE7)
}
@OptIn(ExperimentalTextApi::class)
val Manrope=FontFamily(*listOf(400,500,600,700,800).map { weight -> Font(R.font.manrope,FontWeight(weight),variationSettings=FontVariation.Settings(FontVariation.weight(weight))) }.toTypedArray())
private fun style(size:Int,line:Int,weight:FontWeight=FontWeight.Normal)=TextStyle(fontFamily=Manrope,fontWeight=weight,fontSize=size.sp,lineHeight=line.sp)
@Composable fun SytnoTheme(content:@Composable ()->Unit) {
    MaterialTheme(colorScheme=lightColorScheme(primary=C.Brand,onPrimary=Color.White,primaryContainer=C.Soft,onPrimaryContainer=C.Brand,background=C.Bg,onBackground=C.Text,surface=C.Surface,onSurface=C.Text,surfaceVariant=C.Soft,onSurfaceVariant=C.Secondary,outline=C.Border,error=C.Danger,errorContainer=C.Rose),
        typography=Typography(displaySmall=style(42,50,FontWeight.ExtraBold),headlineLarge=style(30,38,FontWeight.Bold),headlineMedium=style(24,32,FontWeight.Bold),titleLarge=style(20,28,FontWeight.Bold),titleMedium=style(16,24,FontWeight.SemiBold),bodyLarge=style(16,24),bodyMedium=style(14,20,FontWeight.Medium),bodySmall=style(12,18,FontWeight.Medium),labelLarge=style(15,22,FontWeight.Bold)),content=content)
}
private val iconCache=mutableMapOf<String,androidx.compose.ui.graphics.ImageBitmap>()
@Composable fun Glyph(name:String,modifier:Modifier=Modifier,description:String?=null,tint:Color=C.Brand,size:Dp=24.dp) {
    val context=LocalContext.current
    val bitmap=remember(name) { iconCache.getOrPut(name) {
        val picture=context.assets.open("icons/$name.svg").use { SVG.getFromInputStream(it).renderToPicture() }
        val image=createBitmap(96,96); val canvas=Canvas(image)
        canvas.scale(96f/picture.width,96f/picture.height); canvas.drawPicture(picture); image.asImageBitmap()
    } }
    Image(bitmap,description,modifier.size(size),colorFilter=ColorFilter.tint(tint))
}
@Composable fun FoodPhoto(index:Int?,modifier:Modifier=Modifier) {
    if(index==null) Box(modifier.clip(RoundedCornerShape(20.dp)).background(C.Soft),contentAlignment=Alignment.Center) { Glyph("utensils",size=40.dp) }
    else {
        val atlas=androidx.compose.ui.graphics.ImageBitmap.imageResource(R.drawable.food_atlas)
        val painter=remember(index,atlas) { val w=atlas.width/3; val h=atlas.height/2; BitmapPainter(atlas,IntOffset((index%3)*w,(index/3)*h),IntSize(w,h)) }
        Image(painter,null,modifier.clip(RoundedCornerShape(20.dp)),contentScale=ContentScale.Crop)
    }
}
@Composable fun Panel(modifier:Modifier=Modifier,color:Color=C.Surface,content:@Composable ColumnScope.()->Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(color).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)
}
@Composable fun Notice(title:String,body:String="",warning:Boolean=false,error:Boolean=false) {
    val color=if(error)C.Danger else if(warning)C.Warning else C.Brand
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if(error)C.Rose else if(warning)C.Peach else C.Soft).padding(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Glyph(if(error||warning)"triangle-alert" else "circle-check",tint=color)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) { Text(title,style=MaterialTheme.typography.bodyMedium,color=color); if(body.isNotBlank())Text(body,style=MaterialTheme.typography.bodySmall,color=color) }
    }
}
@Composable fun PrimaryButton(label:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,secondary:Boolean=false,danger:Boolean=false) {
    Button(onClick,modifier.fillMaxWidth().heightIn(min=56.dp),enabled=enabled,shape=RoundedCornerShape(16.dp),contentPadding=PaddingValues(horizontal=16.dp,vertical=14.dp),colors=ButtonDefaults.buttonColors(containerColor=if(danger)C.Rose else if(secondary)C.Soft else C.Brand,contentColor=if(danger)C.Danger else if(secondary)C.Brand else Color.White)) { Text(label,style=MaterialTheme.typography.labelLarge) }
}
@Composable fun Field(label:String,value:String,onValueChange:(String)->Unit,modifier:Modifier=Modifier,error:String?=null,numeric:Boolean=false,singleLine:Boolean=true) {
    OutlinedTextField(value,onValueChange,modifier.fillMaxWidth(),label={Text(label)},singleLine=singleLine,isError=error!=null,supportingText=if(error!=null){{Text(error)}}else null,shape=RoundedCornerShape(16.dp),textStyle=MaterialTheme.typography.bodyLarge,
        keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=if(numeric)androidx.compose.ui.text.input.KeyboardType.Decimal else androidx.compose.ui.text.input.KeyboardType.Text),colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=C.Surface,focusedContainerColor=C.Surface,unfocusedBorderColor=C.Border))
}
@Composable fun Choice(label:String,selected:Boolean,onClick:()->Unit,modifier:Modifier=Modifier) {
    FilterChip(selected=selected,onClick=onClick,label={Text(label,style=MaterialTheme.typography.bodyMedium)},modifier=modifier.heightIn(min=48.dp),shape=RoundedCornerShape(100.dp),colors=FilterChipDefaults.filterChipColors(selectedContainerColor=C.Soft,selectedLabelColor=C.Brand,containerColor=C.Surface,labelColor=C.Secondary),border=FilterChipDefaults.filterChipBorder(enabled=true,selected=selected,borderColor=C.Border,selectedBorderColor=C.Soft))
}
@Composable fun Toggle(title:String,body:String="",checked:Boolean,onChecked:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Surface).semantics(mergeDescendants=true){}.toggleable(value=checked,role=Role.Switch,onValueChange=onChecked).padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) { Text(title,style=MaterialTheme.typography.titleMedium); if(body.isNotBlank())Text(body,style=MaterialTheme.typography.bodySmall,color=C.Secondary) }
        Switch(checked,onCheckedChange=null,colors=SwitchDefaults.colors(checkedTrackColor=C.Brand,checkedThumbColor=C.Surface,uncheckedTrackColor=C.Border,uncheckedThumbColor=C.Surface,uncheckedBorderColor=C.Secondary))
    }
}
@Composable fun LinkRow(title:String,subtitle:String="",onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Surface).clickable(onClick=onClick).padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) { Text(title,style=MaterialTheme.typography.titleMedium); if(subtitle.isNotBlank())Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=C.Secondary) }; Glyph("chevron-right",tint=C.Secondary,size=20.dp)
    }
}
@Composable fun Metric(value:String,label:String,modifier:Modifier=Modifier) { Column(modifier.clip(RoundedCornerShape(16.dp)).background(C.Soft).padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) { Text(value,style=MaterialTheme.typography.titleLarge,color=C.Brand); Text(label,style=MaterialTheme.typography.bodySmall,color=C.Brand) } }
@Composable fun Summary(left:String,right:String,color:Color=C.Text) { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) { Text(left,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,color=C.Secondary); Text(right,style=MaterialTheme.typography.titleMedium,color=color) } }
@Composable fun AmountCard(title:String,value:String,subtitle:String="",onClick:(()->Unit)?=null) { Panel(if(onClick!=null)Modifier.clickable(onClick=onClick) else Modifier,C.Deep) { Text(title,style=MaterialTheme.typography.bodyMedium,color=Color.White); Text(value,style=style(36,44,FontWeight.ExtraBold),color=Color.White); if(subtitle.isNotBlank())Text(subtitle,style=MaterialTheme.typography.bodySmall,color=Color.White) } }
@Composable fun Section(title:String,action:String?=null,onAction:(()->Unit)?=null) { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) { Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge); if(action!=null)TextButton(onClick={onAction?.invoke()},modifier=Modifier.heightIn(min=48.dp)) { Text(action) } } }
@Composable fun EmptyState(title:String,body:String,icon:String="refrigerator") { Panel(color=C.Soft) { Glyph(icon,size=52.dp); Text(title,style=MaterialTheme.typography.headlineMedium,color=C.Brand); Text(body,color=C.Brand) } }
@Composable fun RecipeCard(state:AppState,meal:Meal,large:Boolean,onClick:()->Unit,onPin:(()->Unit)?=null) {
    val recipe=state.recipe(meal.recipeId); val n=state.nutrition(meal)
    val modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(C.Surface).clickable(onClick=onClick).padding(12.dp).testTag("meal-${meal.id}")
    if(large) Column(modifier,verticalArrangement=Arrangement.spacedBy(10.dp)) {
        FoodPhoto(recipe.photo,Modifier.fillMaxWidth().height(156.dp))
        Row(verticalAlignment=Alignment.CenterVertically) { Text(recipe.name,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium); if(onPin!=null)IconButton(onClick=onPin) { Glyph(if(state.isCooked(meal.id))"circle-check" else "bookmark",description=if(meal.pinned)"Снять закрепление" else "Закрепить",tint=if(meal.pinned)C.Brand else C.Secondary) } }
        Text("${meal.type.title} · ${recipe.activeMinutes} мин · ${n.protein.toInt()} г белка",style=MaterialTheme.typography.bodyMedium,color=C.Secondary)
        Summary("${n.calories.toInt()} ккал","≈ ${Numbers.money(Shopping.mealCost(state,meal))}",C.Brand)
    } else Row(modifier,horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
        FoodPhoto(recipe.photo,Modifier.size(76.dp)); Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) { Text(recipe.name,style=MaterialTheme.typography.titleMedium); Text("${meal.type.title} · ${recipe.activeMinutes} мин",style=MaterialTheme.typography.bodyMedium,color=C.Secondary); Text("${n.calories.toInt()} ккал · ${n.protein.toInt()} г белка",style=MaterialTheme.typography.bodySmall,color=C.Brand) }; if(state.isCooked(meal.id))Glyph("circle-check",description="Приготовлено")
    }
}
