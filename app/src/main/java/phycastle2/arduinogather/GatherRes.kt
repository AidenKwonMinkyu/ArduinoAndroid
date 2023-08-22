package phycastle2.arduinogather

data class GatherRes(
    val id : Int,
    val mac : String,
    val temperature : Float,
    val humidity : Float,
    val bmpTemperature : Float,
    val bmpPressure : Float,
    val bmpAltitude : Float,
    val concentration : Float,
    val ugm3 : Float,
    val lat : Double,
    val lng : Double,
)
