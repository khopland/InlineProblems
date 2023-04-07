package org.overengineer.inlineproblems;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import org.overengineer.inlineproblems.entities.DrawDetails;
import org.overengineer.inlineproblems.entities.InlineProblem;
import org.overengineer.inlineproblems.settings.SettingsState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class ProblemManager implements Disposable {
    private final List<InlineProblem> activeProblems = new ArrayList<>();

    private final InlineDrawer inlineDrawer = new InlineDrawer();

    private final SettingsState settingsState = SettingsState.getInstance();

    private final Logger logger = Logger.getInstance(ProblemManager.class);

    public void dispose() {
        reset();
    }

    public void removeProblem(InlineProblem problem) {
        InlineProblem problemToRemove = findActiveProblemByRangeHighlighterHashCode(problem.getRangeHighlighterHashCode());

        if (problemToRemove == null) {
            logger.warn("Removal of problem failed, not found by RangeHighlighterHashCode");
            resetForEditor(problem.getTextEditor().getEditor());
            return;
        }

        inlineDrawer.undrawErrorLineHighlight(problemToRemove);
        inlineDrawer.undrawInlineProblemLabel(problemToRemove);

        if (!activeProblems.remove(problemToRemove)) {
            logger.warn("Removal of problem failed, resetting");
            resetForEditor(problemToRemove.getTextEditor().getEditor());
            return;
        }
    }

    /**
     * To add problems, if there are existing problems in the same line, they will be removed and re-added to ensure the
     * correct order (ordered by severity)
     * @param problem problem to add
     */
    public void addProblemSorted(InlineProblem problem) {
        List<InlineProblem> problemsInLine = getProblemsInLine(problem.getLine());
        problemsInLine.add(problem);

        problemsInLine = problemsInLine.stream()
                .sorted((p1, p2) -> Integer.compare(p2.getSeverity(), p1.getSeverity()))
                .collect(Collectors.toList());

        problemsInLine.forEach(p -> {
            if (p != problem)
                removeProblem(p);
        });

        problemsInLine.forEach(this::addProblem);
    }

    public void addProblem(InlineProblem problem) {
        DrawDetails drawDetails = new DrawDetails(problem, problem.getTextEditor().getEditor());

        inlineDrawer.drawProblemLabel(problem, drawDetails);
        inlineDrawer.drawProblemLineHighlight(problem, drawDetails);

        activeProblems.add(problem);
    }

    public void reset() {
        final List<InlineProblem> activeProblemSnapShot = List.copyOf(activeProblems);
        activeProblemSnapShot.forEach(this::removeProblem);
    }

    public void resetForEditor(Editor editor) {
        final List<InlineProblem> activeProblemsSnapShot = List.copyOf(activeProblems);

        activeProblemsSnapShot.stream()
                .filter(aP -> aP.getTextEditor().getEditor().equals(editor))
                .forEach(this::removeProblem);
    }

    public void updateFromNewActiveProblems(List<InlineProblem> problems) {
        final List<InlineProblem> activeProblemsSnapShot = List.copyOf(activeProblems);

        updateFromNewActiveProblems(problems, activeProblemsSnapShot);
    }

    public void updateFromNewActiveProblemsForProjectAndFile(List<InlineProblem> problems, Project project, String filePath) {
        final List<InlineProblem> activeProblemsSnapShot = activeProblems.stream()
                .filter(p -> p.getProject().equals(project) && p.getFile().equals(filePath))
                .collect(Collectors.toList());

        updateFromNewActiveProblems(problems, activeProblemsSnapShot);
    }

    public List<InlineProblem> getProblemsInLine(int line) {
        return activeProblems.stream()
                .filter(p -> p.getLine() == line)
                .collect(Collectors.toList());
    }

    private void updateFromNewActiveProblems(List<InlineProblem> newProblems, List<InlineProblem> activeProblemsSnapShot) {
        final List<InlineProblem> processedProblems = new ArrayList<>();
        List<InlineProblem> usedProblems;

        if (settingsState.isShowOnlyHighestSeverityPerLine()) {
            Map<Integer, InlineProblem> filteredMap = new HashMap<>();

            for (InlineProblem problem : newProblems) {
                if (filteredMap.containsKey(problem.getLine())) {
                    if (filteredMap.get(problem.getLine()).getSeverity() < problem.getSeverity()) {
                        filteredMap.replace(problem.getLine(), problem);
                    }
                }
                else {
                    filteredMap.put(problem.getLine(), problem);
                }
            }

            usedProblems = new ArrayList<>(filteredMap.values());
        }
        else {
            usedProblems = newProblems;
        }

        activeProblemsSnapShot.stream()
                .filter(p -> !usedProblems.contains(p))
                .forEach(p -> {processedProblems.add(p); removeProblem(p);});

        usedProblems.stream()
                .filter(p -> !activeProblemsSnapShot.contains(p) && !processedProblems.contains(p))
                .forEach(this::addProblemSorted);
    }

    private InlineProblem findActiveProblemByRangeHighlighterHashCode(int hashCode) {
        return activeProblems.stream()
                .filter(p -> p.getRangeHighlighterHashCode() == hashCode)
                .findFirst()
                .orElse(null);
    }
}
