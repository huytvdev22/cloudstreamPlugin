package com.vieflix.util;

import org.jsoup.nodes.Element;

/**
 * Các hàm tiện ích xử lý DOM / HTML bằng Jsoup, tái sử dụng cho các Provider.
 */
public class HtmlHelper {

    /**
     * Chuẩn hóa URL tương đối thành URL tuyệt đối.
     *
     * @param baseUrl URL gốc (vd: https://vieflix.top)
     * @param element Element HTML cần lấy link
     * @param attr    Thuộc tính (href hoặc src)
     * @return URL tuyệt đối
     */
    public static String getAbsoluteUrl(String baseUrl, Element element, String attr) {
        if (element == null) return "";
        String absUrl = element.attr("abs:" + attr);
        if (!absUrl.trim().isEmpty()) {
            return absUrl;
        }
        String relUrl = element.attr(attr);
        if (relUrl.startsWith("http://") || relUrl.startsWith("https://")) {
            return relUrl;
        }
        if (relUrl.startsWith("/")) {
            return baseUrl + relUrl;
        }
        return baseUrl + "/" + relUrl;
    }

    /**
     * Lấy text an toàn từ Element, tự động loại bỏ khoảng trắng thừa.
     */
    public static String textOrEmpty(Element element) {
        return element != null ? element.text().trim() : "";
    }

    /**
     * Lấy text của thẻ con đầu tiên khớp với CSS selector.
     */
    public static String selectFirstText(Element parent, String cssQuery) {
        if (parent == null) return "";
        Element first = parent.selectFirst(cssQuery);
        return textOrEmpty(first);
    }
}
