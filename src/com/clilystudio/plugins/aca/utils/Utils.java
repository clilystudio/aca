package com.clilystudio.plugins.aca.utils;

import com.clilystudio.plugins.aca.model.SubViewItem;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.search.EverythingGlobalScope;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.awt.RelativePoint;

import java.util.ArrayList;

public class Utils {
    public static final String VIEWHOLDER_CLASS_NAME = "ViewHolder";
    private static final Logger log = Logger.getInstance(Utils.class);
    private static final String ADD_PREFIX = "androidcodeassistant_add_prefix";
    private static final String PREFIX = "androidcodeassistant_prefix";
    private static final String TRIM_TYPE = "androidcodeassistant_trim_type";

    /**
     * Is using Android SDK?
     */
    public static Sdk findAndroidSDK() {
        Sdk[] allJDKs = ProjectJdkTable.getInstance().getAllJdks();
        for (Sdk sdk : allJDKs) {
            if (sdk.getSdkType().getName().toLowerCase().contains("android")) {
                return sdk;
            }
        }

        return null; // no Android SDK found
    }

    /**
     * Try to find layout XML file in current source on cursor's position
     */
    public static PsiFile getLayoutFileFromCaret(Editor editor, PsiFile file) {
        int offset = editor.getCaretModel().getOffset();

        PsiElement candidateA = file.findElementAt(offset);
        PsiElement candidateB = file.findElementAt(offset - 1);

        PsiFile layout = findLayoutResource(candidateA);
        if (layout != null) {
            return layout;
        }

        return findLayoutResource(candidateB);
    }

    /**
     * Try to find layout XML file in selected element
     */
    private static PsiFile findLayoutResource(PsiElement element) {
        log.info("Finding layout resource for element: " + element.getText());
        if (!(element instanceof PsiIdentifier)) {
            return null; // nothing to be selected
        }

        PsiElement layout = element.getParent().getFirstChild();
        if (layout == null) {
            return null; // no file to process
        }
        if (!"R.layout".equals(layout.getText())) {
            return null; // not layout file
        }

        Project project = element.getProject();
        String name = String.format("%s.xml", element.getText());
        return resolveLayoutResourceFile(element, project, name);
    }

    private static PsiFile resolveLayoutResourceFile(PsiElement element, Project project, String name) {
        // restricting the search to the current module - searching the whole project could return wrong layouts
        Module module = ModuleUtil.findModuleForPsiElement(element);
        PsiFile[] files = null;
        if (module != null) {
            // first omit libraries, it might cause issues like (#103)
            GlobalSearchScope moduleScope = module.getModuleWithDependenciesScope();
            files = FilenameIndex.getFilesByName(project, name, moduleScope);
            if (files == null || files.length <= 0) {
                // now let's do a fallback including the libraries
                moduleScope = module.getModuleWithDependenciesAndLibrariesScope(false);
                files = FilenameIndex.getFilesByName(project, name, moduleScope);
            }
        }
        if (files == null || files.length <= 0) {
            // fallback to search through the whole project
            // useful when the project is not properly configured - when the resource directory is not configured
            files = FilenameIndex.getFilesByName(project, name, new EverythingGlobalScope(project));
            if (files.length <= 0) {
                return null; //no matching files
            }
        }

        // We have a problem here - we still can have multiple layouts (some coming from a dependency)
        // we need to resolve R class properly and find the proper layout for the R class
        for (PsiFile file : files) {
            log.info("Resolved layout resource file for className [" + name + "]: " + file.getVirtualFile());
        }
        return files[0];
    }

    /**
     * Try to find layout XML file by className
     */
    private static PsiFile findLayoutResource(PsiFile file, Project project, String fileName) {
        String name = String.format("%s.xml", fileName);
        // restricting the search to the module of layout that includes the layout we are seaching for
        return resolveLayoutResourceFile(file, project, name);
    }

    /**
     * Obtain all IDs from layout
     */
    public static ArrayList<SubViewItem> getIDsFromLayout(final PsiFile file) {
        final ArrayList<SubViewItem> subViewItems = new ArrayList<>();
        return getIDsFromLayout(file, subViewItems);
    }

    /**
     * Obtain all IDs from layout
     */
    private static ArrayList<SubViewItem> getIDsFromLayout(final PsiFile file, final ArrayList<SubViewItem> subViewItems) {
        file.accept(new XmlRecursiveElementVisitor() {

            @Override
            public void visitElement(final PsiElement element) {
                super.visitElement(element);

                if (element instanceof XmlTag) {
                    XmlTag tag = (XmlTag) element;

                    if (tag.getName().equalsIgnoreCase("include")) {
                        XmlAttribute layout = tag.getAttribute("layout", null);

                        if (layout != null) {
                            Project project = file.getProject();
                            PsiFile include = findLayoutResource(file, project, getLayoutName(layout.getValue()));

                            if (include != null) {
                                getIDsFromLayout(include, subViewItems);

                                return;
                            }
                        }
                    }

                    // get element ID
                    XmlAttribute id = tag.getAttribute("android:id", null);
                    if (id == null) {
                        return; // missing android:id attribute
                    }
                    String value = id.getValue();
                    if (value == null) {
                        return; // empty value
                    }

                    // check if there is defined custom class
                    String className = tag.getName();
                    XmlAttribute clazz = tag.getAttribute("class", null);
                    if (clazz != null) {
                        className = clazz.getValue();
                    }
                    subViewItems.add(new SubViewItem(className, value));
                }
            }
        });

        return subViewItems;
    }

    /**
     * Get layout className from XML identifier (@layout/....)
     */
    private static String getLayoutName(String layout) {
        if (layout == null || !layout.startsWith("@") || !layout.contains("/")) {
            return null; // it's not layout identifier
        }

        String[] parts = layout.split("/");
        if (parts.length != 2) {
            return null; // not enough parts
        }

        return parts[1];
    }

    /**
     * Display simple notification - information
     */
    public static void showInfoNotification(Project project, String text) {
        showNotification(project, MessageType.INFO, text);
    }

    /**
     * Display simple notification - error
     */
    public static void showErrorNotification(Project project, String text) {
        showNotification(project, MessageType.ERROR, text);
    }

    /**
     * Display simple notification of given type
     */
    private static void showNotification(Project project, MessageType type, String text) {
        StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);

        JBPopupFactory.getInstance()
                .createHtmlTextBalloonBuilder(text, type, null)
                .setFadeoutTime(7500)
                .createBalloon()
                .show(RelativePoint.getCenterOf(statusBar.getComponent()), Balloon.Position.atRight);
    }

    /**
     * Load field className prefix from code style
     */
    public static String getPrefix() {
        if (PropertiesComponent.getInstance().isValueSet(PREFIX)) {
            return PropertiesComponent.getInstance().getValue(PREFIX);
        } else {
            CodeStyleSettingsManager manager = CodeStyleSettingsManager.getInstance();
            CodeStyleSettings settings = manager.getCurrentSettings();
            if (!isEmptyString(settings.FIELD_NAME_PREFIX)) {
                return settings.FIELD_NAME_PREFIX;
            } else {
                return "m";
            }
        }
    }

    public static void setPrefix(String prefix) {
        PropertiesComponent.getInstance().setValue(PREFIX, prefix);
    }

    public static boolean isAddPrefix() {
        return PropertiesComponent.getInstance().getBoolean(ADD_PREFIX, false);
    }

    public static void setAddPrefix(boolean isAddPrefix) {
        PropertiesComponent.getInstance().setValue(ADD_PREFIX, isAddPrefix);
    }

    public static boolean isTrimType() {
        return PropertiesComponent.getInstance().getBoolean(TRIM_TYPE, false);
    }

    public static void setTrimType(boolean isTrimType) {
        PropertiesComponent.getInstance().setValue(TRIM_TYPE, isTrimType);
    }

    public static int getInjectCount(ArrayList<SubViewItem> subViewItems) {
        int cnt = 0;
        for (SubViewItem subViewItem : subViewItems) {
            if (subViewItem.isSelected()) {
                cnt++;
            }
        }
        return cnt;
    }

    public static int getClickCount(ArrayList<SubViewItem> subViewItems) {
        int cnt = 0;
        for (SubViewItem subViewItem : subViewItems) {
            if (subViewItem.hasClickEvent()) {
                cnt++;
            }
        }
        return cnt;
    }

    /**
     * Easier way to check if string is empty
     */
    public static boolean isEmptyString(String text) {
        return (text == null || text.trim().length() == 0);
    }

    public static String getRealClassName(String className) {
        if ("WebView".equals(className)) {
            return "android.webkit.WebView";
        } else if ("View".equals(className)) {
            return "android.view.View";
        } else {
            return "android.widget." + className;
        }
    }

    public static boolean isViewHolder(String qualifierName) {
        return "android.widget.ListAdapter".equals(qualifierName) ||
                "android.widget.ArrayAdapter".equals(qualifierName) ||
                "android.widget.BaseAdapter".equals(qualifierName) ||
                "android.widget.HeaderViewListAdapter".equals(qualifierName) ||
                "android.widget.SimpleAdapter".equals(qualifierName) ||
                "android.support.v4.widget.CursorAdapter".equals(qualifierName) ||
                "android.support.v4.widget.SimpleCursorAdapter".equals(qualifierName) ||
                "android.support.v4.widget.ResourceCursorAdapter".equals(qualifierName);
    }
}
