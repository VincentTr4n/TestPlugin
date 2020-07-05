package test;

import com.intellij.lang.ASTNode;
import com.intellij.lang.jvm.annotation.JvmAnnotationAttribute;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.ForeignLeafPsiElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Array;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class GeneratePrepareAction extends AnAction {
    private static final String PREPARE_FOR_TEST = "@PrepareForTest({\n";
    private static final String PREFIX_PREPARE_CLASS = "        ";
    private static final String IMPORT_PREPARE_FOR_TEST = "import org.powermock.core.classloader.annotations.PrepareForTest;";
    private static final String RUN_WITH = "@RunWith(";
    private static final String IMPORT_RUN_WITH = "import org.junit.runner.RunWith;";

    private final Pattern mockStaticPattern = Pattern.compile("mockStatic\\((.*)\\.class\\);");

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        if (editor != null) {
            Document document = editor.getDocument();
            Set<String> classes = getAllMockStaticClass(document);

            final PsiAnnotation annotation = getAnnotation(editor);
            WriteCommandAction.runWriteCommandAction(event.getProject(), () -> {
                String content = document.getCharsSequence().toString();
                if (annotation == null) {
                    insertTextIfNotPresent(document, classes, content);
                    return;
                }
                insertTextIfExists(document, classes, annotation, content);
            });
        }
    }

    @NotNull
    private Set<String> getAllMockStaticClass(Document document) {
        String text = document.getCharsSequence().toString();
        Set<String> classes = new HashSet<>();
        Matcher matcher = mockStaticPattern.matcher(text);
        while (matcher.find()) {
            classes.add(matcher.group(1) + ".class");
        }
        return classes;
    }

    private void insertTextIfExists(Document document, Set<String> classes, PsiAnnotation annotation, String content) {
        int startOffset = content.indexOf(PREPARE_FOR_TEST);
        int endOffset = content.indexOf(RUN_WITH);
        document.replaceString(startOffset, endOffset, makePrepareForTest(classes));
    }

    private void insertTextIfNotPresent(Document document, Set<String> classes, String content) {
        if (!classes.isEmpty()) {
            int importOffset = content.indexOf(IMPORT_RUN_WITH);
            document.insertString(importOffset - 1, "\n" + IMPORT_PREPARE_FOR_TEST);
            content = document.getCharsSequence().toString();
            int index = content.indexOf(RUN_WITH);
            document.insertString(index, makePrepareForTest(classes));
        }
    }

    private boolean isIgnored(PsiElement element) {
        return element instanceof PsiJavaToken ||
                (element instanceof PsiWhiteSpace);
    }

    private PsiAnnotation getAnnotation(Editor editor) {
        PsiClass firstClass = AppUtils.getClassFromEditor(editor);
        if (firstClass != null) {
            for (PsiAnnotation annotation : firstClass.getAnnotations()) {
                String name = annotation.getQualifiedName();
                if (name != null && name.contains("PrepareForTest")) {
                    return annotation;
                }
            }
        }
        return null;
    }

    private String makePrepareForTest(Set<String> classes) {
        return PREPARE_FOR_TEST +
                classes.stream().sorted(Comparator.comparingInt(String::length))
                        .map(s -> PREFIX_PREPARE_CLASS + s + ",")
                        .collect(Collectors.joining("\n")) + "\n})\n";
    }
}
