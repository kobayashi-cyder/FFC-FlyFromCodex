import jp.three_domain.chatvoice.SpeechTextFormatter;
import java.util.List;

public class SpeechTextFormatterTest {
    static void require(boolean pass, String error) { if (!pass) throw new AssertionError(error); }
    static void roundTrip(String input, int limit) {
        List<String> chunks = SpeechTextFormatter.chunks(input, limit);
        require(input.equals(String.join("", chunks)), "round trip failed");
        for (String c : chunks) {
            require(c.length() <= limit, "oversized chunk");
            require(!c.isEmpty(), "empty chunk");
            require(!Character.isHighSurrogate(c.charAt(c.length()-1)), "split surrogate");
        }
    }
    public static void main(String[] args) {
        require(SpeechTextFormatter.chunks("", 100).isEmpty(), "empty");
        roundTrip("こんにちは。今日もよろしくお願いします。", 8);
        roundTrip("0123456789".repeat(1000), 200);
        roundTrip("😀ありがとう😀".repeat(700), 21);
        roundTrip("ABC。DEF。GHI。".repeat(100), 10);
        roundTrip("A\nB\nC\n".repeat(100), 4);
        boolean rejected = false;
        try { SpeechTextFormatter.chunks("abc", 1); } catch (IllegalArgumentException ex) { rejected = true; }
        require(rejected, "invalid max should throw");
        System.out.println("SpeechTextFormatter: 6 tests passed");
    }
}
