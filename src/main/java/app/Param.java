package app;

import pkt.java.CompressMode;
import share.util.FileUtil;

public class Param
{
    //----------------------------------------------------------------------
    // about config file
    //----------------------------------------------------------------------
	static public String MAIN_CONFIG_FILE = "./conf/main_config.xml";
	static public String ENVIRONMENT_CONFIG_FILE = "./conf/env_config.xml";
	static public String SYMBOLINFO_CONFIG_FILE = "./conf/SymbolInfo.xml";
    
    //----------------------------------------------------------------------
    // about channel
    //----------------------------------------------------------------------
    public static final int MIN_CHANNEL = 0;
    public static final int MAX_CHANNEL = 99;
    public static final int DEFAULT_CHANNEL = 0;
    
  //----------------------------------------------------------------------
    // about fieldset
    //----------------------------------------------------------------------
    public static final int NO_FIELDSET = 0;
    
    //----------------------------------------------------------------------
    // about CA
    //----------------------------------------------------------------------
    public static final int CA_BUF_MAX_NOT_COMMIT_COUNT = 10;
    
    //----------------------------------------------------------------------
    // about Send Buffer
    //----------------------------------------------------------------------
    public static final boolean LIMIT_SEND_BUFFER_SIZE = true;//控制 send buffer 是否要有限制大小
    															 // 注意，若設定成 false，表示不控制大小，將會 memory 持續長，所以以後要調回來！！！
    public static final int PACKET_ALLOW_LIFE = 10 * 1000;//要從 send buffer 移除 packet 之前，至少讓 packet 保留在 send buffer 的最短時間, unit: ms
    																//若無此參數，當快速產生封包時，很容易將 send buffer 塞爆而 drop packet
    public static final int SEND_BUFFER_MAX_LIMIT = 10 * 1024 * 1024;//最大最大給一個 user 的 send buffer size，因為會有 PACKET_ALLOW_LIFE 的時間，但又怕這段時間累積過大的 packet

    //----------------------------------------------------------------------
	// for ThreadPool Executor
    //----------------------------------------------------------------------
    public final static int CONNECTION_TIMER_CHECK_PERIOD = 3 * 1000;
    
    //----------------------------------------------------------------------
    // for Packet Encoding
    //----------------------------------------------------------------------
	public final static CompressMode COMPRESS_MODE = CompressMode.NoCompress;
	
    //----------------------------------------------------------------------
    // for InterfacePacket
    //----------------------------------------------------------------------
	public final static int sm_nPacketNoMax = 255; 
	public final static boolean sm_bDropPacketWhenPacketNoExceed = false;//當 p_no 過大時，是否要 drop packet

	public final static int sm_nServerVersion = 1;

	//----------------------------------------------------------------------
	// for Ram Disk Log
	//----------------------------------------------------------------------
	public final static boolean USE_RAM = true;// 是否使用 ram dir.
	public final static String RAM_DIR = "fdtapp"; // log 在 ram directory 擺放的目錄

	public final static String sm_sLogDir = "log"; // log directory
	public final static String sm_sLogLockFile = "log.lock"; // lock file
	public final static int sm_nLogMoverCheckInterval = 1 * 1000; // 1 sec
	public final static int sm_nLogReservedDay = 14;
	public final static int sm_nLogRemoverCheckInterval = 3;
	
	//-----------------------------------------------------------------------
	// for RSA
	//-----------------------------------------------------------------------
	public final static byte[] sm_RSAcert = FileUtil.loadBytes("./conf/RSA/public_key.der");
	
	//-----------------------------------------------------------------------
	// for System Limit
	//-----------------------------------------------------------------------
	public static double sm_CpuLimit = 0.98;
	public static double sm_MemLimit = 0.95;
}
