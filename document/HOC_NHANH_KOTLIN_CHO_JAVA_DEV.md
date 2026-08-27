# ⚡ HỌC NHANH KOTLIN CHO LẬP TRÌNH VIÊN JAVA BACKEND

Tài liệu này được thiết kế dành riêng cho **Java Developer** để nắm bắt cú pháp **Kotlin** trong 15 phút, tập trung vào các điểm khác biệt cốt lõi và ứng dụng thực tế trong dự án Cloudstream Plugin.

---

## 📑 MỤC LỤC
1. [Khai báo biến: `val` vs `var`](#1-khai-báo-biến-val-vs-var)
2. [Null Safety – Xóa bỏ nỗi sợ `NullPointerException`](#2-null-safety--xóa-bỏ-nỗi-sợ-nullpointerexception)
3. [Hàm (Function) & Single-expression](#3-hàm-function--single-expression)
4. [Class, Constructor & Data Class (Lombok / Record)](#4-class-constructor--data-class-lombok--record)
5. [Kế thừa & Interface](#5-kế-thừa--interface)
6. [Collections & Xử lý Functional (`map`, `filter`)](#6-collections--xử-lý-functional-map-filter)
7. [Scope Functions (`let`, `apply`, `run`, `also`)](#7-scope-functions-let-apply-run-also)
8. [Extension Functions (Mở rộng hàm mà không cần kế thừa)](#8-extension-functions)
9. [Bất đồng bộ với Coroutines (`suspend fun`)](#9-bất-đồng-bộ-với-coroutines-suspend-fun)
10. [Bảng so sánh thực chiến: Java vs Kotlin trong Cloudstream](#10-bảng-so-sánh-thực-chiến-java-vs-kotlin-trong-cloudstream)

---

## 1. Khai báo biến: `val` vs `var`

- **`val`** (Value): Biến bất biến (tương đương `final` trong Java) - **Khuyên dùng mặc định**.
- **`var`** (Variable): Biến có thể gán lại giá trị.
- **Type Inference**: Tự suy luận kiểu dữ liệu, không cần khai báo kiểu nếu đã có giá trị khởi tạo.
- **String Template**: Nhúng biến vào chuỗi trực tiếp bằng `$name` hoặc `${object.property}`.

| Java | Kotlin |
| :--- | :--- |
| `final String name = "Cloudstream";` | `val name = "Cloudstream"` |
| `int age = 20; age = 21;` | `var age = 20`<br>`age = 21` |
| `String info = "Name: " + name + ", Age: " + age;` | `val info = "Name: $name, Age: $age"` |
| `String text = "Length: " + name.length();` | `val text = "Length: ${name.length}"` |

---

## 2. Null Safety – Xóa bỏ nỗi sợ `NullPointerException`

Trong Kotlin, kiểu dữ liệu mặc định là **Non-Null** (không thể gán `null`). Nếu muốn cho phép `null`, phải thêm dấu `?`.

```kotlin
var a: String = "Hello" // a KHÔNG THỂ bằng null
// a = null             // ❌ Lỗi biên dịch ngay lập tức!

var b: String? = "Hello" // b CÓ THỂ bằng null (Nullable)
b = null                // ✅ Hợp lệ
```

### Các toán tử xử lý Null cực hay:

| Cú pháp Kotlin | Tên gọi | Tương đương trong Java |
| :--- | :--- | :--- |
| `b?.length` | **Safe Call** | `(b != null) ? b.length() : null` |
| `b ?: "Default"` | **Elvis Operator** | `(b != null) ? b : "Default"` |
| `b as? String` | **Safe Cast** | `(b instanceof String) ? (String) b : null` |
| `b!!.length` | **Not-null Assertion** | Cố tình ép không null (văng NPE nếu null) |

---

## 3. Hàm (Function) & Single-expression

- Khai báo bằng từ khóa `fun`.
- Tham số đặt theo cú pháp: `name: Type`.
- Kiểu trả về đặt sau dấu `:`. Nếu không trả về gì thì là `Unit` (tương đương `void`, có thể bỏ qua).

```kotlin
// Hàm thông thường
fun sum(a: Int, b: Int): Int {
    return a + b
}

// Single-expression function (hàm 1 dòng rút gọn)
fun sum(a: Int, b: Int) = a + b

// Default Parameter (không cần viết quá nhiều Overload như Java)
fun getMovie(url: String, page: Int = 1, sort: String = "latest") {
    println("URL: $url, Page: $page, Sort: $sort")
}

// Gọi hàm với Named Argument
getMovie(url = "https://phim.com", sort = "popular") // page tự lấy mặc định = 1
```

---

## 4. Class, Constructor & Data Class (Lombok / Record)

### Class thông thường:
Không cần viết `getter/setter` rườm rà. `val` tự tạo getter, `var` tự tạo cả getter & setter.

```kotlin
// Khai báo class + constructor + properties chỉ trong 1 dòng!
class Movie(val id: Int, var title: String, var posterUrl: String? = null)

// Sử dụng:
val m = Movie(1, "Inception")
println(m.title)     // Tự gọi getter
m.title = "Avatar"   // Tự gọi setter
```

### Data Class (Tương đương `@Data` của Lombok hoặc `record` của Java 14+):
Tự động sinh ra: `equals()`, `hashCode()`, `toString()`, `copy()`.

```kotlin
data class Episode(
    val name: String,
    val episodeNumber: Int,
    val link: String
)

val ep1 = Episode("Tập 1", 1, "https://...")
val ep2 = ep1.copy(episodeNumber = 2, name = "Tập 2") // Clone & sửa field
```

---

## 5. Kế thừa & Interface

- Mặc định các class trong Kotlin là `final` (không thể kế thừa). Muốn cho kế thừa phải đánh dấu `open`.
- Dùng dấu `:` thay cho cả `extends` và `implements`.
- Dùng từ khóa `override` bắt buộc khi ghi đè method.

```kotlin
// Interface
interface Parser {
    fun parseHtml(html: String): List<String>
}

// Class cha
open class BaseScraper(val baseUrl: String)

// Class con kế thừa BaseScraper và implement Parser
class AnimeScraper(url: String) : BaseScraper(url), Parser {
    override fun parseHtml(html: String): List<String> {
        return listOf()
    }
}
```

---

## 6. Collections & Xử lý Functional (`map`, `filter`)

Kotlin phân biệt rõ ràng giữa **Immutable List** (danh sách chỉ đọc) và **Mutable List** (danh sách có thể thêm/sửa/xóa).

```kotlin
val list = listOf("A", "B", "C")           // Không thể add/remove (List.of trong Java 9+)
val mutableList = mutableListOf("A", "B")  // ArrayList bình thường
mutableList.add("C")

// Thao tác Stream/Functional ngắn gọn hơn Java Stream rất nhiều (không cần .stream() và .collect())
val numbers = listOf(1, 2, 3, 4, 5, 6)

val evenSquares = numbers
    .filter { it % 2 == 0 }   // 'it' là biến ngầm định đại diện cho phần tử hiện tại
    .map { it * it }          // [4, 16, 36]

val found = numbers.find { it == 3 } // Optional.findFirst()
```

---

## 7. Scope Functions (`let`, `apply`, `run`, `also`)

Đây là tính năng đặc trưng nhất của Kotlin giúp code ngắn và sạch hơn:

### 1. `let` (Thường dùng để check null):
```kotlin
val name: String? = getNullableName()
name?.let { 
    // Khối này CHỈ chạy khi name != null
    println("Tên là: $it") // 'it' chính là name đã được ép kiểu non-null
}
```

### 2. `apply` (Thường dùng để khởi tạo Object và set thuộc tính):
```kotlin
val response = MovieSearchResponse().apply {
    this.name = "Spider-Man"
    this.url = "https://phim.com/spider-man"
    this.posterUrl = "https://img.com/spiderman.jpg"
}
```

---

## 8. Extension Functions

Cho phép bạn "thêm hàm mới" vào bất kỳ class có sẵn nào (kể cả class của JDK hay Third-party library) mà không cần kế thừa class đó!

```kotlin
// Thêm hàm toSlug() vào lớp String có sẵn của Java/Kotlin
fun String.toSlug(): String {
    return this.lowercase().replace(" ", "-")
}

// Sử dụng trực tiếp như method của String:
val title = "Phim Hanh Dong Chieu Rap"
println(title.toSlug()) // "phim-hanh-dong-chieu-rap"
```

---

## 9. Bất đồng bộ với Coroutines (`suspend fun`)

Trong Java, bạn dùng `CompletableFuture`, `Thread` hoặc `RxJava`.  
Trong Kotlin / Cloudstream, bạn dùng **Coroutines**:
- **`suspend fun`**: Hàm có thể tạm dừng (suspend) và tiếp tục (resume) mà **không làm block Thread chính**.
- Cú pháp viết tuần tự như code đồng bộ bình thường!

```kotlin
// Không cần Callback hell, không cần .thenApply()!
override suspend fun search(query: String): List<SearchResponse> {
    // app.get() là suspend function gửi HTTP Request bất đồng bộ
    val html = app.get("https://example.com/search?q=$query").text 
    val doc = Jsoup.parse(html)
    
    return doc.select(".item").map { ... }
}
```

---

## 10. Bảng so sánh thực chiến: Java vs Kotlin trong Cloudstream

### Viết bằng Java (Tương đương):
```java
public class PhimProvider extends MainAPI {
    public PhimProvider() {
        setMainUrl("https://phimmoichill.net");
        setName("PhimMoi");
        setLang("vi");
    }

    @Override
    public Object search(String query, Continuation<? super List<SearchResponse>> continuation) {
        // Cực kỳ phức tạp vì phải tự xử lý Continuation của Kotlin Coroutines trong Java
        return null;
    }
}
```

### Viết bằng Kotlin (Chuẩn Cloudstream):
```kotlin
class PhimProvider : MainAPI() {
    override var mainUrl = "https://phimmoichill.net"
    override var name = "PhimMoi"
    override var lang = "vi"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val hasMainPage = true

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/tim-kiem/$query").document
        
        return document.select("div.item").map { item ->
            val title = item.selectFirst(".title")?.text() ?: ""
            val href = item.selectFirst("a")?.attr("href") ?: ""
            val poster = item.selectFirst("img")?.attr("data-src")
            
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
            }
        }
    }
}
```

---

> 💡 **Mẹo:** Bạn cứ viết code logic thoải mái. Bất cứ khi nào cần chuyển logic Java sang Kotlin chuẩn Cloudstream, bạn chỉ cần đưa code Java đây tôi sẽ convert giúp bạn ngay lập tức!
