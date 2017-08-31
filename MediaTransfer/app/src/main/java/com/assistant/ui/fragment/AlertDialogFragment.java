package com.assistant.ui.fragment;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import com.assistant.R;

public class AlertDialogFragment extends DialogFragment {
    public interface AlertDialogClickListener {
        void onPositiveBtnClicked(int dialogId, View view);
        void onNegativeBtnClicked(int dialogId);
        // used for layoutId not 0
        void onDialogViewCreated(int dialogId, View view);
    }

    private static final String DIALOG_ID_KEY = "dialogId";
    private static final String TITLE_KEY = "title";
    private static final String ICON_KEY = "msg";
    private static final String LAYOUT_KEY = "layout";

    private Context mContext;

    private boolean mDialogBtnClicked;

    public static AlertDialogFragment newInstance(int dlgId, int titleId, int iconId, int layoutId) {
        AlertDialogFragment frag = new AlertDialogFragment();
        Bundle args = new Bundle();
        args.putInt(DIALOG_ID_KEY, dlgId);
        args.putInt(TITLE_KEY, titleId);
        args.putInt(ICON_KEY, iconId);
        args.putInt(LAYOUT_KEY, layoutId);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        mContext = context;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final int dialogId = getArguments().getInt(DIALOG_ID_KEY);
        int title = getArguments().getInt(TITLE_KEY);
        int icon = getArguments().getInt(ICON_KEY);
        int layoutId = getArguments().getInt(LAYOUT_KEY);

        mDialogBtnClicked = false;

        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(getActivity());

        if (title > 0) {
            dialogBuilder.setTitle(title);
        }

        if (icon > 0) {
            dialogBuilder.setIcon(icon);
        }

        final View view;
        if (layoutId > 0) {
            view = LayoutInflater.from(mContext).inflate(layoutId, null);
            dialogBuilder.setView(view);

            ((AlertDialogClickListener) getActivity()).onDialogViewCreated(dialogId, view);
        } else {
            view = null;
        }

        dialogBuilder.setPositiveButton(R.string.alert_dialog_ok,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        mDialogBtnClicked = true;
                        ((AlertDialogClickListener) getActivity()).onPositiveBtnClicked(dialogId, view);
                    }
                })
                .setNegativeButton(R.string.alert_dialog_cancel,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                mDialogBtnClicked = true;
                                ((AlertDialogClickListener) getActivity()).onNegativeBtnClicked(dialogId);
                            }
                        }
                );

        return dialogBuilder.create();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);

        if (!mDialogBtnClicked) {
            ((AlertDialogClickListener) getActivity()).onNegativeBtnClicked(getArguments().getInt(DIALOG_ID_KEY));
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Dialog dialog = getDialog();

        if (dialog != null) {
            dialog.dismiss();
        }
    }
}
