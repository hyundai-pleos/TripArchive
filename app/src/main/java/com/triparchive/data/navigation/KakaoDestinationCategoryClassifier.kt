package com.triparchive.data.navigation

import android.util.Log
import com.triparchive.BuildConfig
import com.triparchive.domain.navigation.CategoryLookupResult
import com.triparchive.domain.navigation.CategoryLookupStatus
import com.triparchive.domain.navigation.DestinationCategoryClassifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import kotlin.math.cos
import kotlin.math.hypot

private fun logKakaoResponse(
    status: Int,
    body: String,
) {
    if (!BuildConfig.DEBUG) return
    val sanitized = body.replace(BuildConfig.KAKAO_REST_API_KEY, "<redacted>")
    Log.d("KakaoCategory", "HTTP $status; 응답 길이: ${sanitized.length}")
    sanitized.chunked(2000).forEachIndexed { index, part ->
        Log.d("KakaoCategory", "응답 ${index + 1}: $part")
    }
}

class KakaoDestinationCategoryClassifier
    @Inject
    constructor() : DestinationCategoryClassifier {
        override suspend fun classify(
            name: String,
            latitude: Double,
            longitude: Double,
        ): CategoryLookupResult =
            withContext(Dispatchers.IO) {
                val fallback: (
                    String,
                ) -> CategoryLookupResult = { CategoryLookupResult(null, CategoryLookupStatus.FAILURE, it) }
                if (BuildConfig.KAKAO_REST_API_KEY.isBlank()) return@withContext fallback("REST API 키가 설정되지 않았어요")
                if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0 ||
                    (latitude == 0.0 && longitude == 0.0)
                ) {
                    return@withContext fallback("목적지 좌표가 올바르지 않아요")
                }
                try {
                    val query = URLEncoder.encode(name, "UTF-8")
                    val url =
                        URL(
                            "https://dapi.kakao.com/v2/local/search/keyword.json" +
                                "?query=$query&x=$longitude&y=$latitude&radius=1000&sort=distance&size=15",
                        )
                    val connection = url.openConnection() as HttpURLConnection
                    try {
                        connection.connectTimeout = 5000
                        connection.readTimeout = 5000
                        connection.setRequestProperty("Authorization", "KakaoAK ${BuildConfig.KAKAO_REST_API_KEY}")
                        val status = connection.responseCode
                        val body =
                            (if (status == 200) connection.inputStream else connection.errorStream)
                                ?.bufferedReader()
                                ?.use { it.readText() }
                                .orEmpty()
                        logKakaoResponse(status, body)
                        if (status != 200) return@withContext fallback("카카오 API 응답 오류 ($status)")
                        val documents = JSONObject(body).getJSONArray("documents")
                        val normalized = name.filterNot(Char::isWhitespace)
                        val matches =
                            (0 until documents.length())
                                .map { documents.getJSONObject(it) }
                                .filter {
                                    it
                                        .optString(
                                            "place_name",
                                        ).filterNot(Char::isWhitespace)
                                        .equals(normalized, ignoreCase = true)
                                }.mapNotNull {
                                    val lat = it.optString("y").toDoubleOrNull() ?: return@mapNotNull null
                                    val lon = it.optString("x").toDoubleOrNull() ?: return@mapNotNull null
                                    val distance =
                                        hypot(
                                            (lat - latitude) * 111_000,
                                            (lon - longitude) * 111_000 * cos(Math.toRadians(latitude)),
                                        )
                                    it to distance
                                }.filter { it.second <= 200 }
                                .sortedBy { it.second }
                        val best = matches.firstOrNull() ?: return@withContext fallback("일치하는 장소를 찾지 못했어요")
                        if (matches.size > 1 &&
                            matches[1].second - best.second < 10
                        ) {
                            return@withContext fallback("일치하는 장소를 구분할 수 없어요")
                        }
                        val category =
                            categoryForCode(best.first.optString("category_group_code"))
                                ?: return@withContext fallback("지원하지 않는 카테고리예요")
                        if (BuildConfig.DEBUG) Log.d("KakaoCategory", "선택된 카테고리: ${category.name}")
                        CategoryLookupResult(category, CategoryLookupStatus.SUCCESS)
                    } finally {
                        connection.disconnect()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    fallback("네트워크 또는 응답 처리 오류")
                }
            }
    }
