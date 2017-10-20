package pjsipapp.com.firebird.pjsip;

import android.content.Context;
import android.util.Log;

import org.pjsip.pjsua2.AccountConfig;
import org.pjsip.pjsua2.BuddyConfig;
import org.pjsip.pjsua2.ContainerNode;
import org.pjsip.pjsua2.Endpoint;
import org.pjsip.pjsua2.EpConfig;
import org.pjsip.pjsua2.JsonDocument;
import org.pjsip.pjsua2.LogConfig;
import org.pjsip.pjsua2.StringVector;
import org.pjsip.pjsua2.TransportConfig;
import org.pjsip.pjsua2.UaConfig;
import org.pjsip.pjsua2.pj_log_decoration;
import org.pjsip.pjsua2.pjsip_status_code;
import org.pjsip.pjsua2.pjsip_transport_type_e;

import java.io.File;
import java.util.ArrayList;

/**
 * Created by liyong on 17-7-20.
 */

public class PjSipController {
    private static final String TAG = "PjSipController";


    static {
        // video is not supported right now...
        /*try{
            System.loadLibrary("openh264");
            // Ticket #1937: libyuv is now included as static lib
            //System.loadLibrary("yuv");
        } catch (UnsatisfiedLinkError e) {
            System.out.println("UnsatisfiedLinkError: " + e.getMessage());
            System.out.println("This could be safely ignored if you " +
                    "don't need video.");
        }*/
        System.loadLibrary("pjsua2");
        System.out.println("Library pjsua2 loaded");
    }

    /* Interface to separate UI & engine a bit better */
    public interface PjSipListener {
        abstract void notifyRegState(pjsip_status_code code, String reason,
                                     int expiration);
        abstract void notifyIncomingCall(PjSipCall call);
        abstract void notifyCallState(PjSipCall call);
        abstract void notifyCallMediaState(PjSipCall call);
        abstract void notifyBuddyState(PjSipBuddy buddy);
    }

    private static PjSipController sInstance = null;

    private static final String CONFIG_FILE_NAME = "pjsua2.json";
    private final int DEFAULT_SIP_PORT = 6000;
    private final int LOG_LEVEL = 4;

    private String mConfigFilePath;

    // for what?
    public Endpoint mEndPoint = new Endpoint();

    private TransportConfig mSipTpConfig = new TransportConfig();
    private EpConfig mEpConfig = new EpConfig();

    /* Maintain reference to log writer to avoid premature cleanup by GC */
    private PjSipLogWriter mLogWriter;

    private ArrayList<PjSipAccountConfig> mAccountConfigs =
            new ArrayList<PjSipAccountConfig>();

    public ArrayList<PjSipAccount> mAccountList = new ArrayList<PjSipAccount>();

    public synchronized static PjSipController getInstance(Context context) {
        if (sInstance == null && context != null) {
            sInstance = new PjSipController(context);
        }

        return sInstance;
    }

    public PjSipController(Context context) {
        mConfigFilePath = context.getFilesDir().getAbsolutePath();
    }

    public ArrayList<PjSipAccount> getAccountList() {
        return mAccountList;
    }

    public Endpoint getEndPoint() {
        return mEndPoint;
    }

    public void init() {
        try {
            mEndPoint.libCreate();
        } catch (Exception e) {
            e.printStackTrace();
        }

	    /* Load config */
        String configPath = mConfigFilePath + "/" + CONFIG_FILE_NAME;
        File f = new File(configPath);
        if (f.exists()) {
            loadConfig(configPath);
        } else {
            /* Set 'default' values */
            mSipTpConfig.setPort(DEFAULT_SIP_PORT);
        }

	    /* Override log level setting */
        mEpConfig.getLogConfig().setLevel(LOG_LEVEL);
        mEpConfig.getLogConfig().setConsoleLevel(LOG_LEVEL);

	    /* Set log config. */
        LogConfig log_cfg = mEpConfig.getLogConfig();
        mLogWriter = new PjSipLogWriter();
        log_cfg.setWriter(mLogWriter);
        log_cfg.setDecor(log_cfg.getDecor() &
                ~(pj_log_decoration.PJ_LOG_HAS_CR.swigValue() |
                        pj_log_decoration.PJ_LOG_HAS_NEWLINE.swigValue()));

	    /* Set ua config. */
        UaConfig ua_cfg = mEpConfig.getUaConfig();
        ua_cfg.setUserAgent("Pjsua2 Android " + mEndPoint.libVersion().getFull());
        StringVector stun_servers = new StringVector();
        stun_servers.add("stun.pjsip.org");
        ua_cfg.setStunServer(stun_servers);
        if (/*own_worker_thread*/false) {
            ua_cfg.setThreadCnt(0);
            ua_cfg.setMainThreadOnly(true);
        }

	    /* Init endpoint */
        try {
            mEndPoint.libInit(mEpConfig);
        } catch (Exception e) {
            return;
        }

	    /* Create transports. */
        try {
            mEndPoint.transportCreate(pjsip_transport_type_e.PJSIP_TRANSPORT_UDP,
                    mSipTpConfig);
        } catch (Exception e) {
            System.out.println(e);
        }

        try {
            mEndPoint.transportCreate(pjsip_transport_type_e.PJSIP_TRANSPORT_TCP,
                    mSipTpConfig);
        } catch (Exception e) {
            System.out.println(e);
        }

        try {
            mSipTpConfig.setPort(DEFAULT_SIP_PORT + 1);
            mEndPoint.transportCreate(pjsip_transport_type_e.PJSIP_TRANSPORT_TLS,
                    mSipTpConfig);
        } catch (Exception e) {
            System.out.println(e);
        }

        /* Set SIP port back to default for JSON saved config */
        mSipTpConfig.setPort(DEFAULT_SIP_PORT);

	    /* Create accounts. */
        for (int i = 0; i < mAccountConfigs.size(); i++) {
            PjSipAccountConfig my_cfg = mAccountConfigs.get(i);

	        /* Customize account config */
            my_cfg.accCfg.getNatConfig().setIceEnabled(true);
            my_cfg.accCfg.getVideoConfig().setAutoTransmitOutgoing(true);
            my_cfg.accCfg.getVideoConfig().setAutoShowIncoming(true);

            PjSipAccount acc = addAccount(my_cfg.accCfg);
            if (acc == null)
                continue;

	        /* Add Buddies */
            for (int j = 0; j < my_cfg.buddyCfgs.size(); j++) {
                BuddyConfig bud_cfg = my_cfg.buddyCfgs.get(j);
                acc.addBuddy(bud_cfg);
            }
        }

	    /* Start. */
        try {
            mEndPoint.libStart();
        } catch (Exception e) {
            return;
        }
    }

    public PjSipAccount addAccount(AccountConfig cfg) {
        PjSipAccount acc = new PjSipAccount(cfg);
        try {
            acc.create(cfg);
        } catch (Exception e) {
            Log.d(TAG, "exception when create account!");
            e.printStackTrace();
            return null;
        }

        mAccountList.add(acc);

        String configPath = mConfigFilePath + "/" + CONFIG_FILE_NAME;
        saveConfig(configPath);

        return acc;
    }

    public void removeAccount(PjSipAccount acc)
    {
        mAccountList.remove(acc);
    }

    private void loadConfig(String filename) {
        JsonDocument json = new JsonDocument();

        try {
            /* Load file */
            json.loadFile(filename);
            ContainerNode root = json.getRootContainer();

	        /* Read endpoint config */
            mEpConfig.readObject(root);

	        /* Read transport config */
            ContainerNode tp_node = root.readContainer("SipTransport");
            mSipTpConfig.readObject(tp_node);

	        /* Read account configs */
            mAccountConfigs.clear();
            ContainerNode containerNode = root.readArray("accounts");
            while (containerNode.hasUnread()) {
                PjSipAccountConfig accountConfig = new PjSipAccountConfig();
                accountConfig.readObject(containerNode);
                mAccountConfigs.add(accountConfig);
            }
        } catch (Exception e) {
            System.out.println(e);
        }

	    /* Force delete json now, as I found that Java somehow destroys it
	     * after lib has been destroyed and from non-registered thread.
	     */
        json.delete();
    }

    public void destroy()
    {
        String configPath = mConfigFilePath + "/" + CONFIG_FILE_NAME;
        saveConfig(configPath);

	/* Try force GC to avoid late destroy of PJ objects as they should be
	* deleted before lib is destroyed.
	*/
        Runtime.getRuntime().gc();

	/* Shutdown pjsua. Note that Endpoint destructor will also invoke
	* libDestroy(), so this will be a test of double libDestroy().
	*/
        try {
            mEndPoint.libDestroy();
        } catch (Exception e) {}

	/* Force delete Endpoint here, to avoid deletion from a non-
	* registered thread (by GC?).
	*/
        mEndPoint.delete();
        mEndPoint = null;

        sInstance = null;
    }

    private void buildAccConfigs()
    {
	/* Sync accCfgs from mAccountList */
        mAccountConfigs.clear();
        for (int i = 0; i < mAccountList.size(); i++) {
            PjSipAccount acc = mAccountList.get(i);
            PjSipAccountConfig my_acc_cfg = new PjSipAccountConfig();
            my_acc_cfg.accCfg = acc.cfg;

            my_acc_cfg.buddyCfgs.clear();
            for (int j = 0; j < acc.buddyList.size(); j++) {
                PjSipBuddy bud = acc.buddyList.get(j);
                my_acc_cfg.buddyCfgs.add(bud.cfg);
            }

            mAccountConfigs.add(my_acc_cfg);
        }
    }

    private void saveConfig(String filename)
    {
        JsonDocument json = new JsonDocument();


        try {
	    /* Write endpoint config */
            json.writeObject(mEpConfig);

	    /* Write transport config */
            ContainerNode tp_node = json.writeNewContainer("SipTransport");
            mSipTpConfig.writeObject(tp_node);

	    /* Write account configs */
            buildAccConfigs();
            ContainerNode accs_node = json.writeNewArray("accounts");
            for (int i = 0; i < mAccountConfigs.size(); i++) {
                mAccountConfigs.get(i).writeObject(accs_node);
            }

	    /* Save file */
	        Log.d(TAG, "saveConfig:" + json.readString());
            json.saveFile(filename);
        } catch (Exception e) {}

	/* Force delete json now, as I found that Java somehow destroys it
	* after lib has been destroyed and from non-registered thread.
	*/
        json.delete();
    }
}
