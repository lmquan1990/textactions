public class TestAddress {
    public static void main(String[] args) {
        String value = "số tiền phạt 50.000 đồng.";
        String lowerValue = value.toLowerCase().trim();
        String[] vi = {"đường", "phố", "ngõ", "ngách", "thôn", "xóm", "phường", "xã", "quận", "huyện", "tỉnh", "thành phố", "tòa nhà", "tổ dân phố"};
        String[] en = {"street", "avenue", "boulevard", "road", "lane", "drive", "court", "place", "highway", "district", "ward", "city", "county", "province", "state"};
        int strongCount = 0;
        for (String k : vi) if (lowerValue.contains(k)) { strongCount++; System.out.println("Match: " + k); }
        for (String k : en) if (lowerValue.contains(k)) { strongCount++; System.out.println("Match: " + k); }
        System.out.println("Strong count: " + strongCount);
        
        String[] weakVi = {"số nhà", "tầng", "khu vực", "khu đô thị", "khu công nghiệp"};
        String[] weakEn = {"apt", "apartment", "suite", "floor", "unit", "building", "block"};
        int weakCount = 0;
        for (String k : weakVi) if (lowerValue.contains(k)) { weakCount++; System.out.println("Weak Match: " + k); }
        for (String k : weakEn) if (lowerValue.contains(k)) { weakCount++; System.out.println("Weak Match: " + k); }
        System.out.println("Weak count: " + weakCount);
        
        boolean hasDigit = value.matches(".*\\d.*");
        System.out.println("Has digit: " + hasDigit);
        
        boolean isMeaningful = strongCount >= 2 || (strongCount >= 1 && weakCount >= 1 && hasDigit);
        System.out.println("Is meaningful: " + isMeaningful);
    }
}
