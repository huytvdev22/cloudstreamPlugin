fun getGitHash(): String {
    return try {
        val p = ProcessBuilder("git", "rev-parse", "--short", "HEAD").redirectErrorStream(true).start()
        p.waitFor()
        p.inputStream.bufferedReader().readText().trim()
    } catch (e: Exception) {
        "unknown"
    }
}

// Build configuration for Vieflix Plugin
version = 1

cloudstream {
    description = "Xem phim chất lượng cao từ nguồn Vieflix (Phiên bản: 1.0.0 - Build: ${getGitHash()})"
    authors = listOf("HuyTV")
    status = 1 // 1: Ok, 2: Slow, 3: Beta
    tvTypes = listOf("Movie", "TvSeries", "Anime")
    requiresResources = false
    language = "vi"
    iconUrl = "https://example.com/vieflix-icon.png"
}

tasks.register<Copy>("syncJavaFromTest") {
    val sourceDir = file("${rootProject.projectDir}/JavaRunTest/src/main/java")
    if (sourceDir.exists()) {
        from(sourceDir)
        into(file("src/main/java"))
    }
}

tasks.named("preBuild") {
    dependsOn("syncJavaFromTest")
}

