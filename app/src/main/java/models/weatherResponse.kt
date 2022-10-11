package models

import java.io.Serializable

data class weatherResponse(
    val coord:Coord,
    val weather:List<Weather>,
    val base:String,
    val main:Main,
    val visibility:Int,
    val wind:Wind,
    val clouds:cloud,
    val dt:Int,
    val sys:Sys,
    val id:Int,
    val name:String,
    val cod:Int
):Serializable