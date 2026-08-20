package com.example.album.ui

enum class MediaSort(val label: String) {
    Time("时间"),
    Name("名称"),
    Size("大小"),
    Count("数量"),
    Duration("时长")
}

enum class SortDirection(val label: String) {
    Ascending("顺序"),
    Descending("倒序")
}

enum class MediaLayout(val label: String) {
    Grid("网格"),
    Adaptive("自适应")
}
