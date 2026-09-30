package com.ffc.banc888.fly;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class DocumentExporter {
    private final Activity activity;

    DocumentExporter(Activity activity) {
        this.activity = activity;
    }

    String createDocx(String title, String body, String filename) {
        JSONObject o = new JSONObject();
        try {
            File dir = new File(activity.getCacheDir(), "exports");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("exports directory");
            File temp = new File(dir, safeFileName(filename, "BANC888_document", "docx") + ".tmp");
            File target = new File(dir, safeFileName(filename, "BANC888_document", "docx"));
            writeDocx(temp, title, body);
            if (target.exists() && !target.delete()) throw new IllegalStateException("cannot replace export");
            if (!temp.renameTo(target)) throw new IllegalStateException("cannot finalize export");
            o.put("ok", true);
            o.put("name", target.getName());
            o.put("bytes", target.length());
            o.put("shared", true);
            shareFile(target, "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        } catch (Exception e) {
            try {
                o.put("ok", false);
                o.put("error", e.getMessage() == null ? e.toString() : e.getMessage());
            } catch (Exception ignored) {}
        }
        return o.toString();
    }

    String shareText(String text, String filename, String mime) {
        JSONObject o = new JSONObject();
        try {
            File dir = new File(activity.getCacheDir(), "exports");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("exports directory");
            String ext = "txt";
            int dot = filename == null ? -1 : filename.lastIndexOf('.');
            if (dot >= 0 && dot < filename.length() - 1) {
                ext = filename.substring(dot + 1).replaceAll("[^A-Za-z0-9]", "");
            }
            File target = new File(dir, safeFileName(filename, "BANC888_export", ext.isEmpty() ? "txt" : ext));
            File temp = new File(dir, target.getName() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            }
            if (target.exists() && !target.delete()) throw new IllegalStateException("cannot replace export");
            if (!temp.renameTo(target)) throw new IllegalStateException("cannot finalize export");
            o.put("ok", true);
            o.put("name", target.getName());
            o.put("bytes", target.length());
            o.put("shared", true);
            shareFile(target, mime == null || mime.isEmpty() ? "text/plain" : mime);
        } catch (Exception e) {
            try {
                o.put("ok", false);
                o.put("error", e.getMessage() == null ? e.toString() : e.getMessage());
            } catch (Exception ignored) {}
        }
        return o.toString();
    }

    private void shareFile(File file, String mime) {
        activity.runOnUiThread(() -> {
            try {
                Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".files", file);
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType(mime);
                send.putExtra(Intent.EXTRA_STREAM, uri);
                send.setClipData(ClipData.newRawUri(file.getName(), uri));
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                activity.startActivity(Intent.createChooser(send, "BANC888 から共有"));
            } catch (Exception ignored) {}
        });
    }

    private void writeDocx(File file, String title, String body) throws Exception {
        String types = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                + "<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>"
                + "<Override PartName=\"/word/numbering.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml\"/>"
                + "<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>"
                + "<Override PartName=\"/docProps/app.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.extended-properties+xml\"/>"
                + "</Types>";
        String rels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>"
                + "<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties\" Target=\"docProps/app.xml\"/>"
                + "</Relationships>";
        String docRels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering\" Target=\"numbering.xml\"/>"
                + "</Relationships>";
        String styles = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:styles xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                + "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/><w:rPr><w:sz w:val=\"22\"/></w:rPr></w:style>"
                + "<w:style w:type=\"paragraph\" w:styleId=\"Title\"><w:name w:val=\"Title\"/><w:basedOn w:val=\"Normal\"/><w:rPr><w:b/><w:sz w:val=\"34\"/></w:rPr></w:style>"
                + headingStyle("Heading1", "heading 1", 30)
                + headingStyle("Heading2", "heading 2", 26)
                + headingStyle("Heading3", "heading 3", 23)
                + "<w:style w:type=\"character\" w:styleId=\"Code\"><w:name w:val=\"Code\"/><w:rPr><w:rFonts w:ascii=\"Consolas\" w:hAnsi=\"Consolas\"/><w:sz w:val=\"19\"/></w:rPr></w:style>"
                + "</w:styles>";
        String numbering = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:numbering xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                + "<w:abstractNum w:abstractNumId=\"0\"><w:lvl w:ilvl=\"0\"><w:numFmt w:val=\"bullet\"/><w:lvlText w:val=\"•\"/><w:pPr><w:ind w:left=\"720\" w:hanging=\"360\"/></w:pPr></w:lvl></w:abstractNum>"
                + "<w:abstractNum w:abstractNumId=\"1\"><w:lvl w:ilvl=\"0\"><w:start w:val=\"1\"/><w:numFmt w:val=\"decimal\"/><w:lvlText w:val=\"%1.\"/><w:pPr><w:ind w:left=\"720\" w:hanging=\"360\"/></w:pPr></w:lvl></w:abstractNum>"
                + "<w:num w:numId=\"1\"><w:abstractNumId w:val=\"0\"/></w:num>"
                + "<w:num w:numId=\"2\"><w:abstractNumId w:val=\"1\"/></w:num>"
                + "</w:numbering>";
        String core = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:title>"
                + xmlEscape(title == null ? "" : title) + "</dc:title><dc:creator>BANC888 Fly Agent</dc:creator></cp:coreProperties>";
        String app = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\"><Application>BANC888</Application></Properties>";

        StringBuilder doc = new StringBuilder();
        doc.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>");
        if (title != null && !title.trim().isEmpty()) {
            paragraph(doc, title.trim(), "Title", 0, false);
        }
        String[] lines = (body == null ? "" : body).replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        boolean codeBlock = false;
        for (String raw : lines) {
            String line = raw == null ? "" : raw;
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                codeBlock = !codeBlock;
                continue;
            }
            if (line.isEmpty()) {
                doc.append("<w:p/>");
                continue;
            }
            if (line.startsWith("### ")) {
                paragraph(doc, line.substring(4).trim(), "Heading3", 0, false);
                continue;
            }
            if (line.startsWith("## ")) {
                paragraph(doc, line.substring(3).trim(), "Heading2", 0, false);
                continue;
            }
            if (line.startsWith("# ")) {
                paragraph(doc, line.substring(2).trim(), "Heading1", 0, false);
                continue;
            }
            if (line.startsWith("- ") || line.startsWith("* ")) {
                paragraph(doc, line.substring(2).trim(), null, 1, codeBlock);
                continue;
            }
            if (line.matches("^\\d+[.)]\\s+.*")) {
                paragraph(doc, line.replaceFirst("^\\d+[.)]\\s+", ""), null, 2, codeBlock);
                continue;
            }
            paragraph(doc, line, null, 0, codeBlock);
        }
        doc.append("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\"/></w:sectPr>")
                .append("</w:body></w:document>");

        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            zipText(zip, "[Content_Types].xml", types);
            zipText(zip, "_rels/.rels", rels);
            zipText(zip, "docProps/core.xml", core);
            zipText(zip, "docProps/app.xml", app);
            zipText(zip, "word/document.xml", doc.toString());
            zipText(zip, "word/_rels/document.xml.rels", docRels);
            zipText(zip, "word/styles.xml", styles);
            zipText(zip, "word/numbering.xml", numbering);
        }
    }

    private static String headingStyle(String id, String name, int size) {
        return "<w:style w:type=\"paragraph\" w:styleId=\"" + id + "\"><w:name w:val=\"" + name
                + "\"/><w:basedOn w:val=\"Normal\"/><w:next w:val=\"Normal\"/><w:qFormat/><w:rPr><w:b/><w:sz w:val=\""
                + size + "\"/></w:rPr></w:style>";
    }

    private static void paragraph(StringBuilder doc, String text, String style, int numId, boolean code) {
        doc.append("<w:p><w:pPr>");
        if (style != null) doc.append("<w:pStyle w:val=\"").append(style).append("\"/>");
        if (numId > 0) doc.append("<w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"").append(numId).append("\"/></w:numPr>");
        doc.append("</w:pPr><w:r>");
        if (code) doc.append("<w:rPr><w:rStyle w:val=\"Code\"/></w:rPr>");
        doc.append("<w:t xml:space=\"preserve\">").append(xmlEscape(text)).append("</w:t></w:r></w:p>");
    }

    private static void zipText(ZipOutputStream zip, String path, String text) throws Exception {
        ZipEntry e = new ZipEntry(path);
        zip.putNextEntry(e);
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String xmlEscape(String s) {
        return (s == null ? "" : s)
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static String safeFileName(String name, String fallback, String ext) {
        String n = (name == null || name.trim().isEmpty()) ? fallback : name.trim();
        n = n.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").replace("..", "_");
        if (!n.toLowerCase(Locale.ROOT).endsWith("." + ext)) n += "." + ext;
        return n;
    }

    String diagnosticsJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("docx", "styles+numbering+atomic-temp");
            o.put("share", true);
        } catch (Exception ignored) {}
        return o.toString();
    }
}
