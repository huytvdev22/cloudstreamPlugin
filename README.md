# 🎬 Cloudstream 3 Plugin Repository

Kho lưu trữ và phát triển các **Plugin / Provider (nguồn phim, anime, truyền hình)** cho ứng dụng mã nguồn mở [Cloudstream 3](https://github.com/recloudstream) trên Android & Android TV.

---

## 📺 1. Hướng dẫn Cài đặt Plugin trên Cloudstream (Android TV & Mobile)

Sau khi code được đẩy lên GitHub, GitHub Actions sẽ tự động biên dịch toàn bộ plugin và xuất bản file cấu hình lên nhánh `builds`.

### 🔗 Link Repository của bạn:
```text
https://raw.githubusercontent.com/huytvdev22/cloudstreamPlugin/builds/plugins.json
```

> **💡 Mẹo:** Để không phải gõ URL dài bằng điều khiển TV:
> - Sử dụng ứng dụng **Google TV** trên điện thoại để kết nối với TV và dán (Paste) link.
> - Hoặc tạo link rút gọn qua [TinyURL](https://tinyurl.com).

### 🚀 Các bước cài đặt:
1. **Mở ứng dụng Cloudstream** trên Android TV hoặc điện thoại.
2. Vào **Cài đặt (Settings - biểu tượng bánh răng)** ➔ Chọn mục **Tiện ích mở rộng (Extensions / Plugins)**.
3. Bấm **Thêm kho lưu trữ (Add Repository)**.
4. Nhập thông tin:
   - **Tên kho (Repository Name):** `HuyTV Plugins` *(hoặc tên tùy thích)*
   - **URL kho (Repository URL):**
     ```text
     https://raw.githubusercontent.com/huytvdev22/cloudstreamPlugin/builds/plugins.json
     ```
5. Bấm **Tải xuống / Thêm (Download / Add)**.
6. Mở kho lưu trữ vừa thêm, chọn plugin bạn muốn dùng (ví dụ: **Example provider**) ➔ Bấm **Cài đặt (Install)**.
7. Quay lại màn hình chính của Cloudstream, chuyển sang nguồn vừa cài đặt để thưởng thức nội dung!

> 🔄 **Tự động cập nhật:** Mỗi khi bạn push code mới lên GitHub, Cloudstream trên thiết bị sẽ tự động thông báo và cập nhật plugin mà không cần cài lại.

---

## 🛠️ 2. Hướng dẫn Phát triển Plugin Mới

### 📁 Cấu trúc một Plugin
Mỗi thư mục con (như `ExampleProvider`) đại diện cho 1 Plugin riêng biệt:
- **`build.gradle.kts`**: Khai báo thông tin plugin (Tên, Tác giả, Ngôn ngữ, Loại nội dung: Movie/TvSeries/Anime...).
- **`src/main/kotlin/.../Plugin.kt`**: EntryPoint đăng ký Provider vào hệ thống (`registerMainAPI(...)`).
- **`src/main/kotlin/.../Provider.kt`**: Chứa logic cào dữ liệu từ web (tìm kiếm, lấy thông tin phim, lấy link xem `.m3u8` / `.mp4`).

### 💻 Các lệnh Build và Kiểm thử

| Thao tác | Lệnh Windows (PowerShell/CMD) | Lệnh Linux / macOS / WSL |
| :--- | :--- | :--- |
| **Build toàn bộ plugin** | `.\gradlew.bat make` | `./gradlew make` |
| **Build 1 plugin cụ thể** | `.\gradlew.bat ExampleProvider:make` | `./gradlew ExampleProvider:make` |
| **Deploy trực tiếp vào Android (qua ADB)** | `.\gradlew.bat ExampleProvider:deployWithAdb` | `./gradlew ExampleProvider:deployWithAdb` |
| **Tạo `plugins.json` cục bộ** | `.\gradlew.bat makePluginsJson` | `./gradlew makePluginsJson` |

---

## 📱 3. Cấp quyền truy cập tệp khi Test cục bộ (Android 11+)

Khi test plugin trực tiếp trên thiết bị qua lệnh `deployWithAdb`, Android 11 trở lên yêu cầu quyền truy cập toàn bộ tệp (*All Files Access*):

### Qua lệnh ADB:
```bash
adb shell appops set --uid <PACKAGE_NAME> MANAGE_EXTERNAL_STORAGE allow
```
*Thay `<PACKAGE_NAME>` bằng package Cloudstream đang dùng:*
- Bản Stable: `com.lagradost.cloudstream3`
- Bản Prerelease: `com.lagradost.cloudstream3.prerelease`
- Bản Debug: `com.lagradost.cloudstream3.prerelease.debug`

### Hoặc thao tác thủ công trên thiết bị:
1. Vào **Cài đặt thiết bị (Settings)** ➔ **Ứng dụng (Apps)**.
2. Chọn **Quyền truy cập đặc biệt (Special app access)** ➔ **Quyền truy cập tất cả các tệp (All files access)**.
3. Tìm **Cloudstream** và bật cho phép.
4. Khởi động lại ứng dụng.

---

## 📚 4. Tài liệu Tham khảo cho Lập trình viên

- 📖 [Tổng quan kiến trúc dự án](file:///d:/2.projects/fl/cloudstreamPlugin/cloudstreamPlugin/document/TONG_QUAN_DU_AN.md)
- ⚡ [Hướng dẫn học nhanh cú pháp Kotlin cho Java Backend Dev](file:///d:/2.projects/fl/cloudstreamPlugin/cloudstreamPlugin/document/HOC_NHANH_KOTLIN_CHO_JAVA_DEV.md)

---

## 📄 Giấy phép (License) & Ghi công (Attribution)

- Toàn bộ mã nguồn trong repo này được phát hành theo phạm vi công cộng (Public Domain). Bạn có thể tự do sử dụng, chỉnh sửa và phân phối.
- Template và hệ thống Plugin được xây dựng dựa trên kiến trúc của dự án [Aliucord](https://github.com/Aliucord).
