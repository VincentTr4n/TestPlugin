package test;

import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.local.CoreLocalFileSystem;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileImpl;
import com.intellij.psi.*;
import com.intellij.util.DocumentUtil;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static test.AppUtils.showNotification;
import static test.AppUtils.showNotificationDebug;

public class GenerateTestTemplate extends AnAction {

    private static final String COMMON_JAVA_SRC = "app/src/main/java/";
    private static final String COMMON_TEST_SRC = "app/src/test/java/";
    private static final String WHITE_SPACE = "    ";
    private static final String BEFORE_METHOD = WHITE_SPACE + "public void ";

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                Editor editor = event.getData(CommonDataKeys.EDITOR);
                if (editor != null) {
                    VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
                    showNotification("From file: ", file + "", NotificationType.INFORMATION, event.getProject());
                    if (file != null) {
                        String path = file.getPath();
                        try {
                            Project project = event.getProject();
                            File outputFile = generateTest(editor, file.getPath());
                            VirtualFile testFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(outputFile);
                            showNotification("Destination", testFile + "", NotificationType.INFORMATION, event.getProject());
                            if (project != null && testFile != null) {
                                FileEditorManager.getInstance(project).openFile(testFile, true);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            showNotification("Error", e.toString(), NotificationType.ERROR, event.getProject());
                        }
                    }
                }
            }
        };

        ApplicationManager.getApplication().invokeLater(runnable);
    }

    private File generateTest(Editor editor, String path) throws IOException {
        File srcFile = new File(path);
        List<String> lines = Files.readAllLines(Paths.get(path));
        String packageName = lines.get(0).split("\\s+")[1];
        packageName = packageName.substring(0, packageName.length() - 1);
        String packagePath = packageName.replace(".", "/");

        int index = path.indexOf(COMMON_JAVA_SRC);
        String rootProject = path.substring(0, index);

        String testPath = rootProject + COMMON_TEST_SRC + packagePath;
        File testFileParent = new File(testPath);
        testFileParent.mkdirs();
        String srcName = srcFile.getName();
        srcName = srcName.substring(0, srcName.lastIndexOf('.'));
        String testName = "Test" + srcName;
        File testDstFile = new File(testFileParent, testName + ".java");

        showNotification("Test destination", testDstFile + "", NotificationType.INFORMATION, editor.getProject());
        writeToDestination(editor, packageName, testName, testDstFile);
        return testDstFile;
    }

    private void writeToDestination(Editor editor, String packageName, String testName, File testDstFile) {
        try (PrintWriter writer = new PrintWriter(testDstFile)) {
            List<String> list = makeHeader(testName, packageName);
            List<String> names = setUpMethods(editor);
            for (String name : names) {
                list.add(WHITE_SPACE + "@Test");
                list.add(BEFORE_METHOD + "test" + formatName(name) + "() {");
                list.add(WHITE_SPACE);
                list.add(WHITE_SPACE + "}");
                list.add("");
            }
            for (String s : list) {
                writer.println(s);
            }
            writer.println("}");
        } catch (Exception e) {
            showNotification("Error", e.toString(), NotificationType.ERROR, editor.getProject());
        }
    }

    private String formatName(String name) {
        StringBuilder sb = new StringBuilder(name);
        sb.setCharAt(0, Character.toUpperCase(sb.charAt(0)));
        return sb.toString();
    }

    private List<String> makeHeader(String testClassName, String packageName) {
        List<String> list = new ArrayList<String>(16);
        list.add(String.format("package %s;", packageName));
        list.add("");
        list.add("import org.junit.Before;");
        list.add("import org.junit.BeforeClass;");
        list.add("import org.junit.Test;");
        list.add("import org.junit.runner.RunWith;");
        list.add("import org.powermock.modules.junit4.PowerMockRunner;");
        list.add("");
        list.add("import static org.mockito.MockitoAnnotations.initMocks;");
        list.add("");
        list.add("@RunWith(PowerMockRunner.class)");
        list.add(String.format("public class %s {", testClassName));
        list.add("\n    @BeforeClass\r\n" + "    public static void setUp() {\r\n" + "\r\n" + "    }\r\n" + "\r\n"
                + "    @Before\r\n" + "    public void init() {\r\n" + "        initMocks(this);\r\n" + "    }");
        list.add("");
        return list;
    }

    private List<String> setUpMethods(Editor editor) {
        Set<String> methodNames = new LinkedHashSet<>();
        PsiClass firstClass = AppUtils.getClassFromEditor(editor);
        showNotificationDebug("Psi Class: " + firstClass, editor.getProject());
        if (firstClass != null) {
            for (PsiMethod method : firstClass.getMethods()) {
                if (method.hasModifierProperty(PsiModifier.PUBLIC) && !method.isConstructor()) {
                    String name = method.getName();
                    methodNames.add(name);
                }
            }
        }
        return new ArrayList<>(methodNames);
    }
}
