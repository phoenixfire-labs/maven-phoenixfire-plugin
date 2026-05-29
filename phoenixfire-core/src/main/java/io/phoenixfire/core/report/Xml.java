package io.phoenixfire.core.report;

/** Minimal XML text/attribute escaping for report generation. */
final class Xml {

    private Xml() {
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&':
                    sb.append("&amp;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                case '\'':
                    sb.append("&apos;");
                    break;
                default:
                    // Strip control characters that are illegal in XML 1.0 (except tab/newline/cr).
                    if (c < 0x20 && c != '\t' && c != '\n' && c != '\r') {
                        sb.append(' ');
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
