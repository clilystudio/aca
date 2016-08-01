package com.clilystudio.plugins.aca.form;

import com.clilystudio.plugins.aca.model.SubViewItem;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;

import java.util.ArrayList;

public interface IConfirmListener {

    public void onConfirm(Project project, Editor editor, ArrayList<SubViewItem> subViewItems, String fieldNamePrefix, boolean createHolder);
}
