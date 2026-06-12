package com.filesync.client.document;

import org.apache.poi.xwpf.usermodel.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.List;

public final class DocumentConverter {
    private DocumentConverter() {}

    public static String docxToHtml(File docxFile) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new FileInputStream(docxFile))) {
            StringBuilder html = new StringBuilder("<html><body>");

            for (XWPFParagraph paragraph : document.getParagraphs()) {
                html.append("<p>");

                List<XWPFRun> runs = paragraph.getRuns();
                if (runs.isEmpty()) {
                    String paragraphText = paragraph.getText();
                    if (paragraphText == null || paragraphText.isBlank()) {
                        html.append("<br>");
                    } else {
                        html.append(escapeHtml(paragraphText).replace("\n", "<br>"));
                    }
                } else {
                    for (XWPFRun run : runs) {
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
        Element body = htmlDoc.body();

        try (XWPFDocument document = new XWPFDocument();
             FileOutputStream outputStream = new FileOutputStream(outputFile)) {

            processNode(body, document, null);
            document.write(outputStream);
        }
    }

    private static void processNode(Node node, XWPFDocument document, XWPFParagraph currentParagraph) {
        if (node instanceof TextNode) {
            String text = ((TextNode) node).text();
            if (text != null && !text.isEmpty()) {
                XWPFParagraph paragraph = ensureParagraph(document, currentParagraph);
                XWPFRun run = paragraph.createRun();
                run.setText(text);
                // Apply formatting from current context (handled by parent elements)
                applyFormattingFromParent(node, run);
            }
            return;
        }

        if (node instanceof Element) {
            Element element = (Element) node;
            String tagName = element.tagName().toLowerCase();

            // Line break
            if (tagName.equals("br")) {
                XWPFParagraph paragraph = ensureParagraph(document, currentParagraph);
                paragraph.createRun().addBreak();
                return;
            }

            // Block elements create new paragraph
            if (isBlockElement(tagName)) {
                XWPFParagraph newParagraph = document.createParagraph();
                // Recursively process children inside this block
                for (Node child : element.childNodes()) {
                    processNode(child, document, newParagraph);
                }
                return;
            }

            // For inline elements, we continue with same paragraph but store formatting to apply to runs
            XWPFParagraph paragraph = ensureParagraph(document, currentParagraph);
            for (Node child : element.childNodes()) {
                processNode(child, document, paragraph);
            }
        }
    }

    private static XWPFParagraph ensureParagraph(XWPFDocument document, XWPFParagraph current) {
        if (current != null) {
            return current;
        }
        return document.createParagraph();
    }

    private static boolean isBlockElement(String tagName) {
        return tagName.equals("p") || tagName.equals("div") || tagName.equals("h1") ||
                tagName.equals("h2") || tagName.equals("h3") || tagName.equals("li");
    }

    private static void applyFormattingFromParent(Node node, XWPFRun run) {
        Node parent = node.parent();
        boolean bold = false;
        boolean italic = false;
        boolean underline = false;

        while (parent != null && !(parent instanceof Document)) {
            if (parent instanceof Element) {
                Element elem = (Element) parent;
                String tag = elem.tagName().toLowerCase();
                if (tag.equals("b") || tag.equals("strong")) {
                    bold = true;
                }
                if (tag.equals("i") || tag.equals("em")) {
                    italic = true;
                }
                if (tag.equals("u")) {
                    underline = true;
                }
                // Check style attribute for font-weight, font-style, text-decoration
                String style = elem.attr("style");
                if (style != null && !style.isEmpty()) {
                    if (style.contains("font-weight:bold") || style.contains("font-weight: bold")) {
                        bold = true;
                    }
                    if (style.contains("font-style:italic") || style.contains("font-style: italic")) {
                        italic = true;
                    }
                    if (style.contains("text-decoration:underline") || style.contains("text-decoration: underline")) {
                        underline = true;
                    }
                }
            }
            parent = parent.parent();
        }

        if (bold) {
            run.setBold(true);
        }
        if (italic) {
            run.setItalic(true);
        }
        if (underline) {
            run.setUnderline(UnderlinePatterns.SINGLE);
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
        UnderlinePatterns underline = run.getUnderline();
        if (underline != null && underline != UnderlinePatterns.NONE) {
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