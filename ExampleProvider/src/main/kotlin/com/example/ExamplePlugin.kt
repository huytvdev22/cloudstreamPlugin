package com.example

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

/**
 * Annotation đánh dấu cho Cloudstream nhận diện đây là lớp khởi chạy (EntryPoint) của Plugin.
 * (Tương tự như @Component hay @Service trong Spring Framework).
 */
@CloudstreamPlugin
class ExamplePlugin : Plugin() {

    /**
     * Biến lưu instance của màn hình hiện tại (Activity trong Android).
     * Dấu '?' trong Kotlin nghĩa là biến có thể nhận giá trị null (Nullable - tương tự Optional trong Java).
     * Mặc định khởi tạo là null.
     */
    private var activity: AppCompatActivity? = null

    /**
     * Phương thức vòng đời (Lifecycle) được Cloudstream gọi khi nạp plugin vào ứng dụng.
     * (Tương đương với hàm @PostConstruct hoặc init() trong Java/Spring).
     *
     * @param context Môi trường thực thi của Android (cung cấp tài nguyên hệ thống, UI, database...).
     */
    override fun load(context: Context) {
        // Ép kiểu an toàn (Safe Cast): nếu context là AppCompatActivity thì gán, ngược lại trả về null (thay vì văng ClassCastException như Java)
        activity = context as? AppCompatActivity

        // Đăng ký Provider vào hệ thống Core của Cloudstream để người dùng có thể tìm kiếm và xem phim
        // (Tương tự như đăng ký một Provider Service vào Service Registry)
        registerMainAPI(ExampleProvider())

        // Gán một Lambda (tương tự Functional Interface / Runnable trong Java) cho sự kiện mở trang cài đặt của plugin
        openSettings = {
            // Khởi tạo một Dialog/Fragment giao diện (truyền instance plugin hiện tại vào)
            val frag = BlankFragment(this)

            // Safe Call '?.let': Chỉ thực thi khối lệnh bên trong nếu 'activity' khác null
            // (Tương đương if (activity != null) { ... } trong Java)
            activity?.let {
                // Hiển thị giao diện cài đặt đè lên màn hình hiện tại thông qua FragmentManager
                frag.show(it.supportFragmentManager, "Frag")
            }
        }
    }
}