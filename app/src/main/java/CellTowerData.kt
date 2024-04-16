data class CellTowerData(
    val radio: String,
    val mcc: Int,
    val net: Int,
    val area: Int,
    val cell: Long,
    val unit: Int,
    val longitude: Double,
    val latitude: Double,
    val range: Int,
    val samples: Int,
    val changeable: Int,
    val created: Long,
    val updated: Long,
    val averageSignal: Int
)
