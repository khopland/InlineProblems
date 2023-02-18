package org.overengineer.inlineproblems.listeners;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.overengineer.inlineproblems.ProblemManager;
import org.overengineer.inlineproblems.entities.InlineProblem;
import org.overengineer.inlineproblems.entities.enums.Listener;
import org.overengineer.inlineproblems.settings.SettingsState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class MarkupModelProblemListener implements MarkupModelListener {
    private final SettingsState settingsState;
    private final ProblemManager problemManager;
    private final String filePath;
    private final TextEditor textEditor;

    private static final List<Disposable> disposables = new ArrayList<>();

    public static final String NAME = "MarkupModelListener";

    private enum EventType {
        ADD, REMOVE, CHANGE
    }

    private MarkupModelProblemListener(
            final TextEditor textEditor
    ) {
        this.textEditor = textEditor;
        this.filePath = textEditor.getFile().getPath();

        problemManager = ApplicationManager.getApplication().getService(ProblemManager.class);
        settingsState = SettingsState.getInstance();
    }

    @Override
    public void afterAdded(@NotNull RangeHighlighterEx highlighter) {
        handleEvent(EventType.ADD, highlighter);
    }

    @Override
    public void beforeRemoved(@NotNull RangeHighlighterEx highlighter) {
        handleEvent(EventType.REMOVE, highlighter);
    }

    @Override
    public void attributesChanged(@NotNull RangeHighlighterEx highlighter, boolean renderersChanged, boolean fontStyleOrColorChanged) {
        handleEvent(EventType.CHANGE, highlighter);
    }

    public static void setup(TextEditor textEditor) {
        Editor editor = textEditor.getEditor();
        var documentMarkupModel = DocumentMarkupModel.forDocument(editor.getDocument(), editor.getProject(), false);

        if (!(documentMarkupModel instanceof MarkupModelEx) || textEditor.getFile() == null) {
            return;
        }

        Disposable disposable = new MarkupModelProblemListenerDisposable();
        Disposer.register(ApplicationManager.getApplication().getService(ProblemManager.class), disposable);

        ((MarkupModelEx) documentMarkupModel).addMarkupModelListener(
                disposable,
                new MarkupModelProblemListener(textEditor)
        );

        disposables.add(disposable);
    }

    public static void disposeAll() {
        List.copyOf(disposables)
                .forEach(d -> {
                    Disposer.dispose(d);
                    disposables.remove(d);
                });
    }

    private void handleEvent(EventType type, @NotNull RangeHighlighterEx highlighter) {
        if (settingsState.getEnabledListener() != Listener.MARKUP_MODEL_LISTENER)
            return;

        Editor editor = textEditor.getEditor();

        if (editor.isDisposed())
            return;

        int lineCount = editor.getDocument().getLineCount();
        if (lineCount <= 0)
            return;

        int fileEndOffset = editor.getDocument().getLineEndOffset(lineCount - 1);

        if (fileEndOffset < highlighter.getStartOffset()) {
            return;
        }

        if (!(highlighter.getErrorStripeTooltip() instanceof HighlightInfo))
            return;

        InlineProblem problem = constructProblem(
                editor.getDocument().getLineNumber(highlighter.getStartOffset()),
                (HighlightInfo) highlighter.getErrorStripeTooltip(),
                highlighter
        );

        List<String> problemTextBeginningFilterList = new ArrayList<>(
                Arrays.asList(SettingsState.getInstance().getProblemFilterList().split(";"))
        );

        if (
                problem.getText().equals("") ||
                        problemTextBeginningFilterList.stream()
                                .anyMatch(f -> problem.getText().toLowerCase().startsWith(f.toLowerCase()))
        ) {
            return;
        }

        switch (type) {
            case ADD:
                problemManager.addProblem(problem);
                break;
            case REMOVE:
                problemManager.removeProblem(problem);
                break;
            case CHANGE:
                problemManager.removeProblem(problem);
                problemManager.addProblem(problem);
                break;
        }
    }

    private InlineProblem constructProblem(int line, HighlightInfo info, RangeHighlighter highlighter) {
        return new InlineProblem(
                line,
                info,
                textEditor,
                highlighter
        );
    }
}