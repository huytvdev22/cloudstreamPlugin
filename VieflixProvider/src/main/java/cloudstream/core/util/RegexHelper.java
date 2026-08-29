package com.cloudstream.core.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Các hàm tiện ích xử lý Regex an toàn và tái sử dụng cho mọi Provider.
 */
public class RegexHelper {

    /**
     * Tìm số nguyên đầu tiên khớp với regex pattern.
     *
     * @param input   Chuỗi đầu vào
     * @param pattern Biểu thức chính quy (Nhóm 1 là số cần lấy)
     * @return Integer hoặc null nếu không khớp
     */
    public static Integer parseInt(String input, String pattern) {
        if (input == null || input.trim().isEmpty()) return null;
        try {
            Matcher matcher = Pattern.compile(pattern).matcher(input);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Lấy giá trị chuỗi của nhóm đầu tiên khớp với regex pattern.
     *
     * @param input   Chuỗi đầu vào
     * @param pattern Biểu thức chính quy
     * @param group   Số thứ tự nhóm cần lấy (mặc định 1)
     * @return Chuỗi trích xuất được hoặc null
     */
    public static String extractGroup(String input, String pattern, int group) {
        if (input == null || input.trim().isEmpty()) return null;
        try {
            Matcher matcher = Pattern.compile(pattern, Pattern.DOTALL).matcher(input);
            if (matcher.find()) {
                return matcher.group(group);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Lấy chuỗi nằm giữa hai đoạn text (tiện lợi khi bóc tách JSON hoặc HTML).
     *
     * @param text  Chuỗi nguồn
     * @param start Đoạn bắt đầu
     * @param end   Đoạn kết thúc
     * @return Chuỗi ở giữa hoặc null
     */
    public static String extractBetween(String text, String start, String end) {
        if (text == null) return null;
        int sIdx = text.indexOf(start);
        if (sIdx == -1) return null;
        sIdx += start.length();

        int eIdx = text.indexOf(end, sIdx);
        if (eIdx == -1) return null;

        return text.substring(sIdx, eIdx);
    }
}
