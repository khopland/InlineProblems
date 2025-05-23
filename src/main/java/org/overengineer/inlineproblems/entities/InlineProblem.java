package org.overengineer.inlineproblems.entities;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.overengineer.inlineproblems.definitions.RegexPattern;
import org.overengineer.inlineproblems.settings.SettingsState;

@Getter
@Setter
@EqualsAndHashCode(exclude = {"line", "problemLineHighlighterHashCode", "inlineProblemLabelHashCode", "isBlockElement", "drawDetails"})
public class InlineProblem {

    // The line the problem first appeared
    private final int line;
    @Setter
    private int severity;

    // If two problems with the same text occur in the same line only one will be shown
    private final String text;
    private final String file;
    private final TextEditor textEditor;
    private final Project project;

    private DrawDetails drawDetails;

    private int actualEndOffset;
    private int actualStartffset;

    private boolean isBlockElement = false;

    // Set after drawing the line highlight, used to remove it again
    private int problemLineHighlighterHashCode;

    // Set after drawing the inlay, used to remove the inlay again
    private int inlineProblemLabelHashCode = 0;

    // Used to determine if the problem has moved
    private int highlightInfoStartOffset;

    // Used to identify the problem, should never change even if problem the problem moved
    private int rangeHighlighterHashCode;


    public InlineProblem(
            int line,
            String filePath,
            HighlightInfo highlightInfo,
            TextEditor textEditor,
            RangeHighlighter rangeHighlighter,
            SettingsState settingsState
    ) {
        String usedText = highlightInfo.getDescription();
        if (usedText == null)
            usedText = "";
        else
            usedText = getTextWithHtmlStrippingAndXmlUnescaping(usedText.stripLeading(), settingsState);

        this.line = line;
        this.text = usedText;
        this.severity = highlightInfo.getSeverity().myVal;
        this.textEditor = textEditor;
        this.file = filePath;
        this.project = textEditor.getEditor().getProject();
        this.highlightInfoStartOffset = highlightInfo.hashCode();
        this.rangeHighlighterHashCode = rangeHighlighter.hashCode();
        this.actualStartffset = highlightInfo.getStartOffset();

        if (highlightInfo.getActualEndOffset() == 0)
            this.actualEndOffset = highlightInfo.getActualEndOffset();
        else
            this.actualEndOffset = highlightInfo.getActualEndOffset() -1;
    }

    private String getTextWithHtmlStrippingAndXmlUnescaping(String text, SettingsState settingsState) {
        if (
                settingsState.isEnableHtmlStripping() &&
                text.contains("<") &&
                RegexPattern.HTML_TAG_PATTERN.matcher(text).find()
        ) {
            text = StringUtil.stripHtml(text, " ");
        }

        if (
                settingsState.isEnableHtmlStripping() &&
                text.contains("&")
        ) {
            text = StringUtil.unescapeXmlEntities(text);
        }

        return text;
    }
}
