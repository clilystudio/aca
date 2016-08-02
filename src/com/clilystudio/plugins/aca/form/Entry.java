package com.clilystudio.plugins.aca.form;

import com.clilystudio.plugins.aca.model.SubViewItem;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

public class Entry extends JPanel {

    protected ContentPanel mParent;
    protected SubViewItem mSubViewItem;
    protected OnCheckBoxStateChangedListener mListener;
    // ui
    protected JCheckBox mCheck;
    protected JLabel mType;
    protected JLabel mID;
    protected JCheckBox mEvent;
    protected JTextField mName;
    protected Color mNameDefaultColor;
    protected Color mNameErrorColor = new Color(0x880000);

    public JCheckBox getCheck() {
        return mCheck;
    }

    public void setListener(final OnCheckBoxStateChangedListener onStateChangedListener) {
        this.mListener = onStateChangedListener;
    }

    public Entry(ContentPanel parent, SubViewItem subViewItem) {
        mSubViewItem = subViewItem;
        mParent = parent;

        mCheck = new JCheckBox();
        mCheck.setPreferredSize(new Dimension(40, 26));
        mCheck.setSelected(true);
        mCheck.addChangeListener(new CheckListener());

        mEvent = new JCheckBox();
        mEvent.setPreferredSize(new Dimension(100, 26));

        mType = new JLabel(mSubViewItem.getClassName());
        mType.setPreferredSize(new Dimension(100, 26));

        mID = new JLabel(mSubViewItem.getId());
        mID.setPreferredSize(new Dimension(100, 26));

        mName = new JTextField(mSubViewItem.getFieldName(), 10);
        mNameDefaultColor = mName.getBackground();
        mName.setPreferredSize(new Dimension(100, 26));
        mName.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                // empty
            }

            @Override
            public void focusLost(FocusEvent e) {
                syncElement();
            }
        });

        setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
        setMaximumSize(new Dimension(Short.MAX_VALUE, 54));
        add(mCheck);
        add(Box.createRigidArea(new Dimension(10, 0)));
        add(mType);
        add(Box.createRigidArea(new Dimension(10, 0)));
        add(mID);
        add(Box.createRigidArea(new Dimension(10, 0)));
        add(mEvent);
        add(Box.createRigidArea(new Dimension(10, 0)));
        add(mName);
        add(Box.createHorizontalGlue());

        checkState();
    }

    public void resetFieldName(String prefix, boolean isTrimType) {
        mName.setText(mSubViewItem.getFieldName(prefix, isTrimType));
    }

    public SubViewItem syncElement() {
        mSubViewItem.setSelected(mCheck.isSelected());
        mSubViewItem.setClickEvent(mEvent.isSelected());

        if (mSubViewItem.checkValidity()) {
            mName.setBackground(mNameDefaultColor);
        } else {
            mName.setBackground(mNameErrorColor);
        }

        return mSubViewItem;
    }

    private void checkState() {
        if (mCheck.isSelected()) {
            mType.setEnabled(true);
            mID.setEnabled(true);
            mName.setEnabled(true);
        } else {
            mType.setEnabled(false);
            mID.setEnabled(false);
            mName.setEnabled(false);
        }

        if (mListener != null) {
            mListener.changeState(mCheck.isSelected());
        }
    }

    // classes

    public class CheckListener implements ChangeListener {

        @Override
        public void stateChanged(ChangeEvent event) {
            checkState();
        }
    }

}
