object FormatUtils {
    fun intToRoman(num: Int): String {
        val values = listOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
        val symbols = listOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")

        var remaining = num
        val result = StringBuilder()

        for (i in values.indices) {
            while (remaining >= values[i]) {
                remaining -= values[i]
                result.append(symbols[i])
            }
        }
        return result.toString()
    }
}