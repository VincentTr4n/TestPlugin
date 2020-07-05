package test;

import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;

import java.util.Arrays;

public class AppUtils {
    static PsiClass getClassFromEditor(Editor editor) {
        Project project = editor.getProject();
        if (project != null) {
            final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
            showNotificationDebug("Psi File: " + psiFile, editor.getProject());
            if (psiFile instanceof PsiJavaFile) {
                PsiClass[] classArray = ((PsiJavaFile) psiFile).getClasses();
                showNotificationDebug("Psi Class: " + Arrays.toString(classArray), editor.getProject());
                if (classArray.length > 0) {
                    return classArray[0];
                }
            }
        }
        return null;
    }

    static void showNotification(String error, String s, NotificationType error2, Project project2) {
        new NotificationGroup("My plugin", NotificationDisplayType.BALLOON, true)
                .createNotification(error,
                        s,
                        error2,
                        null
                ).notify(project2);
    }

    static void showNotificationDebug(String s, Project project) {
        showNotification("Debug: ", s, NotificationType.INFORMATION, project);
    }
}
