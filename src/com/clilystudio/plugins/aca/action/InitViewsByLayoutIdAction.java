package com.clilystudio.plugins.aca.action;

import com.clilystudio.plugins.aca.form.ContentPanel;
import com.clilystudio.plugins.aca.model.SubViewItem;
import com.clilystudio.plugins.aca.utils.Utils;
import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.codeInsight.generation.actions.BaseGenerateAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.EverythingGlobalScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;

public class InitViewsByLayoutIdAction extends BaseGenerateAction implements ContentPanel.IConfirmListener, ContentPanel.ICancelListener {

    private JFrame mDialog;

    private PsiElement mElement;

    @SuppressWarnings("unused")
    public InitViewsByLayoutIdAction() {
        super(null);
    }

    @SuppressWarnings("unused")
    public InitViewsByLayoutIdAction(CodeInsightActionHandler handler) {
        super(handler);
    }

    @Override
    protected boolean isValidForClass(final PsiClass targetClass) {
        return (super.isValidForClass(targetClass) && Utils.findAndroidSDK() != null && !(targetClass instanceof PsiAnonymousClass));
    }

    @Override
    public boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        return (super.isValidForFile(project, editor, file) && Utils.getLayoutFileFromCaret(editor, file) != null);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(PlatformDataKeys.PROJECT);
        if (project == null) {
            return;
        }

        Editor editor = e.getData(PlatformDataKeys.EDITOR);
        actionPerformedImpl(project, editor);
    }

    @Override
    public void actionPerformedImpl(@NotNull Project project, Editor editor) {
        PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, project);
        if (file == null) {
            Utils.showErrorNotification(project, "No file found");
            return;
        }
        PsiFile layout = Utils.getLayoutFileFromCaret(editor, file);
        if (layout == null) {
            Utils.showErrorNotification(project, "No layout found");
            return;
        }
        int offset = editor.getCaretModel().getOffset();
        mElement = file.findElementAt(offset);
        if (mElement == null) {
            Utils.showErrorNotification(project, "No element found");
            return;
        }

        ArrayList<SubViewItem> subViewItems = Utils.getIDsFromLayout(layout);
        if (!subViewItems.isEmpty()) {
            showDialog(project, editor, subViewItems);
        } else {
            Utils.showErrorNotification(project, "No IDs found in layout");
        }
    }


    private void showDialog(Project project, Editor editor, ArrayList<SubViewItem> subViewItems) {
        PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, project);
        if (file == null) {
            return;
        }
        PsiClass clazz = getTargetClass(editor, file);
        if (clazz == null) {
            return;
        }

        // get parent classes and check if it's an adapter
        boolean createHolder = false;
        PsiReferenceList list = clazz.getExtendsList();
        if (list != null) {
            for (PsiJavaCodeReferenceElement element : list.getReferenceElements()) {
                if (Utils.isViewHolder(element.getQualifiedName())) {
                    createHolder = true;
                    break;
                }
            }
        }

        ContentPanel panel = new ContentPanel(project, editor, subViewItems, createHolder, this, this);

        mDialog = new JFrame();
        mDialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        mDialog.getRootPane().setDefaultButton(panel.getConfirmButton());
        mDialog.getContentPane().add(panel);
        mDialog.setTitle("Android Code Assistant");
        mDialog.pack();
        mDialog.setLocationRelativeTo(null);
        mDialog.setVisible(true);
    }

    @Override
    public void onCancel() {
        closeDialog();
    }

    @Override
    public void onConfirm(Project project, Editor editor, ArrayList<SubViewItem> subViewItems, String fieldNamePrefix, boolean createHolder) {
        PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, project);
        if (file == null) {
            return;
        }
        PsiFile layout = Utils.getLayoutFileFromCaret(editor, file);

        closeDialog();

        if (Utils.getInjectCount(subViewItems) > 0 || Utils.getClickCount(subViewItems) > 0) {
            // generate injections
            new CodeGenerator(file, getTargetClass(editor, file), "Generate Injections", subViewItems, layout.getName(), fieldNamePrefix, createHolder).execute();
        } else { // just notify user about no element selected
            Utils.showInfoNotification(project, "No injection was selected");
        }
    }

    private void closeDialog() {
        if (mDialog == null) {
            return;
        }
        mDialog.setVisible(false);
        mDialog.dispose();
    }

    private class CodeGenerator extends WriteCommandAction.Simple {
        PsiFile mFile;
        Project mProject;
        PsiClass mClass;
        ArrayList<SubViewItem> mSubViewItems;
        PsiElementFactory mFactory;
        String mLayoutFileName;
        String mFieldNamePrefix;
        boolean mCreateHolder;

        CodeGenerator(PsiFile file, PsiClass clazz, String command, ArrayList<SubViewItem> subViewItems, String layoutFileName, String fieldNamePrefix, boolean createHolder) {
            super(clazz.getProject(), command);

            mFile = file;
            mProject = clazz.getProject();
            mClass = clazz;
            mSubViewItems = subViewItems;
            mFactory = JavaPsiFacade.getElementFactory(mProject);
            mLayoutFileName = layoutFileName;
            mFieldNamePrefix = fieldNamePrefix;
            mCreateHolder = createHolder;
        }

        @Override
        public void run() throws Throwable {
            if (mCreateHolder) {
                generateAdapter();
            } else {
                generateInitViews();
                if (Utils.getInjectCount(mSubViewItems) > 0) {
                    generateFields();
                }
                if (Utils.getClickCount(mSubViewItems) > 0) {
                    generateClick();
                }
                Utils.showInfoNotification(mProject, String.valueOf(Utils.getInjectCount(mSubViewItems)) + " injections and " + String.valueOf(Utils.getClickCount(mSubViewItems)) + " onClick added to " + mFile.getName());
            }

            // reformat class
            JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(mProject);
            styleManager.optimizeImports(mFile);
            styleManager.shortenClassReferences(mClass);
            new ReformatCodeProcessor(mProject, mClass.getContainingFile(), null, false).runWithoutProgress();
        }

        void generateClick() {
            generateImplements();
            StringBuilder method = new StringBuilder();
            method.append("@Override public void onClick(android.view.View v) {switch (v.getId()){");
            mSubViewItems.stream().filter(SubViewItem::hasClickEvent).forEach(subViewItem -> method.append("case ").append(subViewItem.getFullID()).append(": break;"));
            method.append("}}");
            mClass.add(mFactory.createMethodFromText(method.toString(), mClass));


            // add onClick's comment
            String comment = "/**\n" +
                    " * @author\tAndroid Code Assistant, plugin for Android Studio by Clily Studio (https://github.com/clilystudio)\n" +
                    "*/";

            mClass.addBefore(mFactory.createCommentFromText(comment, mClass), mClass.findMethodsByName("onClick", false)[0]);
        }

        void generateImplements() {
            final PsiClassType[] implementsListTypes = mClass.getImplementsListTypes();
            final String implementsType = "android.view.View.OnClickListener";

            for (PsiClassType implementsListType : implementsListTypes) {
                PsiClass resolved = implementsListType.resolve();
                // Already implements View.OnClickListener, no need to add it
                if (resolved != null && implementsType.equals(resolved.getQualifiedName())) {
                    return;
                }
            }

            PsiJavaCodeReferenceElement implementsReference = mFactory.createReferenceFromText(implementsType, mClass);
            PsiReferenceList implementsList = mClass.getImplementsList();

            if (implementsList != null) {
                implementsList.add(implementsReference);
            }
        }

        /**
         * Create ViewHolder for adapters with injections
         */
        void generateAdapter() {
            PsiClass viewHolder = mFactory.createClassFromText(Utils.VIEWHOLDER_CLASS_NAME + "(android.view.View rootView) {}", mClass);
            viewHolder.setName(Utils.VIEWHOLDER_CLASS_NAME);

            PsiMethod constructMethod = viewHolder.findMethodsByName("Utils.getViewHolderClassName()", false)[0];

            // custom package+class
            mSubViewItems.stream().filter(SubViewItem::isSelected).forEach(subViewItem -> {
                StringBuilder injection = new StringBuilder();
                if (subViewItem.getClassFull() != null && subViewItem.getClassFull().length() > 0) {
                    // custom package+class
                    injection.append(subViewItem.getClassFull());
                } else {
                    injection.append(Utils.getRealClassName(subViewItem.getClassName()));
                }
                injection.append(" ");
                injection.append(subViewItem.getFieldName());
                injection.append(";");

                viewHolder.add(mFactory.createFieldFromText(injection.toString(), mClass));
                constructMethod.add(mFactory.createStatementFromText(injection.toString(), viewHolder));
            });

            mClass.add(viewHolder);
            mClass.addBefore(mFactory.createKeyword("static", mClass), mClass.findInnerClassByName(Utils.VIEWHOLDER_CLASS_NAME, true));
        }

        /**
         * Create fields for injections inside main class
         */
        void generateFields() {
            // custom package+class
            mSubViewItems.stream().filter(SubViewItem::isSelected).forEach(subViewItem -> {
                StringBuilder injection = new StringBuilder();
                injection.append("private ");
                if (subViewItem.getClassFull() != null && subViewItem.getClassFull().length() > 0) {
                    // custom package+class
                    injection.append(subViewItem.getClassFull());
                } else {
                    injection.append(Utils.getRealClassName(subViewItem.getClassName()));
                }
                injection.append(" ");
                injection.append(subViewItem.getFieldName());
                injection.append(";");

                mClass.add(mFactory.createFieldFromText(injection.toString(), mClass));
            });
        }

        void generateInitViews() {
            PsiClass activityClass = JavaPsiFacade.getInstance(mProject).findClass(
                    "android.app.Activity", new EverythingGlobalScope(mProject));
            PsiClass fragmentClass = JavaPsiFacade.getInstance(mProject).findClass(
                    "android.app.Fragment", new EverythingGlobalScope(mProject));
            PsiClass supportFragmentClass = JavaPsiFacade.getInstance(mProject).findClass(
                    "android.support.v4.app.Fragment", new EverythingGlobalScope(mProject));

            if (activityClass != null && mClass.isInheritor(activityClass, true)) {
                generateActivityInit();
            } else if ((fragmentClass != null && mClass.isInheritor(fragmentClass, true)) || (supportFragmentClass != null && mClass.isInheritor(supportFragmentClass, true))) {
                generateFragmentInit();
            }
        }

        private void generateActivityInit() {
            PsiMethod method = PsiTreeUtil.getParentOfType(mElement, PsiMethod.class);
            if (method != null && method.getBody() != null) {
                for (PsiStatement statement : method.getBody().getStatements()) {
                    // Search for setContentView()
                    if (statement.getFirstChild() instanceof PsiMethodCallExpression) {
                        PsiReferenceExpression methodExpression = ((PsiMethodCallExpression) statement.getFirstChild()).getMethodExpression();
                        if (methodExpression.getText().equals("setContentView")) {
                            method.getBody().addAfter(mFactory.createStatementFromText("initViews();", mClass), statement);
                            break;
                        }
                    }
                }
            }
            StringBuilder initMethod = new StringBuilder();
            initMethod.append("private void initViews() {");
            mSubViewItems.stream().filter(SubViewItem::isSelected).forEach(subViewItem -> {
                initMethod.append(subViewItem.getFieldName()).append(" = ")
                        .append("(")
                        .append(subViewItem.getClassName())
                        .append(")")
                        .append("findViewById(")
                        .append(subViewItem.getFullID())
                        .append(");");
                if (subViewItem.hasClickEvent()) {
                    initMethod.append(subViewItem.getFieldName())
                            .append(".setOnClickListener(this);");
                }
            });
            initMethod.append("}");
            mClass.add(mFactory.createMethodFromText(initMethod.toString(), mClass));
        }

        private void generateFragmentInit() {
            PsiMethod method = PsiTreeUtil.getParentOfType(mElement, PsiMethod.class);
            if (method != null && method.getBody() != null) {
                for (PsiStatement statement : method.getBody().getStatements()) {
                    if (statement instanceof PsiReturnStatement) {
                        PsiExpression returnValue = ((PsiReturnStatement) statement).getReturnValue();
                        if (returnValue != null) {
                            String returnText = returnValue.getText();
                            if (returnText.contains("R.layout")) {
                                method.getBody().addBefore(mFactory.createStatementFromText("android.view.View view = " + returnText + ";", mClass), statement);
                                method.getBody().addBefore(mFactory.createStatementFromText("initViews(view);", mClass), statement);
                                statement.replace(mFactory.createStatementFromText("return view;", mClass));
                            } else {
                                method.getBody().addBefore(mFactory.createStatementFromText("initViews(" + returnText + ");", mClass), statement);
                            }
                            break;
                        }
                    }
                }
            }
            StringBuilder initMethod = new StringBuilder();
            initMethod.append("private void initViews(android.view.View rootView) {");
            mSubViewItems.stream().filter(SubViewItem::isSelected).forEach(subViewItem -> {
                initMethod.append(subViewItem.getFieldName()).append(" = ")
                        .append("(")
                        .append(subViewItem.getClassName())
                        .append(")")
                        .append("rootView.findViewById(")
                        .append(subViewItem.getFullID())
                        .append(");");
                if (subViewItem.hasClickEvent()) {
                    initMethod.append(subViewItem.getFieldName())
                            .append(".setOnClickListener(this);");
                }
            });
            initMethod.append("}");
            mClass.add(mFactory.createMethodFromText(initMethod.toString(), mClass));
        }
    }
}

