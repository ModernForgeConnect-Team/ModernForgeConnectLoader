package github.com.gengyoubo.mfcl.idea;

/** Syntax only: semantic type compatibility is checked by the MFCL runtime. */
final class JvmDescriptor {
    private JvmDescriptor() {}
    static boolean valid(String text, boolean method) {
        if (text.isEmpty()) return true;
        int position = 0;
        if (method) {
            if (text.charAt(position++) != '(') return false;
            while (position < text.length() && text.charAt(position) != ')') {
                int next = type(text, position, false);
                if (next < 0) return false;
                position = next;
            }
            if (position >= text.length()) return false;
            position++;
        }
        return type(text, position, method) == text.length();
    }
    private static int type(String text, int position, boolean allowVoid) {
        if (position >= text.length()) return -1;
        char c = text.charAt(position++);
        if ("ZBCSIJFD".indexOf(c) >= 0 || c == 'V' && allowVoid) return position;
        if (c == '[') {
            int dimensions = 1;
            while (position < text.length() && text.charAt(position) == '[') { position++; dimensions++; }
            return dimensions <= 255 ? type(text, position, false) : -1;
        }
        if (c != 'L') return -1;
        int end = text.indexOf(';', position);
        if (end <= position) return -1;
        String name = text.substring(position, end);
        if (name.startsWith("/") || name.endsWith("/") || name.contains("//")) return -1;
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (ch == '.' || ch == '[' || ch == ';' || ch == '(' || ch == ')' || Character.isWhitespace(ch)) return -1;
        }
        return end + 1;
    }
}
