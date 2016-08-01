package com.clilystudio.plugins.aca.action;

import com.clilystudio.plugins.aca.form.EntryList;
import com.clilystudio.plugins.aca.form.ICancelListener;
import com.clilystudio.plugins.aca.form.IConfirmListener;
import com.clilystudio.plugins.aca.model.SubViewItem;
import com.clilystudio.plugins.aca.utils.Definitions;
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
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;

/**
 * Created by ShengGL on 2016/7/29.
 */
public class InitViewsByLayoutIdAction extends BaseGenerateAction implements IConfirmListener, ICancelListener {

    private JFrame mDialog;

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
    public boolean isValidForFile(Project project, Editor editor, PsiFile file) {
        return (super.isValidForFile(project, editor, file) && Utils.getLayoutFileFromCaret(editor, file) != null);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(PlatformDataKeys.PROJECT);
        Editor editor = e.getData(PlatformDataKeys.EDITOR);

        actionPerformedImpl(project, editor);
    }

    @Override
    public void actionPerformedImpl(@NotNull Project project, Editor editor) {
        PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, project);
        PsiFile layout = Utils.getLayoutFileFromCaret(editor, file);

        if (layout == null) {
            // no layout found
            Utils.showErrorNotification(project, "No layout found");
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
                if (Definitions.adapters.contains(element.getQualifiedName())) {
                    createHolder = true;
                }
            }
        }

        EntryList panel = new EntryList(project, editor, subViewItems, createHolder, this, this);

        mDialog = new JFrame();
        mDialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        mDialog.getRootPane().setDefaultButton(panel.getConfirmButton());
        mDialog.getContentPane().add(panel);
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
            new InjectWriter(file, getTargetClass(editor, file), "Generate Injections", subViewItems, layout.getName(), fieldNamePrefix, createHolder).execute();
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

    private class InjectWriter extends WriteCommandAction.Simple {

        protected PsiFile mFile;
        protected Project mProject;
        protected PsiClass mClass;
        protected ArrayList<SubViewItem> mSubViewItems;
        protected PsiElementFactory mFactory;
        protected String mLayoutFileName;
        protected String mFieldNamePrefix;
        protected boolean mCreateHolder;

        InjectWriter(PsiFile file, PsiClass clazz, String command, ArrayList<SubViewItem> subViewItems, String layoutFileName, String fieldNamePrefix, boolean createHolder) {
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
                if (Utils.getInjectCount(mSubViewItems) > 0) {
                    generateFields();
                }
                generateInjects();
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
            StringBuilder method = new StringBuilder();
            if (Utils.getClickCount(mSubViewItems) == 1) {
                method.append("@butterknife.OnClick(");
                for (SubViewItem subViewItem : mSubViewItems) {
                    if (subViewItem.hasClickEvent()) {
                        method.append(subViewItem.getFullID() + ")");
                    }
                }
                method.append("public void onClick() {}");
            } else {
                method.append("@butterknife.OnClick({");
                int currentCount = 0;
                for (SubViewItem subViewItem : mSubViewItems) {
                    if (subViewItem.hasClickEvent()) {
                        currentCount++;
                        if (currentCount == Utils.getClickCount(mSubViewItems)) {
                            method.append(subViewItem.getFullID() + "})");
                        } else {
                            method.append(subViewItem.getFullID() + ",");
                        }
                    }
                }
                method.append("public void onClick(android.view.View view) {switch (view.getId()){");
                for (SubViewItem subViewItem : mSubViewItems) {
                    if (subViewItem.hasClickEvent()) {
                        method.append("case " + subViewItem.getFullID() + ": break;");
                    }
                }
                method.append("}}");
            }

            mClass.add(mFactory.createMethodFromText(method.toString(), mClass));

        }

        /**
         * Create ViewHolder for adapters with injections
         */
        void generateAdapter() {
            // view holder class
            StringBuilder holderBuilder = new StringBuilder();
            holderBuilder.append(Utils.getViewHolderClassName());
            holderBuilder.append("(android.view.View rootView) {");
            holderBuilder.append("}");

            PsiClass viewHolder = mFactory.createClassFromText(holderBuilder.toString(), mClass);
            viewHolder.setName(Utils.getViewHolderClassName());

            PsiMethod constuctMethod = viewHolder.findMethodsByName("Utils.getViewHolderClassName()", false)[0];

            // add injections into view holder
            for (SubViewItem subViewItem : mSubViewItems) {
                if (subViewItem.isSelected()) {
                    String rPrefix;
                    if (subViewItem.isAndroidNS()) {
                        rPrefix = "android.R.id.";
                    } else {
                        rPrefix = "R.id.";
                    }

                    StringBuilder injection = new StringBuilder();
//                    injection.append('@');
//                    injection.append(butterKnife.getFieldAnnotationCanonicalName());
//                    injection.append('(');
//                    injection.append(rPrefix);
//                    injection.append(subViewItem.id);
//                    injection.append(") ");
                    if (subViewItem.getClassFull() != null && subViewItem.getClassFull().length() > 0) {
                        // custom package+class
                        injection.append(subViewItem.getClassFull());
                    } else if (Definitions.paths.containsKey(subViewItem.getClassName())) {
                        // listed class
                        injection.append(Definitions.paths.get(subViewItem.getClassName()));
                    } else {
                        // android.widget
                        injection.append("android.widget.");
                        injection.append(subViewItem.getClassName());
                    }
                    injection.append(" ");
                    injection.append(subViewItem.getFieldName());
                    injection.append(";");

                    viewHolder.add(mFactory.createFieldFromText(injection.toString(), mClass));
                    constuctMethod.add(mFactory.createStatementFromText(injection.toString(), viewHolder));
                }
            }

            mClass.add(viewHolder);

            // add view holder's comment
            StringBuilder comment = new StringBuilder();
            comment.append("/**\n");
            comment.append(" * This class contains all butterknife-injected Views & Layouts from layout file '");
            comment.append(mLayoutFileName);
            comment.append("'\n");
            comment.append("* for easy to all layout elements.\n");
            comment.append(" *\n");
            comment.append(" * @author\tButterKnifeZelezny, plugin for Android Studio by Avast Developers (http://github.com/avast)\n");
            comment.append("*/");

//        mClass.addBefore(mFactory.createCommentFromText(comment.toString(), mClass), mClass.findInnerClassByName(Utils.getViewHolderClassName(), true));
            mClass.addBefore(mFactory.createKeyword("static", mClass), mClass.findInnerClassByName(Utils.getViewHolderClassName(), true));
        }

        /**
         * Create fields for injections inside main class
         */
        protected void generateFields() {
            // add injections into main class
            for (SubViewItem subViewItem : mSubViewItems) {
                if (subViewItem.isSelected()) {
                    StringBuilder injection = new StringBuilder();
                    if (subViewItem.getClassFull() != null && subViewItem.getClassFull().length() > 0) {
                        // custom package+class
                        injection.append(subViewItem.getClassFull());
                    } else if (Definitions.paths.containsKey(subViewItem.getClassName())) {
                        // listed class
                        injection.append(Definitions.paths.get(subViewItem.getClassName()));
                    } else {
                        // android.widget
                        injection.append("android.widget.");
                        injection.append(subViewItem.getClassName());
                    }
                    injection.append(" ");
                    injection.append(subViewItem.getFieldName());
                    injection.append(";");

                    mClass.add(mFactory.createFieldFromText(injection.toString(), mClass));
                }
            }
        }

        protected void generateInjects() {
            PsiClass activityClass = JavaPsiFacade.getInstance(mProject).findClass(
                    "android.app.Activity", new EverythingGlobalScope(mProject));
            PsiClass fragmentClass = JavaPsiFacade.getInstance(mProject).findClass(
                    "android.app.Fragment", new EverythingGlobalScope(mProject));
            PsiClass supportFragmentClass = JavaPsiFacade.getInstance(mProject).findClass(
                    "android.support.v4.app.Fragment", new EverythingGlobalScope(mProject));

            // Check for Activity class
            if (activityClass != null && mClass.isInheritor(activityClass, true)) {
                generateActivityBind();
                // Check for Fragment class
            } else if ((fragmentClass != null && mClass.isInheritor(fragmentClass, true)) || (supportFragmentClass != null && mClass.isInheritor(supportFragmentClass, true))) {
                generateFragmentBindAndUnbind();
            }
        }

        private void generateActivityBind() {
            if (mClass.findMethodsByName("onCreate", false).length == 0) {
                // Add an empty stub of onCreate()
                StringBuilder method = new StringBuilder();
                method.append("@Override protected void onCreate(android.os.Bundle savedInstanceState) {\n");
                method.append("super.onCreate(savedInstanceState);\n");
                method.append("\t// TODO: add setContentView(...) invocation\n");
//                method.append(butterKnife.getCanonicalBindStatement());
                method.append("(this);\n");
                method.append("}");

                mClass.add(mFactory.createMethodFromText(method.toString(), mClass));
            } else {
                PsiMethod onCreate = mClass.findMethodsByName("onCreate", false)[0];
                for (PsiStatement statement : onCreate.getBody().getStatements()) {
                    // Search for setContentView()
                    if (statement.getFirstChild() instanceof PsiMethodCallExpression) {
                        PsiReferenceExpression methodExpression
                                = ((PsiMethodCallExpression) statement.getFirstChild())
                                .getMethodExpression();
                        // Insert ButterKnife.inject()/ButterKnife.bind() after setContentView()
                        if (methodExpression.getText().equals("setContentView")) {
                            onCreate.getBody().addAfter(mFactory.createStatementFromText(
                                    "initViews();", mClass), statement);
                            break;
                        }
                    }
                }
            }
        }

        private void generateFragmentBindAndUnbind() {
            boolean generateUnbinder = false;
            String unbinderName = null;

            // onCreateView() doesn't exist, let's create it
            if (mClass.findMethodsByName("onCreateView", false).length == 0) {
                // Add an empty stub of onCreateView()
                StringBuilder method = new StringBuilder();
                method.append("@Override public View onCreateView(android.view.LayoutInflater inflater, android.view.ViewGroup container, android.os.Bundle savedInstanceState) {\n");
                method.append("\t// TODO: inflate a fragment view\n");
                method.append("android.view.View rootView = super.onCreateView(inflater, container, savedInstanceState);\n");
//                if (butterKnife.isUsingUnbinder()) {
//                    // generate new unbinder
//                    generateUnbinder = true;
//                     unbinderName = getNameForUnbinder(butterKnife);
//                    method.append(unbinderName);
//                    method.append(" = ");
//                }
//                method.append(butterKnife.getCanonicalBindStatement());
                method.append("(this, rootView);\n");
                method.append("return rootView;\n");
                method.append("}");

                mClass.add(mFactory.createMethodFromText(method.toString(), mClass));
            } else {
                // onCreateView() exists, let's update it with an inject/bind statement
                PsiMethod onCreateView = mClass.findMethodsByName("onCreateView", false)[0];
                for (PsiStatement statement : onCreateView.getBody().getStatements()) {
                    if (statement instanceof PsiReturnStatement) {
                        String returnValue = ((PsiReturnStatement) statement).getReturnValue().getText();
                        // there's layout inflatiion
                        if (returnValue.contains("R.layout")) {
                            onCreateView.getBody().addBefore(mFactory.createStatementFromText("android.view.View view = " + returnValue + ";", mClass), statement);
                            StringBuilder bindText = new StringBuilder();
//                            if (butterKnife.isUsingUnbinder()) {
//                                // generate new unbinder
//                                generateUnbinder = true;
//                                unbinderName = getNameForUnbinder(butterKnife);
//                                bindText.append(unbinderName);
//                                bindText.append(" = ");
//                            }
//                            bindText.append(butterKnife.getCanonicalBindStatement());
                            bindText.append("(this, view);");
                            PsiStatement bindStatement = mFactory.createStatementFromText(bindText.toString(), mClass);
                            onCreateView.getBody().addBefore(bindStatement, statement);
                            statement.replace(mFactory.createStatementFromText("return view;", mClass));
                        } else {
                            // Insert ButterKnife.inject()/ButterKnife.bind() before returning a view for a fragment
                            StringBuilder bindText = new StringBuilder();
//                            if (butterKnife.isUsingUnbinder()) {
//                                // generate new unbinder
//                                generateUnbinder = true;
//                                unbinderName = getNameForUnbinder(butterKnife);
//                                bindText.append(unbinderName);
//                                bindText.append(" = ");
//                            }
//                            bindText.append(butterKnife.getCanonicalBindStatement());
                            bindText.append("(this, ");
                            bindText.append(returnValue);
                            bindText.append(");");
                            PsiStatement bindStatement = mFactory.createStatementFromText(bindText.toString(), mClass);
                            onCreateView.getBody().addBefore(bindStatement, statement);
                        }
                        break;
                    }
                }
            }
        }
    }
}
