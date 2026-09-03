import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

fun getGitHash(): String {
    return try {
        val p = ProcessBuilder("git", "rev-parse", "--short", "HEAD").redirectErrorStream(true).start()
        p.waitFor()
        p.inputStream.bufferedReader().readText().trim()
    } catch (e: Exception) {
        "unknown"
    }
}

fun getBuildDate(): String {
    val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm")
    formatter.timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
    return formatter.format(Date())
}

// Cấu hình Build cho Xemplay Plugin
version = 2

cloudstream {
    description = "Nguồn xem phim chất lượng cao từ XemPlay (Phiên bản: 1.0.0 - Build: ${getGitHash()} - Ngày: ${getBuildDate()})"
    authors = listOf("HuyTV")
    status = 1 // 1: Ok, 2: Slow, 3: Beta
    tvTypes = listOf("Movie", "TvSeries", "Anime")
    requiresResources = false
    language = "vi"
    iconUrl = "https://xemplay.uk/brand/xemplay-mark.png"
}

// Tự động đồng bộ mã nguồn Java từ module JavaRunTest sang Provider trước khi biên dịch
tasks.register<Copy>("syncJavaFromTest") {
    val sourceDir = file("${rootProject.projectDir}/JavaRunTest/src/main/java")
    if (sourceDir.exists()) {
        from(sourceDir)
        into(file("src/main/java"))
    }
}

tasks.matching { it.name.startsWith("compile") || it.name == "preBuild" }.configureEach {
    dependsOn("syncJavaFromTest")
}
