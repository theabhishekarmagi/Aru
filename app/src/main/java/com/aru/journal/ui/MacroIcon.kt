package com.aru.journal.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform

/** Food symbols shared with the web labels: yellow fish, red apple, purple drop. */
@Composable fun MacroIcon(index: Int, modifier: Modifier = Modifier) {
    val shape = remember(index) { Path().apply {
        when(index) {
            1 -> { moveTo(2f,6f);lineTo(8f,10f);cubicTo(12f,3f,20f,5f,23f,12f);cubicTo(20f,19f,12f,21f,8f,14f);lineTo(2f,18f);lineTo(4f,12f);close() }
            2 -> { moveTo(12f,7f);cubicTo(3f,2f,1f,9f,3f,15f);cubicTo(5f,23f,9f,23f,12f,21f);cubicTo(15f,23f,19f,23f,21f,15f);cubicTo(23f,9f,21f,2f,12f,7f);close() }
            3 -> { moveTo(12f,2f);cubicTo(10f,7f,5f,10f,5f,15f);cubicTo(5f,24f,19f,24f,19f,15f);cubicTo(19f,10f,14f,7f,12f,2f);close() }
        }
    } }
    val leaf = remember { Path().apply {moveTo(12f,6f);cubicTo(12f,2f,16f,1f,18f,2f);cubicTo(17f,5f,15f,6f,12f,6f);close()} }
    Canvas(modifier) {
        withTransform({scale(size.width/24f,size.height/24f,pivot=Offset.Zero)}) {
            drawPath(shape,when(index){1->Color(0xFFF2BF18);2->Color(0xFFFF3868);else->Color(0xFFD92CE6)})
            if(index==1)drawCircle(Color.White,1f,Offset(18f,11f))
            if(index==2)drawPath(leaf,Color(0xFF45AC70))
        }
    }
}
