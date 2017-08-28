package com.assistant.ui.fragment;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.assistant.R;
import com.assistant.connection.Connection;
import com.assistant.connection.ConnectionManager;
import com.assistant.utils.Log;

public class ChattingFragment extends Fragment {
    private static final String TAG = "ChattingFragment";

    private final static String INTENT_EXTRA_CONNECTION_INDEX = "extra_connection_index";

    private ListView mChattingListView;

    private LayoutInflater mLayoutInflater = null;

    private Context mContext;

    private int mConnId = -1;
    private ConnectionManager mConnManager;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mContext = context;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.chatting_layout, container, false);
    }

    @Override
    public void onViewCreated(final View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 向 ViewTreeObserver 注册方法，以获取控件尺寸
        /*ViewTreeObserver vto = view.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            public void onGlobalLayout() {
                int h = view.getHeight();
                Log.d(TAG, "Height=" + h); // 得到正确结果

                // 成功调用一次后，移除 Hook 方法，防止被反复调用
                // removeGlobalOnLayoutListener() 方法在 API 16 后不再使用
                // 使用新方法 removeOnGlobalLayoutListener() 代替
                view.getViewTreeObserver().removeGlobalOnLayoutListener(this);
            }
        });*/

        Log.d(this, "onViewCreated");

        mChattingListView = (ListView)view.findViewById(R.id.chatting_list_view);

        mChattingListView.setAdapter(new ChattingAdapter());
    }

    private class ChattingAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return 10;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup container) {
            Log.d(this, "getView, position:" + position);
            ViewHolder viewHolder;
            if (convertView == null) {
                convertView = mLayoutInflater.inflate(R.layout.chatting_item,
                        container,
                        false);

                viewHolder = new ViewHolder(convertView);
                convertView.setTag(viewHolder);

            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            viewHolder.owner.setText("Index:" + String.valueOf(position));

            String msg = getItem(position) + "\ncontent\n";

            if (position%2 == 0) {
                viewHolder.textMsgLeft.setText(msg);
                viewHolder.textMsgLeft.setVisibility(View.VISIBLE);

                viewHolder.textMsgRight.setVisibility(View.GONE);
            } else {
                viewHolder.textMsgRight.setText(msg);
                viewHolder.textMsgRight.setVisibility(View.VISIBLE);

                viewHolder.textMsgLeft.setVisibility(View.GONE);
                viewHolder.owner.setVisibility(View.INVISIBLE);
            }
            return convertView;
        }

        public String getItem(int position) {
            return String.valueOf(position);
        }
    }

    class ViewHolder {
        TextView owner;
        TextView time;
        TextView textMsgLeft;
        TextView textMsgRight;

        ViewHolder(View parent) {
            owner = (TextView) parent.findViewById(R.id.chat_owner_name);
            time = (TextView) parent.findViewById(R.id.chat_msg_time);
            textMsgLeft = (TextView) parent.findViewById(R.id.chat_message_left);
            textMsgRight = (TextView) parent.findViewById(R.id.chat_message_right);
        }
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mLayoutInflater = (LayoutInflater) getActivity()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        Activity activity = getActivity();
        Intent intent = activity.getIntent();

        mConnId = intent.getIntExtra(INTENT_EXTRA_CONNECTION_INDEX, -1);
        mConnManager = ConnectionManager.getInstance(getActivity().getApplicationContext());


    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }
}
