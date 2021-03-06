package pjsipapp.com.firebird;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.Manifest;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import org.pjsip.pjsua2.AccountConfig;
import org.pjsip.pjsua2.AuthCredInfo;
import org.pjsip.pjsua2.AuthCredInfoVector;
import org.pjsip.pjsua2.BuddyConfig;
import org.pjsip.pjsua2.CallInfo;
import org.pjsip.pjsua2.CallOpParam;
import org.pjsip.pjsua2.StringVector;
import org.pjsip.pjsua2.pjsip_inv_state;
import org.pjsip.pjsua2.pjsip_status_code;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import pjsipapp.com.firebird.permissiongrant.PermissionsActivity;
import pjsipapp.com.firebird.permissiongrant.PermissionsChecker;
import pjsipapp.com.firebird.pjsip.PjSipAccount;
import pjsipapp.com.firebird.pjsip.PjSipBuddy;
import pjsipapp.com.firebird.pjsip.PjSipCall;
import pjsipapp.com.firebird.pjsip.PjSipController;
import pjsipapp.com.firebird.utils.Sort;

public class MainActivity extends AppCompatActivity
        implements Handler.Callback, PjSipController.PjSipListener {
    private static final String TAG = "MainActivity";

    // permission variable
    private PermissionsChecker mPermissionsChecker; // 权限检测器

    private static final int PERMISSION_REQUEST_CODE = 0; // 请求码

    // 所需的全部权限
    static final String[] PERMISSIONS = new String[] {
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
    };


    private PjSipController mPjSipController;
    public static PjSipCall currentCall = null;
    public static PjSipAccount account = null;
    public static AccountConfig accCfg = null;

    private ListView buddyListView;
    private SimpleAdapter buddyListAdapter;
    private int buddyListSelectedIdx = -1;
    ArrayList<Map<String, String>> buddyList;
    private String lastRegStatus = "";

    private LinearLayout mButtonLayout;
    private ImageButton mCallButton;
    private ImageButton mAddBuddyButton;
    private ImageButton mEditBuddyButton;
    private ImageButton mDelBuddyButton;

    private final Handler handler = new Handler(this);
    public class MSG_TYPE {
        public final static int INCOMING_CALL = 1;
        public final static int CALL_STATE = 2;
        public final static int REG_STATE = 3;
        public final static int BUDDY_STATE = 4;
        public final static int CALL_MEDIA_STATE = 5;
    }

    private HashMap<String, String> putData(String uri, String status) {
        HashMap<String, String> item = new HashMap<String, String>();
        item.put("uri", uri);
        item.put("status", status);
        return item;
    }

    private void showCallActivity() {
        Intent intent = new Intent(this, CallActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // permission grant
        mPermissionsChecker = new PermissionsChecker(this);

        mPjSipController = PjSipController.getInstance(getApplicationContext());

        if (mPjSipController.getAccountList().size() == 0) {
            Log.d(TAG, "No account info existed! create temporary one.");
            accCfg = new AccountConfig();
            accCfg.setIdUri("sip:localhost");
            accCfg.getNatConfig().setIceEnabled(true);
            accCfg.getVideoConfig().setAutoTransmitOutgoing(true);
            accCfg.getVideoConfig().setAutoShowIncoming(true);
            account = mPjSipController.addAccount(accCfg);
        } else {
            Log.d(TAG, "there is at least one account existed!");
            account = mPjSipController.getAccountList().get(0);
            accCfg = account.cfg;
        }

        buddyList = new ArrayList<Map<String, String>>();
        for (int i = 0; i < account.buddyList.size(); i++) {
            buddyList.add(putData(account.buddyList.get(i).cfg.getUri(),
                    account.buddyList.get(i).getStatusText()));
        }

        String[] from = { "uri", "status" };
        int[] to = { android.R.id.text1, android.R.id.text2 };
        buddyListAdapter = new SimpleAdapter(
                this, buddyList,
                android.R.layout.simple_list_item_2,
                from, to);

        buddyListView = (ListView) findViewById(R.id.listViewBuddy);;
        buddyListView.setAdapter(buddyListAdapter);
        buddyListView.setOnItemClickListener(
                new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent,
                                            final View view,
                                            int position, long id) {
                        view.setSelected(true);
                        buddyListSelectedIdx = position;
                    }
                }
        );

        mButtonLayout = (LinearLayout)findViewById(R.id.button_panel);
        mCallButton = (ImageButton)findViewById(R.id.buttonCall);
        mAddBuddyButton = (ImageButton)findViewById(R.id.buttonAddBuddy);
        mEditBuddyButton = (ImageButton)findViewById(R.id.buttonEditBuddy);
        mDelBuddyButton = (ImageButton)findViewById(R.id.buttonDelBuddy);

        Animation anim =
                AnimationUtils.loadAnimation(this, R.anim.activity_enter_button_panel_anim);
        mButtonLayout.startAnimation(anim);

        anim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {

            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });

        quickSort();
    }

    private void quickSort() {
        int[] data = new int[10];

        String log = "";

        for(int i = 0; i < 10; i ++) {
            data[i] = (int)(Math.random() * 100) % 31;

            log += data[i] + " ";
        }

        Log.d(TAG, log);

        Sort.quickSort(data, 0, 10);

        log = "";
        for(int i = 0; i < 10; i ++) {
            log += data[i] + " ";
        }

        Log.d(TAG, log);
    }

    public static <E> Set<E> union(Set<E> s1, Set<E> s2) {
        Set<E> result = new HashSet<E>(s1);
        result.addAll(s2);
        return result;
    }


    @Override
    protected void onResume() {
        super.onResume();

        // permission grant
        if (mPermissionsChecker.lacksPermissions(PERMISSIONS)) {
            startPermissionsActivity();
        }
    }

    private void startPermissionsActivity() {
        PermissionsActivity.startActivityForResult(this, PERMISSION_REQUEST_CODE, PERMISSIONS);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // finish application if no permission granted.
        if (requestCode == PERMISSION_REQUEST_CODE && resultCode == PermissionsActivity.PERMISSIONS_DENIED) {
            finish();
            return;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar
        // if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_acc_config:
                dlgAccountSetting();
                break;

            case R.id.action_quit:
                Message m = Message.obtain(handler, 0);
                m.sendToTarget();
                break;

            default:
                break;
        }

        return true;
    }

    @Override
    public boolean handleMessage(Message m) {
        if (m.what == 0) {

            mPjSipController.destroy();
            finish();
            Runtime.getRuntime().gc();
            android.os.Process.killProcess(android.os.Process.myPid());

        } else if (m.what == MSG_TYPE.CALL_STATE) {

            CallInfo ci = (CallInfo) m.obj;

	    /* Forward the message to CallActivity */
            if (CallActivity.handler_ != null) {
                Message m2 = Message.obtain(CallActivity.handler_,
                        MSG_TYPE.CALL_STATE, ci);
                m2.sendToTarget();
            }

        } else if (m.what == MSG_TYPE.CALL_MEDIA_STATE) {

	    /* Forward the message to CallActivity */
            if (CallActivity.handler_ != null) {
                Message m2 = Message.obtain(CallActivity.handler_,
                        MSG_TYPE.CALL_MEDIA_STATE,
                        null);
                m2.sendToTarget();
            }

        } else if (m.what == MSG_TYPE.BUDDY_STATE) {

            PjSipBuddy buddy = (PjSipBuddy) m.obj;
            int idx = account.buddyList.indexOf(buddy);

	    /* Update buddy status text, if buddy is valid and
	    * the buddy lists in account and UI are sync-ed.
	    */
            if (idx >= 0 && account.buddyList.size() == buddyList.size()) {
                buddyList.get(idx).put("status", buddy.getStatusText());
                buddyListAdapter.notifyDataSetChanged();
                // TODO: selection color/mark is gone after this,
                //       dont know how to return it back.
                //buddyListView.setSelection(buddyListSelectedIdx);
                //buddyListView.performItemClick(buddyListView,
                //				     buddyListSelectedIdx,
                //				     buddyListView.
                //		    getItemIdAtPosition(buddyListSelectedIdx));

		/* Return back Call activity */
                notifyCallState(currentCall);
            }

        } else if (m.what == MSG_TYPE.REG_STATE) {

            String msg_str = (String) m.obj;
            lastRegStatus = msg_str;

        } else if (m.what == MSG_TYPE.INCOMING_CALL) {

	    /* Incoming call */
            final PjSipCall call = (PjSipCall) m.obj;
            CallOpParam prm = new CallOpParam();

	    /* Only one call at anytime */
            if (currentCall != null) {
		/*
		prm.setStatusCode(pjsip_status_code.PJSIP_SC_BUSY_HERE);
		try {
		call.hangup(prm);
		} catch (Exception e) {}
		*/
                // TODO: set status code
                call.delete();
                return true;
            }

	    /* Answer with ringing */
            prm.setStatusCode(pjsip_status_code.PJSIP_SC_RINGING);
            try {
                call.answer(prm);
            } catch (Exception e) {}

            currentCall = call;
            showCallActivity();

        } else {

	    /* Message not handled */
            return false;

        }

        return true;
    }


    private void dlgAccountSetting() {
        LayoutInflater li = LayoutInflater.from(this);
        View view = li.inflate(R.layout.dlg_account_config, null);

        if (lastRegStatus.length()!=0) {
            TextView tvInfo = (TextView)view.findViewById(R.id.textViewInfo);
            tvInfo.setText("Last status: " + lastRegStatus);
        }

        AlertDialog.Builder adb = new AlertDialog.Builder(this);
        adb.setView(view);
        adb.setTitle(R.string.account_settings_title);

        final EditText etId    = (EditText)view.findViewById(R.id.editTextId);
        final EditText etReg   = (EditText)view.findViewById(R.id.editTextRegistrar);
        final EditText etProxy = (EditText)view.findViewById(R.id.editTextProxy);
        final EditText etUser  = (EditText)view.findViewById(R.id.editTextUsername);
        final EditText etPass  = (EditText)view.findViewById(R.id.editTextPassword);

        etId.   setText(accCfg.getIdUri());
        etReg.  setText(accCfg.getRegConfig().getRegistrarUri());
        StringVector proxies = accCfg.getSipConfig().getProxies();
        if (proxies.size() > 0)
            etProxy.setText(proxies.get(0));
        else
            etProxy.setText("");
        AuthCredInfoVector creds = accCfg.getSipConfig().getAuthCreds();
        if (creds.size() > 0) {
            etUser. setText(creds.get(0).getUsername());
            etPass. setText(creds.get(0).getData());
        } else {
            etUser. setText("");
            etPass. setText("");
        }

        adb.setCancelable(false);
        adb.setPositiveButton(R.string.btn_ok,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,int id) {
                        String acc_id 	 = etId.getText().toString();
                        String registrar = etReg.getText().toString();
                        String proxy 	 = etProxy.getText().toString();
                        String username  = etUser.getText().toString();
                        String password  = etPass.getText().toString();

                        Log.d(TAG, "acc_id:" + acc_id + ", username:" + username);

                        accCfg.setIdUri(acc_id);
                        accCfg.getRegConfig().setRegistrarUri(registrar);
                        AuthCredInfoVector creds = accCfg.getSipConfig().
                                getAuthCreds();
                        creds.clear();
                        if (username.length() != 0) {
                            creds.add(new AuthCredInfo("Digest", "*", username, 0,
                                    password));
                        }
                        StringVector proxies = accCfg.getSipConfig().getProxies();
                        proxies.clear();
                        if (proxy.length() != 0) {
                            proxies.add(proxy);
                        }

		    /* Enable ICE */
                        accCfg.getNatConfig().setIceEnabled(true);

		    /* Finally */
                        lastRegStatus = "";
                        try {
                            account.modify(accCfg);
                        } catch (Exception e) {
                            Log.d(TAG, "modify account exception:" + e.getMessage());
                        }

                        mPjSipController.addAccount(accCfg);
                    }
                }
        );
        adb.setNegativeButton(R.string.btn_cancel,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,int id)
                    {
                        dialog.cancel();
                    }
                }
        );

        AlertDialog ad = adb.create();
        ad.show();
    }


    public void makeCall(View view) {
        if (buddyListSelectedIdx == -1)
            return;

	/* Only one call at anytime */
        if (currentCall != null) {
            return;
        }

        HashMap<String, String> item = (HashMap<String, String>) buddyListView.
                getItemAtPosition(buddyListSelectedIdx);
        String buddy_uri = item.get("uri");

        PjSipCall call = new PjSipCall(account, -1);
        CallOpParam prm = new CallOpParam(true);

        try {
            call.makeCall(buddy_uri, prm);
        } catch (Exception e) {
            call.delete();
            return;
        }

        currentCall = call;
        showCallActivity();
    }

    private void dlgAddEditBuddy(BuddyConfig initial) {
        final BuddyConfig cfg = new BuddyConfig();
        final BuddyConfig old_cfg = initial;
        final boolean is_add = initial == null;

        LayoutInflater li = LayoutInflater.from(this);
        View view = li.inflate(R.layout.dlg_add_buddy, null);

        AlertDialog.Builder adb = new AlertDialog.Builder(this);
        adb.setView(view);

        final EditText etUri  = (EditText)view.findViewById(R.id.editTextUri);
        final CheckBox cbSubs = (CheckBox)view.findViewById(R.id.checkBoxSubscribe);

        if (is_add) {
            adb.setTitle(R.string.add_buddy);
        } else {
            adb.setTitle(R.string.edit_buddy);
            etUri. setText(initial.getUri());
            cbSubs.setChecked(initial.getSubscribe());
        }

        adb.setCancelable(false);
        adb.setPositiveButton(R.string.btn_ok,
                new DialogInterface.OnClickListener()
                {
                    public void onClick(DialogInterface dialog,int id)
                    {
                        cfg.setUri(etUri.getText().toString());
                        cfg.setSubscribe(cbSubs.isChecked());

                        if (is_add) {
                            account.addBuddy(cfg);
                            buddyList.add(putData(cfg.getUri(), ""));
                            buddyListAdapter.notifyDataSetChanged();
                            buddyListSelectedIdx = -1;
                        } else {
                            if (!old_cfg.getUri().equals(cfg.getUri())) {
                                account.delBuddy(buddyListSelectedIdx);
                                account.addBuddy(cfg);
                                buddyList.remove(buddyListSelectedIdx);
                                buddyList.add(putData(cfg.getUri(), ""));
                                buddyListAdapter.notifyDataSetChanged();
                                buddyListSelectedIdx = -1;
                            } else if (old_cfg.getSubscribe() !=
                                    cfg.getSubscribe())
                            {
                                PjSipBuddy bud = account.buddyList.get(
                                        buddyListSelectedIdx);
                                try {
                                    bud.subscribePresence(cfg.getSubscribe());
                                } catch (Exception e) {}
                            }
                        }
                    }
                }
        );
        adb.setNegativeButton(R.string.btn_cancel,
                new DialogInterface.OnClickListener()
                {
                    public void onClick(DialogInterface dialog,int id) {
                        dialog.cancel();
                    }
                }
        );

        AlertDialog ad = adb.create();
        ad.show();
    }

    public void addBuddy(View view)
    {
        dlgAddEditBuddy(null);
    }

    public void editBuddy(View view) {
        if (buddyListSelectedIdx == -1)
            return;

        BuddyConfig old_cfg = account.buddyList.get(buddyListSelectedIdx).cfg;
        dlgAddEditBuddy(old_cfg);
    }

    public void delBuddy(View view) {
        if (buddyListSelectedIdx == -1)
            return;

        final HashMap<String, String> item = (HashMap<String, String>)
                buddyListView.getItemAtPosition(buddyListSelectedIdx);
        String buddy_uri = item.get("uri");

        DialogInterface.OnClickListener ocl =
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case DialogInterface.BUTTON_POSITIVE:
                                account.delBuddy(buddyListSelectedIdx);
                                buddyList.remove(item);
                                buddyListAdapter.notifyDataSetChanged();
                                buddyListSelectedIdx = -1;
                                break;
                            case DialogInterface.BUTTON_NEGATIVE:
                                break;
                        }
                    }
                };

        AlertDialog.Builder adb = new AlertDialog.Builder(this);
        adb.setTitle(buddy_uri);
        adb.setMessage(R.string.del_buddy);
        adb.setPositiveButton(R.string.btn_ok, ocl);
        adb.setNegativeButton(R.string.btn_cancel, ocl);
        adb.show();
    }


    /*
    *
    * As we cannot do UI from worker thread, the callbacks mostly just send
    * a message to UI/main thread.
    */

    public void notifyIncomingCall(PjSipCall call) {
        Message m = Message.obtain(handler, MSG_TYPE.INCOMING_CALL, call);
        m.sendToTarget();
    }

    public void notifyRegState(pjsip_status_code code, String reason,
                               int expiration) {
        String msg_str = "";
        if (expiration == 0)
            msg_str += "Unregistration";
        else
            msg_str += "Registration";

        if (code.swigValue()/100 == 2)
            msg_str += " successful";
        else
            msg_str += " failed: " + reason;

        Message m = Message.obtain(handler, MSG_TYPE.REG_STATE, msg_str);
        m.sendToTarget();
    }

    public void notifyCallState(PjSipCall call) {
        if (currentCall == null || call.getId() != currentCall.getId())
            return;

        CallInfo ci;
        try {
            ci = call.getInfo();
        } catch (Exception e) {
            ci = null;
        }
        Message m = Message.obtain(handler, MSG_TYPE.CALL_STATE, ci);
        m.sendToTarget();

        if (ci != null &&
                ci.getState() == pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED)
        {
            currentCall = null;
        }
    }

    public void notifyCallMediaState(PjSipCall call) {
        Message m = Message.obtain(handler, MSG_TYPE.CALL_MEDIA_STATE, null);
        m.sendToTarget();
    }

    public void notifyBuddyState(PjSipBuddy buddy) {
        Message m = Message.obtain(handler, MSG_TYPE.BUDDY_STATE, buddy);
        m.sendToTarget();
    }

    /* === end of MyAppObserver ==== */

}
