import java.util.regex.*;

public class TestAnalyzer {
    public static void main(String[] args) {
        String text = "Thứ hai, 27/4/2026, 09:00 (GMT+7)\n" +
            "Chiêu lừa 'phạt nguội 50.000 đồng' để chiếm thẻ ngân hàng\n" +
            "Nhiều người nhận được tin nhắn báo vi phạm giao thông, phải nộp 50.000\n" +
            "số tiền phạt 50.000 đồng.";
            
        Pattern p = Pattern.compile("(?:Tòa|Tầng|Số|\\d+)[a-zA-ZÀ-ỹ0-9 ,./-]+(?:Đường|Phố|Ngõ|Ngách|Thôn|Xóm|Phường|Xã|Quận|Huyện|Tỉnh|Thành phố)[a-zA-ZÀ-ỹ0-9 ,./-]*", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher m = p.matcher(text);
        while (m.find()) {
            String value = m.group();
            System.out.println("Match: " + value);
            
            String[] components = {"đường", "phố", "ngõ", "ngách", "thôn", "xóm", "phường", "xã", "quận", "huyện", "tỉnh", "thành phố", "số", "tòa", "tầng"};
            String lowerValue = value.toLowerCase();
            int matchCount = 0;
            for (String c : components) {
                if (lowerValue.contains(c)) matchCount++;
            }
            System.out.println("Valid: " + (matchCount >= 2 && value.length() > 10));
        }
    }
}
