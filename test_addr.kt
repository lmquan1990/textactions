fun main() {
    val value = "số tiền phạt 50.000 đồng"
    val trimmed = value.trim()
    if (trimmed.length <= 15) { println("too short"); return }

    val lowerValue = trimmed.lowercase()

    val strongKeywordsVi = listOf("đường", "phố", "ngõ", "ngách", "thôn", "xóm", "phường", "xã", "quận", "huyện", "tỉnh", "thành phố", "tòa nhà", "tổ dân phố")
    val strongKeywordsEn = listOf("street", "avenue", "boulevard", "road", "lane", "drive", "court", "place", "highway", "district", "ward", "city", "county", "province", "state")
    val weakKeywordsVi = listOf("số nhà", "tầng", "khu vực", "khu đô thị", "khu công nghiệp")
    val weakKeywordsEn = listOf("apt", "apartment", "suite", "floor", "unit", "building", "block")

    val matchedStrong = (strongKeywordsVi + strongKeywordsEn).filter { lowerValue.contains(it) }
    val matchedWeak = (weakKeywordsVi + weakKeywordsEn).filter { lowerValue.contains(it) }
    println("Strong: $matchedStrong")
    println("Weak: $matchedWeak")
}
