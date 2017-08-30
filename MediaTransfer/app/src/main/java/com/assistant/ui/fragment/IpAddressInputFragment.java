package com.assistant.ui.fragment;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import com.assistant.R;
import com.assistant.utils.Log;
import com.assistant.utils.Utils;

public class IpAddressInputFragment extends DialogFragment {
    private static final String TAG = "IpAddressInputFragment";

    private static final String IP_KEY = "ip_key";

    private Context mContext;
    /**
     * Create a new instance of IpAddressInputFragment, providing
     */
    public static IpAddressInputFragment newInstance(String initIp) {
        IpAddressInputFragment f = new IpAddressInputFragment();

        Bundle args = new Bundle();
        args.putString(IP_KEY, initIp);
        f.setArguments(args);

        return f;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        mContext = context;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Pick a style based on the num.
        int style = DialogFragment.STYLE_NORMAL, theme = 0;
        setStyle(style, theme);
    }


    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(getActivity());

        dialogBuilder.setPositiveButton(R.string.alert_dialog_ok,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    }
                })
                .setNegativeButton(R.string.alert_dialog_cancel,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                            }
                        }
                );

        View view = LayoutInflater.from(mContext).inflate(R.layout.ip_input_dialog_layout, null);
        dialogBuilder.setView(view);

        return dialogBuilder.create();
    }
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.ip_input_dialog_layout, container, false);
        final EditText ipEditText = (EditText)v.findViewById(R.id.ip_address_input_edittext);

        ipEditText.setFilters(new InputFilter[] {
                new InputFilter() {
                    @Override
                    public CharSequence filter(CharSequence source, int start, int end,
                                               Spanned dest, int dstart, int dend) {
                        String result = String.valueOf(dest.subSequence(0, dstart))
                                + source.subSequence(start, end)
                                + dest.subSequence(dend, dest.length());

                        if (!Utils.isIpPattern(result)) {
                            Log.d(TAG, "input string not match ip pattern, result:" + result +
                                    ", source:" + source +
                                    ", start:" + start +
                                    ", end:" + end +
                                    ", dest:" + dest.toString() +
                                    ", dstart:" + dstart +
                                    ", dend:" + dend);

                            return "";
                        }

                        return null;
                    }
                }

        });

        return v;
    }
}
