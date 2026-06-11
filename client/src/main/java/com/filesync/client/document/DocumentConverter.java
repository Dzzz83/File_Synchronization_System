package com.filesync.client.document;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public final class DocumentConverter {
    private DocumentConverter() {}

    public static String docxToHtml(File docxFile) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new FileInputStream(docxFile))) {
            StringBuilder html = new StringBuilder("<html><body>");

            for (XWPFParagraph paragraph : document.getParagraphs()) {
                html.append("<p>");

                if (paragraph.getRuns().isEmpty()) {
                    String paragraphText = paragraph.getText();

                    if (paragraphText == null || paragraphText.isBlank()) {
                        html.append("<br>");
                    } else {
                        html.append(escapeHtml(paragraphText).replace("\n", "<br>"));
                    }
                } else {
                    for (XWPFRun run : paragraph.getRuns()) {
                        appendRunAsHtml(html, run);
                    }
                }

                html.append("</p>");
            }

            html.append("</body></html>");
            return html.toString();
        }
    }

    public static void htmlToDocx(String html, File outputFile) throws Exception {
        Document htmlDoc = Jsoup.parse(html == null ? "" : html);

        try (XWPFDocument document = new XWPFDocument();
             FileOutputStream outputStream = new FileOutputStream(outputFile)) {

            Elements blocks = htmlDoc.body().select("p, div, h1, h2, h3, li");

            if (blocks.isEmpty()) {
                addParagraph(document, htmlDoc.body().text());
            } else {
                for (Element block : blocks) {
                    String text = block.wholeText().trim();

                    if (!text.isEmpty()) {
                        addParagraph(document, text);
                    }
                }
            }

            document.write(outputStream);
        }
    }

    private static void addParagraph(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();

        String[] lines = text.split("\\R", -1);

        for (int i = 0; i < lines.length; i++) {
            XWPFRun run = paragraph.createRun();
            run.setText(lines[i]);

            if (i < lines.length - 1) {
                run.addBreak();
            }
        }
    }

    private static void appendRunAsHtml(StringBuilder html, XWPFRun run) {
        String text = run.text();

        if (text == null || text.isEmpty()) {
            return;
        }

        String escaped = escapeHtml(text).replace("\n", "<br>");

        if (run.isBold()) {
            escaped = "<b>" + escaped + "</b>";
        }

        if (run.isItalic()) {
            escaped = "<i>" + escaped + "</i>";
        }

        if (run.getUnderline() != null) {
            escaped = "<u>" + escaped + "</u>";
        }

        html.append(escaped);
    }

    private static String escapeHtml(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}