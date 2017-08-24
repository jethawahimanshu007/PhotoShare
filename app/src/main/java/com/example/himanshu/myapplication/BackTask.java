package com.example.himanshu.myapplication;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.util.Log;
import org.apache.commons.net.util.SubnetUtils;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Himanshu on 7/29/2017.
 */

public class BackTask extends AsyncTask<Context,Void,Void> {

    //static String IP_ADDRESS="10.106.71.212";
    String TAG="BackTask";
    String NOMAC="00:00:00:00:00:00";
    int TIMEOUT_SCAN = 3600; // seconds
    int TIMEOUT_SHUTDOWN = 10; // seconds
    long ip=0;
    long start = 0;
    long end = 0;
    int pt_move = 2;
    long size=end-start+1;
    ExecutorService mPool;
    ExecutorService multiConnectPool;
    private WifiInfo info;
    public int speed = 0;
    public String ssid = null;
    public String bssid = null;
    public String carrier = null;
    public String macAddress = NOMAC;
    public String netmaskIp = "0.0.0.0";
    public String gatewayIp = "0.0.0.0";
    SQLiteDatabase mydatabase;


    protected Void doInBackground(Context... urls) {

        ConstantsClass.IpAddresses=new ArrayList<String>();
        getWifiInfo(urls[0]);
        executeCode();
        Context context=urls[0];
        mydatabase = context.openOrCreateDatabase(Constants.DATABASE_NAME, context.MODE_PRIVATE, null);
        Log.d("BackTask","Total addresses to try connection for:"+ConstantsClass.IpAddresses.size());
        multiConnectPool= Executors.newFixedThreadPool(10);

        for(String ip_address:ConstantsClass.IpAddresses)
        {
            try {
                //mPool.execute(new CheckRunnable(getIpFromLongUnsigned(i)));
                multiConnectPool.execute(new ConnectRunnable(ip_address));
                System.out.println("Trying to connect to "+ip_address+"...");

            }
            catch(Exception e)
            {
                Log.d(TAG,"Exception in connection:"+e);
            }
        }

     return null;
    }

    private static void writeMessages(SQLiteDatabase mydatabase,Socket sock) {
        Cursor allMessages = mydatabase.rawQuery("SELECT imagePath,fileName,size from MESSAGE_TBL", null);

        //Send total number of messages
        int totalNumberOfMessages = allMessages.getCount();
        byte totalNoByteArray[] = ByteBuffer.allocate(4).putInt(totalNumberOfMessages).array();
        Log.d("BackTask", "Sending totalNoOfMessages:" + totalNumberOfMessages);
        writeByteArray(totalNoByteArray,sock);

        ///Write preamble size, preamble and message
        while (allMessages.moveToNext()) {
            //Write preamble size and preamble first

            String filePath = allMessages.getString(0);
            String fileName = allMessages.getString(1);
            int size = allMessages.getInt(2);

            File myFile = new File(filePath);
            byte[] mybytearray = new byte[(int) myFile.length()];
            size = mybytearray.length;
            Preamble preamble = new Preamble(size, fileName);
            try {
                byte preambleBytes[] = Serializer.serialize(preamble);

                //Write size of preamble byte array
                byte preambleBytesSize[] = ByteBuffer.allocate(4).putInt(preambleBytes.length).array();
                writeByteArray(preambleBytesSize,sock);

                //Write preamble
                writeByteArray(preambleBytes,sock);

                //Write message
                FileInputStream fis = new FileInputStream(myFile);
                BufferedInputStream bis = new BufferedInputStream(fis);
                bis.read(mybytearray, 0, mybytearray.length);
                writeByteArray(mybytearray,sock);
            } catch (Exception e) {
                Log.d("BackTask", "Exception occured");
            }
        }
    }
    public static void writeByteArray(byte[] bytes,Socket sock)
    {
        try {
            Log.d("BackTask","Writing number of bytes:"+bytes.length);
            OutputStream os = sock.getOutputStream();
            System.out.println("Sending...");
            os.write(bytes, 0, bytes.length);
            os.flush();
            Log.d("BackTask","Sent");
        }
        catch(Exception e)
        {
            Log.d("BackTask","Exception:"+e);
        }
    }

    public void executeCode()
    {

        int THREADS = 10;
        mPool= Executors.newFixedThreadPool(THREADS);
        Log.v(TAG, "start=" + getIpFromLongUnsigned(start) + " (" + start
                + "), end=" + getIpFromLongUnsigned(end) + " (" + end
                + "), length=" + size);

        if (ip <= end && ip >= start) {
            Log.i(TAG, "Back and forth scanning");
            // gateway
            launch(start);

            // hosts
            long pt_backward = ip;
            long pt_forward = ip + 1;
            long size_hosts = size - 1;

            for (int i = 0; i < size_hosts; i++) {
                // Set pointer if of limits
                if (pt_backward <= start) {
                    pt_move = 2;
                } else if (pt_forward > end) {
                    pt_move = 1;
                }
                // Move back and forth
                if (pt_move == 1) {
                    launch(pt_backward);
                    pt_backward--;
                    pt_move = 2;
                } else if (pt_move == 2) {
                    launch(pt_forward);
                    pt_forward++;
                    pt_move = 1;
                }
            }

        }
        else {
            Log.i(TAG, "Sequential scanning");
            for (long i = start; i <= end; i++) {
                launch(i);
            }
        }
        mPool.shutdown();
        try {
            if(!mPool.awaitTermination(TIMEOUT_SCAN, TimeUnit.SECONDS)){
                mPool.shutdownNow();
                Log.e(TAG, "Shutting down pool");
                if(!mPool.awaitTermination(TIMEOUT_SHUTDOWN, TimeUnit.SECONDS)){
                    Log.e(TAG, "Pool did not terminate");
                }
            }
        } catch (InterruptedException e){
            Log.e(TAG, e.getMessage());
            mPool.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
        }

    }
    public static String getIpFromLongUnsigned(long ip_long) {
        String ip = "";
        for (int k = 3; k > -1; k--) {
            ip = ip +
                    ((ip_long >> k * 8) & 0xFF) + ".";
        }
        return ip.substring(0, ip.length() - 1);
    }

    class CheckRunnable implements Runnable {
        private String addr;


        CheckRunnable(String addr) {
            this.addr = addr;
        }
        public void run() {
            try{

                InetAddress h = InetAddress.getByName(addr);
                String hardwareAddress=new String();
                // Arp Check #1
                hardwareAddress = getHardwareAddress(addr);
                if(!NOMAC.equals(hardwareAddress)) {
                    //Log.d(TAG, "found using arp #1 " + addr);
                    if(!ConstantsClass.IpAddresses.contains(addr))
                    ConstantsClass.IpAddresses.add(addr);
                    return;
                }

                // Native InetAddress check
                if (h.isReachable(200)) {
                    //Log.d(TAG, "found using InetAddress ping "+addr);
                    if(!ConstantsClass.IpAddresses.contains(addr))
                        ConstantsClass.IpAddresses.add(addr);
                    return;
                }

                // Arp Check #2
                hardwareAddress = getHardwareAddress(addr);
                if(!NOMAC.equals(hardwareAddress)){
                    //Log.d(TAG, "found using arp #2 "+addr);
                    if(!ConstantsClass.IpAddresses.contains(addr))
                        ConstantsClass.IpAddresses.add(addr);
                    return;
                }

                // Arp Check #3
                hardwareAddress = getHardwareAddress(addr);
                if(!NOMAC.equals(hardwareAddress)){
                    //Log.d(TAG, "found using arp #2 "+addr);
                    if(!ConstantsClass.IpAddresses.contains(addr))
                        ConstantsClass.IpAddresses.add(addr);
                    return;
                }
            }
            catch(Exception e)
            {
                Log.d("","Exception occured:"+e);
            }
        }
    }
    private void launch(long i) {
        if(!mPool.isShutdown()) {
            mPool.execute(new CheckRunnable(getIpFromLongUnsigned(i)));
        }
    }
    public String getHardwareAddress(String ip) {
        String hw = "00:00:00:00:00:00";
        BufferedReader bufferedReader = null;
        String MAC_RE = "^%s\\s+0x1\\s+0x2\\s+([:0-9a-fA-F]+)\\s+\\*\\s+\\w+$";
        int BUF = 8 * 1024;
        try {
            if (ip != null) {
                String ptrn = String.format(MAC_RE, ip.replace(".", "\\."));
                Pattern pattern = Pattern.compile(ptrn);
                bufferedReader = new BufferedReader(new FileReader("/proc/net/arp"), BUF);
                String line;
                Matcher matcher;
                while ((line = bufferedReader.readLine()) != null) {
                    matcher = pattern.matcher(line);
                    if (matcher.matches()) {
                        hw = matcher.group(1);
                        break;
                    }
                }
            } else {
                Log.e(TAG, "ip is null");
            }
        } catch (IOException e) {
            Log.e(TAG, "Can't open/read file ARP: " + e.getMessage());
            return hw;
        } finally {
            try {
                if(bufferedReader != null) {
                    bufferedReader.close();
                }
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            }
        }
        return hw;
    }

    public boolean getWifiInfo(Context context) {
        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (wifi != null) {
            info = wifi.getConnectionInfo();
            // Set wifi variables
            speed = info.getLinkSpeed();
            ssid = info.getSSID();
            bssid = info.getBSSID();
            macAddress = info.getMacAddress();

            gatewayIp = getIpFromIntSigned(wifi.getDhcpInfo().gateway);
            // broadcastIp = getIpFromIntSigned((dhcp.ipAddress & dhcp.netmask)
            // | ~dhcp.netmask);
            netmaskIp = getIpFromIntSigned(wifi.getDhcpInfo().netmask);
            SubnetUtils.SubnetInfo subnetInfo=new SubnetUtils(gatewayIp,netmaskIp).getInfo();
            ip=getUnsignedLongFromIp(gatewayIp);
            start=getUnsignedLongFromIp(subnetInfo.getLowAddress());
            end=getUnsignedLongFromIp(subnetInfo.getHighAddress());
            size=end-start+1;
            Log.d(TAG,"Start and end ips are:"+getIpFromLongUnsigned(start)+" and "+getIpFromLongUnsigned(end));

            return true;
        }
        return false;
    }

    private String getInterfaceFirstIp(NetworkInterface ni) {
        if (ni != null) {
            for (Enumeration<InetAddress> nis = ni.getInetAddresses(); nis.hasMoreElements();) {
                InetAddress ia = nis.nextElement();
                if (!ia.isLoopbackAddress()) {
                    if (ia instanceof Inet6Address) {
                        Log.i(TAG, "IPv6 detected and not supported yet!");
                        continue;
                    }
                    return ia.getHostAddress();
                }
            }
        }
        return "0.0.0.0";
    }

    public long getUnsignedLongFromIp(String ip_addr) {
        String[] a = ip_addr.split("\\.");
        return (Integer.parseInt(a[0]) * 16777216 + Integer.parseInt(a[1]) * 65536
                + Integer.parseInt(a[2]) * 256 + Integer.parseInt(a[3]));
    }
    int IpToCidr(String ip) {
        double sum = -2;
        String[] part = ip.split("\\.");
        for (String p : part) {
            sum += 256D - Double.parseDouble(p);
        }
        return 32 - (int) (Math.log(sum) / Math.log(2d));
    }
    public static String getIpFromIntSigned(int ip_int) {
        String ip = "";
        for (int k = 0; k < 4; k++) {
            ip = ip + ((ip_int >> k * 8) & 0xFF) + ".";
        }
        return ip.substring(0, ip.length() - 1);
    }

    class ConnectRunnable implements Runnable {
        private String addr;

        ConnectRunnable(String addr) {
            this.addr = addr;
        }

        public void run() {
            try {
                Socket sock = new Socket(addr, 1149);
                writeMessages(mydatabase, sock);
            }
            catch(Exception e)
            {
                Log.d(TAG,"Exception:"+e);
            }
        }
    }

}

class Preamble implements Serializable
{
    int dataSize;
    String dataFileName;
    static final long serialVersionUID=40L;
    Preamble(int size, String fileName)
    {
        dataSize=size;dataFileName=fileName;
    }
}


