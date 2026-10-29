package jp.three_domain.chatvoice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure Java: splits arbitrarily long replies into valid TextToSpeech chunks. */
public final class SpeechTextFormatter {
    private SpeechTextFormatter() {}

    public static List<String> chunks(String input, int maxUnits) {
        if (input == null || input.isEmpty()) return Collections.emptyList();
        if (maxUnits < 2) throw new IllegalArgumentException("maxUnits must be >= 2");
        List<String> result = new ArrayList<>();
        int cursor = 0;
        while (cursor < input.length()) {
            int end = Math.min(input.length(), cursor + maxUnits);
            if (end < input.length() && Character.isHighSurrogate(input.charAt(end - 1))) end--;
            if (end <= cursor) throw new IllegalStateException("No code-point progress");
            if (end < input.length()) {
                int candidate = -1;
                for (int j = end - 1; j > cursor; --j) {
                    char c = input.charAt(j);
                    if (c == '。' || c == '！' || c == '？' || c == '\n' || c == '.' || c == '!' || c == '?') {
                        candidate = j + 1;
                        break;
                    }
                }
                // Avoid extremely short fragments when no convenient boundary exists.
                if (candidate > cursor + maxUnits / 3) end = candidate;
            }
            result.add(input.substring(cursor, end));
            cursor = end;
        }
        return result;
    }
}
